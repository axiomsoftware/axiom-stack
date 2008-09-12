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
 * $RCSfile: ResponseTrans.java,v $
 * $Author: hannes $
 * $Revision: 1.71 $
 * $Date: 2006/05/18 20:54:08 $
 */

/* 
 * Modified by:
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */

package axiom.framework;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.*;

import javax.servlet.http.HttpServletResponse;

import org.apache.xmlrpc.XmlRpcResponseProcessor;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.ScriptRuntimeWrapper;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import axiom.framework.core.Application;
import axiom.util.Base64;
import axiom.util.HtmlEncoder;
import axiom.util.MD5Encoder;
import axiom.util.SystemMap;
import axiom.util.XmlUtils;

import java.util.regex.*;

/**
 * A Transmitter for a response to the servlet client. Objects of this
 * class are directly exposed to JavaScript as global property res.
 */
public final class ResponseTrans extends Writer implements Serializable {

    static final long serialVersionUID = -8627370766119740844L;
    static final int INITIAL_BUFFER_SIZE = 2048;

    static final String newLine = System.getProperty("line.separator");

    //  MIME content type of the response.
    private String contentType = "text/html";

    // Charset (encoding) to use for the response.
    private String charset;

    // Used to allow or disable client side caching
    private boolean cacheable = true;

    // HTTP response code, defaults to 200 (OK).
    private int status = 200;

    // the actual response
    private byte[] response = null;

    // contains the redirect URL
    private String redir = null;

    // the forward (internal redirect) URL
    private String forward = null;

    // the last-modified date, if it should be set in the response
    private long lastModified = -1;

    // flag to signal that resource has not been modified
    private boolean notModified = false;

    // Entity Tag for this response, used for conditional GETs
    private String etag = null;

    // cookies
    Map cookies;

    // the buffer used to build the response
    private transient StringBuffer buffer = null;
    
    // allow an input stream to be used for the response
    private transient InputStream istream = null;
    
    // manually set inputStreamLength
    private transient int inputStreamLength = 0;

    // an idle StringBuffer waiting to be reused
    private transient StringBuffer cachedBuffer = null;

    // buffer for debug messages - will be automatically appended to response
    private transient StringBuffer debugBuffer;

    // field for error message
    private transient String error;

    // the request trans for this response
    private transient RequestTrans reqtrans;

    // the message digest used to generate composed digests for ETag headers
    private transient MessageDigest digest;

    // the application
    Application app;


    /**
     * Creates a new ResponseTrans object.
     *
     * @param req the RequestTrans for this response
     */
    public ResponseTrans(Application app, RequestTrans req) {
        this.app = app;
        reqtrans = req;
    }
    
    public void finalize() throws Throwable {
        super.finalize();
        this.closeInputStream();
    }

    /**
     * Returns the ServletResponse instance for this ResponseTrans.
     * Returns null for internal and XML-RPC requests.
     */
    public HttpServletResponse getServletResponse() {
        return reqtrans.getServletResponse();
    }

    /**
     * Reset the response object to its initial empty state.
     */
    public synchronized void reset() {
        if (buffer != null) {
            buffer.setLength(0);
        }

        response = null;
        cacheable = true;
        redir = forward = error = null;
        etag = charset = null;
        contentType =  "text/html";
        lastModified = -1;
        notModified = false;
        cookies = null;

        if (digest != null) {
            digest.reset();
        }
    }

    /**
     *  Get the response buffer, creating it if it doesn't exist
     */
    public synchronized StringBuffer getBuffer() {
        if (buffer == null) {
            buffer = new StringBuffer(INITIAL_BUFFER_SIZE);
        }

        return buffer;
    }

    /**
     * Append a string to the response unchanged.
     */
    public synchronized void write(String str) {
        if (str != null) {
            if (buffer == null) {
                buffer = new StringBuffer(Math.max(str.length() + 100, INITIAL_BUFFER_SIZE));
            }
            buffer.append(str);
        }
    }

