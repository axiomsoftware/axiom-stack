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
 * $RCSfile: ApplicationBean.java,v $
 * $Author: hannes $
 * $Revision: 1.36 $
 * $Date: 2006/04/12 14:55:04 $
 */

package axiom.framework.core;


import java.io.File;
import java.io.Serializable;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import axiom.framework.ErrorReporter;
import axiom.framework.repository.FileRepository;
import axiom.framework.repository.Repository;
import axiom.framework.repository.Resource;
import axiom.framework.repository.ZipRepository;
import axiom.main.Server;
import axiom.objectmodel.INode;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.db.DbSource;
import axiom.objectmodel.db.Node;
import axiom.scripting.rhino.AxiomObject;
import axiom.scripting.rhino.QueryBean;
import axiom.scripting.rhino.RhinoEngine;
import axiom.util.CronJob;
import axiom.util.WrappedMap;

/**
 * @jsinstance app
 */
public class ApplicationBean implements Serializable {
    Application app;
    WrappedMap properties = null;
    Object cachedRewriteRules = null;

    /**
     * Creates a new ApplicationBean object.
     *
     * @param app ...
     */
    public ApplicationBean(Application app) {
        this.app = app;
    }

    /**
     * Clear the application cache.
     */
    public void clearCache() {
        app.clearCache();
    }

    /**
     * Get the app's event logger. This is a Log with the
     * category helma.[appname].event.
     *
     * @return the app logger.
     */
    public Log getLogger() {
        return app.getEventLog();
    }

    /**
     * Get the app logger. This is a commons-logging Log with the
     * category <code>logname</code>.
     *
     * @return a logger for the given log name.
     */
    public Log getLogger(String logname) {
        return  LogFactory.getLog(logname);
    }

    /**
     * Log a INFO message to the app log.
     *
     * @param msg the log message
     */
    public void log(Object msg) {
    	if (msg != null) {
    		getLogger().info(msg);
    	}
    }

    /**
     * Log a INFO message to the log defined by logname.
     *
     * @param logname the name (category) of the log
     * @param msg the log message
     */
    public void log(String logname, Object msg) {
        getLogger(logname).info(msg);
    }

    /**
     * Log a DEBUG message to the app log if debug is set to true in
     * app.properties.
     *
     * @param msg the log message
     */
    public void debug(Object msg) {
        if (app.debug()) {
            getLogger().debug(msg);
        }
    }

    /**
     * Log a DEBUG message to the log defined by logname
     * if debug is set to true in app.properties.
     *
     * @param logname the name (category) of the log
     * @param msg the log message
     */
    public void debug(String logname, Object msg) {
        if (app.debug()) {
            getLogger(logname).debug(msg);
        }
    }

    /**
     * Returns the app's repository list.
     *
     * @return the an array containing this app's repositories
     */
    public Object[] getRepositories() {
        return app.getRepositories().toArray();
    }

    /**
     * Add a repository to the app's repository list. The .zip extension
     * is automatically added, if the original library path does not
     * point to an existing file or directory.
     *
     * @param obj the repository, relative or absolute path to the library.
     */
    public void addRepository(Object obj) {
        Repository rep = null;
        if (obj instanceof String) {
            String path = (String) obj;
            File file = new File(path).getAbsoluteFile();
            if (!file.exists()) {
                file = new File(path + ".zip").getAbsoluteFile();
            }
            if (!file.exists()) {
                file = new File(path + ".js").getAbsoluteFile();
            }
            if (!file.exists()) {
                throw new RuntimeException("Repository path does not exist: " + obj);
            }
            if (file.isDirectory()) {
                rep = new FileRepository(file);
            } else if (file.isFile()) {
                if (file.getName().endsWith(".zip")) {
                    rep = new ZipRepository(file);
                } 
            } else {
                throw new RuntimeException("Unrecognized file type in addRepository: " + obj);
            }
        } else if (obj instanceof Repository) {
            rep = (Repository) obj;
        } else {
            throw new RuntimeException("Invalid argument to addRepository: " + obj);
        }
        if(rep != null){
        	app.addRepository(rep);
        }
    }

