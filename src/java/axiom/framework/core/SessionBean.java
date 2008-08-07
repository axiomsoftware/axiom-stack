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

import axiom.objectmodel.INode;

/**
 * The SessionBean wraps a <code>Session</code> object and
 * exposes it to the scripting framework.
 * 
 * @jsinstance session
 */
public class SessionBean implements Serializable {
    // the wrapped session object
    Session session;

    /**
     * Creates a new SessionBean around a Session object.
     *
     * @param {Session} session
     */
    public SessionBean(Session session) {
        this.session = session;
    }

    public void addDraftId(Object id) {
        session.addDraftId(id, null);
    }

    /**
     * Turn on object layering for the AxiomObject id specified, on a given layer.
     * This method adds to the current list of draft ids, and does not replace any of the
     * previously set draft ids. 
     * 
     * @jsfunction
     * @param {String} id The AxiomObject id on which object layering should be 
     *                    turned on for the given layer
     * @param {String|Number} [layer] The layer on which to turn object layering on for the
     *                                input objects. A String indicates a domain that maps 
     *                                to a layer through the draftHost property in 
     *                                app.properties. A Number is a direction specification
     *                                of the layer. If no layer is specified, defaults to
     *                                the layer on which the call is being executed
     */
    public void addDraftId(Object id, Object layer) {
        session.addDraftId(id, layer);
    }

    public void clearDraftIds() {
    	session.clearDraftIds(null);
    }
    
    /**
     * Clear the AxiomObject ids that have layering set at the given layer.
     * 
     * @jsfunction
     * @param {String|Number} [layer] The layer on which to turn object layering on for the
     *                                input objects. A String indicates a domain that maps 
     *                                to a layer through the draftHost property in 
     *                                app.properties. A Number is a direction specification
     *                                of the layer. If no layer is specified, defaults to
     *                                the layer on which the call is being executed
     */
    public void clearDraftIds(Object layer) {
    	session.clearDraftIds(layer);
    }

    /**
     * The unique identifier for a session object (session cookie).
     * @type String
     */
    public String get_id() {
        return session.getSessionId();
    }

    /**
     * The cache/data node for this session. This object may be used
     * to store transient per-session data. 
     * @type Object
     */
    public INode getData() {
        return session.getCacheNode();
    }

    /** 
     * The <code>Debug</code> object for this session, only applicable if in Rhino debug
     * mode (i.e. rhino.debugger = true in app.properties).
     * @type Debug
     */
    public Object getDebug() {
    	return session.getDebugObject();
    }
    
    /**
     * @jsomit
     * See below.
     */
    public Object getDraftIds() {
        return session.getDraftIds(null);
    }

    /**
     * Returns the AxiomObject ids for which layering is turned on at the given layer.
     * 
     * @jsfunction
     * @param {String|Number} [layer] The layer on which to get the AxiomObject ids for 
     *                                which object layering is turned on. 
     *                                A String indicates a domain that maps 
     *                                to a layer through the draftHost property in 
     *                                app.properties. A Number is a direction specification
     *                                of the layer. If no layer is specified, defaults to
     *                                the layer on which the call is being executed
     * @returns {Array} A list of AxiomObject ids
     */
    public Object getDraftIds(Object layer) {
        return session.getDraftIds(layer);
    }

    /**
     * The http_referer that is used for determining where the user should be
     * redirected to when logging in successfully after trying to view unauthorized
     * material.
     * @type String
     */
    public String getHttpReferer(){
    	return session.getHttpReferer();
    }

    /**
     * The time this session was last touched.
     * @type Date
     */
    public Date getLastActive() {
        return new Date(session.lastTouched());
    }

    /**
     * The date at which the session was created or a login or
     * logout was performed the last time.
     * @type Date
     */
    public Date getLastModified() {
        return new Date(session.lastModified());
    }

    /**
     * The date at which a user's session was started.
     * @type Date
     */
    public Date getOnSince() {
        return new Date(session.onSince());
    }

    /**
     * The user object for this session. Value is null unless 
     * the session.login method was previously invoked.
     * @type Object
     */
    public INode getUser() {
        return session.getUserNode();
    }

    /**
     * Associates the current session with the user object.
     *
     * @jsfunction
     * @param {AxiomObject} userNode The AxiomObject node representing the user
     */
    public void login(INode userNode) {
        session.login(userNode);
    }
    
    /**
     * Disassociate this session from any user object it may have been associated with.
     * 
     * @jsfunction
     */
    public void logout() {
        session.logout();
    }

    /**
     * Same as logout(), and also clears the data object (session.data) 
     * and Debug object associated with this session.
     * 
     * @jsfunction
     */
    public void reset() {
        session.reset();
    }
    
    public void setDraftIds(Object arr) {
    	session.setDraftIds(arr, null);
    }
    
    /**
     * Turn on object layering for the AxiomObject ids specified in arr on given layer.
     * 
     * @jsfunction
     * @param {Array} arr An array of AxiomObject ids on which object layering should be 
     *                    turned on for the given layer
     * @param {String|Number} [layer] The layer on which to turn object layering on for the
     *                                input objects. A String indicates a domain that maps 
     *                                to a layer through the draftHost property in 
     *                                app.properties. A Number is a direction specification
     *                                of the layer. If no layer is specified, defaults to
     *                                the layer on which the call is being executed
     */
    public void setDraftIds(Object arr, Object layer) {
        session.setDraftIds(arr, layer);
    }
    
    /**
     * Set the http_referer on to the session.  Http_referer refers to the
     * URL the user requested and was unauthorized for.  When the user authenticates,
     * the url can be retrieved to redirect the user to their original request.
     * 
     * @jsfunction
     * @param {String} httpRef The http_referer
     */
    public void setHttpReferer(String httpRef){
    	session.setHttpReferer(httpRef);
    }
    
    /**
     * Session's toString() method.
     *
     * @jsfunction
     * @returns {String} A string representation of the session
     */
    public String toString() {
        return session.toString();
    }
    
    /**
     * Touching the session marks it as active, avoiding session timeout.
     * Usually, sessions are touched when the user associated with it sends
     * a request. This method may be used to artificially keep a session alive.
     * 
     * @jsfunction
     */
    public void touch() {
        session.touch();
    }
    
}