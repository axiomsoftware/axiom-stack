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
 * $RCSfile: ApplicationManager.java,v $
 * $Author: hannes $
 * $Revision: 1.47 $
 * $Date: 2006/04/07 14:40:20 $
 */

package axiom.main;

import org.apache.xmlrpc.XmlRpcHandler;
import org.mortbay.jetty.*;
import org.mortbay.jetty.handler.*;
import org.mortbay.jetty.servlet.*;

import axiom.framework.core.*;
import axiom.framework.repository.FileRepository;
import axiom.framework.repository.Repository;
import axiom.framework.repository.ZipRepository;
import axiom.util.ResourceProperties;
import axiom.util.StringUtils;

import java.io.*;
import java.rmi.*;
import java.util.*;

/**
 * This class is responsible for starting and stopping Axiom applications.
 */
public class ApplicationManager implements XmlRpcHandler {
    private Hashtable descriptors;
    private Hashtable applications;
    private Hashtable xmlrpcHandlers;
    private int rmiPort;
    private Server server;
    private long lastModified;

    private boolean started = false;
    
    /**
     * Creates a new ApplicationManager object.
     *
     * @param props the properties defining the running apps
     * @param server the server instance
     * @param port The RMI port we're binding to
     */
    public ApplicationManager(Server server, int port) {
        this.server = server;
        this.rmiPort = port;
        descriptors = new Hashtable();
        applications = new Hashtable();
        xmlrpcHandlers = new Hashtable();
        lastModified = 0;
    }

    /**
     *  Start an application by name
     */
    public void start(String appName) {
        AppDescriptor desc = new AppDescriptor(appName);
        desc.start();
    }

    /**
     *  Bind an application by name
     */
    public void register(String appName) {
        AppDescriptor desc = (AppDescriptor) descriptors.get(appName);
        if (desc != null) {
            desc.bind();
        }
    }

    /**
     *  Stop an application by name
     */
    public void stop(String appName) {
        AppDescriptor desc = (AppDescriptor) descriptors.get(appName);
        if (desc != null) {
            desc.stop();
        }
    }


    /**
     * Start all applications listed in the properties
     */
    public void startAll() {
        try {
        	ArrayList<AppDescriptor> descriptors = new ArrayList<AppDescriptor>();
        	
        	File[] apps = new File(this.server.getAxiomHome(), "apps").listFiles();
        	for (int i = 0; i < apps.length; i++) {
        		File appPropsFile = new File(apps[i], "app.properties");
        		if (!appPropsFile.exists() || !appPropsFile.canRead()) {
        			continue;
        		}
        		
        		String name = apps[i].getName();
        		if (name.startsWith(".")) {
        			continue;
        		}
        		
        		if (this.applications.get(name) != null) {
        			continue; // this application is already registered with Axiom
        		}
        		
        		AppDescriptor desc = new AppDescriptor(name);
                desc.start();
                descriptors.add(desc);
        	}
        	
        	final int size = descriptors.size();
        	for (int i = 0; i < size; i++) {
        		descriptors.get(i).bind();
        	}
        	
        	if (size > 0) {
        		lastModified = System.currentTimeMillis();
        	}
        	started = true;
        } catch (Exception mx) {
            server.getLogger().error("Error starting applications", mx);
            mx.printStackTrace();
        }
    }

    /**
     *  Stop all running applications.
     */
    public void stopAll() {
        for (Enumeration en = descriptors.elements(); en.hasMoreElements();) {
            try {
                AppDescriptor appDesc = (AppDescriptor) en.nextElement();

                appDesc.stop();
            } catch (Exception x) {
            	x.printStackTrace(System.out);
            }
        }
    }

    /**
     *  Get an array containing all currently running applications.
     */
    public Object[] getApplications() {
        return applications.values().toArray();
    }

    /**
     *  Get an application by name.
     */
    public Application getApplication(String name) {
        return (Application) applications.get(name);
    }

    /**
     * Implements org.apache.xmlrpc.XmlRpcHandler.execute()
     */
    public Object execute(String method, Vector params)
                   throws Exception {
        int dot = method.indexOf(".");

        if (dot == -1) {
            throw new Exception("Method name \"" + method +
                                "\" does not specify a handler application");
        }

        if ((dot == 0) || (dot == (method.length() - 1))) {
            throw new Exception("\"" + method + "\" is not a valid XML-RPC method name");
        }

        String handler = method.substring(0, dot);
        String method2 = method.substring(dot + 1);
        Application app = (Application) xmlrpcHandlers.get(handler);

        if (app == null) {
            app = (Application) xmlrpcHandlers.get("*");
            // use the original method name, the handler is resolved within the app.
            method2 = method;
        }

        if (app == null) {
            throw new Exception("Handler \"" + handler + "\" not found for " + method);
        }

        return app.executeXmlRpc(method2, params);
    }