    /**
     * The app's classloader
     * @type ClassLoader
     */
    public ClassLoader getClassLoader() {
        return app.getClassLoader();
    }

    /**
     *
     *
     * @return ...
     */
    public int countSessions() {
        return app.countSessions();
    }

    /**
     *
     *
     * @param sessionID ...
     *
     * @return ...
     */
    public SessionBean getSession(String sessionID) {
        if (sessionID == null) {
            return null;
        }

        Session session = app.getSession(sessionID.trim());

        if (session == null) {
            return null;
        }

        return new SessionBean(session);
    }

    /**
     *
     *
     * @return ...
     */
    public SessionBean[] getSessions() {
        Map sessions = app.getSessions();
        SessionBean[] array = new SessionBean[sessions.size()];
        int i = 0;

        Iterator it = sessions.values().iterator();
        while (it.hasNext()) {
            array[i++] = new SessionBean((Session) it.next());
        }

        return array;
    }

    /**
     * Array of currently authenticated users associated with an active session. 
     * @type AxiomObject[]
     */
    public INode[] getActiveUsers() {
        List activeUsers = app.getActiveUsers();

        return (INode[]) activeUsers.toArray(new INode[0]);
    }

    /**
     *
     *
     * @param usernode ...
     *
     * @return ...
     */
    public SessionBean[] getSessionsForUser(INode usernode) {
        if (usernode == null) {
            return new SessionBean[0];
        } else {
            return getSessionsForUser(usernode.getName());
        }
    }

    /**
     *
     *
     * @param username ...
     *
     * @return ...
     */
    public SessionBean[] getSessionsForUser(String username) {
        if ((username == null) || "".equals(username.trim())) {
            return new SessionBean[0];
        }

        List userSessions = app.getSessionsForUsername(username);

        return (SessionBean[]) userSessions.toArray(new SessionBean[0]);
    }

    /**
     *
     *
     * @param functionName ...
     */
    public void addCronJob(String functionName) {
        CronJob job = new CronJob(functionName);

        job.setFunction(functionName);
        app.customCronJobs.put(functionName, job);
    }

    /**
     * Adds a global function to the list of scheduled tasks that are called at the defined interval.
     *
     * @param {String} functionName Name of the global function to be called.
     * @param {String} [year] Year (yyyy)
     * @param {String} [month] Month (1-12)
     * @param {String} [day] Day (0-31)
     * @param {String} [weekday] Day of week (0-6, with 0 being Sunday)
     * @param {String} [hour] Hour (0-23)
     * @param {String} [minute] (0-59)
     */
    public void addCronJob(String functionName, String year, String month, String day,
                           String weekday, String hour, String minute) {
        CronJob job = CronJob.newJob(functionName, year, month, day, weekday, hour, minute);

        app.customCronJobs.put(functionName, job);
    }

    /**
     *
     *
     * @param functionName ...
     */
    public void removeCronJob(String functionName) {
        app.customCronJobs.remove(functionName);
    }

    /**
     * Read-only map of the axiom cron jobs registered with the app.
     * @type Map
     */
    public Map getCronJobs() {
        return new WrappedMap(app.customCronJobs, true);
    }

    /**
     * Number of elements in the NodeManager's cache
     * @type Number
     */
    public int getCacheusage() {
        return app.getCacheUsage();
    }

    /**
     * Cache used to store global application specific data during runtime.
     * @type Object
     */
    public INode getData() {
        return app.getCacheNode();
    }

    /**
     * Returns the absolute path of the app dir. When using repositories this
     * equals the first file based repository.
     *
     * @return the app dir
     */
    public String getDir() {
        return app.getAppDir().getAbsolutePath();
    }
    
    /**
     * The domain accepted for sessions.  Set in app.properties.
     * @type String
     */
    public String getCookieDomain(){
    	return app.getCookieDomain();
    }

    /**
     * @return the app name
     */
    public String getName() {
        return app.getName();
    }

