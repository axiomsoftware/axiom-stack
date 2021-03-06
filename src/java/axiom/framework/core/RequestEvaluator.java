/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile: RequestEvaluator.java,v $
 * $Author: hannes $
 * $Revision: 1.151 $
 * $Date: 2006/05/18 20:54:08 $
 */

/* 
 * Modified by:
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */

package axiom.framework.core;


import java.lang.reflect.*;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.apache.xmlrpc.XmlRpcRequestProcessor;
import org.apache.xmlrpc.XmlRpcServerRequest;
import org.mozilla.javascript.Undefined;

import axiom.framework.*;
import axiom.objectmodel.*;
import axiom.objectmodel.db.*;
import axiom.scripting.*;
import axiom.scripting.rhino.*;

/**
 * This class does the work for incoming requests. It holds a transactor thread
 * and an EcmaScript evaluator to get the work done. Incoming threads are
 * blocked until the request has been serviced by the evaluator, or the timeout
 * specified by the application has passed. In the latter case, the evaluator thread
 * is killed and an error message is returned.
 */
public final class RequestEvaluator implements Runnable {
    static final int NONE = 0; // no request
    static final int HTTP = 1; // via HTTP gateway
    static final int XMLRPC = 2; // via XML-RPC
    static final int INTERNAL = 3; // generic function call, e.g. by scheduler
    static final int EXTERNAL = 4; // function from script etc

    public static final Object[] EMPTY_ARGS = new Object[0];

    public final Application app;

    protected ScriptingEngine scriptingEngine;

    // skin depth counter, used to avoid recursive skin rendering
    protected int skinDepth;

    private volatile RequestTrans req;
    private volatile ResponseTrans res;

    // the one and only transactor thread
    private volatile Transactor rtx;

    // the type of request to be serviced,
    // used to coordinate worker and waiter threads
    private volatile int reqtype;

    // the object on which to invoke a function, if specified
    private Object thisObject;

    // the method to be executed
    private String functionName;

    // the session object associated with the current request
    private Session session;

    // arguments passed to the function
    private Object[] args;

    // the result of the operation
    private Object rgl;

    // the exception thrown by the evaluator, if any.
    private Exception exception;

    // the mode which this request evaluator operates on, either live mode or preview mode, defaults to live
    private int layer = DbKey.LIVE_LAYER;
    
    // the resource refresh action that is used to refresh the application's resources
    private String refreshAction = null;
    
    static final String PREVIEW_URL = "preview"; 
    static final String MODE_REQ_PARAM = "mode";

    // added as private element for omitXmlDeclaration
    private Object currentElement;

    
    /**
     *  Create a new RequestEvaluator for this application.
     */
    public RequestEvaluator(Application app) {
        this.app = app;
        refreshAction = this.app.getProperty("refreshAction");
        if (refreshAction == null) {
            refreshAction = "RefreshResources";
        }
    }

    protected void initScriptingEngine() {
        if (scriptingEngine == null) {
            String engineClassName = app.getProperty("scriptingEngine",
                                                     "axiom.scripting.rhino.RhinoEngine");
            try {
                Class clazz = app.getClassLoader().loadClass(engineClassName);

                scriptingEngine = (ScriptingEngine) clazz.newInstance();
                scriptingEngine.init(app, this);
            } catch (Exception x) {
                Throwable t = x;

                if (x instanceof InvocationTargetException) {
                    t = ((InvocationTargetException) x).getTargetException();
                }

                app.logEvent("******************************************");
                app.logEvent("*** Error creating scripting engine: ");
                app.logEvent("*** " + t.toString());
                app.logEvent("******************************************");
                app.logError("Error creating scripting engine", t);

                // rethrow exception
                if (t instanceof RuntimeException) {
                    throw((RuntimeException) t);
                } else {
                    throw new RuntimeException(t.toString());
                }
            }
        }
    }
    
    public int getLayer() {
        return this.layer;
    }

