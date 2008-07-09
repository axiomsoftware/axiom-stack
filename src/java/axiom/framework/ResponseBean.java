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
 * $RCSfile: ResponseBean.java,v $
 * $Author: hannes $
 * $Revision: 1.28 $
 * $Date: 2006/01/13 16:50:40 $
 */

package axiom.framework;


import javax.servlet.http.HttpServletResponse;

import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.db.Transactor;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * @jsinstance res
 */
public class ResponseBean implements Serializable {
    ResponseTrans res;
    RequestEvaluator reqeval;

    /**
     * Creates a new ResponseBean object.
     *
     * @param res the wrapped ResponseTrans
     */
    public ResponseBean(ResponseTrans res, RequestEvaluator reqeval) {
        this.res = res;
        this.reqeval = reqeval;
    }

    /**
     * Redirect the request to a different URL
     *
     * @param url the URL to redirect to
     * @throws RedirectException to immediately terminate the request
     */
    public void redirect(String url) throws RedirectException {
        res.redirect(url);
    }

    /**
     * Internally forward the request to a different URL
     *
     * @param url the URL to forward to
     * @throws RedirectException to immediately terminate the request
     */
    /*public void forward(String url) throws RedirectException {
        res.forward(url);
    }*/
    
    /**
     * Immediately stop processing the current request
     *
     * @throws RedirectException to immediately terminate the request
     */
    public void stop() throws RedirectException {
        res.redirect(null);
    }

    /**
     * Reset the response object, clearing all content previously written to it
     */
    public void reset() {
        res.reset();
    }

    /**
     * Returns the ServletResponse instance for this Response.
     * Returns null for internal and XML-RPC requests.
     */
    public HttpServletResponse getServletResponse() {
        return res.getServletResponse();
    }

    /**
     * Set a HTTP cookie with the name and value that is discarded when the 
     * HTTP client is closed
     *
     * @param key the cookie name
     * @param value the cookie value
     */
    public void setCookie(String key, String value) {
        res.setCookie(key, value, -1, null, null);
    }

    /**
     * Set a HTTP cookie with the name and value that is stored by the 
     * HTTP client for the given number of days. A days value of 0 means the
     * cookie should be immediately discarded.
     *
     * @param key the cookie name
     * @param value the cookie value
     * @param days number of days the cookie should be stored
     */
    public void setCookie(String key, String value, int days) {
        res.setCookie(key, value, days, null, null);
    }

    /**
     * Set a HTTP cookie with the name and value that is only applied to 
     * the URLs matching the given path and is stored by the 
     * HTTP client for the given number of days. A days value of 0 means the
     * cookie should be immediately discarded.
     *
     * @param key the cookie name
     * @param value the cookie value
     * @param days number of days the cookie should be stored
     * @param path the URL path to apply the cookie to
     */
    public void setCookie(String key, String value, int days, String path) {
        res.setCookie(key, value, days, path, null);
    }

    /**
     * Set a HTTP cookie with the name and value that is only applied to 
     * the URLs matching the given path and is stored by the 
     * HTTP client for the given number of days. A days value of 0 means the
     * cookie should be immediately discarded.
     *
     * @param key the cookie name
     * @param value the cookie value
     * @param days number of days the cookie should be stored
     * @param path the URL path to apply the cookie to
     * @param domain domain
     */
    public void setCookie(String key, String value, int days, String path, String domain) {
        res.setCookie(key, value, days, path, domain);
    }
    
    /**
     * Unset a previously set HTTP cookie, causing it to be discarded immedialtely by the 
     * HTTP client.
     *
     * @param key the name of the cookie to be discarded
     */
    public void unsetCookie(String key) {
        res.setCookie(key, "", 0, null, null);
    }
    
    public Object getCookies() {
    	return res.getCookiesScriptable();
    }

    /**
     * Write an object to the response buffer.
     *
     * @param obj the object to write to the response buffer
     */
    public void write(Object obj) {
    	if (obj instanceof String) {
    		res.write((String) obj);
    	} else {
    		res.write(obj);
    	}
    }
    
    /**
     * Write an object to the response buffer.
     *
     * @param obj the object to write to the response buffer
     */
    public void write(Object obj, String doctype) {
    	res.write(obj, doctype);
    }
    
    /**
     * Write string to response buffer and append a platform dependent newline sequence.
     *
     * @param str the string to write to the response buffer
     */
    public void writeln(String str) {
        res.writeln(str);
    }
    
    /**
     * Write a platform dependent newline sequence to response buffer.
     */
    public void writeln() {
        res.writeln();
    }

    /**
     * Directly write a byte array to the response buffer without any transformation.
     *
     * @param bytes the string to write to the response buffer
     */
    public void writeBinary(byte[] bytes) {
        res.writeBinary(bytes);
    }