    /**
     * @return the application start time
     */
    public Date getUpSince() {
        return new Date(app.starttime);
    }

    /**
     * @return the number of requests processed by this app
     */
    public long getRequestCount() {
        return app.getRequestCount();
    }

    /**
     * Number of unhandled exceptions thrown by the current application. 
     * @type Number
     */
    public long getErrorCount() {
        return app.getErrorCount();
    }

    /**
     * @return the wrapped axiom.framework.core.Application object
     */
    public Application get__app__() {
        return app;
    }

    /**
     * Get a wrapper around the app's properties
     *
     * @return a readonly wrapper around the application's app properties
     */
    public Map getProperties() {
        if (properties == null) {
            properties = new WrappedMap(app.getProperties(), true);
        }
        return properties;
    }

    /**
     * Wrapper around the app's db properties- map of name/value pairs in the db.properties file.	
     * @type Map
     */
    public Map getDbProperties() {
        return new WrappedMap(app.getDbProperties(), true);
    }
    
    /**
     * Return a DbSource object for a given name.
     * @jsfunction
     */
    public DbSource getDbSource(String name) {
        return app.getDbSource(name);
    }

    /**
     * Get an array of this app's prototypes
     *
     * @return an array containing the app's prototypes
     */
    public String[] getPrototypes() {
        return app.getPrototypeNames();
    }

    /**
     * Get a prototype's list of resources
     *
     * @param name the prototype name
     * @return the list of resources
     */
    public Scriptable getPrototypeResources(String name) {
        Resource[] rsrcs = app.getPrototypeByName(name).getResources();
        ArrayList list = new ArrayList();
        Scriptable global = ((RhinoEngine) this.app.getCurrentRequestEvaluator().getScriptingEngine()).getGlobal();
        for (int i = 0; i < rsrcs.length; i++) {
        	list.add(Context.toObject(rsrcs[i], global));
        }
        return Context.getCurrentContext().newArray(global, list.toArray());
    }

    /**
     * Number of free threads for this application.
     * @type Number
     */
    public int getFreeThreads() {
        return app.countFreeEvaluators();
    }

    /**
     * Number of currently active threads. 
     * @type Number
     */
    public int getActiveThreads() {
        return app.countActiveEvaluators();
    }

    /**
     *
     *
     * @return ...
     */
    public int getMaxThreads() {
        return app.countEvaluators();
    }

    /**
     *
     *
     * @param n ...
     */
    public void setMaxThreads(int n) {
        // add one to the number to compensate for the internal scheduler.
        app.setNumberOfEvaluators(n + 1);
    }

    /**
     *
     *
     * @return ...
     */
    public String getServerDir() {
        File f = app.getServerDir();

        if (f == null) {
            return app.getAppDir().getAbsolutePath();
        }

        return f.getAbsolutePath();
    }

    /**
     * The app's default charset/encoding.
     * @type String
     */
    public String getCharset() {
        return app.getCharset();
    }

    /**
     *
     *
     * @return ...
     */
    public String toString() {
        return "[Application " + app.getName() + "]";
    }
    
    /**
     * Allow Javascript to update the application's prototype resources.
     */
    public void updateResources() {
        try {
            this.app.updateResources();
        } catch (Exception ex) {
        	this.app.logError(ErrorReporter.errorMsg(this.getClass(), "updateResources"), ex);
        }
    }
    
    public String getTmpDir() {
        return app.getTmpDir();
    }
 
    /**
     * Current version number of Axiom.
     * @type String
     */
    public String getAxiomVersion() {
        return Server.version;
    }
    
    public Scriptable getFields(Object field) throws Exception {
        return getQueryBean().fields(field, null, null);
    }
    
    public Scriptable getFields(Object field, Object prototype) throws Exception {
        return getQueryBean().fields(field, prototype, null);
    }
    
    public Scriptable getFields(Object field, Object prototype, Object filter) 
    throws Exception {
        return getQueryBean().fields(field, prototype, filter);
    }
    