    /**
     *
     */
    public void run() {
        // first, set a local variable to the current transactor thread so we know
        // when it's time to quit because another thread took over.
        Transactor localrtx = (Transactor) Thread.currentThread();

        // spans whole execution loop - close connections in finally clause
        try {

            // while this thread is serving requests
            while (localrtx == rtx) {
                int defmode = this.layer;

                // Get req and res into local variables to avoid memory caching problems
                // in unsynchronized method.
                RequestTrans req = getRequest();
                ResponseTrans res = getResponse();
                
                HttpServletRequest httprequest = req.getServletRequest();
                String reqServer = httprequest != null ? httprequest.getServerName() : null;
                this.layer = app.getLayer(reqServer);
                
                int tries = 0;
                boolean done = false;
                String error = null;

                while (!done && localrtx == rtx) {
                    // catch errors in path resolution and script execution
                    try {

                        // initialize scripting engine
                        initScriptingEngine();
                        app.setCurrentRequestEvaluator(this);
                        // update scripting prototypes
                        scriptingEngine.updatePrototypes();

                        String rpath = req.getPath();
                        if (rpath != null && this.refreshAction.equals(rpath)) {
                            boolean isAdmin;
                            try {
                                ActionSecurityManager.checkAdmin(app);
                                isAdmin = true;
                            } catch (Exception ex) {
                                isAdmin = false;
                            }
                            
                            if (isAdmin) {
                               this.app.updateResources();
                            } else {
                                throw new FrameworkException("Action not found");
                            }
                            
                            done = true;
                            break;
                        }

                        // avoid going into transaction if called function doesn't exist.
                        // this only works for the (common) case that method is a plain
                        // method name, not an obj.method path
                        if (reqtype == INTERNAL) {
                            // if object is an instance of NodeHandle, get the node object itself.
                            if (thisObject instanceof NodeHandle) {
                                thisObject = ((NodeHandle) thisObject).getNode(app.nmgr.safe);
                                // If no valid node object return immediately
                                if (thisObject == null) {
                                    done = true;
                                    reqtype = NONE;
                                    break;
                                }
                            }
                            // If function doesn't exist, return immediately
                            if (functionName.indexOf('.') < 0 &&
                                    !scriptingEngine.hasFunction(thisObject, functionName)) {
                            	done = true;
                                reqtype = NONE;
                                break;
                            }
                        }

                        // Transaction name is used for logging etc.
                        StringBuffer txname = new StringBuffer(app.getName());
                        txname.append(":").append(req.getMethod().toLowerCase()).append(":");
                        txname.append((error == null) ? req.getPath() : "error");

                        // begin transaction
                        localrtx.begin(txname.toString(), this.layer);
                        
                        Object root = app.getDataRoot();
                        initGlobals(root);
                        
                        this.session.touch();
                        
                        String action = null;

                        if (error != null) {
                            res.setError(error);
                        }

                        switch (reqtype) {
                            case HTTP:
                                // bring over the message from a redirect
                                session.recoverResponseMessages(res);

                                // catch redirect in path resolution or script execution
                                try {
                                    // catch object not found in path resolution
                                    try {
                                        if (error != null) {
                                    		// there was an error in the previous loop, call error handler
                                        	action = null;
                                            // Unauthorized 
                                        	if (res.getStatus() == 401) { 
                                        		String errorAction = app.props.getProperty("unauthorized", "unauthorized");
                                        		action = getAction(currentElement, errorAction, req);
                                        	} 
                                        	
                                        	if (action == null){
                                        		currentElement = root;
                                        		
                                        		// do not reset the requestPath so error handler can use the original one
                                        		// get error handler action
                                        		String errorAction = app.props.getProperty("error", "error");
                                        		
                                        		action = getAction(currentElement, errorAction, req);
                                        		if (action == null) {
                                        			throw new RuntimeException(error);
                                        		}
                                        	}
                                        } else if ((req.getPath() == null) || "".equals(req.getPath().trim())) {
                                            currentElement = root;

                                            action = getAction(currentElement, null, req);

                                            if (action == null) {
                                                throw new FrameworkException("Action not found");
                                            }
                                        } else {

                                            // march down request path...	
                                            StringTokenizer st = new StringTokenizer(req.getPath(), "/");
                                            int ntokens = st.countTokens();

                                            // limit path to < 50 tokens
                                            if (ntokens > 50) {
                                                throw new RuntimeException("Path too long");
                                            }

                                            String[] pathItems = new String[ntokens];

                                            for (int i = 0; i < ntokens; i++)
                                                pathItems[i] = st.nextToken();

                                            currentElement = root;
                                            for (int i = 0; i < ntokens; i++) {
                                            	if (currentElement == null) {
                                                    throw new FrameworkException("Object not found.");
                                                }
                                                if (pathItems[i].length() == 0) {
                                                    continue;
                                                }

                                                // if we're at the last element of the path,
                                                // try to interpret it as action name.
                                                if (i == (ntokens - 1)) {
                                                    action = getAction(currentElement, pathItems[i], req);
                                                }

                                                if (action == null) {
                                                    currentElement = getChildElement(currentElement,
                                                            pathItems[i]);
                                                }
                                            }
                                            
                                            if (currentElement == null) {
                                                throw new FrameworkException("Object not found.");
                                            }

                                            if (action == null) {
                                                action = getAction(currentElement, null, req);
                                            }
                                            
                                            if (action == null) {
                                                throw new FrameworkException("Action not found");
                                            }
                                        }
                                        
                                    } catch (FrameworkException notfound) {
                                        if (error != null) {
                                            // we already have an error and the error template wasn't found,
                                            // display it instead of notfound message
                                            throw new RuntimeException();
                                        }

                                        // The path could not be resolved. Check if there is a "not found" action
                                        // specified in the property file.
                                        res.setStatus(404);

                                        String notFoundAction = app.props.getProperty("notfound", "notfound");

                                        currentElement = root;
                                        action = getAction(currentElement, notFoundAction, req);

                                        if (action == null) {
                                            throw new FrameworkException(notfound.getMessage());
                                        }
                                    }
                                    
                                    // Not so fast.  Security Check! 
                                    if (currentElement instanceof Node) {
                                        String act = action;
                                        try {
                                        	this.app.isAllowed(currentElement, act, 
                                        			(RhinoEngine) this.scriptingEngine, this);
                                        } catch (RuntimeException re) {
                                        	// save url where request came from into the session
                                        	HttpServletRequest httpServletRequest = req.getServletRequest();
                                        	String url = "http://" + httpServletRequest.getHeader("Host") 
                                                                   + httpServletRequest.getContextPath() 
                                                                   + httpServletRequest.getPathInfo(); 
                                            session.setHttpReferer(url);
                                        	throw re;
                                        }
                                    }

                                    /////////////////////////////////////////////////////////////////////////////
                                    // end of path resolution section
                                    /////////////////////////////////////////////////////////////////////////////
                                    // beginning of execution section

                                    // set the req.action property, cutting off the _action suffix
                                    req.setAction(action);

                                    // reset skin recursion detection counter
                                    skinDepth = 0;

                                    // try calling onRequest() function on object before
                                    // calling the actual action
                                    // andy play here instance of
                                    
                                    /*if (mode == DbKeyWithModes.DRAFT_MODE) {
                                    	if(currentElement instanceof Node) {
                                    		currentElement = ((Node)currentElement).getPreviewNode();
                                    	}
                                    }*/
                                    
                                    scriptingEngine.invoke(currentElement,
                                            "onRequest",
                                            EMPTY_ARGS,
                                            ScriptingEngine.ARGS_WRAP_DEFAULT,
                                            false);
                                    
                                    // reset skin recursion detection counter
                                    skinDepth = 0;

                                    // do the actual action invocation
                                    if (req.isXmlRpc()) {
                                        XmlRpcRequestProcessor xreqproc = new XmlRpcRequestProcessor();
                                        XmlRpcServerRequest xreq = xreqproc.decodeRequest(req.getServletRequest()
                                                .getInputStream());
                                        Vector args = xreq.getParameters();
                                        args.add(0, xreq.getMethodName());
                                        rgl = scriptingEngine.invoke(currentElement,
                                                action,
                                                args.toArray(),
                                                ScriptingEngine.ARGS_WRAP_XMLRPC,
                                                false);
                                        res.writeXmlRpcResponse(rgl);
                                    } else {
                                        rgl = scriptingEngine.invoke(currentElement,
                                        		action,
                                                EMPTY_ARGS,
                                                ScriptingEngine.ARGS_WRAP_DEFAULT,
                                                false);

                                        if (rgl != null && rgl != Undefined.instance) {
                                            res.write(rgl);
                                        }
                                    }
                                } catch (RedirectException redirect) {
                                    // if there is a message set, save it on the user object for the next request                                	
                                    session.storeResponseMessages(res);
                                }

                                // check if we're still the one and only or if the waiting thread has given up on us already
                                commitTransaction();

                                done = true;
                                
                                break;

                            case XMLRPC:
                            case EXTERNAL:

                                try {
                                    currentElement = root;

                                    if (functionName.indexOf('.') > -1) {
                                        StringTokenizer st = new StringTokenizer(functionName, ".");
                                        int cnt = st.countTokens();

                                        for (int i = 1; i < cnt; i++) {
                                            String next = st.nextToken();

                                            currentElement = getChildElement(currentElement,
                                                    next);
                                        }

                                        if (currentElement == null) {
                                            throw new FrameworkException("Method name \"" +
                                                    functionName +
                                                    "\" could not be resolved.");
                                        }

                                        functionName = st.nextToken();
                                    }

                                    // reset skin recursion detection counter
                                    skinDepth = 0;

                                    rgl = scriptingEngine.invoke(currentElement,
                                            functionName, args,
                                            ScriptingEngine.ARGS_WRAP_XMLRPC,
                                            false);
                                    commitTransaction();
                                } catch (Exception x) {
                                    abortTransaction();
                                    app.logError(txname + ": " + error, x);

                                    // If the transactor thread has been killed by the invoker thread we don't have to
                                    // bother for the error message, just quit.
                                    if (localrtx != rtx) {
                                        return;
                                    }

                                    this.exception = x;
                                }

                                done = true;
                                break;

                            case INTERNAL:
                                try {
                                    // reset skin recursion detection counter
                                    skinDepth = 0;

                                    rgl = scriptingEngine.invoke(thisObject,
                                            functionName,
                                            args,
                                            ScriptingEngine.ARGS_WRAP_DEFAULT,
                                            true);
                                    commitTransaction();
                                } catch (Exception x) {
                                    abortTransaction();
                                    app.logError(txname + ": " + error, x);

                                    // If the transactor thread has been killed by the invoker thread we don't have to
                                    // bother for the error message, just quit.
                                    if (localrtx != rtx) {
                                        return;
                                    }

                                    this.exception = x;
                                }

                                done = true;
                                break;

                        } // switch (reqtype)
                    } catch (AbortException x) {
                        // res.abort() just aborts the transaction and
                        // leaves the response untouched
                        abortTransaction();
                        done = true;
                    } catch (ConcurrencyException x) {
                        res.reset();

                        if (++tries < 8) {
                            // try again after waiting some period
                            abortTransaction();

                            try {
                                // wait a bit longer with each try
                                int base = 800 * tries;
                                Thread.sleep((long) (base + (Math.random() * base * 2)));
                            } catch (InterruptedException interrupt) {
                                // we got interrupted, create minimal error message 
                                res.reportError(app.getName(), error);
                                done = true;
                                // and release resources and thread
                                rtx = null;
                            }
                        } else {
                            abortTransaction();

                            if (error == null)
                                error = "Application too busy, please try again later";

                            // error in error action. use traditional minimal error message
                            res.reportError(app.getName(), error);
                            done = true;
                        }
                    } catch (Throwable x) {
                        String txname = localrtx.getTransactionName();
                        abortTransaction();

                        // If the transactor thread has been killed by the invoker thread we don't have to
                        // bother for the error message, just quit.
                        if (localrtx != rtx) {
                            return;
                        }

                        res.reset();

                        // check if we tried to process the error already,
                        // or if this is an XML-RPC request
                        if (error == null) {
                            app.errorCount += 1;

                            // set done to false so that the error will be processed
                            done = false;
                            error = x.getMessage();

                            if ((error == null) || (error.length() == 0)) {
                                error = x.toString();
                            }

                            if (error == null) {
                                error = "Unspecified error";
                            }

                            if (x instanceof ScriptingException) {
                                x = ((ScriptingException) x).getWrappedException();
                            }

                            if (!(x instanceof FrameworkException)) {
                                app.logError(txname + ": " + error, x);
                            }

                            if (req.isXmlRpc()) {
                                // if it's an XML-RPC exception immediately generate error response
                                if (!(x instanceof Exception)) {
                                    // we need an exception to pass to XML-RPC responder
                                    x = new Exception(x.toString(), x);
                                }
                                res.writeXmlRpcError((Exception) x);
                                done = true;
                            }
                        } else {
                            // error in error action. use traditional minimal error message
                            res.reportError(app.getName(), error);
                            done = true;
                        }
                    } finally {
                        app.setCurrentRequestEvaluator(null);
                    }
                }
                
                // exit execution context
                scriptingEngine.exitContext();
                notifyAndWait();
                
                this.layer = defmode;
            }

        } finally {
            localrtx.closeConnections();
        }
    }

