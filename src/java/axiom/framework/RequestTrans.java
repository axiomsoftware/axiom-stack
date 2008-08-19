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
 * $RCSfile: RequestTrans.java,v $
 * $Author: hannes $
 * $Revision: 1.24 $
 * $Date: 2006/05/18 20:54:08 $
 */

/* 
 * Modified by:
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */

package axiom.framework;

import java.io.*;
import java.util.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Undefined;

import axiom.scripting.rhino.RhinoCore;
import axiom.util.Base64;
import axiom.util.StringUtils;

/**
 * A Transmitter for a request from the servlet client. Objects of this
 * class are directly exposed to JavaScript as global property req.
 */
public class RequestTrans implements Serializable {

    static final long serialVersionUID = 5398880083482000580L;

    // HTTP methods
    public final static String GET = "GET";
    public final static String POST = "POST";
    public final static String DELETE = "DELETE";
    public final static String HEAD = "HEAD";
    public final static String OPTIONS = "OPTIONS";
    public final static String PUT = "PUT";
    public final static String TRACE = "TRACE";
    // Axiom pseudo-methods
    public final static String XMLRPC = "XMLRPC";
    public final static String EXTERNAL = "EXTERNAL";
    public final static String INTERNAL = "INTERNAL";

    // the servlet request and response, may be null
    final HttpServletRequest request;
    final HttpServletResponse response;

    // the uri path of the request
    private String path;

    // the request's session id
    private String session;

    // the map of form data
    private Scriptable values = null;
    
    private Scriptable queryParams = null;
    private Scriptable postParams = null;
    private Object postBody = null;
    private Scriptable cookies = null;
    
    // the HTTP request method
    private String method;

    // timestamp of client-cached version, if present in request
    private long ifModifiedSince = -1;

    // set of ETags the client sent with If-None-Match header
    private final Set etags = new HashSet();

    // when was execution started on this request?
    private final long startTime;

    // the name of the action being invoked
    private String action;
    private String httpUsername;
    private String httpPassword;
    
    private boolean rewriteDone = false;

    /**
     *  Create a new Request transmitter with an empty data map.
     */
    public RequestTrans(String method, String path) {
        this.method = method;
        this.path = path;
        this.request = null;
        this.response = null;
        startTime = System.currentTimeMillis();
    }

    /**
     *  Create a new request transmitter with the given data map.
     */
    public RequestTrans(HttpServletRequest request,
                        HttpServletResponse response, String path) {
        this.method = request.getMethod();
        this.request = request;
        this.response = response;
        this.path = path;
        startTime = System.currentTimeMillis();
    }

    /**
     * Return true if we should try to handle this as XML-RPC request.
     *
     * @return true if this might be an XML-RPC request.
     */
    public synchronized boolean checkXmlRpc() {
        return "POST".equals(method) && "text/xml".equals(request.getContentType());
    }

    /**
     * Return true if this request is in fact handled as XML-RPC request.
     * This implies that {@link #checkXmlRpc()} returns true and a matching
     * XML-RPC action was found.
     *
     * @return true if this request is handled as XML-RPC request.
     */
    public synchronized boolean isXmlRpc() {
        return XMLRPC.equals(method);
    }

    /**
     *  Set a parameter value in this request transmitter.
     */
    public void set(String name, Object value) {
        values.put(name, values, value);
    }

    /**
     *  Get a value from the requests map by key.
     */
    public Object get(String name) {
        try {
            Object o = values.get(name, values);
            if (o == Scriptable.NOT_FOUND) {
                return null;
            } else {
                return o;
            }
        } catch (Exception x) {
            return null;
        }
    }

    /**
     *  Get the data map for this request transmitter.
     */
    public Scriptable getRequestData() {
        return values;
    }

    /**
     * Returns the Servlet request represented by this RequestTrans instance.
     * Returns null for internal and XML-RPC requests.
     */
    public HttpServletRequest getServletRequest() {
        return request;
    }

    /**
     * Returns the Servlet response for this request.
     * Returns null for internal and XML-RPC requests.
     */
    public HttpServletResponse getServletResponse() {
        return response;
    }
    
    public String getContentType() {
    	return this.request != null ? this.request.getContentType() : null;
    }

    /**
     *  The hash code is computed from the session id if available. This is used to
     *  detect multiple identic requests.
     */
    public int hashCode() {
        if (session == null || path == null) {
            return super.hashCode();
        } else {
            return 17 + (37 * session.hashCode()) +
                        (37 * path.hashCode());
        }
    }

    /**
     * A request is considered equal to another one if it has the same method,
     * path, session, request data, and conditional get data. This is used to
     * evaluate multiple simultanous identical requests only once.
     */
    public boolean equals(Object what) {
        if (what instanceof RequestTrans) {
            if (session == null || path == null) {
                return super.equals(what);
            } else {
                RequestTrans other = (RequestTrans) what;
                return (session.equals(other.session)
                        && path.equalsIgnoreCase(other.path)
                        && valuesMatch(values, other.values)
                        && valuesMatch(cookies, other.cookies)
                        && ifModifiedSince == other.ifModifiedSince
                        && etags.equals(other.etags));
            }
        }
        return false;
    }
    
