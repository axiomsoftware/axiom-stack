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

import java.io.Serializable;
import java.util.Date;

import javax.servlet.http.HttpServletResponse;

import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.db.Transactor;

/**
 * The ResponseBean wraps a <code>ResponseTrans</code> object and
 * exposes it to the scripting framework.
 * 
 * @jsinstance res
 */
public class ResponseBean implements Serializable {
    ResponseTrans res;
    RequestEvaluator reqeval;

    /**
     * Creates a new ResponseBean object.
     *
     * @param {ResponseTrans} res The wrapped ResponseTrans
     * @param {RequestEvaluator} reqeval The RequestEvaluator
     */
    public ResponseBean(ResponseTrans res, RequestEvaluator reqeval) {
        this.res = res;
        this.reqeval = reqeval;
    }

    /**
     * Abort the current transaction by throwing an Error.
     *  
     * @jsfunction
     * @throws AbortException
     */
    public void abort() throws AbortException {
        throw new AbortException();
    }

    /**
     * Proxy to HttpServletResponse.addDateHeader().
     * 
     * @jsfunction
     * @param {String} name The header name
     * @param {Date} value The header value
     */
    public void addDateHeader(String name, Date value) {
        res.addDateHeader(name, value);
    }

    /**
     * Proxy to HttpServletResponse.addHeader().
     * 
     * @jsfunction
     * @param {String} name The header name
     * @param {String} value The header value
     */
    public void addHeader(String name, String value) {
        res.addHeader(name, value);
    }

    /**
     * Commit changes made during the course of the current transaction
     * and start a new one.
     *
     * @jsfunction
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
     * Add an HTML formatted debug message to the end of the page.
     *
     * @jsfunction
     * @param {String} message The message
     */
    public void debug(String message) {
        res.debug(message);
    }

    /**
     * The current response buffer as a string.
     * @type String
     */
    public String getBuffer() {
    	return res.getBuffer().toString();
    }

    /**
     * The current cachability setting for this response. 
     * True if the response may be cached by the HTTP client, false otherwise.
     * @type Boolean
     */
    public boolean getCache() {
        return res.isCacheable();
    }

    /**
     * The current charset/encoding name for the response.
     * @type String
     */
    public String getCharset() {
        return res.getCharset();
    }
    
    /**
     * The current content type for the response.
     * @type String
     */
    public String getContentType() {
        return res.getContentType();
    }
    
    /**
     * The cookies associated with this response.
     * @type Object
     */
    public Object getCookies() {
    	return res.getCookiesScriptable();
    }

    /**
     * The current error message for the response, if any.
     * @type String
     */
    public String getError() {
        return res.getError();
    }
    
    /**
     * The ETag for this response.
     * @type String
     */
    public String getETag() {
        return res.getETag();
    }
    
    /**
     * The last modified date for this response.
     * @type Date
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
     * The ServletResponse instance for this Response.
     * Value is null for internal and XML-RPC requests.
     * @type javax.servlet.http.HttpServletResponse
     */
    public HttpServletResponse getServletResponse() {
        return res.getServletResponse();
    }

    /**
     * The HTTP status code for this response.
     * @type Number
     */
    public int getStatus() {
        return res.getStatus();
    }

    /**
     * Redirect the request to a different URL.
     *
     * @jsfunction
     * @param {String} url The URL to redirect to
     * @throws RedirectException to immediately terminate the request
     */
    public void redirect(String url) throws RedirectException {
        res.redirect(url);
    }

    /**
     * Reset the response object, clearing all content previously written to it.
     * 
     * @jsfunction
     */
    public void reset() {
        res.reset();
    }

    /**
     * Rollback the current transaction and start a new one.
     *
     * @jsfunction
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
     * Set cachability setting for this response.
     *
     * @jsfunction
     * @param {Boolean} cache True if the response may be cached by the HTTP client, 
     * 						  false otherwise
     */
    public void setCache(boolean cache) {
        res.setCacheable(cache);
    }