    /**
     * Called by the transactor thread when it has successfully fulfilled a request.
     */
    synchronized void commitTransaction() throws Exception {
        Transactor localrtx = (Transactor) Thread.currentThread();

        if (localrtx == rtx) {
            localrtx.commit(this.layer, this);
        } else {
            throw new TimeoutException();
        }
    }

    synchronized void abortTransaction() {
        Transactor localrtx = (Transactor) Thread.currentThread();
        localrtx.abort();
    }

    private synchronized void startTransactor() {
        if (!app.isRunning()) {
            throw new ApplicationStoppedException();
        }
        
        if ((rtx == null) || !rtx.isAlive()) {
            rtx = new Transactor(this, app.threadgroup, app.nmgr);
            rtx.setContextClassLoader(app.getClassLoader());
            rtx.start();
        } else {
        	notifyAll();
        }
        
    }

    /**
     * Tell waiting thread that we're done, then wait for next request
     */
    synchronized void notifyAndWait() {
        Transactor localrtx = (Transactor) Thread.currentThread();

        // make sure there is only one thread running per instance of this class
        // if localrtx != rtx, the current thread has been aborted and there's no need to notify
        if (localrtx != rtx) {
            // A new request came in while we were finishing the last one.
            // Return to run() to get the work done.
            localrtx.closeConnections();
            return;
        }

        reqtype = NONE;
        notifyAll();

        try {
            // wait for request, max 10 min
            wait(1000 * 60 * 10);
        } catch (InterruptedException ix) {
            // we got interrrupted, releases resources and thread
            rtx = null;
        }

        //  if no request arrived, release ressources and thread
        if ((reqtype == NONE) && (rtx == localrtx)) {
            // comment this in to release not just the thread, but also the scripting engine.
            // currently we don't do this because of the risk of memory leaks (objects from
            // framework referencing into the scripting engine)
            // scriptingEngine = null;
            rtx = null;
        }
    }

