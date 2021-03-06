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
 * $RCSfile: SessionManager.java,v $
 * $Author: hannes $
 * $Revision: 1.5 $
 * $Date: 2006/05/24 12:29:09 $
 */
package axiom.framework.core;


import java.util.*;
import java.io.*;

import axiom.framework.ErrorReporter;
import axiom.objectmodel.INode;
import axiom.objectmodel.db.Node;
import axiom.scripting.ScriptingEngine;

public class SessionManager {

    protected Hashtable<String, Session> sessions;

    protected Application app;

    public SessionManager() {
        sessions = new Hashtable<String, Session>();
    }

    public void init(Application app) {
        this.app = app;
    }

    public void shutdown() {
        sessions.clear();
    }

    public Session createSession(String sessionId) {
        Session session = getSession(sessionId);
        
        if (session == null) {
            session = new Session(sessionId, app); 
            sessions.put(sessionId, session);
        }
        
        return session;
    }

    public Session getSession(String sessionId) {
        if (sessionId == null)
            return null;

        return (Session) sessions.get(sessionId);
    }

    /**
     *  Return the whole session map. We return a clone of the table to prevent
     * actual changes from the table itself, which is managed by the application.
     * It is safe and allowed to manipulate the session objects contained in the table, though.
     */
    public Map<String, Session> getSessions() {
        return (Map) sessions.clone();
    }

    /**
     * Returns the number of currenty active sessions.
     */
    public int countSessions() {
        return sessions.size();
    }

    /**
     * Remove the session from the sessions-table and logout the user.
     */
    public void discardSession(Session session) {
        logoutSession(session);
        sessions.remove(session.getSessionId());
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
            INode users = app.getUserRoot();
            Node unode = (Node) users.getChildElement(uname);
            String pw = unode.getString("password");

            if ((pw != null) && pw.equals(password)) {
                // let the old user-object forget about this session
                logoutSession(session);
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
     * Return an array of <code>SessionBean</code> objects currently associated with a given
     * Axiom user.
     */
    public List<SessionBean> getSessionsForUsername(String username) {
        ArrayList<SessionBean> list = new ArrayList<SessionBean>();

        if (username == null) {
            return list;
        }

        for (Session s : sessions.values()) {
            if (s != null && username.equals(s.getUID())) {
                // append to list if session is logged in and fits the given username
                list.add(new SessionBean(s));
            }
        }

        return list;
    }

    /**
     * Return a list of Axiom nodes (AxiomObjects -  the database object representing the user,
     *  not the session object) representing currently logged in users.
     */
    public List<INode> getActiveUsers() {
        ArrayList<INode> list = new ArrayList<INode>();

        for (Session s : sessions.values()) {
            if (s != null && s.isLoggedIn()) {
                // returns a session if it is logged in and has not been
                // returned before (so for each logged-in user is only added once)
                INode node = s.getUserNode();

                // we check again because user may have been logged out between the first check
                if (node != null && !list.contains(node)) {
                    list.add(node);
                }
            }
        }

        return list;
    }


    /**
     * Dump session state to a file.
     *
     * @param f the file to write session into, or null to use the default sesssion store.
     */
    public void storeSessionData(File f, ScriptingEngine engine) {
        if (f == null) {
            f = new File(app.dbDir, "sessions");
        }

        try {
            OutputStream ostream = new BufferedOutputStream(new FileOutputStream(f));
            ObjectOutputStream p = new ObjectOutputStream(ostream);

            synchronized (sessions) {
                p.writeInt(sessions.size());

                for (Session s : sessions.values()) {
                    try {
                        engine.serialize(s, p);
                        // p.writeObject(e.nextElement());
                    } catch (NotSerializableException nsx) {
                        // not serializable, skip this session
                        app.logError(ErrorReporter.errorMsg(this.getClass(), "storeSessionData") 
                        		+ "Error serializing session.", nsx);
                    }
                }
            }

            p.flush();
            ostream.close();
            app.logEvent("stored " + sessions.size() + " sessions in file");
        } catch (Exception e) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "storeSessionData") 
            		+ "error storing session data.", e);
        }
    }

    /**
     * loads the serialized session table from a given file or from dbdir/sessions
     */
    public void loadSessionData(File f, ScriptingEngine engine) {
        if (f == null) {
            f = new File(app.dbDir, "sessions");
        }

        // compute session timeout value
        int sessionTimeout = 30;

        try {
            sessionTimeout = Math.max(0,
                                      Integer.parseInt(app.getProperty("sessionTimeout",
                                                                         "30")));
        } catch (Exception ignore) {
            System.out.println(ignore.toString());
        }

        long now = System.currentTimeMillis();

        try {
            // load the stored data:
            InputStream istream = new BufferedInputStream(new FileInputStream(f));
            ObjectInputStream p = new ObjectInputStream(istream);
            int size = p.readInt();
            int ct = 0;
            Hashtable<String, Session> newSessions = new Hashtable<String, Session>();

            while (ct < size) {
                Session session = (Session) engine.deserialize(p);

                if ((now - session.lastTouched()) < (sessionTimeout * 60000)) {
                    session.setApp(app);
                    newSessions.put(session.getSessionId(), session);
                }

                ct++;
            }

            p.close();
            istream.close();
            sessions = newSessions;
            app.logEvent("loaded " + newSessions.size() + " sessions from file");
        } catch (FileNotFoundException fnf) {
            // suppress error message if session file doesn't exist
        } catch (Exception e) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "loadSessionData") 
            		+ "error loading session data.", e);
        }
    }

    public void expire(Session session){    	
    }
}