    /**
     * Appends a objct to the response unchanged.
     * The object is first converted to a string.
     */
    public void write(Object what) {
        if (what != null) {
            if (what instanceof Scriptable) {
                this.write((Scriptable) what);
            } else if (what instanceof Number) { 
            	this.write((Number) what);
            } else {
                this.write(what.toString());
            }
        }
    }
    
    private void write(Number number) {
    	if (number.doubleValue() - (double) number.longValue() != 0.0) {
    		this.write(number.toString());
    	} else {
    		Long l = new Long(number.longValue());
    		this.write(l.toString());
    	}
    }
    
    /**
     * Appends a javascript object to the response buffer, serialization is based on 
     */
    public void write(Scriptable jsobj) {
        if (jsobj != null && jsobj != Undefined.instance) {
        	String className = jsobj.getClassName();
        	if (className.equalsIgnoreCase("XML") || className.equalsIgnoreCase("XMLList")) {
        	    // allow bare ampersands within title tags and urls
                String result = XmlUtils.objectToXMLString(jsobj);
                if(this.contentType.startsWith("text/html")){
                	Matcher urlAndTitleMatcher = Pattern.compile("(((href|src|value)=\"[^\"]*)|<title>[^<]*)").matcher(result);
                	StringBuffer newResult = new StringBuffer();
                	while(urlAndTitleMatcher.find()){
                		urlAndTitleMatcher.appendReplacement(newResult, urlAndTitleMatcher.group(0).replaceAll("&amp;", "&").replaceAll("\\$", "\\\\\\$"));
                	}
                	urlAndTitleMatcher.appendTail(newResult);
                	result = newResult.toString();

                	// fix self closing tags
                    result = result.replaceAll("\\<(?!img|br|hr|input|frame|link|meta|param)(\\w+)([^\\<]*)/\\>","<$1$2></$1>");
                }
        		String doctype = app.getProperty("doctype");
        		this.write(((doctype != null)?doctype+"\n":"\n")+result);
        	} else if (className.equalsIgnoreCase("String")) {
        		this.write(ScriptRuntime.toString(jsobj));
        	} else if (className.equalsIgnoreCase("Date")) {
        		this.write(new Date((long) ScriptRuntime.toNumber(jsobj)));
        	} else if (className.equalsIgnoreCase("Boolean")) {
        		this.write(ScriptRuntime.toBoolean(jsobj));
        	} else if (className.equalsIgnoreCase("Number")) {
        		this.write(ScriptRuntime.toNumber(jsobj));
        	} else {
	            Context cx = Context.getCurrentContext();
	            this.contentType = "text/json";
	            Object f = ScriptableObject.getProperty(jsobj, "toSource");
                Object ret = ((Function) f).call(cx, jsobj, jsobj, new Object[0]);
	            this.write(ret.toString());
        	}
        }
    }
    
    /**
     * Appends a javascript object to the response buffer, serialization is based on 
     */
    public void write(Object obj, String doctype) {
    	Scriptable jsobj = (Scriptable)obj;
        if (jsobj != null && jsobj != Undefined.instance) {
        	if (jsobj.getClassName().equalsIgnoreCase("XML") || jsobj.getClassName().equalsIgnoreCase("XMLList")) {
        		// allow bare ampersands within title tags and urls
                String result = XmlUtils.objectToXMLString(jsobj);
                Matcher urlAndTitleMatcher = Pattern.compile("(((href|src|value)=\"[^\"]*)|<title>[^<]*)").matcher(result);
                StringBuffer newResult = new StringBuffer();
                while(urlAndTitleMatcher.find()){
                    urlAndTitleMatcher.appendReplacement(newResult, urlAndTitleMatcher.group(0).replaceAll("&amp;", "&").replaceAll("\\$", "\\\\\\$"));
                }
                urlAndTitleMatcher.appendTail(newResult);
                result = newResult.toString();
                
                // fix self closing tags
                result = result.replaceAll("\\<(?!img|br|hr|input|frame|link|meta|param)(\\w+)([^\\<]*)/\\>","<$1$2></$1>");
                this.write(((doctype != null)?doctype+"\n":"\n")+result);
        	} else {
	            Context cx = Context.getCurrentContext();
	            this.contentType = "text/json";
	            Object f = ScriptableObject.getProperty(jsobj, "toSource");
                Object ret = ((Function) f).call(cx, jsobj, jsobj, new Object[0]);
	            this.write(ret.toString());
        	}
        }
    }
    