    /**
     * Stop this request evaluator's current thread. This is called by the
     * waiting thread when it times out and stops waiting, or from an outside
     * thread. If currently active kill the request, otherwise just notify.
     */
    public synchronized void stopTransactor() {
        Transactor t = rtx;

        rtx = null;

        if (t != null && t.isActive()) {
            // let the scripting engine know that the
            // current transaction is being aborted.
            if (scriptingEngine != null) {
                scriptingEngine.abort();
            }

            app.logEvent("Killing Thread " + t);

            reqtype = NONE;

            t.kill();
            t.abort();
            t.closeConnections();
            t.shutdown();

            notifyAll();
        }
    }

    /**
     * Invoke an action function for a HTTP request. The function is dispatched
     * in a new thread and waits for it to finish.
     *
     * @param req the incoming HTTP request
     * @param session the client's session
     * @return the result returned by the invocation
     * @throws Exception any exception thrown by the invocation
     */
    public synchronized ResponseTrans invokeHttp(RequestTrans req, Session session) throws Exception{
        initObjects(req, session);
        if (!req.rewriteDone()) {
        	req.setPath(this.app.resolveUrlToPath(req.getPath()));
            req.setRewriteDone(true);
        }
        app.activeRequests.put(req, this);

        startTransactor();
        long begin = System.currentTimeMillis();
        while(reqtype != NONE && app.requestTimeout > System.currentTimeMillis() - begin){
        	wait(app.requestTimeout);
        }	

        if (reqtype != NONE) {
            app.logEvent("Stopping Thread for Request " + app.getName() + "/" + req.getPath());
            stopTransactor();
            res.reset();
            res.reportError(app.getName(), "Request timed out");
        }

        session.commit(this);
        
        return res;
    }