    private boolean valuesMatch(Scriptable values, Scriptable ovalues) {
        if (values == ovalues) {
            return true;
        }
        if (values == null && ovalues == null) {
            return true;
        }
        if ((values == null && ovalues != null) || (values != null && ovalues == null)) {
            return false;
        }
        if ((values == Undefined.instance && ovalues != Undefined.instance) ||
                (values != Undefined.instance && ovalues == Undefined.instance)) {
            return false;
        }
        
        Object[] ids = values.getIds();
        Object[] oids = ovalues.getIds();
        final int len = ids.length;
        if (len != oids.length) {
            return false;
        }
        
        for (int i = 0; i < len; i++) {
            String id = (String) ids[i];
            Object o = values.get(id, values);
            Object oo = ovalues.get(id, values);
            if ((o == null && oo != null) || (o != null && oo == null) || !o.equals(oo)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Return the method of the request. This may either be a HTTP method or
     * one of the Axiom pseudo methods defined in this class.
     */
    public synchronized String getMethod() {
        return method;
    }

    /**
     * Set the method of this request.
     *
     * @param method the method.
     */
    public synchronized void setMethod(String method) {
        this.method = method;
    }

    /**
     *  Return true if this object represents a HTTP GET Request.
     */
    public boolean isGet() {
        return GET.equalsIgnoreCase(method);
    }

    /**
     *  Return true if this object represents a HTTP GET Request.
     */
    public boolean isPost() {
        return POST.equalsIgnoreCase(method);
    }

    /**
     * Get the request's session id
     */
    public String getSession() {
        return session;
    }

    /**
     * Set the request's session id
     */
    public void setSession(String session) {
        this.session = session;
    }

    /**
     * Get the request's path
     */
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Get the request's action.
     */
    public String getAction() {
        return action;
    }

    /**
     * Set the request's action.
     */
    public void setAction(String action) {
        this.action = action; 
    }

    /**
     * Get the time the request was created.
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     *
     *
     * @param since ...
     */
    public void setIfModifiedSince(long since) {
        ifModifiedSince = since;
    }

    /**
     *
     *
     * @return ...
     */
    public long getIfModifiedSince() {
        return ifModifiedSince;
    }

    /**
     *
     *
     * @param etagHeader ...
     */
    public void setETags(String etagHeader) {
        if (etagHeader.indexOf(",") > -1) {
            StringTokenizer st = new StringTokenizer(etagHeader, ", \r\n");
            while (st.hasMoreTokens())
                etags.add(st.nextToken());
        } else {
            etags.add(etagHeader);
        }
    }

    /**
     *
     *
     * @return ...
     */
    public Set getETags() {
        return etags;
    }

    /**
     *
     *
     * @param etag ...
     *
     * @return ...
     */
    public boolean hasETag(String etag) {
        if ((etags == null) || (etag == null)) {
            return false;
        }

        return etags.contains(etag);
    }

    /**
     *
     *
     * @return ...
     */
    public String getUsername() {
        if (httpUsername != null) {
            return httpUsername;
        }

        String auth = (String) get("authorization");

        if ((auth == null) || "".equals(auth)) {
            return null;
        }

        decodeHttpAuth(auth);

        return httpUsername;
    }

    /**
     *
     *
     * @return ...
     */
    public String getPassword() {
        if (httpPassword != null) {
            return httpPassword;
        }

        String auth = (String) get("authorization");

        if ((auth == null) || "".equals(auth)) {
            return null;
        }

        decodeHttpAuth(auth);

        return httpPassword;
    }

    private void decodeHttpAuth(String auth) {
        if (auth == null) {
            return;
        }

        StringTokenizer tok;

        if (auth.startsWith("Basic ")) {
            tok = new StringTokenizer(new String(Base64.decode((auth.substring(6)).toCharArray())),
                                      ":");
        } else {
            tok = new StringTokenizer(new String(Base64.decode(auth.toCharArray())), ":");
        }

        try {
            httpUsername = tok.nextToken();
        } catch (NoSuchElementException e) {
            httpUsername = null;
        }

        try {
            httpPassword = tok.nextToken();
        } catch (NoSuchElementException e) {
            httpPassword = null;
        }
    }
    
    public void setData(Scriptable json) {
        this.values = json;
    }
    
    public Scriptable getData() {
        return this.values;
    }
    
    public void setRewriteDone(boolean done) {
        this.rewriteDone = done;
    }
    
    public boolean rewriteDone() {
        return this.rewriteDone;
    }
    
       /**
     * @return get the query parameters for this request
     */
    public Scriptable getQueryParams() {
        return this.queryParams;
    }
    
    /**
     * set the query parameters for this request
     */
    public void setQueryParams(Scriptable json) {
        this.queryParams = json;
    }

    /**
     * @return get the post parameters for this request
     */
    public Scriptable getPostParams() {
        return this.postParams;
    }
    
    public Object getPostBody() {
        return this.postBody;
    }
    
    public void setPostBody(Object obj) {
        this.postBody = obj;
    }

    /**
     * set the post parameters for this request
     */
    public void setPostParams(Scriptable json) {
        this.postParams = json;
    }


    /**
     * Set a cookie
     * @param name the cookie name
     * @param cookie the cookie
     */
    public void setCookies(Scriptable json) {
        this.cookies = json;
    }

    /**
     * @return a scriptable containing the cookies sent with this request
     */
    public Scriptable getCookies() {
        return cookies;
    }
    
    /**
     * Proxy to HttpServletRequest.getHeaders(), returns header values as string array.
     * @param name the header name
     * @return the header values as string array
     */
    public String[] getHeaders(String name) {
        return request == null ?
                null : StringUtils.collect(request.getHeaders(name));
    }

    /**
     * Proxy to HttpServletRequest.getHeader().
     * @param name the header name
     * @return the header value, or null
     */
    public String getHeader(String name) {
        return request == null ? null : request.getHeader(name);
    }

}