    /**
     * Overwrites the response buffer with the input object
     */
    public void overwrite(Object obj) {
        this.buffer = null;
        this.write(obj);
    }

    /**
     *  Appends a part from a char array to the response buffer.
     *
     * @param chars
     * @param offset
     * @param length
     */
    public synchronized void write(char[] chars, int offset, int length) {
        if (buffer == null) {
            buffer = new StringBuffer(Math.max(length + 100, INITIAL_BUFFER_SIZE));
        }
        buffer.append(chars, offset, length);
    }

    /**
     *  Appends a char array to the response buffer.
     *
     * @param chars
     */
    public void write(char chars[]) {
        write(chars, 0, chars.length);
    }


    /**
     * Appends a signle character to the response buffer.
     * @param c
     */
    public synchronized void write(int c) {
        if (buffer == null) {
            buffer = new StringBuffer(INITIAL_BUFFER_SIZE);
        }
        buffer.append((char) c);
    }

    /**
     * Appends a part from a string to the response buffer.
     * @param str
     * @param offset
     * @param length
     */
    public void write(String str, int offset, int length) {
        char cbuf[]  = new char[length];
        str.getChars(offset, (offset + length), cbuf, 0);
        write(cbuf, 0, length);
    }

    /**
     * Write object to response buffer and append a platform dependent newline sequence.
     */
    public synchronized void writeln(Object what) {
        write(what);
        // if what is null, buffer may still be uninitialized
        if (buffer == null) {
            buffer = new StringBuffer(INITIAL_BUFFER_SIZE);
        }
        buffer.append(newLine);
    }

    /**
     * Writes a platform dependent newline sequence to response buffer.
     */
    public synchronized void writeln() {
        // buffer may still be uninitialized
        if (buffer == null) {
            buffer = new StringBuffer(INITIAL_BUFFER_SIZE);
        }
        buffer.append(newLine);
    }

    /**
     *  Insert string somewhere in the response buffer. Caller has to make sure
     *  that buffer exists and its length is larger than offset. str may be null, in which
     *  case nothing happens.
     */
    public void debug(Object message) {
        if (debugBuffer == null) {
            debugBuffer = new StringBuffer();
        }

        String str = (message == null) ? "null" : message.toString();

        debugBuffer.append("<p><span style=\"background: yellow; color: black\">");
        debugBuffer.append(str);
        debugBuffer.append("</span></p>");
    }

    /**
     * Encode HTML entities, but leave newlines alone. This is for the content of textarea forms.
     */
    public synchronized void encodeForm(Object what) {
        if (what != null) {
            String str = what.toString();

            if (buffer == null) {
                buffer = new StringBuffer(Math.max(str.length() + 100, INITIAL_BUFFER_SIZE));
            }

            HtmlEncoder.encodeAll(str, buffer, false);
        }
    }

    /**
     *
     *
     * @param url ...
     *
     * @throws RedirectException ...
     */
    public void redirect(String url) throws RedirectException {
        redir = url;
        throw new RedirectException(url);
    }

    /**
     *
     *
     * @return ...
     */
    public String getRedirect() {
        return redir;
    }

    /**
     *
     *
     * @param url ...
     *
     * @throws RedirectException ...
     */
    public void forward(String url) throws RedirectException {
        forward = url;
        throw new RedirectException(url);
    }

    /**
     *
     *
     * @return ...
     */
    public String getForward() {
        return forward;
    }

    /**
     *  Allow to directly set the byte array for the response. Calling this more than once will
     *  overwrite the previous output. We take a generic object as parameter to be able to
     * generate a better error message, but it must be byte[].
     */
    public void writeBinary(byte[] what) {
        response = what;
    }
    
