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

/* 
 * Modified by:
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
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
 * The ApplicationBean class exposes many application level functions and utilities at
 * the scripting framework level.
 * 
 * @jsinstance app
 */

public class ApplicationBean implements Serializable {
	
    Application app;
    WrappedMap properties = null;
    Object cachedRewriteRules = null;

    /**
     * Creates a new ApplicationBean object.
     *
     * @param Application app
     */
    public ApplicationBean(Application app) {
        this.app = app;
    }
    
    public void addCronJob(String functionName) {
        CronJob job = new CronJob(functionName);

        job.setFunction(functionName);
        app.customCronJobs.put(functionName, job);
    }

    public void addCronJob(String functionName, String year, String month, String day,
    		String weekday, String hour, String minute) {
    	CronJob job = CronJob.newJob(functionName, year, month, 
    			day, weekday, hour, minute, null);

    	app.customCronJobs.put(functionName, job);
    }
    
    /**
     * Adds a global function to the list of scheduled tasks that are called at 
     * the defined interval.
     *
     * @jsfunction
     * @param {String} functionName Name of the global function to be called
     * @param {String} [year] Year (yyyy)
     * @param {String} [month] Month (1-12)
     * @param {String} [day] Day (0-31)
     * @param {String} [weekday] Day of week (0-6, with 0 being Sunday)
     * @param {String} [hour] Hour (0-23)
     * @param {String} [minute] Minute (0-59)
     * @param {Number} [timeout] In seconds
     */
    public void addCronJob(String functionName, String year, String month, String day,
                           String weekday, String hour, String minute, Number timeout) {
        CronJob job = CronJob.newJob(functionName, year, month, 
        								day, weekday, hour, minute, timeout);

        app.customCronJobs.put(functionName, job);
    }

    /**
     * Add a repository to the app's repository list. The .zip extension
     * is automatically added, if the original library path does not
     * point to an existing file or directory.
     *
     * @jsfunction
     * @param {Object} repository The repository, relative or absolute path to the library
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
                throw new RuntimeException("Unrecognized file type in addRepository: " 
                		+ obj);
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
     * Clear the application cache.
     * 
     * @jsfunction
     */
    public void clearCache() {
        app.clearCache();
    }

    /**
     * Number of currently active sessions in this application.
     * @jsfunction
     * @returns {Number} Number of currently active sessions in this application
     */
    public int countSessions() {
        return app.countSessions();
    }

    public void debug(Object msg) {
        if (app.debug()) {
            getLogger().debug(msg);
        }
    }

    /**
     * Log a DEBUG message to the log defined by logname
     * if debug is set to true in app.properties.
     *
     * @jsfunction
     * @param {String} [logname] The name (category) of the log. Defaults to the app log
     * @param {Object} msg The log message
     */
    public void debug(String logname, Object msg) {
        if (app.debug()) {
            getLogger(logname).debug(msg);
        }
    }

    public void deleteDraft(Object obj) throws Exception {
    	this.deleteDraft(obj, null);
    }

    /**
     * Delete an object in the system at the specified layer.
     *
     * @jsfunction
     * @param {AxiomObject} object The object to be deleted at the specified layer
     * @param {Number} [layer] The layer on which to delete the object,  
     *                         if no layer is specified, default to the layer above the 
     *                         one on which the input AxiomObject resides
     * @throws Exception
     */
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

    /**
     * @type the wrapped axiom.framework.core.Application object
     */
    public Application get__app__() {
        return app;
    }

    /**
     * Number of currently active threads. 
     * @type Number
     */
    public int getActiveThreads() {
        return app.countActiveEvaluators();
    }

    /**
     * Array of currently authenticated users associated with an active session. 
     * @type Array
     */
    public Object getActiveUsers() {
        List activeUsers = app.getActiveUsers();
        return this.app.getCurrentRequestEvaluator().getScriptingEngine()
        							.newArray(activeUsers.toArray());
    }

    /**
     * Current version number of Axiom.
     * @type String
     */
    public String getAxiomVersion() {
        return Server.version;
    }