    private String getMountpoint(String mountpoint) {
        mountpoint = mountpoint.trim();

        if ("".equals(mountpoint)) {
            return "/";
        } else if (!mountpoint.startsWith("/")) {
            return "/" + mountpoint;
        }

        return mountpoint;
    }

    private String joinMountpoint(String prefix, String suffix) {
        if (prefix.endsWith("/") || suffix.startsWith("/")) {
            return prefix+suffix;
        } else {
            return prefix+"/"+suffix;
        }
    }

    private String getPathPattern(String mountpoint) {
        if (!mountpoint.startsWith("/")) {
            mountpoint = "/"+mountpoint;
        }

        if ("/".equals(mountpoint)) {
            return "/";
        }

        if (mountpoint.endsWith("/")) {
            return mountpoint + "*";
        }

        return mountpoint + "/*";
    }

    public boolean getStarted(){
    	return started;
    }
    
    /**
     *  Inner class that describes an application and its start settings.
     */
    class AppDescriptor {

        Application app;

        String appName;
        File appDir;
        String mountpoint;
        String pathPattern;
        String domain;
        Object[] staticDirs;
        String protectedStaticDir;
        String staticMountpoint;
        String xmlrpcHandlerName;
        String cookieDomain;
        String sessionCookieName;
        String protectedSessionCookie;
        String uploadLimit;
        String uploadSoftfail;
        String debug;
        boolean encode;

        /**
         * extend apps.properties, add [appname].ignore
         */
        String ignoreDirs;

        /**
         *  Creates an AppDescriptor from the properties.
         */
        AppDescriptor(String name) {
            appName = name;
            this.appDir = new File("apps/" + name);
        }
        
        private void init() {
        	ResourceProperties conf = app.getProperties();
        	mountpoint = getMountpoint(conf.getProperty("mountpoint", appName));
            pathPattern = getPathPattern(mountpoint);
            domain = conf.getProperty("domain", null);
                       
            ArrayList staticDirNames = new ArrayList();
            String staticArgs = "";
            // Axiom changes
            for(int i = 0; staticArgs != null; i++){ 
                staticArgs = conf.getProperty("static." + i);
                if (staticArgs != null) {
                    staticDirNames.add(staticArgs);
                }
            }
            staticDirs = staticDirNames.toArray();
            // end Axiom changes
            
            staticMountpoint = getPathPattern(conf.getProperty("staticMountpoint",
                                        joinMountpoint(mountpoint, "static")));
            protectedStaticDir = conf.getProperty("protectedStatic");

            cookieDomain = conf.getProperty("cookieDomain");
            sessionCookieName = conf.getProperty("sessionCookieName");
            protectedSessionCookie = conf.getProperty("protectedSessionCookie");
            uploadLimit = conf.getProperty("uploadLimit");
            uploadSoftfail = conf.getProperty("uploadSoftfail");
            debug = conf.getProperty("debug");
            encode = "true".equalsIgnoreCase(conf.getProperty("responseEncoding"));

            // got ignore dirs
            ignoreDirs = conf.getProperty("ignore");
        }

        void start() {
            server.getLogger().info("Building application " + appName);

            try {
                // create the application instance
                app = new Application(appName, server, null, appDir);
                
            	this.init();

                // the application is started later in the register method, when it's bound
                app.init(ignoreDirs);

                // set application URL prefix if it isn't set in app.properties
                if (!app.hasExplicitBaseURI()) {
                    app.setBaseURI(mountpoint);
                }

                // register ourselves
                descriptors.put(appName, this);
                applications.put(appName, app);
                
                app.start();
            } catch (Exception x) {
                server.getLogger().error("Error creating application " + appName, x);
                x.printStackTrace();
            }
        }

        void stop() {
            server.getLogger().info("Stopping application " + appName);

            // unbind application
            unbind();

            // stop application
            try {
                System.out.println("Stopping app " + app.getName());
                app.stop();
                server.getLogger().info("Stopped application " + appName);
            } catch (Exception x) {
                server.getLogger().error("Couldn't stop app", x);
            }

            descriptors.remove(appName);
            applications.remove(appName);
        }

