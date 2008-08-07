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

import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;

import org.mozilla.javascript.Scriptable;

/**
 * The RequestBean wraps a <code>RequestTrans</code> object and
 * exposes it to the scripting framework.
 * 
 * @jsinstance req
 */
public class RequestBean implements Serializable {
    RequestTrans req;

    /**
     * Creates a new RequestBean object.
     *
     * @param {RequestTrans} req
     */
    public RequestBean(RequestTrans req) {
        this.req = req;
    }

    /**
     * Get the value for a given request parameter.
     *
     * @jsfunction
     * @param {String} name The name of the request parameter
     * @returns {Object} The value for the input request parameter
     */
    public Object get(String name) {
        return req.get(name);
    }

    /**
     * The request's action.
     * @type String
     */
    public String getAction() {
        return req.getAction();
    }

    /**
     * The request's content type.
     * @type String
     */
    public String getContentType() {
    	return this.req.getContentType();
    }

    /**
     * The cookies map containing cookies parsed from post data.
     * @type Object
     */
    public Scriptable getCookies() {
        return req.getCookies();
    }

    /**
     * The request data.
     * @type Object
     */
    public Scriptable getData() {
        return req.getRequestData();
    }

    /**
     * Proxy to HttpServletRequest.getHeader().
     * 
     * @jsfunction
     * @param {String} name The header name
     * @returns {String} The header value, or null
     */
    public String getHeader(String name) {
        return req.getHeader(name);        
    }

    /**
     * Proxy to HttpServletRequest.getHeaders(), returns header values as an array.
     * 
     * @jsfunction
     * @param {String} name The header name
     * @returns {Array} The header values as string array
     */
    public String[] getHeaders(String name) {
        return req.getHeaders(name);
    }

    /**
     * The method of the request. This may either be a HTTP method or
     * one of the Axiom pseudo methods defined in RequestTrans.
     * @type String
     */
    public String getMethod() {
        return req.getMethod();
    }
    
    /**
     * The request's path.
     * @type String
     */
    public String getPath() {
        return req.getPath();
    }
    
    /**
     * The body of the request's post.
     * @type Object
     */
    public Object getPostBody() {
        return req.getPostBody();
    }

    /**
     * The request parameters parsed from post data.
     * @type Object
     */
    public Scriptable getPostParams() {
        return req.getPostParams();
    }

    /**
     * The request parameters parsed from the query string.
     * @type Object
     */
    public Scriptable getQueryParams() {
        return req.getQueryParams();
    }

    /**
     * The amount of time the request has been running for (in milliseconds).
     * @type Number
     */
    public long getRuntime() {
        return (System.currentTimeMillis() - req.getStartTime());
    }
    
    /**
     * Returns the Servlet request represented by this RequestTrans instance.
     * Returns null for internal and XML-RPC requests.
     * @type javax.servlet.http.HttpServletRequest
     */
    public HttpServletRequest getServletRequest() {
        return req.getServletRequest();
    }
    
    /**
     * Returns whether the request is of type HTTP GET.
     *
     * @jsfunction
     * @returns {Boolean} True if is a GET request, false if otherwise
     */
    public boolean isGet() {
        return req.isGet();
    }

    /**
     * Returns whether the request is of type HTTP POST.
     *
     * @jsfunction
     * @returns {Boolean} True if is a POST request, false if otherwise
     */
    public boolean isPost() {
        return req.isPost();
    }
    
    /**
     * Request's toString() method.
     *
     * @jsfunction
     * @returns {String} A string representation of the request
     */
    public String toString() {
        return "[Request]";
    }

}