    /**
     * The absolute path to the directory where Axiom stores binary blobs for 
     * File and Image objects.
     * @type String 
     */
    public String getBlobDir() {
        return this.app.getBlobDir();
    }

    /**
     * Fetch an AxiomObject by its canonical path.
     * 
     * @jsfunction
     * @param {String} path The path
     * @returns {AxiomObject} AxiomObject at the given path
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

    /**
     * Number of AxiomObjects stored in cache.
     * @type Number
     */
    public int getCacheusage() {
        return app.getCacheUsage();
    }

    /**
     * The app's default charset/encoding.
     * @type String
     */
    public String getCharset() {
        return app.getCharset();
    }

    /**
     * The app's classloader.
     * @type java.lang.ClassLoader
     */
    public ClassLoader getClassLoader() {
        return app.getClassLoader();
    }

    /**
     * The domain accepted for sessions.  Set in app.properties.
     * @type String
     */
    public String getCookieDomain(){
    	return app.getCookieDomain();
    }

    /**
     * Read-only map of the axiom cron jobs registered with the app.
     * @type java.util.Map
     */
    public Map getCronJobs() {
        return new WrappedMap(app.customCronJobs, true);
    }

    /**
     * Cache used to store global application specific data during runtime.
     * @type Object
     */
    public INode getData() {
        return app.getCacheNode();
    }

    /**
     * Wrapper around the app's db properties (name/value pairs in the db.properties file).	
     * @type java.util.Map
     */
    public Map getDbProperties() {
        return new WrappedMap(app.getDbProperties(), true);
    }

    /**
     * Return a DbSource object for a given name, DbSource objects are defined 
     * through the db.properties file.
     * 
     * @jsfunction
     * @param {String} name The name of the db source
     * @returns {axiom.objectmodel.db.DbSource} The DbSource object
     */
    public DbSource getDbSource(String name) {
        return app.getDbSource(name);
    }
    
    /**
     * Returns the absolute path of the app dir. 
     * @type String
     */
    public String getDir() {
        return app.getAppDir().getAbsolutePath();
    }

    /**
     * Get the domains set as draftHosts for the specified layer.
     * @jsfunction
     * @param {Number} [layer] The layer for which to get the corresponding domains, 
     * 						   defaults to the live layer if non specified
     * @returns {Array} Array of domains matched 
     */
    public Object getDomains(Object layer) {
    	int mode = DbKey.LIVE_LAYER;
    	if (layer != null && layer != Undefined.instance) {
    		mode = getLayer(layer);
    	}
    	
    	Object[] domains = this.app.getDomainsForLayer(mode);
    	return this.app.getCurrentRequestEvaluator().getScriptingEngine()
								.newArray(domains);
    }

    /**
     * @jsomit
     * See below.
     */
    public Object getDraft(Object obj) throws Exception {
    	return this.getDraft(obj, null);
    }

    /**
     * Get the draft copy of obj residing at layer, creating one if none currently exists.
     * @jsfunction
     * @param {AxiomObject} obj The Object
     * @param {Number} [layer] The layer on which to delete the object, if no layer is 
     *                         specified, default to the layer above the one on which
     *                         the AxiomObject resides
     * @returns {AxiomObject} Draft copy of obj at layer
     * @throws Exception
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

    /**
     * Number of unhandled exceptions thrown by the current application. 
     * @type Number
     */
    public long getErrorCount() {
        return app.getErrorCount();
    }

    /**
     * @jsomit
     * See below.
     */
    public Scriptable getFields(Object field) throws Exception {
        return getQueryBean().fields(field, null, null);
    }

    /**
     * @jsomit
     * See below.
     */
    public Scriptable getFields(Object field, Object prototype) throws Exception {
        return getQueryBean().fields(field, prototype, null);
    }

    /**
     * @jsomit
     * See below.
     */
    public Scriptable getFields(Object field, Object prototype, Object filter) 
    throws Exception {
        return getQueryBean().fields(field, prototype, filter);
    }
    
