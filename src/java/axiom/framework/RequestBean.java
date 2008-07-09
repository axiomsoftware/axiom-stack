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
 * $RCSfile: RequestBean.java,v $
 * $Author: hannes $
 * $Revision: 1.11 $
 * $Date: 2005/08/18 22:55:30 $
 */

package axiom.framework;

import javax.servlet.http.HttpServletRequest;

import org.mozilla.javascript.Scriptable;

import java.io.Serializable;
import java.util.Map;

/**
 * @jsinstance req
 */
public class RequestBean implements Serializable {
    RequestTrans req;

    /**
     * Creates a new RequestBean object.
     *
     * @param req ...
     */
    public RequestBean(RequestTrans req) {
        this.req = req;
    }

    /**
     *
     *
     * @param name ...
     *
     * @return ...
     */
    public Object get(String name) {
        return req.get(name);
    }


    /**
     * Return the method of the request. This may either be a HTTP method or
     * one of the Helma pseudo methods defined in RequestTrans.
     */
    public String getMethod() {
        return req.getMethod();
    }

    /**
     *
     *
     * @return ...
     */
    public boolean isGet() {
        return req.isGet();
    }

    /**
     *
     *
     * @return ...
     */
    public boolean isPost() {
        return req.isPost();
    }

    /**
     * Returns the Servlet request represented by this RequestTrans instance.
     * Returns null for internal and XML-RPC requests.
     */
    public HttpServletRequest getServletRequest() {
        return req.getServletRequest();
    }

    /**
     *
     *
     * @return ...
     */
    public String toString() {
        return "[Request]";
    }

    // property related methods:
    public String getAction() {
        return req.getAction();
    }

    /**
     *
     *
     * @return ...
     */
    public Scriptable getData() {
        return req.getRequestData();
    }
    
    public Object getPostBody() {
        return req.getPostBody();
    }
    
    /**
     *
     *
     * @return ...
     */
    public long getRuntime() {
        return (System.currentTimeMillis() - req.getStartTime());
    }

    /**
     *
     *
     * @return ...
     */
    public String getPath() {
        return req.getPath();
    }

    /* Helma 1.6 Changes */
    
    /**
     * @return the req.queryParams map containing parameters parsed from the query string
     */
    public Scriptable getQueryParams() {
        return req.getQueryParams();
    }

    /**
     * @return the req.postParams map containing params parsed from post data
     */
    public Scriptable getPostParams() {
        return req.getPostParams();
    }
    
    public String getContentType() {
    	return this.req.getContentType();
    }
    
    /**
     * @return the req.cookies map containing cookies parsed from post data
     */
    public Scriptable getCookies() {
        return req.getCookies();
    }

    /**
     * Proxy to HttpServletRequest.getHeaders(), returns header values as string array.
     * @param name the header name
     * @return the header values as string array
     */
    public String[] getHeaders(String name) {
        return req.getHeaders(name);
    }
    
    /**
     * Proxy to HttpServletRequest.getHeader().
     * @param name the header name
     * @return the header value, or null
     */
    public String getHeader(String name) {
        return req.getHeader(name);        
    }

}