        void bind() {
            try {
                server.getLogger().info("Binding application " + appName);

                // set application URL prefix if it isn't set in app.properties
                if (!app.hasExplicitBaseURI()) {
                    app.setBaseURI(mountpoint);
                }

                // bind to Jetty HTTP server
                if (server.http != null) {     
                    ServletHolder holder = new ServletHolder(new axiom.servlet.EmbeddedServletClient()); 
                    holder.setInitParameter("application", appName);
                    // holder.setInitParameter("mountpoint", mountpoint);
                    if (cookieDomain != null) {
                        holder.setInitParameter("cookieDomain", cookieDomain);
                    }
                    if (sessionCookieName != null) {
                        holder.setInitParameter("sessionCookieName", sessionCookieName);
                    }
                    if (protectedSessionCookie != null) {
                        holder.setInitParameter("protectedSessionCookie", protectedSessionCookie);
                    }
                    if (uploadLimit != null) {
                        holder.setInitParameter("uploadLimit", uploadLimit);
                    }
                    if (uploadSoftfail != null) {
                        holder.setInitParameter("uploadSoftfail", uploadSoftfail);
                    }
                    if (debug != null) {
                        holder.setInitParameter("debug", debug);
                    }

                    String contextPath = new String(); 
                    if(pathPattern != null && pathPattern.indexOf("*") == -1 && pathPattern.length() < 2){
                    	contextPath = pathPattern;
                    }
                    else{
                    	contextPath = pathPattern.substring(0, pathPattern.length()-2);
                    }
                    
                    ServletHandler sh = new ServletHandler();
                    sh.addServletWithMapping(holder, "/*");

                    ContextHandler ch = new ContextHandler(server.contexts, contextPath);
                    ch.addHandler(sh);

                    if (domain != null) {
                    	String[] domains = domain.split(",");
                    	for (int i = 0; i < domains.length; i++) {
                    		domains[i] = domains[i].trim();
                    	}
                    	ch.setVirtualHosts(domains);
                    }
                    
                    if(this.app.getProperty("enableRequestLog") == null 
                    		|| Boolean.parseBoolean(app.getProperty("enableRequestLog"))){
	                    RequestLogHandler requestLogHandler = new RequestLogHandler();
	                    NCSARequestLog requestLog = new NCSARequestLog("log/axiom." + app.getName() + ".request.log");
	                    requestLog.setRetainDays(1);
	                    requestLog.setAppend(true);
	                    requestLog.setExtended(false);
	                    requestLog.setLogTimeZone("GMT");
	                    requestLogHandler.setRequestLog(requestLog);     
	                    ch.addHandler(requestLogHandler);
                    }
                    ch.start();
                    
                    if (encode) {
                        // FIXME: ContentEncodingHandler is broken/removed in Jetty 4.2
                        // context.addHandler(new ContentEncodingHandler());
                        server.getLogger().warn("Warning: disabling response encoding for Jetty 4.2 compatibility");
                    }
                    /* FIXME: What is a protect static dir?
                    if (protectedStaticDir != null) {
                        File protectedContent = new File(protectedStaticDir);
                        if (!protectedContent.isAbsolute()) {
                            protectedContent = new File(server.getAxiomHome(), protectedStaticDir);
                        }
                        context.setResourceBase(protectedContent.getAbsolutePath());
                        server.getLogger().info("Serving protected static from " +
                                       protectedContent.getAbsolutePath());
                        context.addHandler(new ResourceHandler());
                    }
                    */

                    // if there is a static direcory specified, mount it
                    if (staticDirs.length > 0) {
                    	for(int i =0; i < staticDirs.length; i++){
                    		File staticContent = new File((String) staticDirs[i]); 
                    		if (!staticContent.isAbsolute()) {
                    			staticContent = new File(server.getAxiomHome(), (String) staticDirs[i]);
                    		}

                    		server.getLogger().info("Serving static from " +
                    								staticContent.getAbsolutePath());
                    		server.getLogger().info("Mounting static at " +
                    								staticMountpoint);
                    		ch = new ContextHandler(server.contexts, staticMountpoint.substring(0, staticMountpoint.length() - 2));
                            ResourceHandler rh = new ResourceHandler();
                            rh.setResourceBase(staticContent.getAbsolutePath());
                            ch.addHandler(rh);
                            ch.start();
                    	}
                    }
                }
                //xmlrpcHandlers.put(xmlrpcHandlerName, app);
            } catch (Exception x) {
                server.getLogger().error("Couldn't bind app", x);
                x.printStackTrace();
            }
        }

        void unbind() {
            server.getLogger().info("Unbinding application " + appName);

            try {
               // unbind from RMI server
                if (rmiPort > 0) {
                    Naming.unbind("//:" + rmiPort + "/" + appName);
                }
                // unbind from Jetty HTTP server
                if (server.http != null) {
                    Handler[] handlers = server.http.getHandlers();
                    for(int i = 0; i < handlers.length; i++){
                    	if(handlers[i] instanceof Context){
                    		if(handlers[i] != null){
                    			Context context = (Context)handlers[i];
                    			if(context.getContextPath().startsWith(pathPattern.substring(0, pathPattern.length()-2))){
		                			if(!context.isStopped()){
	                    				context.stop();
	                    				context.destroy();
		                			}
                    			}
                    		}
                    	}
                    	
                    }
                }
                // unregister as XML-RPC handler
                if (xmlrpcHandlerName != null) {
                    xmlrpcHandlers.remove(xmlrpcHandlerName);
                }
            } catch (Exception x) {
                server.getLogger().error("Couldn't unbind app", x);
            }

        }

        public String toString() {
            return "[AppDescriptor "+app+"]";
        }
    }
    
}