    /**
     * Get an array of field values for the specified fields on all the objects that match the search criteria.
     * 
     * @jsfunction
     * @param {String|Array} field The field(s) values to return in the array 
     * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {Filter} [filter] The filter to apply to the search
     * @param {Object} [options] The optional parameters to pass in to the search.  
     *                           These are all specified in name/value pairs in a 
     *                           javascript object
     *                           
	 * 		<br><br>Possible values for the optional parameters are:
     * 		   <ul>
     * 	   	   <li>'sort' - A <code>SortObject</code> dictating the sort order of the results
     * 		   <li>'maxlength' - A Number indicating the maximum number of results to return
     *         <li>'layer' - A number indicating the layer to execute the query on, if this
     *                   is not specified, execute the query on the layer on which this
     *                   function is being invoked.
     * 		   </ul>
     *      <br>Example: { 'maxlength':50, 'sort':new Sort({'propname','asc'}), 'layer':1 }
     *            
     * @returns {Array} An array of field values
     * @throws Exception
     */
    public Scriptable getFields(Object field, Object prototype, Object filter, 
    		Object optional1) throws Exception {
        return getQueryBean().fields(field, prototype, filter, optional1);
    }

    /**
     * Number of free threads for this application.
     * @type Number
     */
    public int getFreeThreads() {
        return app.countFreeEvaluators();
    }

    /**
     * @jsomit
     * See below.
     */
    public int getHitCount() throws Exception {
        return getQueryBean().getHitCount(null, null, null);
    }

    /**
     * @jsomit
     * See below.
     */
    public int getHitCount(Object prototype) throws Exception {
        return getQueryBean().getHitCount(prototype, null, null);
    }

    /**
     * @jsomit
     * See below.
     */
    public int getHitCount(Object prototype, Object filter) throws Exception {
        return getQueryBean().getHitCount(prototype, filter, null);
    }

    /**
     * Returns the number of objects that match the search criteria.
     * 
     * @jsfunction
     * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {Filter} [filter] The filter to apply to the search
     * @param {Object} [options] The optional parameters to pass in to the search. 
     *                           These are all specified in name/value pairs in a 
     *                           javascript object
     *                           
	 * 		<br><br>Possible values for the optional parameters are:
     * 		   <ul>
     * 	   	   <li>'sort' - A <code>SortObject</code> dictating the sort order of the results
     * 		   <li>'maxlength' - A Number indicating the maximum number of results to return
     *         <li>'layer' - A number indicating the layer to execute the query on, if this
     *                   is not specified, execute the query on the layer on which this
     *                   function is being invoked.
     * 		   </ul>
     *      <br>Example: { 'maxlength':50, 'sort':new Sort({'propname','asc'}), 'layer':1 }
     *            
     * @returns {Number} The number of objects that match the search criteria
     * @throws Exception
     */
    public int getHitCount(Object prototype, Object filter, Object options) 
    throws Exception {
        return getQueryBean().getHitCount(prototype, filter, options);
    }

    /**
     * @jsomit
     * See below.
     */
    public Object getHits() throws Exception {
        return getQueryBean().hits(null, null);
    }

    /**
     * @jsomit
     * See below.
     */
    public Object getHits(Object prototype) throws Exception {
        return getQueryBean().hits(prototype, null);
    }

    /**
     * @jsomit
     * See below.
     */
    public Object getHits(Object prototype, Object filter) 
    throws Exception {
        return getQueryBean().hits(prototype, filter);
    }

    /**
     * Returns a HitsObject that contains the results of the search.
     * 
     * @jsfunction
     * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {Filter} [filter] The filter to apply to the search
     * @param {Object} [options] The optional parameters to pass in to the search. 
     *                           These are all specified in name/value pairs in a 
     *                           javascript object
     *                           
	 * 		<br><br>Possible values for the optional parameters are:
     * 		   <ul>
     * 	   	   <li>'sort' - A <code>SortObject</code> dictating the sort order of the results
     * 		   <li>'maxlength' - A Number indicating the maximum number of results to return
     *         <li>'layer' - A number indicating the layer to execute the query on, if this
     *                   is not specified, execute the query on the layer on which this
     *                   function is being invoked.
     * 		   </ul>
     *      <br>Example: { 'maxlength':50, 'sort':new Sort({'propname','asc'}), 'layer':1 }
     *            
     *            
     * @returns {LuceneHits} The results in HitsObject form, so they are not all
     * 						 loaded at once
     * @throws Exception
     */
    public Object getHits(Object prototype, Object filter, Object optional1) 
    throws Exception {
        return getQueryBean().hits(prototype, filter, optional1);
    }
    