    /**
     * Append binary contents to the response.  We use this for directly serving static 
     * content.
     */
    public void appendBinary(byte[] what, int len) {
        if (response == null) {
            response = new byte[len];
            System.arraycopy(what, 0, response, 0, len);
        } else {
            byte[] old = response;
            response = new byte[old.length + len];
            System.arraycopy(old, 0, response, 0, old.length);
            System.arraycopy(what, 0, response, old.length - 1, len);
        }
    }
    
    public void setInputStream(InputStream istream) {
        this.istream = istream;
    }
    
    public InputStream getInputStream() {
        return this.istream;
    }
    
    public void closeInputStream() {
        if (istream != null) {
            try { 
                istream.close(); 
            } catch (IOException ioex) { 
            }
            istream = null;
        }
        this.inputStreamLength = 0;
    }

    /**
     * Write a vanilla error report. Callers should make sure the ResponeTrans is
     * new or has been reset.
     *
     * @param appName the application name
     * @param message the error message
     */
    public void reportError(String appName, String message) {
        if (reqtrans.isXmlRpc()) {
            writeXmlRpcError(new RuntimeException(message));
        } else {
            status = 500;
            write("<html><body><h3>");
            write("Error in application ");
            write(appName);
            write("</h3>");
            write(message);
            write("</body></html>");
        }
    }

    public void writeXmlRpcResponse(Object result) {
        try {
            reset();
            contentType = "text/xml";
            if (charset == null) {
                charset = "UTF-8";
            }
            XmlRpcResponseProcessor xresproc = new XmlRpcResponseProcessor();
            writeBinary(xresproc.encodeResponse(result, charset));
        } catch (Exception x) {
            writeXmlRpcError(x);
        }
    }

    public void writeXmlRpcError(Exception x) {
        contentType = "text/xml";
        if (charset == null) {
            charset = "UTF-8";
        }
        XmlRpcResponseProcessor xresproc = new XmlRpcResponseProcessor();
        writeBinary(xresproc.encodeException(x, charset));
    }

    public void flush() {
        // does nothing!
    }

    /**
     * This has to be called after writing to this response has finished and before it is shipped back to the
     * web server. Transforms the string buffer into a byte array for transmission.
     */
    public void close() throws UnsupportedEncodingException {
        close(null);
    }

    /**
     * This has to be called after writing to this response has finished and before it is shipped back to the
     * web server. Transforms the string buffer into a byte array for transmission.
     */
    public synchronized void close(String cset) throws UnsupportedEncodingException {
        // if the response was already written and committed by the application
        // there's no point in closing the response buffer
        HttpServletResponse res = reqtrans.getServletResponse();
        if (res != null && res.isCommitted()) {
            return;
        }

        // only use default charset if not explicitly set for this response.
        if (charset == null) {
            charset = cset;
        }

        // if charset is not set, use western encoding
        if (charset == null) {
            charset = "UTF-8";
        }

        boolean encodingError = false;

        // only close if the response hasn't been closed yet
        if (response == null) {
            // if debug buffer exists, append it to main buffer
            if (debugBuffer != null) {
                if (buffer == null) {
                    buffer = debugBuffer;
                } else {
                    buffer.append(debugBuffer);
                }
            }

            // get the buffer's bytes in the specified encoding
            if (buffer != null) {
                try {
                    response = buffer.toString().getBytes(/*charset*/); 
                } catch (Exception uee) {    
                    encodingError = true;
                    response = buffer.toString().getBytes();
                }

                // make sure this is done only once, even with more requsts attached
                buffer = null;
            } else {
                response = new byte[0];
            }
        }

        boolean autoETags = "true".equals(app.getProperty("autoETags", "true"));
        // if etag is not set, calc MD5 digest and check it, but only if
        // not a redirect or error
        if (autoETags &&
                etag == null &&
                lastModified == -1 &&
                status == 200 &&
                redir == null) {
            try {
                digest = MessageDigest.getInstance("MD5");
                // if (contentType != null)
                //     digest.update (contentType.getBytes());
                byte[] b = digest.digest(response);
                etag = "\"" + new String(Base64.encode(b)) + "\"";
                // only set response to 304 not modified if no cookies were set
                if (reqtrans.hasETag(etag) && countCookies() == 0) {
                    response = new byte[0];
                    notModified = true;
                }
            } catch (Exception ignore) {
                // Etag creation failed for some reason. Ignore.
            }
        }

        notifyAll();

        // if there was a problem with the encoding, let the app know
        if (encodingError) {
            throw new UnsupportedEncodingException(charset);
        }
    }

