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
 * $RCSfile: StandaloneServletClient.java,v $
 * $Author: hannes $
 * $Revision: 1.16 $
 * $Date: 2005/12/12 17:48:44 $
 */

package axiom.servlet;

import java.io.*;
import javax.servlet.*;

import axiom.framework.core.Application;
import axiom.framework.repository.FileRepository;
import axiom.framework.repository.Repository;

/**
 *  Standalone servlet client that runs a Helma application all by itself
 *  in embedded mode without relying on a central instance of axiom.main.Server
 *  to start and manage the application.
 *
 *  StandaloneServletClient takes the following init parameters:
 *     <ul>
 *       <li> application - the application name </li>
 *       <li> appdir - the path of the application home directory </li>
 *       <li> dbdir - the path of the embedded XML data store </li>
 *     </ul>
 */
public final class StandaloneServletClient extends AbstractServletClient {
    private Application app = null;
    private String appName;
    private String appDir;
    private String dbDir;

    /**
     *
     *
     * @param init ...
     *
     * @throws ServletException ...
     */
    public void init(ServletConfig init) throws ServletException {
        super.init(init);

        appName = init.getInitParameter("application");

        if ((appName == null) || (appName.trim().length() == 0)) {
            throw new ServletException("application parameter not specified");
        }

        appDir = init.getInitParameter("appdir");

        if ((appDir == null) || (appDir.trim().length() == 0)) {
            throw new ServletException("appdir parameter not specified");
        }

        dbDir = init.getInitParameter("dbdir");

        if ((dbDir == null) || (dbDir.trim().length() == 0)) {
            throw new ServletException("dbdir parameter not specified");
        }
    }

    /**
     * Returns the {@link axiom.framework.core.Application Applicaton}
     * instance the servlet is talking to.
     *
     * @return this servlet's application instance
     */
    Application getApplication() {
        if (app == null || !app.isRunning()) {
            createApp();
        }

        return app;
    }

    /**
     * Create the application. Since we are synchronized only here, we
     * do another check if the app already exists and immediately return if it does.
     */
    synchronized void createApp() {
        if (app != null && app.isRunning()) {
            return;
        }
        
        if (app != null) {
            app.stop();
            app = null;
        }

        try {
            Repository[] repositories = new Repository[1];
            repositories[0] = new FileRepository(new File(appDir));
            File dbHome = new File(dbDir);

            app = new Application(appName, repositories, dbHome);
            app.init();
            app.start();
        } catch (Exception x) {
            log("Error starting Application " + appName + ": " + x);
            x.printStackTrace();
        }
    }

    /**
     * The servlet is being destroyed. Close and release the application if
     * it does exist.
     */
    public void destroy() {
        if (app != null) {
            try {
                app.stop();
            } catch (Exception x) {
                log("Error shutting down app " + app.getName() + ": ");
                x.printStackTrace();
            }
        }

        app = null;
    }
}