    /**
     * This checks if the Evaluator is already executing an equal request.
     * If so, attach to it and wait for it to complete. Otherwise return null,
     * so the application knows it has to run the request.
     */
    public synchronized ResponseTrans attachHttpRequest(RequestTrans req)
                                             throws Exception {
        // Get a reference to the res object at the time we enter
        ResponseTrans localRes = res;

        if ((localRes == null) || !req.equals(this.req)) {
            return null;
        }

        if (reqtype != NONE) {
            wait(app.requestTimeout);
        }

        return localRes;
    }

    /*
     * TODO invokeXmlRpc(), invokeExternal() and invokeInternal() are basically the same
     * and should be unified
     */

    /**
     * Invoke a function for an XML-RPC request. The function is dispatched in a new thread
     * and waits for it to finish.
     *
     * @param functionName the name of the function to invoke
     * @param args the arguments
     * @return the result returned by the invocation
     * @throws Exception any exception thrown by the invocation
     */
    public synchronized Object invokeXmlRpc(String functionName, Object[] args)
                                     throws Exception {
        initObjects(functionName, XMLRPC, RequestTrans.XMLRPC);
        this.functionName = functionName;
        this.args = args;

        startTransactor();
        long begin = System.currentTimeMillis();
        while(reqtype != NONE && app.requestTimeout > System.currentTimeMillis() - begin){
        	wait(app.requestTimeout);
        }

        if (reqtype != NONE) {
            stopTransactor();
            exception = new RuntimeException("Request timed out");
        }

        // reset res for garbage collection (res.data may hold reference to evaluator)
        res = null;

        if (exception != null) {
            throw (exception);
        }

        return rgl;
    }