    /**
     * add an HTML formatted debug message to the end of the page.
     *
     * @param message the message
     */
    public void debug(String message) {
        res.debug(message);
    }

    /**
     * Return a string representation for this object
     *
     * @return string representation
     */
    public String toString() {
        return "[Response]";
    }

    // property-related methods
    
    /**
     * Return the current cachability setting for this response
     * 
     * @return true if the response may be cached by the HTTP client, false otherwise
     */
    public boolean getCache() {
        return res.isCacheable();
    }

    /**
     * Set true cachability setting for this response
     *
     * @param cache true if the response may be cached by the HTTP client, false otherwise
     */
    public void setCache(boolean cache) {
        res.setCacheable(cache);
    }

    /**
     * Get the current charset/encoding name for the response
     *
     * @return The charset name
     */
    public String getCharset() {
        return res.getCharset();
    }

    /**
     * Set the charset/encoding name for the response
     *
     * @param charset The charset name
     */
    public void setCharset(String charset) {
        res.setCharset(charset);
    }

    /**
     * Get the current content type name for the response
     *
     * @return the content type
     */
    public String getContentType() {
        return res.getContentType();
    }

    /**
     * Set the content type for the response
     *
     * @param contentType The charset name
     */
    public void setContentType(String contentType) {
        res.setContentType(contentType);
    }

    /**
     * Get the current error message for the response, if any
     *
     * @return the error message
     */
    public String getError() {
        return res.getError();
    }

    /**
     * Get the HTTP status code for this response
     *
     * @return the HTTP status code
     */
    public int getStatus() {
        return res.getStatus();
    }

    /**
     * Set the HTTP status code for this response
     *
     * @param status the HTTP status code
     */
    public void setStatus(int status) {
        res.setStatus(status);
    }

    /**
     * Get the last modified date for this response
     *
     * @return the last modified date
     */
    public Date getLastModified() {
        long modified = res.getLastModified();

        if (modified > -1) {
            return new Date(modified);
        } else {
            return null;
        }
    }

    /**
     * Set the last modified date for this response
     *
     * @param date the last modified date
     */
    public void setLastModified(Date date) {
        if (date == null) {
            res.setLastModified(-1);
        } else {
            res.setLastModified(date.getTime());
        }
    }

    /**
     * Get the ETag for this response
     *
     * @return the HTTP etag
     */
    public String getETag() {
        return res.getETag();
    }

    /**
     * Set the HTTP Etag for this response
     *
     * @param etag the HTTP ETag
     */
    public void setETag(String etag) {
        res.setETag(etag);
    }

   /**
     * Returns the current response buffer as string.
     *
     * @return the response buffer as string
     */
    public String getBuffer() {
        return res.getBuffer().toString();
    }

    /**
     * Commit changes made during the course of the current transaction
     * and start a new one
     *
     * @throws Exception
     */
    public void commit() throws Exception {
    	if (Thread.currentThread() instanceof Transactor) {
            Transactor tx = (Transactor) Thread.currentThread();
            String tname = tx.getTransactionName();
            final int mode = this.reqeval.getLayer();
            tx.commit(mode);
            tx.begin(tname, mode);
        }
    }
    
    /**
     * Rollback the current transaction and start a new one.
     *
     * @throws Exception thrown if rollback fails
     */
    public void rollback() throws Exception {
        if (Thread.currentThread() instanceof Transactor) {
            Transactor tx = (Transactor) Thread.currentThread();
            String tname = tx.getTransactionName();
            final int mode = this.reqeval.getLayer();
            tx.abort();
            tx.begin(tname, mode);
        }
    }

    /**
     * Abort the current transaction by throwing an Error
     *  
     * @throws AbortException
     */
    public void abort() throws AbortException {
        throw new AbortException();
    }
    

    /* Helma 1.6 Changes */
    
    /**
     * Proxy to HttpServletResponse.addHeader()
     * @param name the header name
     * @param value the header value
     */
    public void addHeader(String name, String value) {
        res.addHeader(name, value);
    }

    /**
     * Proxy to HttpServletResponse.addDateHeader()
     * @param name the header name
     * @param value the header value
     */
    public void addDateHeader(String name, Date value) {
        res.addDateHeader(name, value);
    }

    /**
     * Proxy to HttpServletResponse.setHeader()
     * @param name the header name
     * @param value the header value
     */
    public void setHeader(String name, String value) {
        res.setHeader(name, value);
    }

    /**
     * Proxy to HttpServletResponse.setDateHeader()
     * @param name the header name
     * @param value the header value
     */
    public void setDateHeader(String name, Date value) {
        res.setDateHeader(name, value);
    }
    
}