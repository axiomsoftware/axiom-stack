/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile: Application.java,v $
 * $Author: hannes $
 * $Revision: 1.182 $
 * $Date: 2006/05/24 12:29:09 $
 */

/* 
 * Modified by:
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */

package axiom.framework.core;

import java.io.*;
import java.lang.reflect.*;
import java.rmi.*;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import axiom.cluster.ClusterCommunicator;
import axiom.extensions.ConfigurationException;
import axiom.extensions.AxiomExtension;
import axiom.framework.*;
import axiom.framework.repository.FileRepository;
import axiom.framework.repository.FileResource;
import axiom.framework.repository.Repository;
import axiom.framework.repository.Resource;
import axiom.framework.repository.ResourceComparator;
import axiom.framework.repository.ZipRepository;
import axiom.main.Server;
import axiom.objectmodel.*;
import axiom.objectmodel.db.*;
import axiom.scripting.rhino.AxiomObject;
import axiom.scripting.rhino.QueryBean;
import axiom.scripting.rhino.RhinoCore;
import axiom.scripting.rhino.RhinoEngine;
import axiom.scripting.rhino.debug.AxiomDebugger;
import axiom.util.*;

import java.util.ArrayList;


/**
 * The central class of a Axiom application. This class keeps a pool of so-called
 * request evaluators (threads with JavaScript interpreters), waits for
 * requests from the Web server or XML-RPC port and dispatches them to
 * the evaluators.
 */
public final class Application implements IPathElement, Runnable {
    
	private boolean omitXmlDecl = true;
	
	// the name of this application
	private String name;

	// application sources
	ArrayList repositories;

	// properties and db-properties
	ResourceProperties props;

	// properties and db-prkoperties
	ResourceProperties dbProps;
    
    // search profiles
    ResourceProperties searchProps;
    
    // rewrite rules
    String[][] rewriteRules;
    
	// This application's main directory
	File appDir;

	// Axiom server axiomHome directory
	File axiomHome;

	// embedded db directory
	File dbDir;
    
    // file/image storage directory
    String blobDir;
    
    String cookieDomain;
    
    // check for resource updates on a periodic basis?
    boolean autoUpdate = true;

	// this application's node manager
	protected NodeManager nmgr;

	// the root of the website, if a custom root object is defined.
	// otherwise this is managed by the NodeManager and not cached here.
	Object rootObject = null;
	String rootObjectClass;

	// The session manager
	SessionManager sessionMgr;

	/**
	 *  The type manager checks if anything in the application's prototype definitions
	 * has been updated prior to each evaluation.
	 */
	public TypeManager typemgr;

	/**
	 * Collections for evaluator thread pooling
	 */
	protected Stack freeThreads;
	protected Vector allThreads;
	boolean running = false;
	boolean debug;
	long starttime;
	Hashtable dbSources;

	Set onstartFunctions;

	// internal worker thread for scheduler, session cleanup etc.
	Thread worker;
	// request timeout defaults to 60 seconds
	long requestTimeout = 60000;
	ThreadGroup threadgroup;
	
    // threadlocal variable for the current RequestEvaluator
    ThreadLocal currentEvaluator = new ThreadLocal();

	// Map of requesttrans -> active requestevaluators
	Hashtable activeRequests;

	String logDir;

	// Two logs for each application
	Log eventLog;
	Log accessLog;
	Log errorLog;

	// Symbolic names for each log
	String eventLogName;
	String accessLogName;
	String errorLogName;
	String requestLogName;
	
	// A transient node that is shared among all evaluators
	protected INode cachenode;

	// some fields for statistics
	protected volatile long requestCount = 0;
	protected volatile long xmlrpcCount = 0;
	protected volatile long errorCount = 0;

	// the URL-prefix to use for links into this application
	private String baseURI;
	// the name of the root prototype as far as href() is concerned
	private String hrefRootPrototype;

	// the id of the object to use as root object
	String rootId = "0";

	// Added the id for the sessions object that stores all sessions and their
	// data, if persistent sessions are used
	String sessionsId = "2";

	// Db mappings for some standard prototypes
	private DbMapping rootMapping;
	private DbMapping userRootMapping;
	private DbMapping userMapping;
	private DbMapping sessionRootMapping; 
	private DbMapping sessionMapping; 

	// name of response encoding
	String charset;

	// Map of java class names to object prototypes
	ResourceProperties classMapping;

	// Map of extensions allowed for public skins
	Properties skinExtensions;

	// time we last read the properties file
	private long lastPropertyRead = -1L;
	
	// time we last read the module properties
	private long lastModuleRead = -1L;

	// the list of currently active cron jobs
	Hashtable activeCronJobs = null;
	// the list of custom cron jobs
	Hashtable customCronJobs = null;

	private ResourceComparator resourceComparator;

	// for static mountpoint for file/image api
	private String staticMountpoint;

	// for specifying the transaction manager
	protected TransSource tsource; 

	// the object that manages the path indexing for this application
	protected PathIndexer pathIndexer;
    
    // the interface to query objects in this application
    protected QueryBean qbean;
    
    protected ExecutionCache executionCache;
    protected ExecutionCache talCache;
    
    // Axiom Cluster communication interface if this app is part of a clustered environment
    // will be null if there is no cluster
    private ClusterCommunicator clusterComm = null;
    private String clusterHost = null;
    
    private HashMap draftHosts = new HashMap();
    private int highestPreviewLayer = 1;
    
    private ArrayList<String> extDbTypes = new ArrayList<String>();
    
    private Server server = null;
    
    // Stores the context paths of the application
    private ArrayList<String> contextPaths = new ArrayList<String>();
    
	/**
	 *  Simple constructor for dead application instances.
	 */
	public Application(String name) {
		this.name = name;
	}

	/**
	 * Build an application with the given name with the given sources. No
	 * Server-wide properties are created or used.
	 */
	public Application(String name, Repository[] repositories, File dbDir)
	throws RemoteException, IllegalArgumentException, IOException	 {
		this(name, null, repositories, null);
	}

	/**
	 * Build an application with the given name and server instance. The
	 * app directories will be created if they don't exist already.
	 */
	public Application(String name, Server server)
	throws RemoteException, IllegalArgumentException, IOException {
		this(name, server, null, null);
	}

	/**
	 * Build an application with the given name, server instance, sources and
	 * db directory.
	 */
	public Application(String name, Server server, Repository[] repositories, File customAppDir)
	throws RemoteException, IllegalArgumentException, IOException {
		if ((name == null) || (name.trim().length() == 0)) {
			throw new IllegalArgumentException("Invalid application name: " + name);
		}

		this.name = name;
		
		this.server = server;

		appDir = customAppDir;

		// system-wide properties, default to null
		ResourceProperties sysProps;

		// system-wide properties, default to null
		ResourceProperties sysDbProps;

		sysProps = sysDbProps = null;
		axiomHome = null;

		if (server != null) {
			axiomHome = server.getAxiomHome();

			// get system-wide properties
			sysProps = server.getProperties();
			sysDbProps = server.getDbProperties();
		}
		
		// give the Thread group a name so the threads can be recognized
		threadgroup = new ThreadGroup("TX-" + name);

		this.repositories = new ArrayList();
		try {
			// assume that the appdir is, in fact, a directory...
			Repository newRepository = new FileRepository(appDir);
			this.repositories.add(newRepository);           	
		} catch (Exception ex) {
			System.out.println("Adding application directory "+ appDir+ " failed. " +
								"Will not use that repository. Check your initArgs!");
		}          
		
		// create app-level properties
		props = new ResourceProperties(this, "app.properties", sysProps);
		
		if (repositories == null) {
			repositories = this.getAppRepositories();
		}
		if (repositories.length == 0) {
			throw new java.lang.IllegalArgumentException("No sources defined for application: " + name);
		}

		this.repositories.addAll(Arrays.asList(repositories));
		resourceComparator = new ResourceComparator(this);
		
		if (appDir == null) {      
			if (repositories[0] instanceof FileRepository) {
				appDir = new File(repositories[0].getName()); 
				SampleApp sa = new SampleApp();
				sa.setupSampleApp(appDir);
			}
		}
		
		String dbdir = props.getProperty("dbdir");
		if (dbdir != null) {
			dbDir = new File(dbdir);
			if(!dbDir.isAbsolute()){
				dbDir = new File(server.getAxiomHome(), dbdir);
			}
		} else {
			dbDir = new File(server.getDbHome(), name);
		}
		if (!dbDir.exists()) {
			dbDir.mkdirs();
		}
		
		
		updateDbLocation(name);
		
		this.cookieDomain = props.getProperty("cookieDomain", "");
		this.staticMountpoint = props.getProperty("staticMountpoint", props.getProperty("mountpoint", "/" + this.name)+ "/static");
		
		this.rewriteRules = setupRewriteRules();
        
		// get log names
		accessLogName = props.getProperty("accessLog",
				new StringBuffer("axiom.").append(name).append(".access").toString());
		eventLogName = props.getProperty("eventLog", 
				new StringBuffer("axiom.").append(name).toString());
		errorLogName = props.getProperty("errorLog");
		requestLogName = props.getProperty("requestLog",
				new StringBuffer("axiom.").append(name).append(".request.log").toString());
		if(!requestLogName.endsWith(".log")){
			requestLogName += ".log";
		}

		// insert xml declarations into rendered tal?
		omitXmlDecl = props.containsKey("omitxmldeclaration")?(new Boolean((String)props.get("omitXmlDeclaration"))).booleanValue():true;
		
        ResourceProperties dhprops = props.getSubProperties("draftHost.");
        int count = 1;
        final int dhpropsSize = dhprops.size();
        for (; count <= dhpropsSize; count++) {
        	String dhvalue = dhprops.getProperty("" + count);
        	if (dhvalue != null) {
            	String[] dhvalues = dhvalue.split(",");
            	for (int j = 0; j < dhvalues.length; j++) {
            		this.draftHosts.put(dhvalues[j].trim(), new Integer(count));
            	}
        	}
        }
        this.highestPreviewLayer = dhpropsSize == 0 ? 0 : count;
        
		// create app-level db sources
		dbProps = new ResourceProperties(this, "db.properties", sysDbProps, false);
        
        setupDefaultDb(dbProps);
        
        searchProps = new ResourceProperties(this, "search.properties", null, false);
        
		// reads in and creates a transaction manager properties file for this app 
		try {
			this.tsource = new TransSource(this, dbProps.getSubProperties("_default."));
		} catch (Exception ex) {
			throw new IllegalArgumentException("Could not create the transaction database source: " + ex.getMessage());
		}
		
		// the passwd file, to be used with the authenticate() function
		CryptResource parentpwfile = null;

		if (axiomHome != null) {
			parentpwfile = new CryptResource(new FileResource(new File(axiomHome, "passwd")), null);
		}

		// the properties that map java class names to prototype names
		classMapping = new ResourceProperties(this, "class.properties");
		classMapping.setIgnoreCase(false);

		// get class name of root object if defined. Otherwise native Axiom objectmodel will be used.
		rootObjectClass = classMapping.getProperty("root");

		onstartFunctions = new HashSet();
		updateProperties();

		dbSources = new Hashtable();
		
		cachenode = new TransientNode("app");

		ArrayList names = this.getDbNames();
		for(int i = 0; i < names.size(); i++){
			try{
				String dbname = names.get(i).toString();
				DbSource dbsource = this.getDbSource(dbname);
				String initClass = dbsource.getProperty("initClass", null);
				if(initClass != null){
			        Class[] parameters = { Application.class };
					IDBSourceInitializer dbsi = (IDBSourceInitializer)Class.forName(initClass)
					.getConstructor(parameters).newInstance(new Object[] {this});
					dbsi.init();
				}
			}
			catch(Exception e){
				throw new IOException(e.getMessage());
			}
		}
	
	}
	