    /**
     * Invoke a function for an external request. The function is dispatched
     * in a new thread and waits for it to finish.
     *
     * @param functionName the name of the function to invoke
     * @param args the arguments
     * @return the result returned by the invocation
     * @throws Exception any exception thrown by the invocation
     */
    public synchronized Object invokeExternal(String functionName, Object[] args)
                                     throws Exception {
        initObjects(functionName, EXTERNAL, RequestTrans.EXTERNAL);
        this.functionName = functionName;
        this.args = args;

        startTransactor();
        while(reqtype != NONE){
        	wait();
        }
        
        if (reqtype != NONE) {
            stopTransactor();
            exception = new RuntimeException("Request timed out");
        }

        // reset res for garbage collection (res.data may hold reference to evaluator)
        res = null;

        if (exception != null) {
            throw (exception);
        }

        return rgl;
    }

    /**
     * Invoke a function internally and directly, using the thread we're running on.
     *
     * @param obj the object to invoke the function on
     * @param functionName the name of the function to invoke
     * @param args the arguments
     * @return the result returned by the invocation
     * @throws Exception any exception thrown by the invocation
     */
    public Object invokeDirectFunction(Object obj, String functionName, Object[] args)
                                throws Exception {
        return scriptingEngine.invoke(obj, functionName, args,
                ScriptingEngine.ARGS_WRAP_DEFAULT, false);
    }

    /**
     * Invoke a function internally. The function is dispatched in a new thread
     * and waits for it to finish.
     *
     * @param object the object to invoke the function on
     * @param functionName the name of the function to invoke
     * @param args the arguments
     * @return the result returned by the invocation
     * @throws Exception any exception thrown by the invocation
     */
    public synchronized Object invokeInternal(Object object, String functionName,
                                              Object[] args)
                                       throws Exception {
        // give internal call more time (15 minutes) to complete
        return invokeInternal(object, functionName, args, 60000L * 15);
    }

    /**
     * Invoke a function internally. The function is dispatched in a new thread
     * and waits for it to finish.
     *
     * @param object the object to invoke the function on
     * @param functionName the name of the function to invoke
     * @param args the arguments
     * @param timeout the time in milliseconds to wait for the function to return
     * @return the result returned by the invocation
     * @throws Exception any exception thrown by the invocation
     */
    public synchronized Object invokeInternal(Object object, String functionName,
                                              Object[] args, long timeout)
                                       throws Exception {
        initObjects(functionName, INTERNAL, RequestTrans.INTERNAL);
        thisObject = object;
        this.functionName = functionName;
        this.args = args;

        startTransactor();
        long begin = System.currentTimeMillis();
        while(reqtype != NONE && timeout > System.currentTimeMillis() - begin){
        	wait(timeout);
        }	
        
        if (reqtype != NONE) {
            stopTransactor();
            exception = new RuntimeException("Request timed out");
        }

        // reset res for garbage collection (res.data may hold reference to evaluator)
        res = null;

        if (exception != null) {
            throw (exception);
        }

        return rgl;
    }


    /**
     * Init this evaluator's objects from a RequestTrans for a HTTP request
     *
     * @param req
     * @param session
     */
    private synchronized void initObjects(RequestTrans req, Session session) {
        this.req = req;
        this.reqtype = HTTP;
        this.session = session;
        res = new ResponseTrans(app, req);
        rgl = null;
        exception = null;
    }

