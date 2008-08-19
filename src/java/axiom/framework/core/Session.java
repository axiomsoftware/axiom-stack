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
 * $RCSfile: Session.java,v $
 * $Author: hannes $
 * $Revision: 1.13 $
 * $Date: 2006/01/13 16:50:40 $
 */

package axiom.framework.core;

import java.io.*;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import axiom.framework.ErrorReporter;
import axiom.framework.ResponseTrans;
import axiom.framework.UploadStatus;
import axiom.objectmodel.*;
import axiom.objectmodel.db.*;
import axiom.scripting.rhino.RhinoCore;
import axiom.scripting.rhino.RhinoEngine;
import axiom.scripting.rhino.debug.Debug;

/**
 * This represents a session currently using the Axiom application.
 * This includes anybody who happens to request a page from this application.
 * Depending on whether the user is logged in or not, the session holds a
 * persistent user node.
 */
public class Session implements Serializable { 

    transient protected Application app;
    protected String sessionId;

    // the unique id (login name) for the user, if logged in
    protected String uid;

    // the handle to this user's persistent db node, if logged in
    protected NodeHandle userHandle;

    // the transient cache node that is exposed to javascript
    // this stays the same across logins and logouts.
    protected INode cacheNode; 
    protected Debug debugObject;
    private boolean isDebuggerOn;
    protected long onSince;
    protected long lastTouched;
    protected long lastModified;

    // used to remember messages to the user between requests, mainly between redirects.
    protected StringBuffer debugBuffer;

    protected HashMap uploads = null;

    protected String httpReferer;
    
    protected HashMap draftIds = new HashMap();
    
    /**
     * Creates a new Session object.
     *
     * @param sessionId ...
     * @param app ...
     */
    public Session(String sessionId, Application app) {
        this.sessionId = sessionId;
        this.app = app;
        this.uid = null;
        this.userHandle = null;
        onSince = System.currentTimeMillis();
        lastTouched = lastModified = onSince;
        cacheNode = new TransientNode("data");
        debugObject = null;
    }

    /**
     * Attach the given user node to this session.
     */
    public void login(INode usernode) {
        if (usernode == null) {
            userHandle = null;
            uid = null;
        } else {
            userHandle = ((Node) usernode).getHandle();
            uid = usernode.getElementName();
        }

        lastModified = System.currentTimeMillis();
    }

    /**
     * Try logging in this session given the userName and password.
     *
     * @param userName
     * @param password
     * @return true if session was logged in.
     */
    public boolean login(String userName, String password) {
        return app.loginSession(userName, password, this);
    }

    /**
     * Remove this sessions's user node.
     */
    public void logout() {
        this.app.sessionMgr.expire(this);
        userHandle = null;
        uid = null;
        lastModified = System.currentTimeMillis();
    }

    public void reset() {
        this.logout();
        this.cacheNode = new TransientNode("data");
        this.debugObject = null;
    }

    /**
     * Returns true if this session is currently associated with a user object.
     *
     * @return ...
     */
    public boolean isLoggedIn() {
        return (userHandle != null) && (uid != null);
    }

    /**
     * Set the user handle for this session.
     */ 
    public void setUserHandle(NodeHandle handle) {
        this.userHandle = handle;
    }

    /**
     * Get the Node handle for the current user, if logged in.
     */
    public NodeHandle getUserHandle() {
        return userHandle;
    }

    /**
     * Gets the user Node from this Application's NodeManager.
     */
    public INode getUserNode() {
        if (userHandle != null) {
            return userHandle.getNode(app.getWrappedNodeManager());
        } else {
            return null;
        }
    }

    /**
     * Set the cache node for this session.
     */
    public void setCacheNode(TransientNode node) {
        this.cacheNode = node;
    }
    
    /**
     * Gets the transient cache node.
     */
    public INode getCacheNode() {
        return cacheNode;
    }
    
    public Debug getDebugObject() {
    	if (debugObject == null) {
    		try {
    			debugObject = (Debug) Context.getCurrentContext().newObject(
    				RhinoEngine.getRhinoCore(app).getScope(), 
    				"Debug", 
    				new Object[]{}
    				);
    			debugObject.setSession(this);
    		} catch (Exception ex) {
    			this.app.logError(ErrorReporter.errorMsg(this.getClass(), "getDebugObject() - Make sure rhino.debugger is set to true to invoke this method"));
    			throw new RuntimeException("Can not call session.debug unless rhino.debugger is set to true in your app.properties file");
    		}
    	}
    	return debugObject;
    }