	private Repository[] getAppRepositories() {
		Repository[] repositories;
		ResourceProperties conf = this.props;
		// parse main application directory	
        ArrayList repositoryList = new ArrayList();
        
        // read and configure additional app repositories
        Class[] parameters = { String.class };
        String modulesStr = conf.getProperty("modules");
        String[] modules;
        if (modulesStr != null) {
        	modules = modulesStr.split(",");
        } else {
        	modules = new String[0];
        }
        
        String pathPrefix = this.axiomHome.getPath();
        if (!pathPrefix.endsWith(File.separator)) {
        	pathPrefix += File.separator;
        }
        pathPrefix += "modules" + File.separator;
        for (int i = 0; i < modules.length; i++) {
        	String repositoryArgs = pathPrefix + modules[i].trim();

        	String repositoryImpl;
        	// implementation not set manually, have to guess it
        	if (repositoryArgs.endsWith(".zip")) {
        		repositoryImpl = "axiom.framework.repository.ZipRepository";
        	} else if (repositoryArgs.endsWith(".js")) {
        		repositoryImpl = "axiom.framework.repository.SingleFileRepository";
        	} else {
        		repositoryImpl = "axiom.framework.repository.FileRepository";
        	}

        	try {
        		Repository newRepository = (Repository) Class.forName(repositoryImpl)
        					.getConstructor(parameters)
        					.newInstance(new Object[] { repositoryArgs });
        		repositoryList.add(newRepository);
        	} catch (Exception ex) {
        		System.out.println("Adding repository " + repositoryArgs + " failed. " +
        							"Will not use that repository. Check your initArgs!");
        	}
        }
        
        // Load any zip files in the lib directory as repositories for the application
        
        File libDir = new File(Server.getServer().getAxiomHome(), "lib");
        File[] libFiles = libDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                String n = name.toLowerCase();
                
                return n.endsWith(".zip");
            }
        });
        
        for (int i = 0; i < libFiles.length; i++) {
            try {
                Repository newRepository = new ZipRepository(libFiles[i]);
                repositoryList.add(newRepository);
            } catch (Exception ex) {
                System.out.println("Adding repository " + libFiles[i].getAbsolutePath() 
                        + " failed. Will not use that repository. Check your initArgs!");
            }
        }
        
        repositories = new Repository[repositoryList.size()];
        repositoryList.toArray(repositories);
        
        return repositories;
	}

	/**
	 * Get the application ready to run, initializing the evaluators and type manager.
	 */
	public synchronized void init()
	throws DatabaseException, IllegalAccessException,
	InstantiationException, ClassNotFoundException {
		init(null);
	}

	/**
	 * Get the application ready to run, initializing the evaluators and type manager.
	 *
	 * @param ignoreDirs comma separated list of directory names to ignore
	 */
	public synchronized void init(String ignoreDirs)
	throws DatabaseException, IllegalAccessException,
	InstantiationException, ClassNotFoundException {

		running = true;

		// create and init type mananger
		typemgr = new TypeManager(this, ignoreDirs);
		// set the context classloader. Note that this must be done before
		// using the logging framework so that a new LogFactory gets created
		// for this app.
		Thread.currentThread().setContextClassLoader(typemgr.getClassLoader());
		try {
			typemgr.createPrototypes();
		} catch (Exception x) {
			logError("Error creating prototypes", x);
		}

		if (Server.getServer() != null) {
			Vector extensions = Server.getServer().getExtensions();

			for (int i = 0; i < extensions.size(); i++) {
				AxiomExtension ext = (AxiomExtension) extensions.get(i);

				try {
					ext.applicationStarted(this);
				} catch (ConfigurationException e) {
					logEvent("couldn't init extension " + ext.getName() + ": " +
							e.toString());
				}
			}
		}

		// create and init evaluator/thread lists
		freeThreads = new Stack();
		allThreads = new Vector();

		// preallocate minThreads request evaluators
		int minThreads = 0;

		try {
			minThreads = Integer.parseInt(props.getProperty("minThreads"));
		} catch (Exception ignore) {
			try {
                minThreads = Integer.parseInt(props.getProperty("maxThreads"));
                minThreads /= 4;
            } catch (Exception ignoreagain) {
                minThreads = 0;
            }
		}

		if (minThreads > 0) {
			logEvent("Starting "+minThreads+" evaluator(s) for " + name);
		}

		for (int i = 0; i < minThreads; i++) {
			RequestEvaluator ev = new RequestEvaluator(this);

			ev.initScriptingEngine();
            
			freeThreads.push(ev);
			allThreads.addElement(ev);
		}

		activeRequests = new Hashtable();
		activeCronJobs = new Hashtable();
		customCronJobs = new Hashtable();

		// read in root id, root prototype, user prototype
		rootId = props.getProperty("rootid", "0");
		String rootPrototype = props.getProperty("rootprototype", "root");
		String userPrototype = props.getProperty("userprototype", "user");

		rootMapping = getDbMapping(rootPrototype);
		if (rootMapping == null)
			throw new RuntimeException("rootPrototype does not exist: " + rootPrototype);
		userMapping = getDbMapping(userPrototype);
		if (userMapping == null)
			throw new RuntimeException("userPrototype does not exist: " + userPrototype);

		// The whole user/userroot handling is basically old
		// ugly obsolete crap. Don't bother.
		ResourceProperties p = new ResourceProperties();
		String usernameField = (userMapping != null) ? userMapping.getNameField() : null;

		if (usernameField == null) {
			usernameField = "name";
		}

		p.put("_children", "");
		p.put("_children.type", "collection(" + userPrototype + ")");
		p.put("_children.accessname", usernameField);
		p.put("roles","");
		p.put("roles.type","String");
		p.put("roles.multivalue","true");
		userRootMapping = new DbMapping(this, "__userroot__", p);
		userRootMapping.update();

		// add the session mappings for persisting sessions in the object database
		String sessionPrototype = props.getProperty("sessionprototype", "session");
		sessionMapping = getDbMapping(sessionPrototype);
		if (sessionMapping == null)
			throw new RuntimeException("sessionPrototype does not exist: " + sessionPrototype);
		p = new ResourceProperties();
		String nameField = (sessionMapping != null) ? sessionMapping.getNameField() : null;
		if (nameField == null) {
			nameField = "name";
		}
		p.put("_children", "");
		p.put("_children.type", "collection(" + sessionPrototype + ")");
		p.put("_children.accessname", nameField);
		sessionRootMapping = new DbMapping(this, "__sessionroot__", p);
		sessionRootMapping.update();

		// create/setup the path indexer for this application
		try {
			pathIndexer = new PathIndexer(this);
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException("Could not create the path indexer for the application " + this.name);
		}
        
        String cluster = this.getProperty("cluster", "false");
        if ("true".equalsIgnoreCase(cluster)) {
            try {
                this.clusterComm = new ClusterCommunicator(this);
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new InstantiationException("Could not initiate the jgroups cluster for " + this.name);
            }
            
            this.clusterHost = this.getProperty("cluster.host");
            if (this.clusterHost == null) {
                throw new InstantiationException("ERROR: cluster.host not specified in app.properties");
            }
        }
        
        // create the node manager
        nmgr = new NodeManager(this);
        nmgr.init(dbDir.getAbsoluteFile(), props);
        
        this.executionCache = new ExecutionCache(this, "rhino");
        
        this.talCache = new ExecutionCache(this, "tal");
        
		// create and init session manager
		String sessionMgrImpl = props.getProperty("sessionManagerImpl",
                "axiom.framework.core.SessionManager");
		sessionMgr = (SessionManager) Class.forName(sessionMgrImpl).newInstance();
		logEvent("Using session manager class " + sessionMgrImpl);
		sessionMgr.init(this);

		// read the sessions if wanted
		if ("true".equalsIgnoreCase(getProperty("persistentSessions"))) {
			RequestEvaluator ev = getEvaluator();
			try {
				ev.initScriptingEngine();
				sessionMgr.loadSessionData(null, ev.scriptingEngine);
			} finally {
				releaseEvaluator(ev);
			}
		}

		// reset the classloader to the parent/system/server classloader.
		Thread.currentThread().setContextClassLoader(typemgr.getClassLoader().getParent());
        
        try {
            this.qbean = new QueryBean(this, "query-filter-" + getName());
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new InstantiationException("Could not instantiate the QueryBean for " + this.name);
        }
        
    	Enumeration e = this.dbProps.keys();
    	while (e.hasMoreElements()) {
    		String key = (String) e.nextElement();
    		if(key.indexOf(".type") != -1){
    			String value = this.dbProps.getProperty(key);
    			if(!extDbTypes.contains(value) && !value.equalsIgnoreCase("relational")){
    				extDbTypes.add(value);
    			}
    		}
    	}
    	try{updateResources();}catch(IOException ex){ex.printStackTrace();}
	}
    
    private void setupDefaultDb(ResourceProperties dbprops) {
        if (dbprops.get("_default.driver") == null || dbprops.get("_default.url") == null) {
            dbprops.put("_default.driver", TransSource.DEFAULT_DRIVER);
            if (Server.getServer().isDbServerTcp()) {
                String port = Server.getServer().getTcpServerPort();
                String host = Server.getServer().getTcpServerHost();
                if (host == null) {
                    host = "localhost";
                }
                StringBuffer defurl = new StringBuffer();
                defurl.append("jdbc:h2:tcp://").append(host).append(":");
                defurl.append(port).append("/").append(TransSource.TRANSACTIONS_DB_NAME).append("_" + name);
                dbprops.put("_default.url", defurl.toString());
            } else {
            	File db = dbDir;

                if (!db.exists() || !db.isDirectory()) {
                    db.mkdir();
                }
                String path = db.getPath();
//              String path = db.getAbsolutePath();
              if (!path.endsWith(File.separator)) {
                    path += File.separator;
                }
                path += TransSource.TRANSACTIONS_DB_DIR;
                
                db = new File(path);
                if (!path.endsWith(File.separator)) {
                    path += File.separator;
                }

                path += TransSource.TRANSACTIONS_DB_NAME;
                dbprops.put("_default.url", TransSource.DEFAULT_URL + path);
            }
        }
    }


	/**
	 *  Create and start scheduler and cleanup thread
	 */
	public void start() {
		starttime = System.currentTimeMillis();

        this.onStart();
        
        synchronized (this) {
        	worker = new Thread(this, "Worker-" + name);
        	worker.setPriority(Thread.NORM_PRIORITY + 1);
        	worker.start();
        }
	}

	/**
	 * This is called to shut down a running application.
	 */
	public synchronized void stop() {
		// invoke global onStop() function
		RequestEvaluator eval = null;
		try {
			eval = getEvaluator();
			eval.invokeInternal(null, "onStop", RequestEvaluator.EMPTY_ARGS);
		} catch (Exception x) {
			logError("Error in " + name + "onStop()", x);
		} 
		// mark app as stopped
		running = false;

		// stop all threads, this app is going down
		if (worker != null) {
			worker.interrupt();
		}

		worker = null;

		// stop evaluators
		if (allThreads != null) {
			for (Enumeration e = allThreads.elements(); e.hasMoreElements();) {
				RequestEvaluator ev = (RequestEvaluator) e.nextElement();

				ev.stopTransactor();
			}
		}

		// remove evaluators
		allThreads.removeAllElements();
		freeThreads.clear();

		// shut down node manager and embedded db
		try {
			nmgr.shutdown();
		} catch (DatabaseException dbx) {
			System.err.println("Error shutting down embedded db: " + dbx);
		} catch (Exception e) {
			e.printStackTrace();
		}
        
        this.executionCache.shutdown();
        this.talCache.shutdown();

		// tell the extensions that we're stopped.
		if (Server.getServer() != null) {
			Vector extensions = Server.getServer().getExtensions();

			for (int i = 0; i < extensions.size(); i++) {
				AxiomExtension ext = (AxiomExtension) extensions.get(i);

				ext.applicationStopped(this);
			}
		}

		// store the sessions if wanted
		if ("true".equalsIgnoreCase(getProperty("persistentSessions"))) {
			// sessionMgr.storeSessionData(null);
			sessionMgr.storeSessionData(null, eval.scriptingEngine);
		}
		sessionMgr.shutdown();
        
        if (this.pathIndexer != null) {
            try {
                this.pathIndexer.shutdown();
            } catch (Exception ex) {
                ex.printStackTrace();
                this.logError("Failed to shutdown the PathIndexer", ex);
            }
        }
        
        if (this.qbean != null) {
            this.qbean.shutdown();
        }
        
        AxiomDebugger.removeDebugger(this);

        releaseEvaluator(eval);
        
        Handler[] handlers = this.server.getContexts().getHandlers();
        for(int i = 0; i < handlers.length; i++){
	    	if(handlers[i] instanceof ContextHandler){
	    		if(handlers[i] != null){
	    			ContextHandler context = (ContextHandler)handlers[i];
	    			if(this.contextPaths.contains(context.getContextPath())){
	    				if(!context.isStopped()){
	    					try{
	    						context.stop();
	    						context.destroy();
	    					} catch(Exception e){ }
	    				}
	    			}
	    		}
	    	}
        }
        contextPaths.clear();
	}

	/**
	 * Returns true if this app is currently running
	 *
	 * @return true if the app is running
	 */
	public synchronized boolean isRunning() {
		return running;
	}

	/**
	 * Get the application directory.
	 *
	 * @return the application directory, or first file based repository
	 */
	public File getAppDir() {
		return appDir;
	}

	public String getCookieDomain(){
		return this.cookieDomain;
	}
	
	/**
	 * Get the application's database directory.
	 * 
	 * Useful function overall to have, but in particular, used in the 
	 * QueryBean class to get the Lucene index location.
	 */
	public File getDbDir() {
		return this.dbDir;
	}
    
    /**
     * Get the applications File/Image (blob) storage location.
     */
    public String getBlobDir() {
        return this.blobDir;
    }

	/**
	 * Get a comparator for comparing Resources according to the order of
	 * repositories they're contained in.
	 *
	 * @return a comparator that sorts resources according to their repositories
	 */
	public ResourceComparator getResourceComparator() {
		return resourceComparator;
	}

	/**
	 * Returns a free evaluator to handle a request.
	 */
	public RequestEvaluator getEvaluator() {
		if (!running) {
			throw new ApplicationStoppedException();
		}

		// first try
		try {
			RequestEvaluator ev = (RequestEvaluator) freeThreads.pop();
            //this.logEvent("Getting evaluator " + ev + ", total evaluators = " + allThreads.size() + ", free evaluators = " + freeThreads.size());
            return ev;
		} catch (EmptyStackException nothreads) {
			int maxThreads = 50;

			try {
				maxThreads = Integer.parseInt(props.getProperty("maxThreads"));
			} catch (Exception ignore) {
				// property not set, use default value
			}

			synchronized (this) {
				// allocate a new evaluator
				if (allThreads.size() < maxThreads) {
					RequestEvaluator ev = new RequestEvaluator(this);

					allThreads.addElement(ev);
                    this.logEvent("Getting evaluator " + ev + ", total evaluators = " + allThreads.size() + ", free evaluators = " + freeThreads.size());

					return (ev);
				}
			}
		}

		// we can't create a new evaluator, so we wait if one becomes available.
		// give it 3 more tries, waiting 3 seconds each time.
		for (int i = 0; i < 4; i++) {
			try {
				Thread.sleep(3000);

				RequestEvaluator ev = (RequestEvaluator) freeThreads.pop();
                this.logEvent("Getting evaluator " + ev + ", total evaluators = " + allThreads.size() + ", free evaluators = " + freeThreads.size());
                return ev;
			} catch (EmptyStackException nothreads) {
				logEvent("Exception in Application::getEvaluator:"+nothreads);
			} catch (InterruptedException inter) {
				throw new RuntimeException("Thread interrupted.");
			}
		}

		// no luck, give up.
		throw new RuntimeException("Maximum Thread count reached.");
	}

	/**
	 * Returns an evaluator back to the pool when the work is done.
	 */
	public void releaseEvaluator(RequestEvaluator ev) {
		if (ev != null) {
			ev.recycle();
			freeThreads.push(ev);
			//this.logEvent("Releasing evaluator " + ev + ", total evaluators = " + allThreads.size() + ", free evaluators = " + freeThreads.size());
        }
	}

	/**
	 * This can be used to set the maximum number of evaluators which will be allocated.
	 * If evaluators are required beyound this number, an error will be thrown.
	 */
	public boolean setNumberOfEvaluators(int n) {
		int current = allThreads.size();

		synchronized (allThreads) {
			if (n > current) {
				int toBeCreated = n - current;

				for (int i = 0; i < toBeCreated; i++) {
					RequestEvaluator ev = new RequestEvaluator(this);

					freeThreads.push(ev);
					allThreads.addElement(ev);
				}
			} else if (n < current) {
				int toBeDestroyed = current - n;

				for (int i = 0; i < toBeDestroyed; i++) {
					try {
						RequestEvaluator re = (RequestEvaluator) freeThreads.pop();

						allThreads.removeElement(re);

						// typemgr.unregisterRequestEvaluator (re);
						re.stopTransactor();
					} catch (EmptyStackException empty) {
						return false;
					}
				}
			}
		}

		return true;
	}

	/**
	 *  Return the number of currently active threads
	 */
	public int getActiveThreads() {
		return 0;
	}

	/**
	 *  Execute a request coming in from a web client.
	 */
	public ResponseTrans execute(RequestTrans req) {
		requestCount += 1;
	
		// get user for this request's session
		Session session = createSession(req.getSession());

		ResponseTrans res = null;
		RequestEvaluator ev = null;

		// are we responsible for releasing the evaluator and closing the result?
		boolean primaryRequest = false;

		try {
			// first look if a request with same user/path/data is already being executed.
			// if so, attach the request to its output instead of starting a new evaluation
			// this helps to cleanly solve "doubleclick" kind of users
			ev = (RequestEvaluator) activeRequests.get(req);

			if (ev != null) {
				res = ev.attachHttpRequest(req);
			}

			if (res == null) {
				primaryRequest = true;

				// if attachHttpRequest returns null this means we came too late
				// and the other request was finished in the meantime
				// check if the properties file has been updated
				updateProperties();

				// get evaluator and invoke
				ev = getEvaluator();
				
				res = ev.invokeHttp(req, session);
			}
			
		} catch (ApplicationStoppedException stopped) {
			// let the servlet know that this application has gone to heaven
			throw stopped;
		} catch (Exception x) {
			errorCount += 1;
			res = new ResponseTrans(this, req);
			res.reportError(name, x.getMessage());
		} finally {
			if (primaryRequest) {
				activeRequests.remove(req);
				releaseEvaluator(ev);
                
				// response needs to be closed/encoded before sending it back
				try {
					res.close(charset);
				} catch (UnsupportedEncodingException uee) {
					logError("Unsupported response encoding", uee);
				}
			} else {
				res.waitForClose();
			}
		}

		return res;
	}

	/**
	 *  Called to execute a method via XML-RPC, usally by axiom.main.ApplicationManager
	 *  which acts as default handler/request dispatcher.
	 */
	public Object executeXmlRpc(String method, Vector args)
	throws Exception {
		xmlrpcCount += 1;

		Object retval = null;
		RequestEvaluator ev = null;

		try {
			// check if the properties file has been updated
			updateProperties();

			// get evaluator and invoke
			ev = getEvaluator();
			retval = ev.invokeXmlRpc(method, args.toArray());
		} finally {
			releaseEvaluator(ev);
		}

		return retval;
	}


	public Object executeExternal(String method, Vector args)
	throws Exception {
		Object retval = null;
		RequestEvaluator ev = null;
		try {
			// check if the properties file has been updated
			updateProperties();
			// get evaluator and invoke
			ev = getEvaluator();
			retval = ev.invokeExternal(method, args.toArray());
		} finally {
			releaseEvaluator(ev);
		}
		return retval;
	}

	/**
	 * Reset the application's object cache, causing all objects to be refetched from
	 * the database.
	 */
	public void clearCache() {
		nmgr.clearCache();
	}

	/**
	 * Returns the number of elements in the NodeManager's cache
	 */
	public int getCacheUsage() {
		return nmgr.countCacheEntries();
	}

	/**
	 *  Set the application's root element to an arbitrary object. After this is called
	 *  with a non-null object, the Axiom node manager will be bypassed. This function
	 * can be used to script and publish any Java object structure with Axiom.
	 */
	public void setDataRoot(Object root) {
		this.rootObject = root;
	}

	/**
	 * This method returns the root object of this application's object tree.
	 */
	public Object getDataRoot() {
		// check if we have a custom root object class
		if (rootObjectClass != null) {
			// create custom root element.
			if (rootObject == null) {
				try {
					if (classMapping.containsKey("root.factory.class") &&
							classMapping.containsKey("root.factory.method")) {
						String rootFactory = classMapping.getProperty("root.factory.class");
						Class c = typemgr.getClassLoader().loadClass(rootFactory);
						Method m = c.getMethod(classMapping.getProperty("root.factory.method"),
								(Class[]) null);

						rootObject = m.invoke(c, (Object[]) null);
					} else {
						String rootClass = classMapping.getProperty("root");
						Class c = typemgr.getClassLoader().loadClass(rootClass);

						rootObject = c.newInstance();
					}
				} catch (Exception e) {
					throw new RuntimeException("Error creating root object: " +
							e.toString());
				}
			}

			return rootObject;
		}
		// no custom root object is defined - use standard Axiom objectmodel
		else {
			return nmgr.safe.getRootNode();
		}
	}

	/**
	 *  Return the prototype of the object to be used as this application's root object
	 */
	public DbMapping getRootMapping() {
		return rootMapping;
	}

	/**
	 *  Return the id of the object to be used as this application's root object
	 */
	public String getRootId() {
		return rootId;
	}

	/**
	 * Returns the Object which contains registered users of this application.
	 */
	public INode getUserRoot() {
		INode users = nmgr.safe.getNode("1", userRootMapping);

		users.setDbMapping(userRootMapping);

		return users;
	}

	/**
	 * Returns the node manager for this application. The node manager is
	 * the gateway to the axiom.objectmodel packages, which perform the mapping
	 * of objects to relational database tables or the embedded database.
	 */
	public NodeManager getNodeManager() {
		return nmgr;
	}

	/**
	 * Returns a wrapper containing the node manager for this application. The node manager is
	 * the gateway to the axiom.objectmodel packages, which perform the mapping of objects to
	 * relational database tables or the embedded database.
	 */
	public WrappedNodeManager getWrappedNodeManager() {
		return nmgr.safe;
	}

	/**
	 *  Return a transient node that is shared by all evaluators of this application ("app node")
	 */
	public INode getCacheNode() {
		return cachenode;
	}

	/**
	 * Returns a Node representing a registered user of this application by his or her user name.
	 */
	public INode getUserNode(String uid) {
		try {
			INode users = getUserRoot();

			return (INode) users.getChildElement(uid);
		} catch (Exception x) {
			return null;
		}
	}

	/**
	 * Return a prototype for a given node. If the node doesn't specify a prototype,
	 * return the generic axiomobject prototype.
	 */
	public Prototype getPrototype(Object obj) {
		String protoname = getPrototypeName(obj);

		if (protoname == null) {
			return typemgr.getPrototype("axiomobject");
		}

		Prototype p = typemgr.getPrototype(protoname);

		if (p == null) {
			p = typemgr.getPrototype("axiomobject");
		}

		return p;
	}

	/**
	 * Return the prototype with the given name, if it exists
	 */
	public Prototype getPrototypeByName(String name) {
		return typemgr.getPrototype(name);
	}

	/**
	 * Return a collection containing all prototypes defined for this application
	 */
	public java.util.Collection getPrototypes() {
		return typemgr.getPrototypes();
	}
	
	public String[] getPrototypeNames() {
		java.util.Collection protos = typemgr.getPrototypes();
		String[] protoNames = new String[protos.size()];
		Iterator iter = protos.iterator();
		int count = 0;
		while (iter.hasNext()) {
			protoNames[count++] = ((Prototype) iter.next()).getName();
		}
		return protoNames;
	}

	/**
	 * Return the session currently associated with a given Axiom session ID.
	 * Create a new session if necessary.
	 */
	public Session createSession(String sessionId) {
		return sessionMgr.createSession(sessionId);
	}

	/**
	 * Return a list of Axiom nodes (AxiomObjects -  the database object representing the user,
	 *  not the session object) representing currently logged in users.
	 */
	public List getActiveUsers() {
		return sessionMgr.getActiveUsers();
	}

	/**
	 * Return an array of <code>SessionBean</code> objects currently associated
	 * with a given Axiom user.
	 */
	public List getSessionsForUsername(String username) {
		return sessionMgr.getSessionsForUsername(username);
	}

	/**
	 * Return the session currently associated with a given Axiom session ID.
	 */
	public Session getSession(String sessionId) {
		return sessionMgr.getSession(sessionId);
	}

	/**
	 *  Return the whole session map.
	 */
	public Map getSessions() {
		return sessionMgr.getSessions();
	}

	/**
	 * Returns the number of currenty active sessions.
	 */
	public int countSessions() {
		return sessionMgr.countSessions();
	}

	/**
	 * Register a user with the given user name and password.
	 */
	public INode registerUser(String uname, String password) {
		if (uname == null) {
			return null;
		}

		uname = uname.toLowerCase().trim();

		if ("".equals(uname)) {
			return null;
		}

		INode unode;

		try {
			INode users = getUserRoot();

			// check if a user with this name is already registered
			unode = (INode) users.getChildElement(uname);
			if (unode != null) {
				return null;
			}

			unode = new Node(uname, "user", nmgr.safe);

			String usernameField = (userMapping != null) ? userMapping.getNameField() : null;
			String usernameProp = null;

			if (usernameField != null) {
				usernameProp = userMapping.columnNameToProperty(usernameField);
			}

			if (usernameProp == null) {
				usernameProp = "name";
			}

			unode.setName(uname);
			unode.setString(usernameProp, uname);
			unode.setString("password", password);

			return users.addNode(unode);

		} catch (Exception x) {
			logEvent("Error registering User: " + x);

			return null;
		}
	}

	/**
	 * Log in a user given his or her user name and password.
	 */
	public boolean loginSession(String uname, String password, Session session) {
		// Check the name/password of a user and log it in to the current session
		if (uname == null) {
			return false;
		}

		uname = uname.toLowerCase().trim();

		if ("".equals(uname)) {
			return false;
		}

		try {
			INode users = getUserRoot();
			Node unode = (Node) users.getChildElement(uname);
			if (unode == null)
				return false;

			String pw = unode.getString("password");

			if ((pw != null) && pw.equals(password)) {
				// let the old user-object forget about this session
				session.logout();
				session.login(unode);

				return true;
			}
		} catch (Exception x) {
			return false;
		}

		return false;
	}

	/**
	 * Log out a session from this application.
	 */
	public void logoutSession(Session session) {
		session.logout();
	}

	/**
	 *  Return the href to the root of this application.
	 */
	public String getRootHref() throws UnsupportedEncodingException {
		return getNodeHref(getDataRoot(), null);
	}

	/**
	 * Return a path to be used in a URL pointing to the given element  and action
	 */
    
    public String getNodeHref(Object elem, String actionName)
    throws UnsupportedEncodingException {
        return this.getNodeHref(elem, actionName, false);
    }
    
	public String getNodeHref(Object elem, String actionName, boolean apply_rewrite) 
	throws UnsupportedEncodingException {
		StringBuffer b = new StringBuffer();
		composeHref(elem, b, 0);
		if (actionName != null) {
			b.append(UrlEncoded.encode(actionName, charset));
		} 

		String uri = baseURI + (apply_rewrite ? applyUrlRewrite(b.toString()) : b.toString());
        int len;
        if ((len = uri.length()) > 1 && uri.endsWith("/")) {
		    uri = uri.substring(0, len - 1);
        }
        
        return uri;
    }
    
    public String applyUrlRewrite(String href) {
        String[][] rules = this.rewriteRules;
        final int length = rules.length;
        boolean addedSlash = false;
        if (!href.startsWith("/")) {
            href = "/" + href;
            addedSlash = true;
        }
        for (int i = 0; i < length; i++) {
            if (href.startsWith(rules[i][1])) {
                href = href.replaceFirst(rules[i][1], rules[i][0]);
                break;
            }
        }
        if (addedSlash) {
            href = href.substring(1);
        }
        return href;
    }

	private void composeHref(Object elem, StringBuffer b, int pathCount)
	throws UnsupportedEncodingException {
		if ((elem == null) || (pathCount > 50)) {
			return;
		}

		if ((hrefRootPrototype != null) &&
				hrefRootPrototype.equals(getPrototypeName(elem))) {
			return;
		}

		Object parent = getParentElement(elem);
        
		if (parent == null) {
			return;
		}

		// recurse to parent element
		composeHref(parent, b, ++pathCount);

		// append ourselves
		String ename = getElementName(elem);
		if (ename != null) {
			b.append(UrlEncoded.encode(ename, charset));
			b.append("/");
		}
	}

	/**
	 *  Returns the baseURI for Hrefs in this application.
	 */
	public String getBaseURI() {
		return baseURI;
	}


	/**
	 *  This method sets the base URL of this application which will be prepended to
	 *  the actual object path.
	 */
	public void setBaseURI(String uri) {
		if (uri == null) {
			this.baseURI = "/";
		} else if (!uri.endsWith("/")) {
			this.baseURI = uri + "/";
		} else {
			this.baseURI = uri;
		}
	}

	/**
	 *  Return true if the baseURI property is defined in the application
	 *  properties, false otherwise.
	 */
	public boolean hasExplicitBaseURI() {
		return props.containsKey("baseuri");
	}

	/**
	 * Returns the prototype name that Hrefs in this application should
	 * start with.
	 */
	public String getHrefRootPrototype() {
		return hrefRootPrototype;
	}

	/**
	 * Tell other classes whether they should output logging information for
	 * this application.
	 */
	public boolean debug() {
		return debug;
	}

	/**
	 *  Utility function invoker for the methods below. This *must* be called
	 *  by an active RequestEvaluator thread.
	 */
	private Object invokeFunction(Object obj, String func, Object[] args) {
		RequestEvaluator reval = getCurrentRequestEvaluator();
		if (reval != null) {
			if (args == null) {
				args = RequestEvaluator.EMPTY_ARGS;
			}
			try {
				return reval.invokeDirectFunction(obj, func, args);
			} catch (Exception x) {
				//if (debug) {
					System.err.println("Error in Application.invokeFunction (" +
							func + "): " + x);
				//}
			}
		}
		return null;
	}

	/**
	 *  Return the application's classloader
	 */
	public ClassLoader getClassLoader() {
		return typemgr.getClassLoader();
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////
	///   The following methods mimic the IPathElement interface. This allows us
	///   to script any Java object: If the object implements IPathElement (as does
	///   the Node class in Axiom's internal objectmodel) then the corresponding
	///   method is called in the object itself. Otherwise, a corresponding script function
	///   is called on the object.
	//////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 *  Return the name to be used to get this element from its parent
	 */
	public String getElementName(Object obj) {
		if (obj instanceof IPathElement) {
			return ((IPathElement) obj).getElementName();
		}

		Object retval = invokeFunction(obj, "getElementName", RequestEvaluator.EMPTY_ARGS);

		if (retval != null) {
			return retval.toString();
		}

		return null;
	}

	/**
	 * Retrieve a child element of this object by name.
	 */
	public Object getChildElement(Object obj, String name) {
		if (obj instanceof IPathElement) {
			return ((IPathElement) obj).getChildElement(name);
		}

		Object[] arg = new Object[1];

		arg[0] = name;

		return invokeFunction(obj, "getChildElement", arg);
	}

	/**
	 * Return the parent element of this object.
	 */
	public Object getParentElement(Object obj) {
		if (obj instanceof IPathElement) {
			return ((IPathElement) obj).getParentElement();
		}

		return invokeFunction(obj, "getParentElement", RequestEvaluator.EMPTY_ARGS);
	}

	/**
	 * Get the name of the prototype to be used for this object. This will
	 * determine which scripts, actions and skins can be called on it
	 * within the Axiom scripting and rendering framework.
	 */
	public String getPrototypeName(Object obj) {
		if (obj == null) {
			return "global";
		}

		// check if e implements the IPathElement interface
		if (obj instanceof IPathElement) {
			// e implements the getPrototype() method
			return ((IPathElement) obj).getPrototype();
		} else {
			// look up prototype name by java class name
			Class clazz = obj.getClass();
			String protoname = classMapping.getProperty(clazz.getName());
			if (protoname != null) {
				return protoname;
			}
			// walk down superclass path
			while ((clazz = clazz.getSuperclass()) != null) {
				protoname = classMapping.getProperty(clazz.getName());
				if (protoname != null) {
					// cache the class name for the object so we run faster next time
					classMapping.setProperty(obj.getClass().getName(), protoname);
					return protoname;
				}
			}
			// check interfaces, too
			Class[] classes = obj.getClass().getInterfaces();
			for (int i = 0; i < classes.length; i++) {
				protoname = classMapping.getProperty(classes[i].getName());
				if (protoname != null) {
					// cache the class name for the object so we run faster next time
					classMapping.setProperty(obj.getClass().getName(), protoname);
					return protoname;
				}
			}
			// nada
			return null;
		}
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////
	///   The following methods are the IPathElement interface for this application.
	///   this is useful for scripting and url-building in the base-app.
	//////////////////////////////////////////////////////////////////////////////////////////////////////////
	public String getElementName() {
		return name;
	}

	/**
	 *
	 *
	 * @param name ...
	 *
	 * @return ...
	 */
	public IPathElement getChildElement(String name) {
		return null;
	}

	/**
	 *
	 *
	 * @return ...
	 */
	public IPathElement getParentElement() {
		return axiom.main.Server.getServer();
	}

	/**
	 *
	 *
	 * @return ...
	 */
	public String getPrototype() {
		return "application";
	}

	////////////////////////////////////////////////////////////////////////

	/**
	 * Log an application error
	 */
	public void logError(String msg, Throwable error) {
		getErrorLog().error(msg, error);
	}

	/**
	 * Log an application error
	 */
	public void logError(String msg) {
		getErrorLog().error(msg);
	}

	/**
	 * Log a generic application event
	 */
	public void logEvent(String msg) {
		getEventLog().info(msg);
	}

	/**
	 * Log an exception's message and stack trace
	 */
	public void logEvent(Exception ex) {
		StringBuffer msg = new StringBuffer();
		msg.append(ex.getLocalizedMessage());
		msg.append("\n");
		
		StackTraceElement[] trace = ex.getStackTrace();
		int len = trace.length;
		for(int i=0; i<len; i++){
			msg.append("\tat "+trace[i].toString());
			msg.append("\n");
		}
		
		getEventLog().info(msg);
	}

	
	/**
	 * Log an application access
	 */
	public void logAccess(String msg) {
		getAccessLog().info(msg);
	}

	/**
	 * get the app's event log.
	 */
	Log getEventLog() {
		if (eventLog == null) {
			eventLog = getLogger(eventLogName);
			// set log level for event log in case it is a axiom.util.Logger
			if (eventLog instanceof Logger) {
				((Logger) eventLog).setLogLevel(debug ? Logger.DEBUG : Logger.INFO);
			}

		}
		return eventLog;
	}
	
	Log getErrorLog() {
		if (errorLog == null && errorLogName != null) {
			errorLog = getLogger(errorLogName);
			// set log level for event log in case it is a axiom.util.Logger
			if (errorLog instanceof Logger) {
				((Logger) errorLog).setLogLevel(debug ? Logger.DEBUG : Logger.INFO);
			}
			return errorLog;
		}
		
		return (errorLog != null ? errorLog : getEventLog());
	}

	/**
	 * get the app's access log.
	 */
	Log getAccessLog() {
		if (accessLog == null) {
			accessLog = getLogger(accessLogName);
		}
		return accessLog;
	}

	/**
	 *  Get a logger object to log events for this application.
	 */
	public Log getLogger(String logname) {
		if ("console".equals(logDir) || "console".equals(logname)) {
			return Logging.getConsoleLog();
		} else {
			return LogFactory.getLog(logname);
		}
	}

    public void onStart() {
    	for(Object f: onstartFunctions){
    		String func = f.toString();
    		RequestEvaluator eval = null;
        	try {
            	eval = getEvaluator();
            	// initialize scripting engine
                eval.initScriptingEngine();
                this.setCurrentRequestEvaluator(eval);
                // update scripting prototypes
                eval.scriptingEngine.updatePrototypes();
            	eval.invokeInternal(null, func, RequestEvaluator.EMPTY_ARGS);
        	} catch (Exception xcept) {
            	logError("Error in " + name + " " + func, xcept);
        	} finally {
        		releaseEvaluator(eval);
        		this.setCurrentRequestEvaluator(null);
        	}
    	}
    }
    
	/**
	 * The run method performs periodic tasks like executing the scheduler method and
	 * kicking out expired user sessions.
	 */
	public void run() {

		// interval between session cleanups
		long lastSessionCleanup = System.currentTimeMillis();

		// logEvent ("Starting scheduler for "+name);

		while (Thread.currentThread() == worker) {

			// purge sessions
			try {
				lastSessionCleanup = cleanupSessions(lastSessionCleanup);
			} catch (Exception x) {
				logError("Error in session cleanup", x);
			}

			// execute cron jobs
			try {
				executeCronJobs();
			} catch (Exception x) {
				logError("Error in cron job execution", x);
			}

			long sleepInterval = 60000;
			try {
				String sleepProp = props.getProperty("schedulerInterval");
				if (sleepProp != null) {
					sleepInterval = Math.max(1000, Integer.parseInt(sleepProp) * 1000);
				} else {
					sleepInterval = CronJob.millisToNextFullMinute();
				}
			} catch (Exception ignore) {
				logEvent("Error in Application::run (error parsing sleepInterval)"+ignore.getMessage());
			}

			// sleep until the next full minute
			try {
				Thread.sleep(sleepInterval);
			} catch (InterruptedException x) {
				worker = null;
				break;
			}
		}

		// when interrupted, shutdown running cron jobs
		synchronized (activeCronJobs) {
			for (Iterator i = activeCronJobs.values().iterator(); i.hasNext();) {
				((CronRunner) i.next()).interrupt();
				i.remove();
			}
		}

		logEvent("Scheduler for " + name + " exiting");
	}

	/**
	 * Purge sessions that have not been used for a certain amount of time.
	 * This is called by run().
	 *
	 * @param lastSessionCleanup the last time sessions were purged
	 * @return the updated lastSessionCleanup value
	 */
	private long cleanupSessions(long lastSessionCleanup) {

		long now = System.currentTimeMillis();
		long sessionCleanupInterval = 60000;

		// check if we should clean up user sessions
		if ((now - lastSessionCleanup) > sessionCleanupInterval) {

			// get session timeout
			int sessionTimeout = 30;

			try {
				sessionTimeout = Math.max(0,
						Integer.parseInt(props.getProperty("sessionTimeout",
						"30")));
			} catch (NumberFormatException nfe) {
				logEvent("Invalid sessionTimeout setting: " + props.getProperty("sessionTimeout"));
			}

			RequestEvaluator thisEvaluator = null;

			try {

				thisEvaluator = getEvaluator();

				Map sessions = sessionMgr.getSessions();

				Iterator it = sessions.values().iterator();
				while (it.hasNext()) {
					Session session = (Session) it.next();

					if ((now - session.lastTouched()) > (sessionTimeout * 60000)) {
						NodeHandle userhandle = session.userHandle;

						if (userhandle != null) {
							try {
								Object[] param = {session.getSessionId()};

								thisEvaluator.invokeInternal(userhandle, "onLogout", param);
							} catch (Exception x) {
								// errors should already be logged by requestevaluator, but you never know
								logError("Error in onLogout", x);
							}
						}

						sessionMgr.discardSession(session);
					}
				}
			} catch (Exception cx) {
				logEvent("Error cleaning up sessions: " + cx);
			} finally {
				if (thisEvaluator != null) {
					releaseEvaluator(thisEvaluator);
				}
			}
			return now;
		} else {
			return lastSessionCleanup;
		}
	}

	/**
	 * Executes cron jobs for the application, which are either
	 * defined in app.properties or via app.addCronJob().
	 * This method is called by run().
	 */
	private void executeCronJobs() {
		// loop-local cron job data
		List jobs = CronJob.parse(props);
		Date date = new Date();

		jobs.addAll(customCronJobs.values());
		CronJob.sort(jobs);

		if (!activeCronJobs.isEmpty()) {
			logEvent("Cron jobs still running from last minute: " + activeCronJobs);
		}

		for (Iterator i = jobs.iterator(); i.hasNext();) {
			CronJob job = (CronJob) i.next();

			if (job.appliesToDate(date)) {
				// check if the job is already active ...
				if (activeCronJobs.containsKey(job.getName())) {
					logEvent(job + " is still active, skipped in this minute");

					continue;
				}

				RequestEvaluator evaluator;

				try {
					evaluator = getEvaluator();
				} catch (RuntimeException rt) {
					if (running) {
						logEvent("couldn't execute " + job +
						", maximum thread count reached");

						continue;
					} else {
						break;
					}
				}

				// if the job has a long timeout or we're already late during this minute
				// the job is run from an extra thread
				if ((job.getTimeout() > 20000) ||
						(CronJob.millisToNextFullMinute() < 30000)) {
					CronRunner runner = new CronRunner(evaluator, job);

					activeCronJobs.put(job.getName(), runner);
					runner.start();
				} else {
					try {
						evaluator.invokeInternal(null, job.getFunction(),
								RequestEvaluator.EMPTY_ARGS, job.getTimeout());
					} catch (Exception ex) {
						logEvent("error running " + job + ": " + ex);
					} finally {
						releaseEvaluator(evaluator);
					}
				}
			}
		}
	}

	/**
	 * Check whether a prototype is for scripting a java class, i.e. if there's an entry
	 * for it in the class.properties file.
	 */
	public boolean isJavaPrototype(String typename) {
		for (Enumeration en = classMapping.elements(); en.hasMoreElements();) {
			String value = (String) en.nextElement();

			if (typename.equals(value)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Return the java class that a given prototype wraps, or null.
	 */
	public String getJavaClassForPrototype(String typename) {

		for (Iterator it = classMapping.entrySet().iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();

			if (typename.equals(entry.getValue())) {
				return (String) entry.getKey();
			}
		}

		return null;
	}


	/**
	 * Return a DbSource object for a given name. A DbSource is a relational database defined
	 * in a db.properties file.
	 */
	public DbSource getDbSource(String name) {
		String dbSrcName = name.toLowerCase();
		DbSource dbs = (DbSource) dbSources.get(dbSrcName);

		if (dbs != null) {
			return dbs;
		}

		try {
			dbs = new DbSource(name, dbProps);
			dbSources.put(dbSrcName, dbs);
		} catch (Exception problem) {
			logEvent("Error creating DbSource " + name +": ");
			logEvent(problem.toString());
		}

		return dbs;
	}

	/**
	 * Return the name of this application
	 */
	public String getName() {
		return name;
	}

	/**
	 * Add a repository to this app's repository list. This is used for
	 * ZipRepositories contained in top-level file repositories, for instance.
	 *
	 * @param rep the repository to add
	 * @return if the repository was not yet contained
	 */
	public boolean addRepository(Repository rep) {
		if (rep != null && !repositories.contains(rep)) {
			// Add the new repository before its parent repository.
			// This establishes the order of compilation between FileRepositories
			// and embedded ZipRepositories.
			Repository parent = rep.getParentRepository();
			if (parent != null) {
				int idx = repositories.indexOf(parent);
				if (idx > -1) {
					repositories.add(idx, rep);
					return true;
				}
			}
			// no parent or parent not in app's repositories, add at end of list.
			repositories.add(rep);
			return true;
		}
		return false;
	}

	/**
	 * Searches for the index of the given repository for this app.
	 * The arguement must be a root argument, or -1 will be returned.
	 *
	 * @param   rep one of this app's root repositories.
	 * @return  the index of the first occurrence of the argument in this
	 *          list; returns <tt>-1</tt> if the object is not found.
	 */
	public int getRepositoryIndex(Repository rep) {
		return repositories.indexOf(rep);
	}

	/**
	 * Returns the repositories of this application
	 * @return iterator through application repositories
	 */
	public List getRepositories() {
		return Collections.unmodifiableList(repositories);
	}

	/**
	 * Return the directory of the Axiom server
	 */
	public File getServerDir() {
		return axiomHome;
	}

	/**
	 * Get the DbMapping associated with a prototype name in this application
	 */
	public DbMapping getDbMapping(String typename) {
		Prototype proto = typemgr.getPrototype(typename);

		if (proto == null) {
			return null;
		}

		return proto.getDbMapping();
	}

    /**
     * Return the current upload status.
     * @param req the upload RequestTrans
     * @return the current upload status.
     */
    public UploadStatus getUploadStatus(RequestTrans req) {
        String uploadId = (String) req.get("upload_id");
        if (uploadId == null)
            return null;

        String sessionId = req.getSession();
        Session session = getSession(sessionId);
        if (session == null)
            return null;
        return session.createUpload(uploadId);
    }

    private synchronized void updateProperties() {
		// if so property file has been updated, re-read props.
		if (props.lastModified() > lastPropertyRead) {
			// force property update
			props.update();

			// character encoding to be used for responses
			charset = props.getProperty("charset", "ISO-8859-1");

			// debug flag
			debug = "true".equalsIgnoreCase(props.getProperty("debug"));

			// if rhino debugger is enabled use higher (10 min) default request timeout
			String defaultReqTimeout =
				"true".equalsIgnoreCase(props.getProperty("rhino.debug")) ?
						"600" : "60";
			String reqTimeout = props.getProperty("requesttimeout", defaultReqTimeout);
			try {
				requestTimeout = Long.parseLong(reqTimeout) * 1000L;
			} catch (Exception ignore) {
				// go with default value
				requestTimeout = 60000L;
			}

			// set base URI
			String base = props.getProperty("baseuri");

			if (base != null) {
				setBaseURI(base);
			} else if (baseURI == null) {
				baseURI = "/";
			}
			hrefRootPrototype = props.getProperty("hrefrootprototype");

			// if node manager exists, update it
			if (nmgr != null) {
				nmgr.updateProperties(props);
			}

			// update extensions
			if (Server.getServer() != null) {
				Vector extensions = Server.getServer().getExtensions();

				for (int i = 0; i < extensions.size(); i++) {
					AxiomExtension ext = (AxiomExtension) extensions.get(i);

					try {
						ext.applicationUpdated(this);
					} catch (ConfigurationException e) {
						logEvent("Error updating extension "+ext+": "+e);
					}
				}
			}
			logDir = props.getProperty("logdir");
			if (logDir != null) {
				File dir = new File(logDir);
				System.setProperty("axiom.logdir", dir.getAbsolutePath());
			} else {
				logDir = "log";
			}
            
            String repos = props.getProperty("db.blob.dir");
            if (repos == null) {
            	File dir = new File(this.dbDir, "blob");
                if (!dir.exists() || !dir.isDirectory()) {
                    if (!dir.mkdir()) {
                        throw new IllegalArgumentException("Could not create the blob dir for " + this.name);
                    }
                }
                repos = dir.getPath();
            } else {
            	File dir = new File(repos);
            	if(!dir.isAbsolute()){
            		dir = new File(axiomHome, repos);
            	}
            	if (!dir.exists() || !dir.isDirectory()) {
                    if (!dir.mkdirs()) {
                        throw new IllegalArgumentException("Could not create the blob dir for " + this.name);
                    }
                }
            	repos = dir.getPath();
            }
            this.blobDir = repos;
            
            this.autoUpdate = new Boolean(props.getProperty("automaticResourceUpdate", 
                    "true")).booleanValue();

			// set log level for event log in case it is a axiom.util.Logger
			if (eventLog instanceof Logger) {
				((Logger) eventLog).setLogLevel(debug ? Logger.DEBUG : Logger.INFO);
			}
			
			String onStart = props.getProperty("onStart");
			if (onStart != null) {
				for (String funcs : onStart.split(",")) {
					this.onstartFunctions.add(funcs.trim());
				}
			}
			
			// set prop read timestamp
			lastPropertyRead = props.lastModified();
		}
    }

	/**
	 *  Get a checksum that mirrors the state of this application in the sense
	 *  that if anything in the applciation changes, the checksum hopefully will
	 *  change, too.
	 */
	public long getChecksum() {
		return starttime +
		typemgr.getLastCodeUpdate() +
		props.getChecksum();
	}

	/**
	 * Proxy method to get a property from the applications properties.
	 */
	public String getProperty(String propname) {
		return props.getProperty(propname);
	}

	/**
	 * Proxy method to get a property from the applications properties.
	 */
	public String getProperty(String propname, String defvalue) {
		return props.getProperty(propname, defvalue);
	}

	/**
	 * Get the application's app properties
	 *
	 * @return the properties reflecting the app.properties
	 */
	public ResourceProperties getProperties() {
		return props;
	}

	/**
	 * Get the application's db properties
	 *
	 * @return the properties reflecting the db.properties
	 */
	public ResourceProperties getDbProperties() {
		return dbProps;
	}
    
    public ResourceProperties getSearchProperties() {
        return this.searchProps;
    }


	/**
	 * Return a string representation for this app.
	 */
	public String toString() {
		return "[Application "+name+"]";
	}

	/**
	 *
	 */
	public int countThreads() {
		return threadgroup.activeCount();
	}

	/**
	 *
	 */
	public int countEvaluators() {
		return allThreads.size();
	}

	/**
	 *
	 */
	public int countFreeEvaluators() {
		return freeThreads.size();
	}

	/**
	 *
	 */
	public int countActiveEvaluators() {
		return allThreads.size() - freeThreads.size();
	}

	/**
	 *
	 */
	public int countMaxActiveEvaluators() {
		// return typemgr.countRegisteredRequestEvaluators () -1;
		// not available due to framework refactoring
		return -1;
	}

	/**
	 *
	 */
	public long getRequestCount() {
		return requestCount;
	}

	/**
	 *
	 */
	public long getXmlrpcCount() {
		return xmlrpcCount;
	}

	/**
	 *
	 */
	public long getErrorCount() {
		return errorCount;
	}

	/**
	 *
	 *
	 * @return ...
	 */
	public long getStarttime() {
		return starttime;
	}

	/**
	 * Return the name of the character encoding used by this application
	 *
	 * @return the character encoding
	 */
	public String getCharset() {
		return charset;
	}

	/**
	 * Periodically called to log thread stats for this application
	 */
	public void printThreadStats() {
		logEvent("Thread Stats for " + name + ": " + threadgroup.activeCount() +
		" active");

		Runtime rt = Runtime.getRuntime();
		long free = rt.freeMemory();
		long total = rt.totalMemory();

		logEvent("Free memory: " + (free / 1024) + " kB");
		logEvent("Total memory: " + (total / 1024) + " kB");
	}

	class CronRunner extends Thread {
		RequestEvaluator thisEvaluator;
		CronJob job;

		public CronRunner(RequestEvaluator thisEvaluator, CronJob job) {
			this.thisEvaluator = thisEvaluator;
			this.job = job;
		}

		public void run() {
			try {
				thisEvaluator.invokeInternal(null, job.getFunction(),
						RequestEvaluator.EMPTY_ARGS, job.getTimeout());
			} catch (Exception ex) {
				logEvent("error running " + job + ": " + ex);
			} finally {
				releaseEvaluator(thisEvaluator);
				thisEvaluator = null;
				activeCronJobs.remove(job.getName());
			}
		}

		public String toString() {
			return "CronRunner[" + job + "]";
		}
	}

	/**
	 * Method to get the static mountpoint for this application, for use in the href() methods
	 * of the file/image api
	 */

	public String getStaticMountpoint() {
		return this.staticMountpoint;
	}

    public String getStaticMountpoint(Object action){
    	String act = "";
    	if (action != null && !(action instanceof Undefined)) {
            if (action instanceof Wrapper) {
                act = ((Wrapper) action).unwrap().toString();
            } else {
                act = action.toString();
            }
            
            if (act != null && !act.startsWith("/")) {
                act = "/"+act;
            }
        }

    	String mountPoint = this.getStaticMountpoint();
    	if(mountPoint.endsWith("/")){
    		mountPoint = mountPoint.substring(0, mountPoint.length()-1);
    	}
    	
    	return mountPoint + act;
    }
	
	/**
	 * Method to get the Transaction Db Source, for implementing transactionality of commits
	 */

	public TransSource getTransSource() {
		return this.tsource;
	}

	/**
	 * Method to get the Path Indexer, for storing id to path mappings on all nodes in an 
	 * appliation.
	 */
	public PathIndexer getPathIndexer() {
		return this.pathIndexer;
	}

	/**
	 * Update the application's prototype resources.
	 */
	public void updateResources() throws IOException {
		RequestEvaluator reqEval = this.getCurrentRequestEvaluator();
		if (reqEval != null && reqEval.scriptingEngine instanceof RhinoEngine) {
			RhinoCore core = ((RhinoEngine) reqEval.scriptingEngine).getCore();
			if (core != null) {
				core.updatePrototypes(true);
			} 
		}
	}

	/**
	 * Method to return the Sessions object that is the root of all stored session data
	 * in the object database
	 */
	public INode getSessionsRoot() {
		INode sessions = nmgr.safe.getNode(this.sessionsId, sessionRootMapping);

		sessions.setDbMapping(sessionRootMapping);

		return sessions;
	}

	public String getSessionsRootId() {
		return this.sessionsId;
	}

	public ArrayList getSearchablePrototypes() {
		ArrayList names = new ArrayList();        
		Iterator prototypes = this.getPrototypes().iterator();
		Object n = null;
		while (prototypes.hasNext()) {
			n = prototypes.next();
			if (n != null) {
				String name = ((Prototype) n).getName();
				if ((name != null) && (!name.equals("AxiomObject")) && (!name.equals("Global"))) {
					names.add(name);
				}
			}
		}
		return names;
	}

	public static boolean resourceExists(Prototype prototype, String resourceName) {
		while (prototype != null) {
			Resource[] resources = prototype.getResources();
			for (int i = resources.length - 1; i > -1; i--) {
				Resource resource = resources[i];
				if (resource.exists() && resource.getShortName().equals(resourceName))
					return true;
			}
			prototype = prototype.getParentPrototype();
		}

		return false;
	}

	public String getTmpDir() {
		String tmpdir = this.getProperty("tmpdir");
		if (tmpdir == null) {
			tmpdir = System.getProperty("java.io.tmpdir");
		}
		if (tmpdir != null && !tmpdir.endsWith(File.separator)) {
			tmpdir += File.separator;
		}
		return tmpdir;
	}

	public void isAllowed(Object obj, String action, RhinoEngine rhinoEng, 
			RequestEvaluator reqeval) {
		if (!(obj instanceof AxiomObject)) {
			obj = Context.toObject(obj, rhinoEng.getCore().getScope());
		}

		reqeval.getRequest().setAction(action);

		boolean allowed = ActionSecurityManager.isAllowed(this, obj, action, rhinoEng, reqeval);

		if (!allowed) {
			logError("Unauthorized for action " + action + "."); 
			reqeval.getResponse().setStatus(401); 
			throw new RuntimeException("Unauthorized for action " + action + "."); 
		}
	}

    public boolean getOmitXmlDecl() {
    	return omitXmlDecl;
    }
    
    public QueryBean getQueryBean() {
        QueryBean qb = this.qbean;
        RequestEvaluator reqev = this.getCurrentRequestEvaluator();
        if (reqev != null) {
            RhinoEngine re = (RhinoEngine) reqev.scriptingEngine;
            qb.setRhinoCore(re.getCore());
        } else {
            qb.setRhinoCore(null);
        }
        return qb;
    }
    
    public String[][] getRewriteRules() {
        return this.rewriteRules;
    }
    
    public String resolveUrlToPath(String url) {
    	if (url.startsWith(this.getStaticMountpoint())) {
            return url;
        }
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        boolean addedSlash = false;
        if (!url.endsWith("/")) {
            url += "/";
            addedSlash = true;
        }
        String[][] rules = this.getRewriteRules();
        final int length = rules.length;
        for (int i = 0; i < length; i++) {
            if (url.startsWith(rules[i][0])) {
                url = url.replaceFirst(rules[i][0], rules[i][1]);
                break;
            }
        }
        if (addedSlash) {
            url = url.substring(0, url.length() - 1);
        }
        url = url.substring(1);
        
        return url;
    }
    
    protected String[][] setupRewriteRules() {
        String[][] rules = null;
        Iterator iterator = this.getRepositories().iterator();
        while (iterator.hasNext()) {
            BufferedReader br = null;
            boolean found_resource = false;
            try {
                Repository repository = (Repository) iterator.next();
                Resource res = repository.getResource("rewrite.properties");
                if (res != null && res.exists()) {
                    br = new BufferedReader(new InputStreamReader(res.getInputStream()));
                    String line;
                    ArrayList lines = new ArrayList();
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }
                    
                    final int size = lines.size();
                    rules = new String[size][2];
                    for (int i = 0; i < size; i++) {
                        String[] split = lines.get(i).toString().split("=");
                        if (split.length > 1) {
                            split[0] = split[0].trim();
                            split[1] = split[1].trim();
                            if (!split[0].equals("/") && !split[0].endsWith("/")) {
                                split[0] += "/";
                            }
                            if (!split[1].equals("/") && !split[1].endsWith("/")) {
                                split[1] += "/";
                            }
                            rules[i][0] = split[0].trim();
                            rules[i][1] = split[1].trim();
                        } else {
                            rules[i][0] = "";
                            rules[i][1] = "";
                        }
                    }
                    
                    lines.clear();
                    lines = null;
                    
                    found_resource = true;
                }
            } catch (Exception ignore) {
            	logEvent("Error applying rewrite rules: "+ignore.getMessage());
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (Exception ignoreagain) {
                    	logEvent("Error applying rewrite rules: "+ignoreagain.getMessage());
                    }
                    br = null;
                }
            }
            
            if (found_resource) {
                break;
            }
        }
        
        if (rules == null) {
            rules = new String[0][0];
        }
        
        return rules;
    }
    
    public boolean autoUpdate() {
        return this.autoUpdate;
    }
    
    public ClusterCommunicator getClusterCommunicator() {
        return this.clusterComm;
    }
    
    public String getClusterHost() {
        return this.clusterHost;
    }
    
    public ExecutionCache getExecutionCache() {
        return this.executionCache;
    }
    
    public ExecutionCache getTALCache() {
        return this.talCache;
    }
    
    public boolean isFunctionResponseCachable(Scriptable obj, Object result, String func) {
        if (result != null && obj instanceof AxiomObject) {
            INode n = ((AxiomObject) obj).getNode();
            String prototype = n.getPrototype();
            Prototype p = this.getPrototypeByName(prototype);
            if (p.isFunctionResponseCachable(func)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFunctionResultCachable(Scriptable obj, Object result, String func) {

        if (result != null && obj instanceof AxiomObject) {
            INode n = ((AxiomObject) obj).getNode();
            if (n == null) {
                return false;
            }
            String prototype = n.getPrototype();
            Prototype p = this.getPrototypeByName(prototype);
            if (p.isFunctionReturnCachable(func)) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isPropertyFilesIgnoreCase() {
        String s = (String) this.props.get("propertyFilesIgnoreCase");
        if (s != null && "true".equalsIgnoreCase(s)) {
            return true;
        }
        return false;
    }
    
    /**
     * Get the current RequestEvaluator, or null if the calling thread
     * is not evaluating a request.
     *
     * @return the RequestEvaluator belonging to the current thread
     */
    public RequestEvaluator getCurrentRequestEvaluator() {
        return (RequestEvaluator) currentEvaluator.get();
    }

    /**
     * Set the current RequestEvaluator for the calling thread.
     * @param eval the RequestEvaluator belonging to the current thread
     */
    protected void setCurrentRequestEvaluator(RequestEvaluator eval) {
        currentEvaluator.set(eval);
    }
    
    public int getLayer(String host) {
        int mode = DbKey.LIVE_LAYER;
        if (host != null) {
        	Object pos = (Object) this.draftHosts.get(host);
        	if (pos != null) {
        		mode = ((Integer) pos).intValue();
        	}
        }
        return mode;
    }
    
    public int getHighestPreviewLayer() {
    	return this.highestPreviewLayer;
    }
    
    public Object[] getDomainsForLayer(int layer) {
    	if (layer <= DbKey.LIVE_LAYER) {
    		return new Object[0];
    	}
    	
    	String value = this.props.getProperty("draftHost." + layer);
    	if (value != null) {
    		String[] split = value.split(",");
    		Object[] ret = new Object[split.length];
    		for (int i = 0; i < split.length; i++) {
    			ret[i] = split[i].trim();
    		}
    		return ret;
    	}
    	
    	return new Object[0];
    }
    
    public boolean dbTypeExists(String dbType) {
    	if (dbType == null) {
    		return false;
    	}
    	
    	dbType = dbType.toLowerCase();
    	Enumeration e = this.dbProps.keys();
    	while (e.hasMoreElements()) {
    		String key = (String) e.nextElement();
    		if (key.endsWith(".type")) {
    			String value = this.dbProps.getProperty(key);
    			if (value != null && dbType.equals(value.toLowerCase())) {
    				return true;
    			}
    		}
    	}
    	
    	return false;
    }
    
    public ArrayList getDbNames(){
    	ArrayList<String> ret = new ArrayList<String>();
    	Enumeration e = this.dbProps.keys();
    	while (e.hasMoreElements()) {
    		String key = (String) e.nextElement();
    		if(key.indexOf(".") == -1){
    			ret.add(key);
    		}
    	}
    	return ret;
    }
   
    public ArrayList getExtDBTypes(){
    	return extDbTypes;
    }

    private void updateDbLocation(String appName){
    	try{
    		File oldDbDir = new File(dbDir, TransSource.TRANSACTIONS_DB_DIR + "_" + appName);
    		if(oldDbDir.exists()){
    			File appDbDir = new File(dbDir, appName);
    			if(appDbDir.exists()){
    				File appTransDbDir = new File(appDbDir, TransSource.TRANSACTIONS_DB_DIR);
    				if(!appTransDbDir.exists()){
    					appTransDbDir.mkdir();
    				}
    				File[] appFiles = oldDbDir.listFiles();
        			System.out.println("Updating database location on file system...");
					for(int i = 0; i < appFiles.length; i++){
						appFiles[i].renameTo(new File(appTransDbDir, appFiles[i].getName()));
					}
    			}
    		}
    		oldDbDir.delete();
    	}
    	catch(Exception e){
    		System.out.println("Error in updateDb: " + e);
    	}
    }
    
    public String getLogDir(){
    	return logDir;
    }
    
    public String getRequestLogName(){
    	return requestLogName;
    }
 
    public void addContextPath(String path){
    	contextPaths.add(path);
    }
    
    public Server getServer(){
    	return server;
    }
}