    /**
     * Set the charset/encoding name for the response.
     *
     * @jsfunction
     * @param {String} charset The charset name
     */
    public void setCharset(String charset) {
        res.setCharset(charset);
    }

    /**
     * Set the content type for the response.
     *
     * @jsfunction
     * @param {String} contentType The content type
     */
    public void setContentType(String contentType) {
        res.setContentType(contentType);
    }

    public void setCookie(String key, String value) {
        res.setCookie(key, value, -1, null, null);
    }

    public void setCookie(String key, String value, int days) {
        res.setCookie(key, value, days, null, null);
    }

    public void setCookie(String key, String value, int days, String path) {
        res.setCookie(key, value, days, path, null);
    }

    /**
     * Set a HTTP cookie with the name and value that is only applied to 
     * the URLs matching the given path and is stored by the 
     * HTTP client for the given number of days. A days value of 0 means the
     * cookie should be immediately discarded.
     *
     * @jsfunction
     * @param {String} key The cookie name
     * @param {String} value The cookie value
     * @param {Number} [days] The number of days the cookie should be stored, default to -1,
     * 						  meaning its stored until the session is discarded
     * @param {String} [path] The URL path to apply the cookie to
     * @param {String} [domain] The domain
     */
    public void setCookie(String key, String value, int days, String path, String domain) {
        res.setCookie(key, value, days, path, domain);
    }

    /**
     * Proxy to HttpServletResponse.setDateHeader().
     * 
     * @jsfunction
     * @param {String} name The header name
     * @param {Date} value The header value
     */
    public void setDateHeader(String name, Date value) {
        res.setDateHeader(name, value);
    }

    /**
     * Set the HTTP Etag for this response.
     *
     * @jsfunction
     * @param {String} etag The HTTP ETag
     */
    public void setETag(String etag) {
        res.setETag(etag);
    }

    /**
     * Proxy to HttpServletResponse.setHeader().
     * 
     * @jsfunction
     * @param {String} name The header name
     * @param {String} value The header value
     */
    public void setHeader(String name, String value) {
        res.setHeader(name, value);
    }

    /**
     * Set the last modified date for this response.
     *
     * @jsfunction
     * @param {Date} date The last modified date
     */
    public void setLastModified(Date date) {
        if (date == null) {
            res.setLastModified(-1);
        } else {
            res.setLastModified(date.getTime());
        }
    }

    /**
     * Set the HTTP status code for this response.
     *
     * @jsfunction
     * @param {Number} status The HTTP status code
     */
    public void setStatus(int status) {
        res.setStatus(status);
    }

    /**
     * Immediately stop processing the current request.
     *
     * @jsfunction
     * @throws RedirectException to immediately terminate the request
     */
    public void stop() throws RedirectException {
    	res.redirect(null);
    }

    /**
     * Response's toString() method.
     *
     * @jsfunction
     * @returns {String} A string representation of the response
     */
    public String toString() {
        return "[Response]";
    }
    
    /**
     * Unset a previously set HTTP cookie, causing it to be discarded immedialtely by the 
     * HTTP client.
     *
     * @jsfunction
     * @param {String} key The name of the cookie to be discarded
     */
    public void unsetCookie(String key) {
        res.setCookie(key, "", 0, null, null);
    }

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
     * @jsfunction
     * @param {Object} obj Yhe object to write to the response buffer
     * @param {String} [doctype] The doctype to append to the response,
     *                           in the case of XML objects
     */
    public void write(Object obj, String doctype) {
    	res.write(obj, doctype);
    }

    /**
     * Directly write a byte array to the response buffer without any transformation.
     *
     * @jsfunction
     * @param {byte[]} bytes The byte array to write to the response buffer
     */
    public void writeBinary(byte[] bytes) {
        res.writeBinary(bytes);
    }

    public void writeln() {
        res.writeln();
    }

    /**
     * Write string to response buffer and append a platform dependent newline sequence.
     *
     * @jsfunction
     * @param {String} [str] The string to write to the response buffer, if no string is 
     *                       specified, simply write a newline sequence to the response
     *                       buffer
     */
    public void writeln(String str) {
        res.writeln(str);
    }
    
}