    public Scriptable getFields(Object field, Object prototype, Object filter, Object optional1) 
    throws Exception {
        return getQueryBean().fields(field, prototype, filter, optional1);
    }
    
    public Scriptable getObjects() throws Exception {
        return getQueryBean().objects(null, null);
    }
    
    public Scriptable getObjects(Object prototype) throws Exception {
        return getQueryBean().objects(prototype, null);
    }
    
    public Scriptable getObjects(Object prototype, Object filter) 
    throws Exception {
        return getQueryBean().objects(prototype, filter);
    }
    
    public Scriptable getObjects(Object prototype, Object filter, Object optional1) 
    throws Exception {
        return getQueryBean().objects(prototype, filter, optional1);
    }
    
    public Object getHits() throws Exception {
        return getQueryBean().hits(null, null);
    }
    
    public Object getHits(Object prototype) throws Exception {
        return getQueryBean().hits(prototype, null);
    }
    
    public Object getHits(Object prototype, Object filter) 
    throws Exception {
        return getQueryBean().hits(prototype, filter);
    }
    
    /**
     * @jsfunction getHits 
     */
    public Object getHits(Object prototype, Object filter, Object optional1) 
    throws Exception {
        return getQueryBean().hits(prototype, filter, optional1);
    }
    
    public int getHitCount() throws Exception {
        return getQueryBean().getHitCount(null, null, null);
    }
    
    public int getHitCount(Object prototype) throws Exception {
        return getQueryBean().getHitCount(prototype, null, null);
    }
    
    public int getHitCount(Object prototype, Object filter) throws Exception {
        return getQueryBean().getHitCount(prototype, filter, null);
    }
    
    public int getHitCount(Object prototype, Object filter, Object options) throws Exception {
        return getQueryBean().getHitCount(prototype, filter, options);
    }
    
    public Object getTargets(Object source) throws Exception {
        return getQueryBean().targets(source);
    }
    
    public Object getTargets(Object source, Object prototypes) throws Exception {
        return getQueryBean().targets(source, prototypes);
    }
    
    public Object getTargets(Object source, Object prototypes, Object filter) 
    throws Exception {
        return getQueryBean().targets(source, prototypes, filter);
    }
    
    public Object getTargets(Object source, Object prototypes, Object filter, Object options) 
    throws Exception {
        return getQueryBean().targets(source, prototypes, filter, options);
    }
    
    public Object getSources(Object target) throws Exception {
        return getQueryBean().sources(target);
    }
    
    public Object getSources(Object target, Object prototypes) throws Exception {
        return getQueryBean().sources(target, prototypes);
    }
    
    public Object getSources(Object target, Object prototypes, Object filter) 
    throws Exception {
        return getQueryBean().sources(target, prototypes, filter);
    }
    
    public Object getSources(Object target, Object prototypes, Object filter, Object options) 
    throws Exception {
        return getQueryBean().sources(target, prototypes, filter, options);
    }
    
    public int getTargetCount(Object source) throws Exception {
        return getQueryBean().targetCount(source);
    }
    
    public int getTargetCount(Object source, Object prototypes) throws Exception {
        return getQueryBean().targetCount(source, prototypes);
    }
    
    public int getTargetCount(Object source, Object prototypes, Object filter) 
    throws Exception {
        return getQueryBean().targetCount(source, prototypes, filter);
    }
    
    public int getTargetCount(Object source, Object prototypes, Object filter, Object options) 
    throws Exception {
        return getQueryBean().targetCount(source, prototypes, filter, options);
    }
    
    public int getSourceCount(Object target) throws Exception {
        return getQueryBean().sourceCount(target);
    }
    
    public int getSourceCount(Object target, Object prototypes) throws Exception {
        return getQueryBean().sourceCount(target, prototypes);
    }
    
    public int getSourceCount(Object target, Object prototypes, Object filter) 
    throws Exception {
        return getQueryBean().sourceCount(target, prototypes, filter);
    }
    
    public int getSourceCount(Object target, Object prototypes, Object filter, Object options) 
    throws Exception {
        return getQueryBean().sourceCount(target, prototypes, filter, options);
    }