    /**
     * Get this session's application
     *
     * @return ...
     */
    public Application getApp() {
        return app;
    }

    /**
     * Set this session's application
     *
     * @param app ...
     */
    public void setApp(Application app) {
        this.app = app;
    }

    /**
     * Return this session's id.
     *
     * @return ...
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Called at the beginning of a request to let the session know it's
     * being used.
     */
    public void touch() {
        lastTouched = System.currentTimeMillis();
        //this.setInteger("__lasttouched__", this.lastTouched);
    }

    /**
     * Called after a request has been handled.
     *
     * @param reval the request evaluator that handled the request
     */
    public void commit(RequestEvaluator reval) {
        // nothing to do
    }

    /**
     * Returns the time this session was last touched.
     *
     * @return ...
     */
    public long lastTouched() {
        return lastTouched;
    }

    /**
     * Returns the time this session was last modified, meaning the last time
     * its user status changed or its cache node was modified.
     *
     * @return ...
     */
    public long lastModified() {
        return lastModified;
    }

    /**
     * Set the last modified time on this session.
     *
     * @param date ...
     */
    public void setLastModified(Date date) {
        if (date != null) {
            lastModified = date.getTime();
        }
    }

    /**
     * Return the time this session was created.
     *
     * @return ...
     */
    public long onSince() {
        return onSince;
    }

    /**
     * Return a string representation for this session.
     *
     * @return ...
     */
    public String toString() {
        if (uid != null) {
            return "[Session for user " + uid + "]";
        } else {
            return "[Anonymous Session]";
        }
    }

    /**
     * Get the persistent user id of a registered user.
     * This is usually the user name, or null if the user is not logged in.
     */
    public String getUID() {
        return uid;
    }


    /**
     * Set the user and debug messages over from a previous response.
     * This is used for redirects, where messages can't be displayed immediately.
     * @param res the response to set the messages on
     */
    public synchronized void recoverResponseMessages(ResponseTrans res) {
        if (debugBuffer != null) {
            res.setDebugBuffer(debugBuffer);
            debugBuffer = null;
        }
    }

    /**
     * Remember the response's user and debug messages for a later response.
     * This is used for redirects, where messages can't be displayed immediately.
     * @param res the response to retrieve the messages from
     */
    public synchronized void storeResponseMessages(ResponseTrans res) {
        debugBuffer = res.getDebugBuffer();
    }

    /**
     * Return the debug buffer that is to be displayed upon the next
     * request within this session.
     *
     * @return the debug buffer, or null if none was set.
     */
    public StringBuffer getDebugBuffer() {
        return debugBuffer;
    }

    /**
     * Set the debug buffer to be displayed to this session's user. This
     * can be used to save the debug buffer over to the next request when
     * the current request can't be used to display a user visible
     * message.
     *
     * @param buffer the buffer
     */
    public void setDebugBuffer(StringBuffer buffer) {
        debugBuffer = buffer;
    }

    protected UploadStatus createUpload(String uploadId) {
        if (uploads == null) {
            uploads = new HashMap();
        }
        UploadStatus status = new UploadStatus();
        uploads.put(uploadId, status);
        return status;
    }

    /**
     * Set the http_referer on to the session.  Http_referer refers to the
     * URL the user reuqested and was unauthorized for.  When the user authenticates,
     * the url can be retreived to redirect the user to their original request.
     * 
     * @param the http_referer
     */
    
    public void setHttpReferer(String httpRef) {
    	httpReferer = httpRef;
    }
    
    /**
     * Returns the http_referer that is used for determing where the user should be
     * redirected to when logging in successfully after trying to view unauthorized
     * material
     * 
     * @return http_referer, null if none was set
     */
    
    public String getHttpReferer() {
        return httpReferer;
    }
    