    /**
     * If we just attached to evaluation we call this instead of close because only the primary thread
     * is responsible for closing the result
     */
    public synchronized void waitForClose() {
        try {
            if (response == null) {
                wait(10000L);
            }
        } catch (InterruptedException ix) {
            // Ignore
        }
    }

    /**
     * Get the body content for this response as byte array, encoded using the
     * response's charset.
     *
     * @return the response body
     */
    public byte[] getContent() {
        return (response == null) ? new byte[0] : response;
    }

    /**
     * Get the number of bytes of the response body.
     *
     * @return the length of the response body
     */
    public int getContentLength() {
        if (this.inputStreamLength > 0) {
            return this.inputStreamLength;
        }
        
        if (response != null) {
            return response.length;
        }

        return 0;
    }
    
    public void setInputStreamLength(int len) {
        this.inputStreamLength = len;
    }

    /**
     * Get the response's MIME content type
     *
     * @return the MIME type for this response
     */
    public String getContentType() {
        if (charset != null) {
            return contentType + "; charset=" + charset;
        }

        return contentType;
    }


    /**
     * Set the response's MIME content type
     *
     * @param contentType MIME type for this response
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Set the Last-Modified header for this response
     *
     * @param modified the Last-Modified header in milliseconds
     */
    public void setLastModified(long modified) {
        // date headers don't do milliseconds, round to seconds
        lastModified = (modified / 1000) * 1000;
        if (reqtrans.getIfModifiedSince() == lastModified) {
            notModified = true;
            throw new RedirectException(null);
        }
    }

    /**
     * Get the value of the Last-Modified header for this response.
     *
     * @return the Last-Modified header in milliseconds
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Set the ETag header value for this response.
     *
     * @param value the ETag header value
     */
    public void setETag(String value) {
        etag = (value == null) ? null : ("\"" + value + "\"");
        if (etag != null && reqtrans.hasETag(etag)) {
            notModified = true;
            throw new RedirectException(null);
        }
    }

    /**
     * Get the ETag header value for this response.
     *
     * @return the ETag header value
     */
    public String getETag() {
        return etag;
    }

    /**
     * Check if this response should generate a Not-Modified response.
     *
     * @return true if the the response wasn't modified since the client last saw it.
     */
    public boolean getNotModified() {
        return notModified;
    }

    /**
     * Add a dependency to this response.
     *
     * @param what an item this response's output depends on.
     */
    public void dependsOn(Object what) {
        if (digest == null) {
            try {
                digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException nsa) {
                // MD5 should always be available
            }
        }

        if (what == null) {
            digest.update(new byte[0]);
        } else if (what instanceof Date) {
            digest.update(MD5Encoder.toBytes(((Date) what).getTime()));
        } else if (what instanceof byte[]) {
            digest.update((byte[]) what);
        } else {
            String str = what.toString();

            if (str != null) {
                digest.update(str.getBytes());
            } else {
                digest.update(new byte[0]);
            }
        }
    }

    /**
     * Digest all dependencies to a checksum to see if the response has changed.
     */
    public void digestDependencies() {
        if (digest == null) {
            return;
        }

        // add the application checksum as dependency to make ETag
        // generation sensitive to changes in the app
        byte[] b = digest.digest(MD5Encoder.toBytes(app.getChecksum()));

        setETag(new String(Base64.encode(b)));
    }