    public Scriptable getReferences(Object source, Object target) throws Exception {
        return getQueryBean().references(source, target);    	
    }
    
    public Object getRewriteRules() {
        if (this.cachedRewriteRules != null) {
            return this.cachedRewriteRules;
        }
        Context cx = Context.getCurrentContext();
        RequestEvaluator reqev = this.app.getCurrentRequestEvaluator();
        Object ret = null;
        if (reqev != null) {
            Scriptable scope = ((RhinoEngine) reqev.scriptingEngine).getCore().getScope();
            String[][] rules = this.app.rewriteRules;
            final int length = rules.length;
            StringBuffer b = new StringBuffer();
            b.append("eval([");
            for (int i = 0; i < length; i++) {
                b.append("['").append(rules[i][0]).append("',");
                b.append("'").append(rules[i][1]).append("']");
                if (i < length - 1) {
                    b.append(",");
                }
            }
            b.append("]);");
            
            ret = cx.evaluateString(scope, b.toString(), "getRewriteRules()", 1, null);
            this.cachedRewriteRules = ret;
        } 
        return ret;
    }
    
    private QueryBean getQueryBean() {
        QueryBean qb = this.app.qbean;
        RequestEvaluator reqev = this.app.getCurrentRequestEvaluator();
        if (reqev != null) {
            RhinoEngine re = (RhinoEngine) reqev.scriptingEngine;
            qb.setRhinoCore(re.getCore());
        } else {
            qb.setRhinoCore(null);
        }
        return qb;
    }
    
    /**
     * The absolute path to the directory where Axiom stores binary blobs for File and Image objects.
     * @type String 
     */
    public String getBlobDir() {
        return this.app.getBlobDir();
    }
    
    public String getStaticMountpoint(){
    	return this.getStaticMountpoint(null);    	
    }
    
    public String getStaticMountpoint(Object action){
    	return this.app.getStaticMountpoint(action);
    }
    
    public Object getDraft(Object obj) throws Exception {
    	return this.getDraft(obj, null);
    }
    
    /**
     * Get the draft copy of obj residing at layer, creating one if none currently exists.
     * @jsfunction
     * @param AxiomObject obj
     * @param Number layer
     * @return AxiomObject
     */
    public Object getDraft(Object obj, Object layer) throws Exception {
    	Node node = null;
    	if (obj instanceof AxiomObject) {
    		node = (Node) ((AxiomObject) obj).getNode();
    	} else if (obj instanceof Node) {
    		node = (Node) obj;
    	}
    	
    	if (node == null) {
    		throw new Exception("Input parameter " + obj + " is not a valid object.");
    	}
    	
    	int mode = node.getLayerInStorage() + 1;
    	if (layer != null && layer != Undefined.instance) {
    		mode = getLayer(layer);
    	}
    	
    	return this.app.getNodeManager().getNodeInLayer(node, mode);
    }
    
    public void deleteDraft(Object obj) throws Exception {
    	this.deleteDraft(obj, null);
    }
    
    public void deleteDraft(Object obj, Object layer) throws Exception {
    	Node node = null;
    	if (obj instanceof AxiomObject) {
    		node = (Node) ((AxiomObject) obj).getNode();
    	} else if (obj instanceof Node) {
    		node = (Node) obj;
    	}
    	
    	if (node == null) {
    		throw new Exception("Input parameter " + obj + " is not a valid object.");
    	}
    	
    	DbKey key = (DbKey) node.getKey();
    	int mode = key.getLayer() + 1;
    	if (layer != null && layer != Undefined.instance) {
    		mode = getLayer(layer);
    	}
    	
    	this.app.getNodeManager().deleteNodeInLayer(node, mode);
    }
    
    public void saveDraft(Object obj) throws Exception {
    	this.saveDraft(obj, null);
    }
    
