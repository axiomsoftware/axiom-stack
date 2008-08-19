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
 * $RCSfile: Server.java,v $
 * $Author: hannes $
 * $Revision: 1.101 $
 * $Date: 2006/05/23 10:47:22 $
 */

/* 
 * Modified by:
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */
package axiom.main;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlrpc.*;
import org.mortbay.util.ByteArrayISO8859Writer;
import org.mortbay.util.IO;
import org.mortbay.util.MultiException;
import org.mortbay.util.StringUtil;
import org.mortbay.xml.XmlConfiguration;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.HttpHeaders;
import org.mortbay.jetty.HttpMethods;
import org.mortbay.jetty.MimeTypes;
import org.mortbay.jetty.NCSARequestLog;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.handler.RequestLogHandler;

import axiom.extensions.AxiomExtension;
import axiom.framework.*;
import axiom.framework.core.*;
import axiom.framework.repository.FileResource;
import axiom.objectmodel.db.DbSource;
import axiom.objectmodel.db.TransSource;
import axiom.util.*;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Axiom server main class..
 */
public class Server implements IPathElement, Runnable {

	// version string
    public static final String version = "3.2.5";
    
	protected ContextHandlerCollection contexts = new ContextHandlerCollection();
	protected HandlerCollection handlers = new HandlerCollection();

    // static server instance
    private static Server server;

    // Server home directory
    protected File axiomHome;

    // server-wide properties
    ResourceProperties dbProps;
    ResourceProperties sysProps;

    // our logger
    private Log logger;
    // are we using axiom.util.Logging?
    private boolean axiomLogging;

    // server start time
    public final long starttime;

    private ApplicationManager appManager;
    private Vector extensions;
    private Thread mainThread;

    String websrvPort = null;

    // map of server-wide database sources
    Hashtable dbSources;

    // the embedded web server
    // protected Serve websrv;
    protected org.mortbay.jetty.Server http;

    Thread shutdownhook;

    private org.h2.tools.Server defaultDbServer;


    /**
     * Constructs a new Server instance with an array of command line options.
     */
    public Server(Config config) {
        server = this;
        starttime = System.currentTimeMillis();
        websrvPort = config.websrvPort;
        if (websrvPort == null) {
            try {
                websrvPort = "8080";
            } catch (Exception ignore) {
            }
        }
        
        axiomHome    = config.homeDir;

        // create system properties
        sysProps = new ResourceProperties();
        sysProps.addResource(new FileResource(config.propFile));
    }


    /**
     *  static main entry point.
     */
    public static void main(String[] args) {
        checkJavaVersion();

        Config config = null;
        try {
            config = getConfig(args);
        } catch (Exception cex) {
            printUsageError("error reading configuration: " + cex.getMessage());
            System.exit(1);
        }
        
        checkRunning(config);
        
        // create new server instance
        server = new Server(config);

        // parse properties files etc
        server.init();
        
        // start the server main thread
        server.start();
    }


    /**
      * check if we are running on a Java 2 VM - otherwise exit with an error message
      */
    public static void checkJavaVersion() {
        String javaVersion = System.getProperty("java.version");

        if ((javaVersion == null) || javaVersion.startsWith("1.4")
                                  || javaVersion.startsWith("1.3")
                                  || javaVersion.startsWith("1.2")
                                  || javaVersion.startsWith("1.1")
                                  || javaVersion.startsWith("1.0")) {
            System.err.println("This version of Axiom requires Java 1.6 or greater.");

            if (javaVersion == null) { // don't think this will ever happen, but you never know
                System.err.println("Your Java Runtime did not provide a version number. Please update to a more recent version.");
            } else {
                System.err.println("Your Java Runtime is version " + javaVersion +
                                   ". Please update to a more recent version.");
            }

            System.exit(1);
        }
    }