    /**
     * Set a cookie.
     *
     * @param key the cookie key
     * @param value the cookie value
     * @param days the cookie's lifespan in days
     * @param path the URL path to apply the cookie to
     * @param domain the domain to apply the cookie to
     */
    public void setCookie(String key, String value, int days, String path, String domain) {
        CookieTrans c = null;

        if (cookies == null) {
            cookies = new HashMap();
        } else {
            c = (CookieTrans) cookies.get(key);
        }

        if (c == null) {
            c = new CookieTrans(key, value);
            cookies.put(key, c);
        } else {
            c.setValue(value);
        }

        c.setDays(days);
        c.setPath(path);
        c.setDomain(domain);
    }

    /**
     * Reset all previously set cookies.
     */
    public void resetCookies() {
        if (cookies != null) {
            cookies.clear();
        }
    }

    /**
     * Get the number of cookies set in this response.
     *
     * @return the number of cookies
     */
    public int countCookies() {
        if (cookies != null) {
            return cookies.size();
        }

        return 0;
    }

    /**
     * Get the cookies set in this response.
     *
     * @return the cookies
     */
    public CookieTrans[] getCookies() {
        if (cookies == null) {
            return new CookieTrans[0];
        }

        CookieTrans[] c = new CookieTrans[cookies.size()];
        cookies.values().toArray(c);
        return c;
    }
    
    public Object getCookiesScriptable() {
    	Object[] carr = cookies.values().toArray();
    	Scriptable c = null;
    	
    	try {
    		Context cx = Context.getCurrentContext();
    		ImporterTopLevel scope = new ImporterTopLevel(cx, true);
    		c = cx.newObject(scope);
    		for (int i = 0; i < carr.length; i++) {
    			CookieTrans ct = (CookieTrans) carr[i];
    			c.put(ct.getName(), c, ct.getValue());
    		}
    	} catch (Exception ex) {
    		// ignore
    	}
    	
    	return c;
    }

    /**
     * Get the error message to display to the user, if any.
     * @return the error message
     */
    public String getError() {
        return error;
    }

    /**
     * Set a message to display to the user.
     * @param error the error message
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Get debug messages to append to the response, if any.
     * @return the response's debug buffer
     */
    public StringBuffer getDebugBuffer() {
        return debugBuffer;
    }

    /**
     * Set debug messages to append to the response.
     * @param debugBuffer the response's debug buffer
     */
    public void setDebugBuffer(StringBuffer debugBuffer) {
        this.debugBuffer = debugBuffer;
    }

    /**
     * Get the charset/encoding for this response
     * @return the charset name
     */
    public String getCharset() {
        return charset;
    }

    /**
     * Set the charset/encoding for this response
     * @param charset the charset name
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }

    /**
     * Returns true if this response may be cached by the client
     * @return true if the response may be cached
     */
    public boolean isCacheable() {
        return cacheable;
    }

    /**
     * Set the cacheability of this response
     * @param cache true if the response may be cached
     */
    public void setCacheable(boolean cache) {
        this.cacheable = cache;
    }

    /**
     * Get the HTTP response status code
     * @return the HTTP response code
     */
    public int getStatus() {
        return status;
    }

    /**
     * Set the HTTP response status code
     * @param status the HTTP response code
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Proxy to HttpServletResponse.addHeader()
     * @param name the header name
     * @param value the header value
     */
    public void addHeader(String name, String value) {
    	this.reqtrans.response.addHeader(name, value);
    }

    /**
     * Proxy to HttpServletResponse.addDateHeader()
     * @param name the header name
     * @param value the header value
     */
    public void addDateHeader(String name, Date value) {
    	this.reqtrans.response.addDateHeader(name, value.getTime());
    }

    /**
     * Proxy to HttpServletResponse.setHeader()
     * @param name the header name
     * @param value the header value
     */
    public void setHeader(String name, String value) {
        this.reqtrans.response.setHeader(name, value);
    }

    /**
     * Proxy to HttpServletResponse.setDateHeader()
     * @param name the header name
     * @param value the header value
     */
    public void setDateHeader(String name, Date value) {
    	this.reqtrans.response.setDateHeader(name, value.getTime());
    }


}