    public void setDraftIds(Object arr, Object layer) {
        ArrayList<String> servers = getDomains(layer);
        HashSet<String> ids = new HashSet<String>();
        if (arr instanceof NativeArray) {
            NativeArray narr = (NativeArray) arr;
            int len = (int) narr.getLength();
            for (int i = 0; i < len; i++) {
                Object value = narr.get(i, narr);
                if (value instanceof Number) {
                    value = ((Number) value).intValue() + "";
                }
                ids.add(value.toString());
            }
        }
        
        final int size = servers.size();
        for (int i = 0; i < size; i++) {
        	String server = servers.get(i);
        	HashSet idSet;
        	synchronized (this.draftIds) {
        		idSet = (HashSet) this.draftIds.get(server);
        		if (idSet == null) {
        			idSet = new HashSet();
        			this.draftIds.put(server, idSet);
        		}
        	}

        	synchronized (idSet) {
        		idSet.clear();
        		idSet.addAll(ids);
        	}
        }
    }
    
    public void clearDraftIds(Object layer) {
    	ArrayList<String> servers = getDomains(layer);
    	final int size = servers.size();
        for (int i = 0; i < size; i++) {
        	String server = servers.get(i);
        	HashSet idSet;
        	synchronized (this.draftIds) {
        		idSet = (HashSet) this.draftIds.get(server);
        		if (idSet != null) {
                	synchronized (idSet) {
                		idSet.clear();
                	}
        		}
        		this.draftIds.remove(server);
        	}
        }
    }
    
    public void addDraftId(Object id, Object layer) {
    	ArrayList<String> servers = getDomains(layer);
        
        final int size = servers.size();
        for (int i = 0; i < size; i++) {
        	String server = servers.get(i);
        	HashSet idSet;
        	synchronized (this.draftIds) {
        		idSet = (HashSet) this.draftIds.get(server);
        		if (idSet == null) {
        			idSet = new HashSet();
        			this.draftIds.put(server, idSet);
        		}
        	}

        	synchronized (idSet) {
        		if (id != null && id != Undefined.instance) {
        			idSet.add(id.toString());
        		}
        	}
        }
    }
    
    public boolean isDraftIdOn(String id, String server, final int layer) {
        if (server == null || layer < this.app.getLayer(server)) {
            return true;
        }
        
        HashSet idSet;
        synchronized (this.draftIds) {
            idSet = (HashSet) this.draftIds.get(server);
        }
        if (idSet == null) {
            return true;
        }
        synchronized (idSet) {
        	return idSet.contains(id);
        }
    }
    
    public Object getDraftIds(Object layer) {
    	ArrayList<String> servers = getDomains(layer);
    	RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
    	
    	final int size = servers.size();
    	HashSet ids = new HashSet();
    	for (int i = 0; i < size; i++) {
    		String server = servers.get(i);
    		HashSet idSet;
    		synchronized (this.draftIds) {
    			idSet = (HashSet) this.draftIds.get(server);
    		}
    		if (idSet != null) {
    			ids.addAll(idSet);
    		}
    	}
    	return reqeval.scriptingEngine.newArray(ids.toArray());
    }
    
    private ArrayList<String> getDomains(Object layer) {
    	ArrayList<String> servers = new ArrayList<String>();
        if (layer == null || layer == Undefined.instance) {
        	HttpServletRequest sreq = this.app.getCurrentRequestEvaluator().getRequest().getServletRequest();
        	String server = sreq.getServerName();
        	if (server != null) { 
        		servers.add(server); 
        	}
        } else {
        	if (layer instanceof String) {
        		servers.add((String) layer);
        	} else if (layer instanceof Number) {
        		Object[] domains = this.app.getDomainsForLayer(((Number) layer).intValue());
        		for (int i = 0; i < domains.length; i++) {
        			servers.add((String) domains[i]);
        		}
        	} else if (layer instanceof Scriptable) {
        		String classname = ((Scriptable) layer).getClassName();
        		if ("String".equals(classname)) {
        			servers.add(ScriptRuntime.toString(layer));
        		} else if ("Number".equals(classname)) {
        			Object[] domains = this.app.getDomainsForLayer(ScriptRuntime.toInt32(layer));
        			for (int i = 0; i < domains.length; i++) {
            			servers.add((String) domains[i]);
            		}
        		}
        	}
        }
        return servers;
    }

}