    public void saveDraft(Object obj, Object layer) throws Exception {
    	Node node = null;
    	if (obj instanceof AxiomObject) {
    		node = (Node) ((AxiomObject) obj).getNode();
    	} else if (obj instanceof Node) {
    		node = (Node) obj;
    	}
    	
    	if (node == null) {
    		throw new Exception("Input parameter " + obj + " is not a valid object.");
    	}
    	
    	DbKey key = (DbKey) node.getKey();
    	int mode = key.getLayer() - 1;
    	if (mode < 0) {
    		this.app.logEvent(ErrorReporter.warningMsg(this.getClass(), "saveDraft") 
    				+ "Called on " + obj + ", which is in the LIVE layer. No operation performed.");
    		return;
    	}
    	
    	if (layer != null && layer != Undefined.instance) {
    		mode = getLayer(layer);
    	}
    	
    	this.app.getNodeManager().saveNodeInLayer(node, mode);
    }

    /**
     * Get the domains set as draftHosts for the specified layer.
     * @jsfunction
     * @param Number layer
     * @return String[] Array of domains matched. 
     */
    public Object getDomains(Object layer) {
    	int mode = DbKey.LIVE_LAYER;
    	if (layer != null && layer != Undefined.instance) {
    		mode = getLayer(layer);
    	}
    	
    	Object[] domains = this.app.getDomainsForLayer(mode);
    	Context cx = Context.getCurrentContext();
    	return cx.newArray(new ImporterTopLevel(cx), domains);
    }
    
    private int getLayer(Object layer) {
    	int mode = DbKey.LIVE_LAYER;
    	if (layer == null || layer == Undefined.instance) {
    		return mode;
    	}
    	if (layer instanceof Number) {
			mode = ((Number) layer).intValue();
		} else if (layer instanceof String) {
			mode = this.app.getLayer((String) layer);
		} else if (layer instanceof Scriptable) {
			String classname = ((Scriptable) layer).getClassName();
			if ("Number".equals(classname)) {
				mode = ScriptRuntime.toInt32(layer);
			} else if ("String".equals(classname)) {
				mode = this.app.getLayer(ScriptRuntime.toString(layer));
			}
		} 
    	return mode;
    }
    
    /**
     * Fetch an AxiomObject by its canonical path.
     * @param String path
     * @jsfunction
     * @return AxiomObject AxiomObject at path
     */
    public Object getByPath(Object path) {
    	if (path == null || path == Undefined.instance) {
    		return null;
    	}
    	
    	String spath = null;
    	if (path instanceof String) {
    		spath = (String) path;
    	} else if (path instanceof Scriptable) {
    		spath = ScriptRuntime.toString(path);
    	} else {
    		spath = path.toString();
    	}
    	
    	Object node = AxiomObject.traverse(spath, this.app);
    	
    	if (node != null) {
    		return Context.toObject(node, RhinoEngine.getRhinoCore(this.app).getScope());
    	}
    	
    	return null;
    }
    
    public Scriptable getSchema(Object proto) {
    	return this.getSchema(proto, null);
    }
    
    public Scriptable getSchema(Object proto, Object arg) {
    	if (proto == null || proto == Undefined.instance) {
    		return null;
    	}
    	
    	String prototype = null;
    	if (proto instanceof String) {
    		prototype = (String) proto;
    	} else if (proto instanceof Scriptable) {
    		prototype = ScriptRuntime.toString(proto);
    	} else {
    		prototype = proto.toString();
    	}
    	
    	if (prototype != null) {
    		return AxiomObject.getSchema(prototype, arg, RhinoEngine.getRhinoCore(this.app));
    	}
    	
    	return null;
    }
    
    public Object getHostName() {
    	try {
    		return java.net.InetAddress.getLocalHost().getHostName();
    	} catch (Exception ex) {
    		this.app.logError(ErrorReporter.errorMsg(this.getClass(), "getHostName"), ex);
    		return null;
    	}
    }

    public Scriptable getVersionFields(Object obj, Object fields, Object prototypes, Object filter, Object options) throws Exception{
        return getQueryBean().getVersionFields(obj, fields, prototypes, filter, options);
    }
   
}