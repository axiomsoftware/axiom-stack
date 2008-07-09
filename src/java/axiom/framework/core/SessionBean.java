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
 * $RCSfile: SessionBean.java,v $
 * $Author: michi $
 * $Revision: 1.13 $
 * $Date: 2005/11/29 11:22:26 $
 */

package axiom.framework.core;


import java.io.Serializable;
import java.util.Date;

import org.mozilla.javascript.Scriptable;

import axiom.objectmodel.INode;

/**
 * The SessionBean wraps a <code>Session</code> object and
 * exposes it to the scripting framework.
 * @jsinstance session
 */
public class SessionBean implements Serializable {
    // the wrapped session object
    Session session;

    /**
     * Creates a new SessionBean around a Session object.
     *
     * @param session ...
     */
    public SessionBean(Session session) {
        this.session = session;
    }

    /**
     *
     *
     * @return ...
     */
    public String toString() {
        return session.toString();
    }

    /**
     * Attempts to log in a user with the given username/password credentials.
     * If username and password match, the user node is associated with the session
     * and bound to the session.user property.
     *
     * @param username the username
     * @param password the password
     *
     * @return true if the user exists and the password matches the user's password property.
     */
    /*public boolean login(String username, String password) {
        return session.login(username, password);
    }*/

    /**
     * Directly associates the session with a user object without requiring
     * a username/password pair. This is for applications that use their own
     * authentication mechanism.
     *
     * @param userNode the AxiomObject node representing the user.
     */
    public void login(INode userNode) {
        session.login(userNode);
    }

    /**
     * Disassociate this session from any user object it may have been associated with.
     */
    public void logout() {
        session.logout();
    }
    
    public void reset() {
        session.reset();
    }

    /**
     * Touching the session marks it as active, avoiding session timeout.
     * Usually, sessions are touched when the user associated with it sends
     * a request. This method may be used to artificially keep a session alive.
     */
    public void touch() {
        session.touch();
    }

    /**
     * Returns the time this session was last touched.
     *
     * @return ...
     */
    public Date lastActive() {
        return new Date(session.lastTouched());
    }

    /**
     * Returns the time this session was created.
     *
     * @return ...
     */
    public Date onSince() {
        return new Date(session.onSince());
    }

    // property-related methods:

    /**
     * Get the cache/data node for this session. This object may be used
     * to store transient per-session data. It is reflected to the scripting
     * environment as session.data.
     */
    public INode getData() {
        return session.getCacheNode();
    }
    
    /** 
     * Get the debug object for this session
     */
    public Object getDebug() {
    	return session.getDebugObject();
    }

    /**
     * Gets the user object for this session. This method returns null unless
     * one of the session.login methods was previously invoked.
     *
     * @return ...
     */
    public INode getUser() {
        return session.getUserNode();
    }

    /**
     * Returns the unique identifier for a session object (session cookie).
     *
     * @return ...
     */
    public String get_id() {
        return session.getSessionId();
    }

    /**
     * Returns the unique identifier for a session object (session cookie).
     *
     * @return ...
     */
    public String getCookie() {
        return session.getSessionId();
    }

    /**
     * Returns the time this session was last touched.
     *
     * @return ...
     */
    public Date getLastActive() {
        return new Date(session.lastTouched());
    }

    /**
     * Returns a date object representing the time a user's session was started.
     *
     * @return ...
     */
    public Date getOnSince() {
        return new Date(session.onSince());
    }

    /**
     * Gets the date at which the session was created or a login or
     * logout was performed the last time.
     *
     * @return ...
     */
    public Date getLastModified() {
        return new Date(session.lastModified());
    }

    /**
     * Set's the http_referer on the session
     * 
     * @param httpRef
     */
    
    public void setHttpReferer(String httpRef){
    	session.setHttpReferer(httpRef);
    }
    
    /**
     * Returns the http_referer on the session
     * 
     */
    public String getHttpReferer(){
    	return session.getHttpReferer();
    }
    
    public void setDraftIds(Object arr) {
    	session.setDraftIds(arr, null);
    }

    public void setDraftIds(Object arr, Object layer) {
        session.setDraftIds(arr, layer);
    }
    
    public void clearDraftIds() {
    	session.clearDraftIds(null);
    }
    
    public void clearDraftIds(Object layer) {
    	session.clearDraftIds(layer);
    }
    
    public void addDraftId(Object id) {
        session.addDraftId(id, null);
    }
    
    public void addDraftId(Object id, Object layer) {
        session.addDraftId(id, layer);
    }
    
    public Object getDraftIds() {
        return session.getDraftIds(null);
    }
    
    public Object getDraftIds(Object layer) {
        return session.getDraftIds(layer);
    }
    
}