    /**
      * parse the command line arguments, read a given server.properties file
      * and check the values given for server ports
      * @return Config if successfull
      * @throws Exception on any configuration error
      */
    public static Config getConfig(String[] args) throws Exception {

        Config config = new Config();

        // get possible environment setting for axiom home
        if (System.getProperty("axiom.home")!=null) {
            config.homeDir = new File(System.getProperty("axiom.home"));
        }

        parseArgs(config, args);

        guessConfig(config);

        // create system properties
        ResourceProperties sysProps = new ResourceProperties();
        sysProps.addResource(new FileResource(config.propFile));

        // check if there's a property setting for those ports not specified via command line
        if ((config.websrvPort == null) && (sysProps.getProperty("webPort") != null)) {
            try {
                config.websrvPort = sysProps.getProperty("webPort");
            } catch (Exception portx) {
                throw new Exception("Error parsing web server port property from server.properties: " + portx);
            }
        }
       
        return config;
    }


    /**
      * parse argument list from command line and store values
      * in given Config object
      * @throws Exception when argument can't be parsed into an InetAddrPort
      * or invalid token is given.
      */
    public static void parseArgs(Config config, String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-h") && ((i + 1) < args.length)) {
                config.homeDir = new File(args[++i]);
            } else if (args[i].equals("-f") && ((i + 1) < args.length)) {
                config.propFile = new File(args[++i]);
            } else if (args[i].equals("-p") && ((i + 1) < args.length)) {
            	++i;
            } else if (args[i].equals("-x") && ((i + 1) < args.length)) {
            	++i;
            } else if (args[i].equals("-w") && ((i + 1) < args.length)) {
                try {
                	config.websrvPort = args[i+1];
                	++i;
                } catch (Exception portx) {
                    throw new Exception("Error parsing web server port property: " + portx);
                }
            } else if (args[i].equals("-jk") && ((i + 1) < args.length)) {
            	++i;
            } else if (args[i].equals("-i") && ((i + 1) < args.length)) {
                // eat away the -i parameter which is meant for axiom.main.launcher.Main
                i++;
            } else {
                throw new Exception("Unknown command line token: " + args[i]);
            }
        }
    }


    /**
      * get main property file from home dir or vice versa,
      * depending on what we have
      */
    public static void guessConfig(Config config) throws Exception {
        // get property file from axiomHome:
        if (config.propFile == null) {
            if (config.homeDir != null) {
                config.propFile = new File(config.homeDir, "server.properties");
            } else {
                config.propFile = new File("server.properties");
            }
        }

        // create system properties
        ResourceProperties sysProps = new ResourceProperties();
        sysProps.addResource(new FileResource(config.propFile));

        // try to get axiomHome from property file
        if (config.homeDir == null && sysProps.getProperty("axiomhome") != null) {
            config.homeDir = new File(sysProps.getProperty("axiomhome"));
        }

        // use the directory where server.properties is located:
        if (config.homeDir == null && config.propFile != null) {
            config.homeDir = config.propFile.getAbsoluteFile().getParentFile();
        }

        if (!config.hasPropFile()) {
            throw new Exception ("no server.properties found");
        }

        if (!config.hasHomeDir()) {
            throw new Exception ("couldn't determine Axiom directory");
        }

        // try to transform axiomHome directory to its canonical representation
        try {
            config.homeDir = config.homeDir.getCanonicalFile();
        } catch (IOException iox) {
            config.homeDir = config.homeDir.getAbsoluteFile();
        }
    }


    /**
      * print the usage hints and prefix them with a message.
      */
    public static void printUsageError(String msg) {
        System.out.println(msg);
        printUsageError();
    }


    /**
      * print the usage hints
      */
    public static void printUsageError() {
        System.out.println("");
        System.out.println("Usage: java axiom.main.Server [options]");
        System.out.println("Possible options:");
        System.out.println("  -h dir       Specify axiom home directory");
        System.out.println("  -f file      Specify server.properties file");
        System.out.println("  -w [ip:]port      Specify embedded web server address/port");
        System.out.println("  -x [ip:]port      Specify XML-RPC address/port");
        System.out.println("  -jk [ip:]port     Specify AJP13 address/port");
        System.out.println("  -p [ip:]port      Specify RMI address/port");
        System.out.println("");
        System.out.println("Supported formats for server ports:");
        System.out.println("   <port-number>");
        System.out.println("   <ip-address>:<port-number>");
        System.out.println("   <hostname>:<port-number>");
        System.out.println("");
        System.err.println("Usage Error - exiting");
        System.out.println("");
    }



    /**
     *  Check wheter a server is already running on any of the given ports
     *  - otherwise exit with an error message
     */
    public static void checkRunning(Config config) {
        // check if any of the specified server ports is in use already
        try {
            if (config.websrvPort != null) {
                checkPort(config.websrvPort);
            }
        } catch (Exception running) {
            System.out.println(running.getMessage());
            System.exit(1);
        }
    }


    /**
     *  A primitive method to check whether a server is already running on our port.
     */
    private static void checkPort(String addrPort) throws Exception {
        // checkRunning is disabled until we find a fix for the socket creation
        // timeout problems reported on the list.
        return;
    }


    /**
      * initialize the server
      */
    public void init() {

        // set the log factory property
        String logFactory = sysProps.getProperty("loggerFactory",
                                                 "axiom.util.Logging");

        axiomLogging = "axiom.util.Logging".equals(logFactory);
        // remove comment below to control Jetty Logging, axiom.util.JettyLogger
        // is an implemention of the Jetty Log class, used for logging Jetty error messages
        System.setProperty("org.mortbay.log.class", "axiom.util.JettyLogger");

        System.setProperty("org.apache.commons.logging.LogFactory", logFactory);

        // set the current working directory to the Axiom home dir.
        // note that this is not a real cwd, which is not supported
        // by java. It makes sure relative to absolute path name
        // conversion is done right, so for Axiom code, this should
        // work.
        System.setProperty("user.dir", axiomHome.getPath());

        // from now on it's safe to call getLogger() because axiomHome is set up
        getLogger();

        String startMessage = "Starting Axiom " + version + " on Java " +
                              System.getProperty("java.version");

        logger.info(startMessage);

        // also print a msg to System.out
        System.out.println(startMessage);

        logger.info("Setting Axiom Home to " + axiomHome);


        // read db.properties file in Axiom home directory
        dbProps = new ResourceProperties();
        dbProps.setIgnoreCase(false);
        dbProps.addResource(new FileResource(new File(axiomHome, "db.properties")));
        DbSource.setDefaultProps(dbProps);

        String language = sysProps.getProperty("language");
        String country = sysProps.getProperty("country");
        String timezone = sysProps.getProperty("timezone");

        if ((language != null) && (country != null)) {
            Locale.setDefault(new Locale(language, country));
        }

        if (timezone != null) {
            TimeZone.setDefault(TimeZone.getTimeZone(timezone));
        }

        dbSources = new Hashtable();

        // try to load the extensions
        extensions = new Vector();
        initExtensions();   
        // start the default DB TCP Server with SSL enabled
        try {
            if (this.isDbHostServer()) {
                String h2port = this.sysProps.getProperty("db.port", "9092");
                
                String dbhome = this.sysProps.getProperty("dbHome");
                File dir;
                if (dbhome != null) {
                    dir = new File(dbhome);
                } else {
                    dir = new File(this.axiomHome, "db");
                }
                dir = new File(dir, "TransactionsDB");
                String baseDir = dir.getPath();
                
                System.setProperty("h2.lobFilesPerDirectory", 
                        new Long(Long.MAX_VALUE).toString());

                String[] args = new String[] { "-tcpPort", h2port, "-baseDir", baseDir };
                this.defaultDbServer = org.h2.tools.Server.createTcpServer(args).start();
                System.out.println("Starting H2 TCP Server on port " + h2port);
            }
        } catch (Exception sqle) {
            throw new RuntimeException("FATAL ERROR::Could not start the default " +
                    "H2 database server, " + sqle.getMessage());
        }
    }


    /**
      * initialize extensions
      */
    private void initExtensions() {
        StringBuffer exts = new StringBuffer(sysProps.getProperty("extensions", ""));
        if (exts.length() > 0) {
            exts.append(",");
        }
        exts.append("axiom.extensions.tal.TALExtension");
        
        StringTokenizer tok = new StringTokenizer(exts.toString(), ",");
        while (tok.hasMoreTokens()) {
            String extClassName = tok.nextToken().trim();

            try {
                Class extClass = Class.forName(extClassName);
                AxiomExtension ext = (AxiomExtension) extClass.newInstance();
                ext.init(this);
                extensions.add(ext);
                logger.info("Loaded: " + extClassName);
            } catch (Throwable e) {
                logger.error("Error loading extension " + extClassName + ": " + e.toString());
            }
        }
    }



    public void start() {
    	// Start running, finishing setup and then entering a loop to check changes
        // for new applications
        mainThread = new Thread(this);
        mainThread.start();
    }

    public void stop() {
        mainThread = null;
        
        getLogger().info("Shutting down Axiom");

        appManager.stopAll();

        if (http != null) {
            try {
                http.stop();
                http.destroy();
            } catch (InterruptedException irx) {
                // http.stop() interrupted by another thread. ignore.
            }
            catch(Exception e){
            	System.out.println(e);
            }
        }

        if (axiomLogging) {
            Logging.shutdown();
        }
        
        server = null;
        
        // stop the default DB Server
        if (this.defaultDbServer != null) {
            this.defaultDbServer.stop();
        } else {
            this.shutdownDefaultDb();
        }
        
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownhook);
            // HACK: running the shutdownhook seems to be necessary in order
            // to prevent it from blocking garbage collection of Axiom 
            // classes/classloaders. Since we already set server to null it 
            // won't do anything anyhow.
            shutdownhook.start();
            shutdownhook = null;
        } catch (Exception x) {
            // invalid shutdown hook or already shutting down. ignore.
        }
    }
    
    private void shutdownDefaultDb() {
    	Object apps[] = getApplications();

    	for(int i = 0; i < apps.length; i++){
    		Application app = (Application)apps[i];
	        File db = new File(this.getDbHome(), TransSource.TRANSACTIONS_DB_DIR + "_" + app.getName());
	        StringBuffer url = new StringBuffer(db.getPath());
	        if (!url.toString().endsWith(File.separator)) {
	            url.append(File.separator);
	        }

	        url.append(app.getName() + File.separator + TransSource.TRANSACTIONS_DB_DIR);
	        if (!url.toString().endsWith(File.separator)) {
	            url.append(File.separator);
	        }
	        url.append(TransSource.TRANSACTIONS_DB_NAME);

	        url.insert(0, TransSource.DEFAULT_URL);
	        
	        Connection conn = null;
	        PreparedStatement pstmt = null;
	        try {
	            conn = DriverManager.getConnection(url.toString());
	            pstmt = conn.prepareStatement("SHUTDOWN");
	            pstmt.execute();
	        } catch (Exception ex) {
	            // ignore, if shutdown failed, must not be using the default db
	        } finally {
	            if (pstmt != null) {
	                try { pstmt.close(); } catch (SQLException sqle) { }
	                pstmt = null;
	            }
	            if (conn != null) {
	                try { conn.close(); } catch (SQLException sqle) { }
	                conn = null;
	            }
	        }
    	}
    }
    
    /**
     *  The main method of the Server. Basically, we set up Applications and than
     *  periodically check for changes in the apps.properties file, shutting down
     *  apps or starting new ones.
     */
    public void run() {
        try {
    		if ((websrvPort != null) ) {
                File f = new File(this.axiomHome, "axiom-config.xml");
                if (f.exists() && f.canRead() && f.isFile()) {
                    try {
                    	http = new org.mortbay.jetty.Server();
                        XmlConfiguration config = new XmlConfiguration(f.toURI().toURL());
                        config.configure(http);
                    } catch (Exception ex) { 
                        ex.printStackTrace();
                        throw new RuntimeException("Could not setup the web server, errors in axiom-config.xml");
                    }
                } else {
                	http = new org.mortbay.jetty.Server(Integer.valueOf(websrvPort).intValue());

                }

                RequestLogHandler requestLogHandler = new RequestLogHandler();
//                handlers.setHandlers(new Handler[]{contexts, new DefaultHandler(), requestLogHandler});
                handlers.setHandlers(new Handler[]{contexts, new AxiomHandler(), requestLogHandler});
                http.setHandler(handlers);
                
                if(this.sysProps.getProperty("enableRequestLog") == null 
                		|| Boolean.parseBoolean(this.sysProps.getProperty("enableRequestLog"))){
	                NCSARequestLog requestLog = new NCSARequestLog("log/server" + ".request.log");
	                requestLog.setRetainDays(90);
	                requestLog.setAppend(true);
	                requestLog.setExtended(false);
	                requestLog.setLogTimeZone("GMT");
	                requestLogHandler.setRequestLog(requestLog);
                } 
    		
    		}
 
            appManager = new ApplicationManager(this, 0);
            
            // add shutdown hook to close running apps and servers on exit
            shutdownhook = new AxiomShutdownHook();
            Runtime.getRuntime().addShutdownHook(shutdownhook);
        } catch (Exception x) {
            throw new RuntimeException("Error setting up Server", x);
        }
        // set the security manager.
        // the default implementation is axiom.main.AxiomSecurityManager.
        try {
            String secManClass = sysProps.getProperty("securityManager");

            if (secManClass != null) {
                SecurityManager secMan = (SecurityManager) Class.forName(secManClass)
                                                                .newInstance();

                System.setSecurityManager(secMan);
                logger.info("Setting security manager to " + secManClass);
            }
        } catch (Exception x) {
            logger.error("Error setting security manager", x);
        }

        // start embedded web server
        if (http != null) {
            try {
                http.start();
            } catch (MultiException m) {
                throw new RuntimeException("Error starting embedded web server", m);
            }
            catch(Exception e){
            	System.out.println(e);
                throw new RuntimeException("Error starting embedded web server", e);
            }
        }
        
        // start applications
        appManager.startAll();
        
        while (Thread.currentThread() == mainThread) {
            try {
                Thread.sleep(7000L);
            } catch (InterruptedException ie) {
            }

            try {
                appManager.startAll();
            } catch (Exception x) {
                logger.warn("Caught in app manager loop: " + x);
            }
        }

    }
    
    /**
     * Make sure this server has an ApplicationManager (e.g. used when
     * accessed from CommandlineRunner)
     */
    public void checkAppManager(int port) {
        if (appManager == null) {
            appManager = new ApplicationManager(this, port);
        }
    }

    /**
     *  Get an Iterator over the applications currently running on this Server.
     */
    public Object[] getApplications() {
        return appManager.getApplications();
    }

    /**
     * Get an Application by name
     */
    public Application getApplication(String name) {
        return appManager.getApplication(name);
    }

    /**
     *  Get a logger to use for output in this server.
     */
    public Log getLogger() {
        if (logger == null) {
            if (axiomLogging) {
                // set up system properties for axiom.util.Logging
                String logDir = sysProps.getProperty("logdir", "log");

                if (!"console".equals(logDir)) {
                    // try to get the absolute logdir path

                    // set up axiom.logdir system property
                    File dir = new File(logDir);
                    if (!dir.isAbsolute()) {
                        dir = new File(axiomHome, logDir);
                    }

                    logDir = dir.getAbsolutePath();
                }
                System.setProperty("axiom.logdir", logDir);
            }
            logger = LogFactory.getLog("axiom.server");
        }

        return logger;
    }

    /**
     *  Get the Home directory of this server.
     */
    public File getAxiomHome() {
        return axiomHome;
    }

    /**
     * Get the main Server instance.
     */
    public static Server getServer() {
        return server;
    }

    /**
     *
     *
     * @param key ...
     *
     * @return ...
     */
    public String getProperty(String key) {
        return (String) sysProps.get(key);
    }

    /**
     *
     *
     * @return ...
     */
    public ResourceProperties getProperties() {
        return sysProps;
    }

    /**
     *
     *
     * @return ...
     */
    public ResourceProperties getDbProperties() {
        return dbProps;
    }

    public boolean isDbServerTcp() {
        String dbmode = this.sysProps.getProperty("db.mode");
        return (dbmode != null && "TCP_SERVER".equalsIgnoreCase(dbmode));
    }
    
    public boolean isDbHostServer() {
        return this.isDbServerTcp() && this.getTcpServerHost() == null; 
    }
    
    public String getTcpServerPort() {
        return this.sysProps.getProperty("db.port");
    }
    
    public String getTcpServerHost() {
        return this.sysProps.getProperty("db.host");
    }

    /**
     *
     *
     * @return ...
     */
    public File getAppsHome() {
        String appHome = sysProps.getProperty("appHome", "");

        if (appHome.trim().length() != 0) {
            return new File(appHome);
        } else {
            return new File(axiomHome, "apps");
        }
    }

    /**
     *
     *
     * @return ...
     */
    public File getDbHome() {
        String dbHome = sysProps.getProperty("dbHome", "");

        if (dbHome.trim().length() != 0) {
            return new File(dbHome);
        } else {
            return new File(axiomHome, "db");
        }
    }

    /**
     *
     *
     * @return ...
     */
    public Vector getExtensions() {
        return extensions;
    }

    /**
     *
     *
     * @param name ...
     */
    public void startApplication(String name) {
        appManager.start(name);
        appManager.register(name);
    }

    /**
     *
     *
     * @param name ...
     */
    public void stopApplication(String name) {
        appManager.stop(name);
    }

    /**
     * method from axiom.framework.IPathElement
     */
    public String getElementName() {
        return "root";
    }

    /**
     * method from axiom.framework.IPathElement,
     * returning active applications
     */
    public IPathElement getChildElement(String name) {
        return appManager.getApplication(name);
    }

    /**
     * method from axiom.framework.IPathElement
     */
    public IPathElement getParentElement() {
        return null;
    }

    /**
     * method from axiom.framework.IPathElement
     */
    public String getPrototype() {
        return "root";
    }
    

	public class AxiomHandler extends AbstractHandler {
		
		private String fileName = "default.html";
		
		public AxiomHandler() {
		}		   

		public void handle(String target, HttpServletRequest request,
				HttpServletResponse response, int dispatch) throws IOException,
				ServletException {
			Request base_request = request instanceof Request ? (Request) request
					: HttpConnection.getCurrentConnection().getRequest();

			
			if (response.isCommitted() || base_request.isHandled())
				return;
			base_request.setHandled(true);

			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			response.setContentType(MimeTypes.TEXT_HTML);
			
			ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(1500);
			
			if(!appManager.getStarted()){
				writer.write("<HTML><HEAD><TITLE>axiomstack");
				writer.write("</TITLE><BODY>");
				writer.write("No application on this server matched this request.<br/>");
				writer.write("Please allow axiomstack to finish initializing...<br/>");
				writer.write("</BODY></HTML>");
			}
			else{
			    File f = new File(axiomHome + "/apps", fileName);
			    if(f.exists() && f.canRead() && f.isFile()) {
			    	FileReader fr = new FileReader(f);
			    	int c = 0;
			    	while( (c = fr.read()) != -1){
			    		writer.write(c);
			    	}
			    	fr.close();
			    }
			    else{
					writer.write("<HTML><HEAD><TITLE>axiomstack");
					writer.write("</TITLE><BODY>");
					writer.write("No application on this server matched this request.<br/>");
					writer.write("Known applications are:");
					writer.write("<ul>");
					Object apps[] = getApplications();
					for(int i = 0; i < apps.length; i++){ 
						Application app = (Application)apps[i]; 
						writer.write("<li><a href=\"" + app.getBaseURI() + "\">" + app.getName() + "</a></li>"); 
					}
					writer.write("</ul>");
					writer.write("</BODY></HTML>");
			    }
				
			}
			
			writer.flush();
			response.setContentLength(writer.size());
			OutputStream out = response.getOutputStream();
			writer.writeTo(out);
			out.close();
			
			return;
		}
	}
}