    /**
     * Init this evaluator's objects for an internal, external or XML-RPC type
     * request.
     *
     * @param functionName
     * @param reqtype
     * @param reqtypeName
     */
    private synchronized void initObjects(String functionName, int reqtype, String reqtypeName) {
        this.functionName = functionName;
        this.reqtype = reqtype;
        req = new RequestTrans(reqtypeName, functionName);
        session = new Session(functionName, app); 
        res = new ResponseTrans(app, req);
        rgl = null;
        exception = null;
    }

    /**
     * Initialize the globals in the scripting engine for the current request.
     *
     * @param root
     * @throws ScriptingException
     */
    private synchronized void initGlobals(Object root)
                throws ScriptingException {
        HashMap globals = new HashMap();

        globals.put("root", root);
        globals.put("session", new SessionBean(session));
        globals.put("req", new RequestBean(req));
        globals.put("res", new ResponseBean(res, this));
        globals.put("app", new ApplicationBean(app));

        // enter execution context
        scriptingEngine.enterContext(globals);
    }

    /**
     * Get the child element with the given name from the given object.
     *
     * @param obj
     * @param name
     * @return
     * @throws ScriptingException
     */
    private Object getChildElement(Object obj, String name) throws ScriptingException {
        if (scriptingEngine.hasFunction(obj, "getChildElement")) {
            return scriptingEngine.invoke(obj, "getChildElement", new Object[] {name},
                                          ScriptingEngine.ARGS_WRAP_DEFAULT, false);
        }

        if (obj instanceof Node) {
            return ((Node) obj).getChildElement(name, false, this.layer);
        } else if (obj instanceof IPathElement) {
            return ((IPathElement) obj).getChildElement(name);
        }

        return null;
    }

    /**
     *  Null out some fields, mostly for the sake of garbage collection.
     */
    public synchronized void recycle() {
        res = null;
        req = null;
        session = null;
        args = null;
        rgl = null;
        exception = null;
    }

    /**
     * Check if an action with a given name is defined for a scripted object. If it is,
     * return the action's function name. Otherwise, return null.
     */
    public String getAction(Object obj, String action, RequestTrans req) {
        if (obj == null)
            return null;

        if (action == null)
            action = "main";
        
        StringBuffer buffer = new StringBuffer(action);
        // record length so we can check without method
        // afterwards for GET, POST, HEAD requests
        int length = buffer.length();

        if (req.checkXmlRpc()) {
            // append _methodname
            buffer.append("_xmlrpc");
            if (scriptingEngine.hasFunction(obj, buffer.toString())) {
                // handle as XML-RPC request
                req.setMethod(RequestTrans.XMLRPC);
                return buffer.toString();
            }
            // cut off method in case it has been appended
            buffer.setLength(length);
        }

        String method = req.getMethod();
        // append HTTP method to action name
        if (method != null) {
            // append _methodname
            buffer.append('_').append(method.toLowerCase());
            if (scriptingEngine.hasFunction(obj, buffer.toString()))
                return buffer.toString();

            // cut off method in case it has been appended
            buffer.setLength(length);
        }

        // if no method specified or "ordinary" request try action without method
        if (method == null || "GET".equalsIgnoreCase(method) ||
                              "POST".equalsIgnoreCase(method) ||
                              "HEAD".equalsIgnoreCase(method)) {
            if (scriptingEngine.hasFunction(obj, buffer.toString())) {
                return buffer.toString();
            }
        }
        
        return null;
    }

    /**
     * Returns this evaluator's scripting engine
     */
    public ScriptingEngine getScriptingEngine() {
        if (scriptingEngine == null) {
            initScriptingEngine();
        }
        return scriptingEngine;
    }

    /**
     * Get the request object for the current request.
     *
     * @return the request object
     */
    public synchronized RequestTrans getRequest() {
        return req;
    }

    /**
     * Get the response object for the current request.
     *
     * @return the response object
     */
    public synchronized ResponseTrans getResponse() {
        return res;
    }

    /**
     * Get the current transactor thread
     *
     * @return the current transactor thread
     */
    public synchronized Transactor getThread() {
        return rtx;
    }

    /**
     * Return the current session
     *
     * @return the session for the current request
     */
    public synchronized Session getSession() {
        return session;
    }
    
    public Object getCurrentElement(){
    	return currentElement;
    }
    
}