    /**
     * The host name of the server upon which this application is running.
     * @type String
     */
    public Object getHostName() {
    	try {
    		return java.net.InetAddress.getLocalHost().getCanonicalHostName();
    	} catch (Exception ex) {
    		this.app.logError(ErrorReporter.errorMsg(this.getClass(), "getHostName"), ex);
    		return null;
    	}
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Log getLogger() {
        return app.getEventLog();
    }
 
    /**
     * Get the app logger. This is a commons-logging Log with the
     * category <code>logname</code>.
     *
     * @jsfunction
     * @param {String} [logname] The name of the log, if none is specified, 
     *                           defaults to the axiom.[appname].log file
     * @returns {Log} A logger for the given log name
     */
    public Log getLogger(String logname) {
        return  LogFactory.getLog(logname);
    }
    
    /**
     * Maximum number of simultaneous threads allowed by this application.
     * @type Number
     */
    public int getMaxThreads() {
        return app.countEvaluators();
    }
    
    /**
     * The name of the application.
     * @type String
     */
    public String getName() {
        return app.getName();
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Scriptable getObjects() throws Exception {
        return getQueryBean().objects(null, null);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Scriptable getObjects(Object prototype) throws Exception {
        return getQueryBean().objects(prototype, null);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Scriptable getObjects(Object prototype, Object filter) 
    throws Exception {
        return getQueryBean().objects(prototype, filter);
    }
    
    /**
     * Returns an array of AxiomObjects that match the search criteria.
     * 
     * @jsfunction
     * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {Filter} [filter] The filter to apply to the search
     * @param {Object} [options] The optional parameters to pass in to the search. 
     *                           These are all specified in name/value pairs in a 
     *                           javascript object
     *                           
	 * 		<br><br>Possible values for the optional parameters are:
     * 		   <ul>
     * 	   	   <li>'sort' - A <code>SortObject</code> dictating the sort order of the results
     * 		   <li>'maxlength' - A Number indicating the maximum number of results to return
     *         <li>'layer' - A number indicating the layer to execute the query on, if this
     *                   is not specified, execute the query on the layer on which this
     *                   function is being invoked.
     * 		   </ul>
     *      <br>Example: { 'maxlength':50, 'sort':new Sort({'propname','asc'}), 'layer':1 }
     *            
     * @returns {Array} An array of AxiomObjects
     * @throws Exception
     */
    public Scriptable getObjects(Object prototype, Object filter, Object options) 
    throws Exception {
        return getQueryBean().objects(prototype, filter, options);
    }
    
    /**
     * A readonly wrapper around the application's app properties.
     * @type java.util.Map 
     */
    public Map getProperties() {
        if (properties == null) {
            properties = new WrappedMap(app.getProperties(), true);
        }
        return properties;
    }
    
    /**
     * @jsomit 
     */
    
    public Object getProperty(String name) {
    	return getProperty(name, null);
    }
    
    /**
     * Get the value of a property from the application's app.properties file.
     * 
     * @jsfunction
     * @param {String} propname The property name
     * @param {String} [defvalue] The default value to return for the property name, 
     *                            if none is specified in the application's app.properties
     * @returns {String} The value of the property
     */
    public Object getProperty(String propname, Object defvalue) {
    	if (defvalue == null || defvalue == Undefined.instance) {
            return app.getProperty(propname);
        } else {
            return app.getProperty(propname, Context.toString(defvalue));
        }
    }
    
    /**
     * Get a prototype's list of resources.
     *
     * @jsfunction
     * @param {String} name The prototype name
     * @returns {Array} The list of resources
     */
    public Scriptable getPrototypeResources(String name) {
        Resource[] rsrcs = app.getPrototypeByName(name).getResources();
        ArrayList list = new ArrayList();
        Scriptable global = ((RhinoEngine) this.app.getCurrentRequestEvaluator()
        										.getScriptingEngine()).getGlobal();
        for (int i = 0; i < rsrcs.length; i++) {
        	list.add(Context.toObject(rsrcs[i], global));
        }
        return Context.getCurrentContext().newArray(global, list.toArray());
    }
    
    /**
     * An array of this app's prototypes.
     * @type Array
     */
    public Object getPrototypes() {
    	String[] arr = app.getPrototypeNames();
    	Object[] oarr = new Object[arr.length];
    	for (int i = 0; i < arr.length; i++) {
    		oarr[i] = arr[i];
    	}
    	return this.app.getCurrentRequestEvaluator().getScriptingEngine()
							.newArray(oarr);
    }
    
    /**
     * Returns an array of Reference objects representing all references in the system 
     * where source has a reference to target.
     * 
     * @jsfunction
     * @param {AxiomObject|String} source An AxiomObject as the source of the reference, 
     *                                    or a String denoting the AxiomObject's path. 
     *                                    If a String is specified and the path ends with
     *                                    '**' (e.g. /path/to/foo/**), then all objects
     *                                    located under foo will be included in 
     *                                    retrieving references
     *
     * @param {AxiomObject|String} target An AxiomObject as the target of the reference, 
     *                                   or a String denoting the AxiomObject's path. 
     *                                   If a String is specified and the path ends with
     *                                   '**' (e.g. /path/to/foo/**), then all objects
     *                                   located under foo will be included in 
     *                                   retrieving references
     * @returns {Array} An array of reference objects
     * @throws Exception
     */
    public Scriptable getReferences(Object source, Object target) throws Exception {
        return getQueryBean().references(source, target);    	
    }
    
    /**
     * An array containing this app's repositories.
     * @type Array
     */
    public Object getRepositories() {
        Object[] arr = app.getRepositories().toArray();
        return this.app.getCurrentRequestEvaluator().getScriptingEngine()
								.newArray(arr);
    }
    
    /**
     * The total number of requests processed by this app.
     * @type Number
     */
    public long getRequestCount() {
        return app.getRequestCount();
    }
    
    /**
     * A javascript object containing a mapping of all the rewrite rules 
     * defined in rewrite.properties.
     * @type Object
     */
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
    
    /**
     * @jsomit
     * See below.
     */
    public Scriptable getSchema(Object proto) {
    	return this.getSchema(proto, null);
    }
    
    /**
     * Get the schema (defined in prototype.properties) for a particular prototype.
     * 
     * @jsfunction
     * @param {String} prototype The prototype to get the schema for
     * @param {Boolean} [ignoreInternalProperties] True to ignore the internal properties 
     *                                           (those starting with an underscore), 
     *                                           false to include them. Defaults to true
     * @returns {Object} Javascript hash of the schema for the prototype
     */
    
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
    
    /**
     * The directory of the Axiom server.
     * @type String
     */
    public String getServerDir() {
        File f = app.getServerDir();

        if (f == null) {
            return app.getAppDir().getAbsolutePath();
        }

        return f.getAbsolutePath();
    }
    
    /**
     * Return a <code>SessionBean</code> object associated with the given Axiom session ID.
     *
     * @jsfunction
     * @param {String} sessionID The Axiom session ID
     * @returns {axiom.framework.core.SessionBean} A SessionBean object associated with the session ID
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
     * An array of all the active sessions in this application.
     * @type Array
     */
    public Object getSessions() {
        Map sessions = app.getSessions();
        Object[] array = new Object[sessions.size()];
        int i = 0;

        Iterator it = sessions.values().iterator();
        while (it.hasNext()) {
            array[i++] = new SessionBean((Session) it.next());
        }

        return this.app.getCurrentRequestEvaluator().getScriptingEngine()
						.newArray(array);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Object getSessionsForUser(INode usernode) {
        if (usernode == null) {
            return getSessionsForUser("");
        } else {
            return getSessionsForUser(usernode.getName());
        }
    }
    
    /**
     * Return an array of <code>SessionBean</code> objects currently associated
	 * with a given Axiom user.
     *
     * @jsfunction
     * @param {AxiomObject|String} user Either an AxiomObject specifying the user 
     *                             or a String specifying the username of the user
     * @returns {Array} An array of SessionBean objects
     */
    public Object getSessionsForUser(String username) {
    	Scriptable global = ((RhinoEngine) this.app.getCurrentRequestEvaluator()
    			.getScriptingEngine()).getGlobal();
        if ((username == null) || "".equals(username.trim())) {
        	return this.app.getCurrentRequestEvaluator().getScriptingEngine()
								.newArray(new Object[0]);
        }

        List userSessions = app.getSessionsForUsername(username);

        return this.app.getCurrentRequestEvaluator().getScriptingEngine()
							.newArray(userSessions.toArray());
    }
    
    /**
     * @jsomit
     * See below.
     */
    public int getSourceCount(Object target) throws Exception {
        return getQueryBean().sourceCount(target);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public int getSourceCount(Object target, Object prototypes) throws Exception {
        return getQueryBean().sourceCount(target, prototypes);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public int getSourceCount(Object target, Object prototypes, Object filter) 
    throws Exception {
        return getQueryBean().sourceCount(target, prototypes, filter);
    }
    
    /**
     * The number of AxiomObjects in the array that an equivalent getSources() call
     * would return.
     * 
     * @jsfunction
     * @param {AxiomObject|String} target An AxiomObject as the target, or a String 
     * 									  denoting the AxiomObject's path. 
     *                                    If a String is specified and the path ends with
     *                                    '**' (e.g. /path/to/foo/**), then all objects
     *                                    located under foo will be included in 
     *                                    retrieving objects with references to target
     * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {Filter} [filter] The filter to apply to the search
     * @param {Object} [options] The optional parameters to pass in to the search. 
     *                           These are all specified in name/value pairs in a 
     *                           javascript object
     *                           
	 * 		<br><br>Possible values for the optional parameters are:
     * 		   <ul>
     * 	   	   <li>'sort' - A <code>SortObject</code> dictating the sort order of the results
     * 		   <li>'maxlength' - A Number indicating the maximum number of results to return
     *         <li>'layer' - A number indicating the layer to execute the query on, if this
     *                   is not specified, execute the query on the layer on which this
     *                   function is being invoked.
     * 		   </ul>
     *      <br>Example: { 'maxlength':50, 'sort':new Sort({'propname','asc'}), 'layer':1 }
     *            
     * @returns {Number} The number of AxiomObjects 
     * @throws Exception
     */
    public int getSourceCount(Object target, Object prototypes, Object filter, 
    		Object options) throws Exception {
        return getQueryBean().sourceCount(target, prototypes, filter, options);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Object getSources(Object target) throws Exception {
        return getQueryBean().sources(target);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Object getSources(Object target, Object prototypes) throws Exception {
        return getQueryBean().sources(target, prototypes);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Object getSources(Object target, Object prototypes, Object filter) 
    throws Exception {
        return getQueryBean().sources(target, prototypes, filter);
    }
    
    /**
     * Returns an array of AxiomObjects which have references to target and
     * match the search criteria.
     * 
     * @jsfunction
     * @param {AxiomObject|String} target An AxiomObject as the target, or a String 
     * 									  denoting the AxiomObject's path. 
     *                                    If a String is specified and the path ends with
     *                                    '**' (e.g. /path/to/foo/**), then all objects
     *                                    located under foo will be included in 
     *                                    retrieving objects with references to target
     * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {Filter} [filter] The filter to apply to the search
     * @param {Object} [options] The optional parameters to pass in to the search. 
     *                           These are all specified in name/value pairs in a 
     *                           javascript object
     *                           
	 * 		<br><br>Possible values for the optional parameters are:
     * 		   <ul>
     * 	   	   <li>'sort' - A <code>SortObject</code> dictating the sort order of the results
     * 		   <li>'maxlength' - A Number indicating the maximum number of results to return
     *         <li>'layer' - A number indicating the layer to execute the query on, if this
     *                   is not specified, execute the query on the layer on which this
     *                   function is being invoked.
     * 		   </ul>
     *      <br>Example: { 'maxlength':50, 'sort':new Sort({'propname','asc'}), 'layer':1 }
     *            
     * @returns {Array} An array of AxiomObjects
     * @throws Exception
     */
    public Object getSources(Object target, Object prototypes, Object filter, 
    		Object options) throws Exception {
        return getQueryBean().sources(target, prototypes, filter, options);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public String getStaticMountpoint(){
    	return this.getStaticMountpoint(null);    	
    }
    
    /**
     * Get this application's static mountpoint, where those files specified in the application's static repositories are mounted.
     * 
     * @jsfunction
     * @param {String} [action] Optional action to append to the static mountpoint
     * @returns {String} The static mountpoint
     */
    public String getStaticMountpoint(Object action){
    	return this.app.getStaticMountpoint(action);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public int getTargetCount(Object source) throws Exception {
        return getQueryBean().targetCount(source);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public int getTargetCount(Object source, Object prototypes) throws Exception {
        return getQueryBean().targetCount(source, prototypes);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public int getTargetCount(Object source, Object prototypes, Object filter) 
    throws Exception {
        return getQueryBean().targetCount(source, prototypes, filter);
    }

    /**
     * The number of AxiomObjects in the array that an equivalent getTargets() call
     * would return.
     * 
     * @jsfunction
     * @param {AxiomObject|String} source An AxiomObject as the source, or a String 
     * 									  denoting the AxiomObject's path. 
     *                                    If a String is specified and the path ends with
     *                                    '**' (e.g. /path/to/foo/**), then all objects
     *                                    located under foo will be included in 
     *                                    retrieving objects with references 
     *                                    originating from source
     * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {Filter} [filter] The filter to apply to the search
     * @param {Object} [options] The optional parameters to pass in to the search. 
     *                           These are all specified in name/value pairs in a 
     *                           javascript object
     *                           
	 * 		<br><br>Possible values for the optional parameters are:
     * 		   <ul>
     * 	   	   <li>'sort' - A <code>SortObject</code> dictating the sort order of the results
     * 		   <li>'maxlength' - A Number indicating the maximum number of results to return
     *         <li>'layer' - A number indicating the layer to execute the query on, if this
     *                   is not specified, execute the query on the layer on which this
     *                   function is being invoked.
     * 		   </ul>
     *      <br>Example: { 'maxlength':50, 'sort':new Sort({'propname','asc'}), 'layer':1 }
     *            
     * @returns {Number} The number of AxiomObjects
     * @throws Exception
     */
    public int getTargetCount(Object source, Object prototypes, Object filter, 
    		Object options) throws Exception {
        return getQueryBean().targetCount(source, prototypes, filter, options);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Object getTargets(Object source) throws Exception {
        return getQueryBean().targets(source);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Object getTargets(Object source, Object prototypes) throws Exception {
        return getQueryBean().targets(source, prototypes);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Object getTargets(Object source, Object prototypes, Object filter) 
    throws Exception {
        return getQueryBean().targets(source, prototypes, filter);
    }
    
    /**
     * Returns an array of AxiomObjects which have references originating from source 
     * and match the search criteria.
     * 
     * @jsfunction
     * @param {AxiomObject|String} source An AxiomObject as the source, or a String 
     * 									  denoting the AxiomObject's path. 
     *                                    If a String is specified and the path ends with
     *                                    '**' (e.g. /path/to/foo/**), then all objects
     *                                    located under foo will be included in 
     *                                    retrieving objects with references 
     *                                    originating from source
     * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {Filter} [filter] The filter to apply to the search
     * @param {Object} [options] The optional parameters to pass in to the search. 
     *                           These are all specified in name/value pairs in a 
     *                           javascript object
     *                           
	 * 		<br><br>Possible values for the optional parameters are:
     * 		   <ul>
     * 	   	   <li>'sort' - A <code>SortObject</code> dictating the sort order of the results
     * 		   <li>'maxlength' - A Number indicating the maximum number of results to return
     *         <li>'layer' - A number indicating the layer to execute the query on, if this
     *                   is not specified, execute the query on the layer on which this
     *                   function is being invoked.
     * 		   </ul>
     *      <br>Example: { 'maxlength':50, 'sort':new Sort({'propname','asc'}), 'layer':1 }
     *            
     * @returns {Array} An array of AxiomObjects
     * @throws Exception
     */
    public Object getTargets(Object source, Object prototypes, Object filter, 
    		Object options) throws Exception {
        return getQueryBean().targets(source, prototypes, filter, options);
    }
    
    /**
     * This application's tmp directory, either set through java System property "tmpdir"
     * or "java.io.tmpdir."
     * @type String
     */
    public String getTmpDir() {
        return app.getTmpDir();
    }
    
    /**
     * The application start time.
     * @type Date
     */
    public Date getUpSince() {
        return new Date(app.starttime);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Scriptable getVersionFields(Object obj, Object fields, Object prototypes, 
    		Object filter, Object options) throws Exception {
        return getQueryBean().getVersionFields(obj, fields, prototypes, filter, options);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public void log(Object msg) {
    	if (msg != null) {
    		getLogger().info(msg);
    	}
    }
    
    /**
     * Log a INFO message to the log defined by logname.
     *
     * @jsfunction
     * @param {String} [logname] The name (category) of the log, defaults to the app log 
     *                           (axiom.[appname].log)
     * @param {String} msg The log message
     */
    public void log(String logname, Object msg) {
        getLogger(logname).info(msg);
    }
    
    /**
     * Remove a cron job from this application's cron runner.
     *
     * @jsfunction
     * @param {String} functionName The name of the cron function to remove
     */
    public void removeCronJob(String functionName) {
        app.customCronJobs.remove(functionName);
    }
    
    /**
     * @jsomit
     * See below.
     */
    public void saveDraft(Object obj) throws Exception {
    	this.saveDraft(obj, null);
    }

    /**
     * Save an object in the system at the specified layer.
     *
     * @jsfunction
     * @param {AxiomObject} object The object to be saved at the specified layer
     * @param {Number} [layer] The layer on which to save the object.  If no layer is 
     *                         specified, default to the layer below the one on which the 
     *                         input AxiomObject resides
     * @throws Exception
     */
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
    				+ "Called on " + obj + ", which is in the LIVE layer. " +
    						"No operation performed.");
    		return;
    	}
    	
    	if (layer != null && layer != Undefined.instance) {
    		mode = getLayer(layer);
    	}
    	
    	this.app.getNodeManager().saveNodeInLayer(node, mode);
    }
    
    /**
     * Set the maximum number of simultaneous threads (i.e. requests) 
     * allowed by this application
     *
     * @jsfunction
     * @param {Number} n The maximum number of simultaneous threads allowed
     */
    public void setMaxThreads(int n) {
        // add one to the number to compensate for the internal scheduler.
        app.setNumberOfEvaluators(n + 1);
    }
    
    /**
     * App's toString() method.
     *
     * @jsfunction
     * @returns {String} Returns the string '[Application appname]'
     */
    public String toString() {
        return "[Application " + app.getName() + "]";
    }
    
    /**
     * Allow Javascript to update the application's prototype resources.
     * 
     * @jsfunction
     */
    public void updateResources() {
        try {
            this.app.updateResources();
        } catch (Exception ex) {
        	this.app.logError(ErrorReporter.errorMsg(this.getClass(), "updateResources"), 
        			ex);
        }
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
    
    private QueryBean getQueryBean() {
    	return app.getQueryBean();
    }

    /**
     * The absolue path to the log directory of the application.
     * @type String
     */
    public String getLogDir() {
        File f = new File(app.getLogDir());

        return f.getAbsolutePath();
    }

    /**
     * The absolue path to the db directory of the application.
     * @type String
     */
    public String getDbDir() {
        return app.getDbDir().getAbsolutePath();
    }
    
   /**
    * Stops the application. 
    *
    * @jsfunction
    */
   public void stop(){
    	app.stop();
    }
    
    /**
     * Returns the Server object. 
     *
     * @jsfunction
     * @returns {axiom.main.Server} Returns the Server object
     */
    public Server getServer(){
    	return app.getServer();
    }
}