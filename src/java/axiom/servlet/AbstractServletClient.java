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
 * $RCSfile: AbstractServletClient.java,v $
 * $Author: hannes $
 * $Revision: 1.68 $
 * $Date: 2006/06/03 07:13:06 $
 */

/* Portierung von helma.asp.AspClient auf Servlets */
/* Author: Raphael Spannocchi Datum: 27.11.1998 */

/* 
 * Modified by:
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */
package axiom.servlet;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.servlet.ServletRequestContext;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;

import axiom.framework.*;
import axiom.framework.core.Application;
import axiom.util.*;

/**
 * This is an abstract Axiom servlet adapter. This class communicates with axiom applications
 * via RMI. Subclasses are either one servlet per app, or one servlet that handles multiple apps
 */
public abstract class AbstractServletClient extends HttpServlet {

    // host on which Axiom app is running
    String host = null;

    // port of Axiom RMI server
    int port = 0;

    // RMI url of Axiom app
    String axiomUrl;

    // limit to HTTP uploads in kB
    int uploadLimit = 1024;

    // limit to HTTP upload
    int totalUploadLimit = 1024;

    // cookie domain to use
    String cookieDomain;

    // cookie name for session cookies
    String sessionCookieName = "AxiomSession";

    // this tells us whether to bind session cookies to client ip subnets
    // so they can't be easily used from other ip addresses when hijacked
    boolean protectedSessionCookie = true;

    // allow caching of responses
    boolean caching;

    // enable debug output
    boolean debug;

    // soft fail on file upload errors by setting flag "axiom_upload_error" in RequestTrans
    // if fals, an error response is written to the client immediately without entering Axiom
    boolean uploadSoftfail = false;

    /**
     * Init this servlet.
     *
     * @param init the servlet configuration
     *
     * @throws ServletException ...
     */
    public void init(ServletConfig init) throws ServletException {
        super.init(init);

        // get max size for file uploads
        String upstr = init.getInitParameter("uploadLimit");
        try {
            uploadLimit = (upstr == null) ? 1024 : Integer.parseInt(upstr);
        } catch (NumberFormatException x) {
            System.err.println("Bad number format for uploadLimit: " + upstr);
            uploadLimit = 1024;
        }
        
        // get max total upload size
        upstr = init.getInitParameter("totalUploadLimit");
        try {
            totalUploadLimit = (upstr == null) ? uploadLimit : Integer.parseInt(upstr);
        } catch (NumberFormatException x) {
            log("Bad number format for totalUploadLimit: " + upstr);
            totalUploadLimit = uploadLimit;
        }

        // soft fail mode for upload errors
        uploadSoftfail = ("true".equalsIgnoreCase(init.getInitParameter("uploadSoftfail")));

        // get cookie domain
        cookieDomain = init.getInitParameter("cookieDomain");
        if (cookieDomain != null) {
            cookieDomain = cookieDomain.toLowerCase();
        }

        // get session cookie name
        sessionCookieName = init.getInitParameter("sessionCookieName");
        if (sessionCookieName == null) {
            sessionCookieName = "AxiomSession";
        }

        // disable binding session cookie to ip address?
        protectedSessionCookie = !("false".equalsIgnoreCase(init.getInitParameter("protectedSessionCookie")));

        // debug mode for printing out detailed error messages
        debug = ("true".equalsIgnoreCase(init.getInitParameter("debug")));

        // generally disable response caching for clients?
        caching = !("false".equalsIgnoreCase(init.getInitParameter("caching")));
    }

    /**
     * Abstract method to get the {@link axiom.framework.core.Application Applicaton}
     * instance the servlet is talking to.
     *
     * @return this servlet's application instance
     */
    abstract Application getApplication();

    /**
     * Handle a request.
     *
     * @param request ...
     * @param response ...
     *
     * @throws ServletException ...
     * @throws IOException ...
     */
    protected void service (HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
        
        final String httpMethod = request.getMethod();
        if (!"POST".equalsIgnoreCase(httpMethod) && !"GET".equalsIgnoreCase(httpMethod)) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "HTTP Method " + httpMethod + " not supported.");
            return;
        }
        
        RequestTrans reqtrans = new RequestTrans(request, response, getPathInfo(request));

        try {
            // get the character encoding
            String encoding = request.getCharacterEncoding();

            if (encoding == null) {
                // no encoding from request, use the application's charset
                encoding = getApplication().getCharset();
            }

            // read and set http parameters
            parseParameters(request, reqtrans, encoding);

            List uploads = null; 
            ServletRequestContext reqcx = new ServletRequestContext(request);

            if (ServletFileUpload.isMultipartContent(reqcx)) {            
                // get session for upload progress monitoring
                UploadStatus uploadStatus = getApplication().getUploadStatus(reqtrans);
            	try {
            		uploads = parseUploads(reqcx, reqtrans, uploadStatus, encoding);
	            } catch (Exception upx) {
	                System.err.println("Error in file upload: " + upx);
	                if (uploadSoftfail) {
	                    String msg = upx.getMessage();
	                    if (msg == null || msg.length() == 0) {
	                        msg = upx.toString();
	                    }
	                    reqtrans.set("axiom_upload_error", msg);
	                } else if (upx instanceof FileUploadBase.SizeLimitExceededException) {
	                    sendError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
	                            "File upload size exceeds limit of " + uploadLimit + "kB");
	                    return;
	                } else {
	                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
	                            "Error in file upload: " + upx);
	                    return;
	                }
	            }
            }

            parseCookies(request, reqtrans, encoding);

            // do standard HTTP variables
            String host = request.getHeader("Host");

            if (host != null) {
                host = host.toLowerCase();
                reqtrans.set("http_host", host);
            }

            String referer = request.getHeader("Referer");

            if (referer != null) {
                reqtrans.set("http_referer", referer);
            }

            try {
                long ifModifiedSince = request.getDateHeader("If-Modified-Since");

                if (ifModifiedSince > -1) {
                    reqtrans.setIfModifiedSince(ifModifiedSince);
                }
            } catch (IllegalArgumentException ignore) {
            }

            String ifNoneMatch = request.getHeader("If-None-Match");

            if (ifNoneMatch != null) {
                reqtrans.setETags(ifNoneMatch);
            }

            String remotehost = request.getRemoteAddr();

            if (remotehost != null) {
                reqtrans.set("http_remotehost", remotehost);
            }

            // get the cookie domain to use for this response, if any.
            String resCookieDomain = cookieDomain;

            if (resCookieDomain != null) {
                // check if cookieDomain is valid for this response.
                // (note: cookieDomain is guaranteed to be lower case)
                // check for x-forwarded-for header, fix for bug 443
                String proxiedHost = request.getHeader("x-forwarded-host");
                if (proxiedHost != null) {
                    if (proxiedHost.toLowerCase().indexOf(cookieDomain) == -1) {
                        resCookieDomain = null;
                    }
                } else if ((host != null) &&
                        host.toLowerCase().indexOf(cookieDomain) == -1) {
                    resCookieDomain = null;
                }
            }

            // check if session cookie is present and valid, creating it if not.
            checkSessionCookie(request, response, reqtrans, resCookieDomain);

            String browser = request.getHeader("User-Agent");

            if (browser != null) {
                reqtrans.set("http_browser", browser);
            }
           
            String language = request.getHeader("Accept-Language");
            
            if (language != null) {
                reqtrans.set("http_language", language);
            } 
            
            String authorization = request.getHeader("authorization");

            if (authorization != null) {
                reqtrans.set("authorization", authorization);
            }

            ResponseTrans restrans = getApplication().execute(reqtrans);
            
            // if the response was already written and committed by the application
            // we can skip this part and return
            if (response.isCommitted()) {
                return;
            }

            // set cookies
            if (restrans.countCookies() > 0) {
                CookieTrans[] resCookies = restrans.getCookies();

                for (int i = 0; i < resCookies.length; i++)
                    try {
                        Cookie c = resCookies[i].getCookie("/", resCookieDomain);

                        response.addCookie(c);
                    } catch (Exception ignore) {
                    	ignore.printStackTrace();
                    }
            }

            // write response
            writeResponse(request, response, reqtrans, restrans);
           
        } catch (Exception x) {
            try {
                if (debug) {
                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                              "Server error: " + x);
                    x.printStackTrace();
                } else {
                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                              "The server encountered an error while processing your request. " +
                              "Please check back later.");
                }

                log("Exception in execute: " + x);
            } catch (IOException io_e) {
                log("Exception in sendError: " + io_e);
            }
        }
    }

    protected void writeResponse(HttpServletRequest req, HttpServletResponse res,
                                 RequestTrans axiomreq, ResponseTrans axiomres)
            throws IOException {
        if (axiomres.getForward() != null) {
            sendForward(res, req, axiomres);
            return;
        }

        if (axiomres.getETag() != null) {
            res.setHeader("ETag", axiomres.getETag());
        } 

        if (axiomres.getRedirect() != null) {
            sendRedirect(req, res, axiomres.getRedirect());
        } else if (axiomres.getNotModified()) {
            res.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        } else {
            if (!axiomres.isCacheable() || !caching) {
                // Disable caching of response.
                // for HTTP 1.0
                res.setDateHeader("Expires", System.currentTimeMillis() - 10000);
                res.setHeader("Pragma", "no-cache");

                // for HTTP 1.1
                res.setHeader("Cache-Control",
                              "no-cache, no-store, must-revalidate, max-age=0");
            }

            if (axiomres.getStatus() > 0) {
                res.setStatus(axiomres.getStatus());
            }

            // set last-modified header to now
            long modified = axiomres.getLastModified();
            if (modified > -1) {
                res.setDateHeader("Last-Modified", modified);
            }
            res.setDateHeader("Date", System.currentTimeMillis());

            res.setContentLength(axiomres.getContentLength());
            res.setContentType(axiomres.getContentType());

            if ("HEAD".equalsIgnoreCase(req.getMethod())) {
                return;
            }

            try {
                OutputStream out = res.getOutputStream();

                InputStream istream = axiomres.getInputStream();
                if (istream != null) {
                    try {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = istream.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                        out.flush();
                    } finally {
                        axiomres.closeInputStream();
                    }
                } else {
                    out.write(axiomres.getContent());
                    out.flush();
                }
            } catch (Exception io_e) {
                log("Exception in writeResponse: " + io_e);
            }
        }
    }

    void sendError(HttpServletResponse response, int code, String message)
            throws IOException {
        response.reset();
        response.setStatus(code);
        response.setContentType("text/html");

        Writer writer = response.getWriter();

        writer.write("<html><body><h3>");
        writer.write("Error in application ");
        try {
            writer.write(getApplication().getName());
        } catch (Exception besafe) {besafe.printStackTrace();}
        writer.write("</h3>");
        writer.write(message);
        writer.write("</body></html>");
        writer.flush();
    }

    void sendRedirect(HttpServletRequest req, HttpServletResponse res, String url) {
        String location = url;

        if (url.indexOf("://") == -1) {
            // need to transform a relative URL into an absolute one
            String scheme = req.getScheme();
            StringBuffer loc = new StringBuffer(scheme);

            loc.append("://");
            loc.append(req.getServerName());

            int p = req.getServerPort();

            // check if we need to include server port
            if ((p > 0) &&
                    (("http".equals(scheme) && (p != 80)) ||
                    ("https".equals(scheme) && (p != 443)))) {
                loc.append(":");
                loc.append(p);
            }

            if (!url.startsWith("/")) {
                String requri = req.getRequestURI();
                int lastSlash = requri.lastIndexOf("/");

                if (lastSlash == (requri.length() - 1)) {
                    loc.append(requri);
                } else if (lastSlash > -1) {
                    loc.append(requri.substring(0, lastSlash + 1));
                } else {
                    loc.append("/");
                }
            }

            loc.append(url);
            location = loc.toString();
        }

        // send status code 303 for HTTP 1.1, 302 otherwise
        if (isOneDotOne(req.getProtocol())) {
            res.setStatus(HttpServletResponse.SC_SEE_OTHER);
        } else {
            res.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        }

        res.setContentType("text/html");
        res.setHeader("Location", location);
    }

    /**
     * Forward the request to a static file. The file must be reachable via
     * the context's protectedStatic resource base.
     */
    void sendForward(HttpServletResponse res, HttpServletRequest req,
                     ResponseTrans axiomres) throws IOException {
        String forward = axiomres.getForward();
        ServletContext cx = getServletConfig().getServletContext();
        String path = cx.getRealPath(forward);
        if (path == null)
            throw new IOException("Resource "+forward+" not found");

        File file = new File(path);
        // calculate checksom on last modified date and content length.
        byte[] checksum = getChecksum(file);
        String etag = "\"" + new String(Base64.encode(checksum)) + "\"";
        res.setHeader("ETag", etag);
        String etagHeader = req.getHeader("If-None-Match");
        if (etagHeader != null) {
            StringTokenizer st = new StringTokenizer(etagHeader, ", \r\n");
            while (st.hasMoreTokens()) {
                if (etag.equals(st.nextToken())) {
                    res.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return;
                }
            }
        }
        int length = (int) file.length();
        res.setContentLength(length);
        res.setContentType(axiomres.getContentType());

        InputStream in = cx.getResourceAsStream(forward);
        if (in == null)
            throw new IOException("Can't read " + path);
        try {
            OutputStream out = res.getOutputStream();
    
            int bufferSize = 4096;
            byte buffer[] = new byte[bufferSize];
            int l;
    
            while (length>0) {
                if (length < bufferSize)
                    l = in.read(buffer, 0, length);
                else
                    l = in.read(buffer, 0, bufferSize);
    
                if (l == -1)
                    break;
    
                length -= l;
                out.write(buffer, 0, l);
            }
        } finally {
            in.close();
        }
    }

    private byte[] getChecksum(File file) {
        byte[] checksum = new byte[16];
        long n = file.lastModified();
        for (int i=0; i<8; i++) {
            checksum[i] = (byte) (n);
            n >>>= 8;
        }
        n = file.length();
        for (int i=8; i<16; i++) {
            checksum[i] = (byte) (n);
            n >>>= 8;
        }
        return checksum;
    }

    /**
     * Used to build the form value array when a multipart (file upload) form has
     * multiple values for one form element name.
     * 
     * @param reqtrans
     * @param name
     * @param value
     */
    private void appendFormValue(RequestTrans reqtrans, String name, Object value) {
        String arrayName = name; 
        try {
        	Context cx = Context.enter();
            Object curr = reqtrans.get(arrayName);
            if (curr instanceof NativeArray) {
            	NativeArray na = (NativeArray) curr;
            	na.put((int) na.getLength(), na, value);
            } else {
            	Object[] values = new Object[2];
            	values[0] = curr;
            	values[1] = value;
            	NativeArray na = new NativeArray(values);
            	ImporterTopLevel scope = new ImporterTopLevel(cx, true);
                ScriptRuntime.setObjectProtoAndParent(na, scope);
                reqtrans.set(arrayName, na);
            }
        } catch (ClassCastException x) {
            // name_array is defined as something else in the form - don't overwrite it
        } finally {
        	Context.exit();
        }
    }

    /**
     *  Check if the session cookie is set and valid for this request.
     *  If not, create a new one.
     */
    private void checkSessionCookie(HttpServletRequest request,
                                    HttpServletResponse response,
                                    RequestTrans reqtrans,
                                    String domain) {
        // check if we need to create a session id.
        if (protectedSessionCookie) {
            // If protected session cookies are enabled we also force a new session
            // if the existing session id doesn't match the client's ip address
            StringBuffer b = new StringBuffer();
            if (reqtrans.getSession() == null || !reqtrans.getSession().startsWith(b.toString())) {
                response.addCookie(createSessionCookie(b, reqtrans, domain));
            }
        } else if (reqtrans.getSession() == null) {
            response.addCookie(createSessionCookie(new StringBuffer(), reqtrans, domain));
        }
    }

    /**
     * Create a new session cookie.
     *
     * @param b
     * @param reqtrans
     * @param domain
     * @return the session cookie
     */
    private Cookie createSessionCookie(StringBuffer b,
                                       RequestTrans reqtrans,
                                       String domain) {
        b.append (Long.toString(Math.round(Math.random() * Long.MAX_VALUE) -
                    System.currentTimeMillis(), 36));

        reqtrans.setSession(b.toString());
        Cookie cookie = new Cookie(sessionCookieName, reqtrans.getSession());
        
        cookie.setPath("/");

        if (domain != null) {
            cookie.setDomain(domain);
        }
        
        return cookie;
    }

    /**
     *  Adds an the 3 most significant bytes of an IP address header to the
     *  session cookie id. Some headers may contain a list of IP addresses
     *  separated by comma - in that case, care is taken that only the first
     *  one is considered.
     */
    private void addIPAddress(StringBuffer b, String addr) {
        if (addr != null) {
            int cut = addr.indexOf(',');
            if (cut > -1) {
                addr = addr.substring(0, cut);
            }
            cut = addr.lastIndexOf('.');
            if (cut == -1) {
                cut = addr.lastIndexOf(':');
            }
            if (cut > -1) {
                b.append(addr.substring(0, cut+1));
            }
        }
    }


    /**
     * Put name and value pair in map.  When name already exist, add value
     * to array of values.
     */
    private static void putMapEntry(Map map, String name, String value) {
        String[] newValues = null;
        String[] oldValues = (String[]) map.get(name);

        if (oldValues == null) {
            newValues = new String[1];
            newValues[0] = value;
        } else {
            newValues = new String[oldValues.length + 1];
            System.arraycopy(oldValues, 0, newValues, 0, oldValues.length);
            newValues[oldValues.length] = value;
        }

        map.put(name, newValues);
    }

    protected List parseUploads(ServletRequestContext reqcx, RequestTrans reqtrans,
            					final UploadStatus uploadStatus, String encoding)
			throws FileUploadException, UnsupportedEncodingException {
    	
    	List uploads = null;
    	Context cx = Context.enter();
    	ImporterTopLevel scope = new ImporterTopLevel(cx, true);
    	
    	try {
    		// handle file upload
    		DiskFileItemFactory factory = new DiskFileItemFactory();
    		FileUpload upload = new FileUpload(factory);
    		// use upload limit for individual file size, but also set a limit on overall size
    		upload.setFileSizeMax(uploadLimit * 1024);
    		upload.setSizeMax(totalUploadLimit * 1024);

    		// register upload tracker with user's session
    		if (uploadStatus != null) {
    			upload.setProgressListener(new ProgressListener() {
    				public void update(long bytesRead, long contentLength, int itemsRead) {
    					uploadStatus.update(bytesRead, contentLength, itemsRead);
    				}
    			});
    		}

    		uploads = upload.parseRequest(reqcx);
    		Iterator it = uploads.iterator();

    		while (it.hasNext()) {
    			FileItem item = (FileItem) it.next();
    			String name = item.getFieldName();
    			Object value = null;
    			// check if this is an ordinary HTML form element or a file upload
    			if (item.isFormField()) {
    				value =  item.getString(encoding);
    			} else {
    				String itemName = item.getName().trim();
    				if (itemName == null || itemName.equals("")) {
    					continue;
    				}
    				value = new MimePart(itemName, 
    						item.get(),
    						item.getContentType());
    				value = new NativeJavaObject(scope, value, value.getClass());
    			}
    			item.delete();
    			// if multiple values exist for this name, append to _array

    			// reqtrans.addPostParam(name, value); ????

    			Object ret = reqtrans.get(name);
    			if (ret != null && ret != Scriptable.NOT_FOUND) {
    				appendFormValue(reqtrans, name, value);
    			} else {
    				reqtrans.set(name, value);
    			}
    		}
    	} finally {
    		Context.exit();
    	}

        return uploads;
	}
    
    protected void parseParameters(HttpServletRequest request, RequestTrans reqtrans, String encoding) 
    throws Exception {

        HashMap parameters = new HashMap();

        try {
            Context cx = Context.enter();
            cx.setClassShutter(new ClassShutter() {
            	public boolean visibleToScripts(String fullClassName) {
            		return false;
            	}
            });
            
            ImporterTopLevel scope = new ImporterTopLevel(cx, true);

            // Parse any posted parameters in the input stream
            String contentType;
            boolean isPost = false, isForm = false, isJson = false, isXml = false;
            if ("POST".equals(request.getMethod())) { 
                isPost = true; 
            }
            if (isPost && (contentType = request.getContentType()) != null) {
                contentType = contentType.split(";")[0];
                if ("application/x-www-form-urlencoded".equals(contentType)) {
                    isForm = true;
                } else if ("text/json".equals(contentType)) {
                    isJson = true;
                } else if ("text/xml".equals(contentType)) {
                    isXml = true;
                }
            }
            
            // Parse any query string parameters from the request
            String queryString = request.getQueryString();
            if (queryString != null) {
                try {
                    parseParameters(parameters, queryString.getBytes(), encoding, isPost);

                    Scriptable sqparam = cx.newObject(scope);
                    for (Iterator i = parameters.entrySet().iterator(); i.hasNext();) {
                        Map.Entry entry = (Map.Entry) i.next();
                        String key = (String) entry.getKey();
                        String[] values = (String[]) entry.getValue();

                        if ((values != null) && (values.length > 0)) {
                            if (values.length == 1) {
                                sqparam.put(key, sqparam, values[0]);
                            } else {
                            	NativeArray na = new NativeArray(values);
                                ScriptRuntime.setObjectProtoAndParent(na, scope);
                                sqparam.put(key, sqparam, na);
                            }
                        }
                    }                
                    reqtrans.setQueryParams(sqparam);
                } catch (Exception e) {
                    System.err.println("Error parsing query string: " + e);
                }
            }

            if (isForm || isJson || isXml) {
                try {
                    int max = request.getContentLength();
                    int len = 0;
                    byte[] buf = new byte[max];
                    ServletInputStream is = request.getInputStream();

                    while (len < max) {
                        int next = is.read(buf, len, max - len);

                        if (next < 0) {
                            break;
                        }

                        len += next;
                    }

                    if (isForm) {
                        HashMap formMap = new HashMap();
                        parseParameters(formMap, buf, encoding, isPost);
                        Scriptable spparam = cx.newObject(scope);
                        for (Iterator i = formMap.entrySet().iterator(); i.hasNext();) {
                            Map.Entry entry = (Map.Entry) i.next();
                            String key = (String) entry.getKey();
                            String[] values = (String[]) entry.getValue();
                            if (values.length > 0) {
                                if (values.length == 1) {
                                    spparam.put(key, spparam, values[0]);
                                } else {
                                    NativeArray na = new NativeArray(values);
                                    ScriptRuntime.setObjectProtoAndParent(na, scope);
                                	spparam.put(key, spparam, na);
                                }
                            }
                        }
                        
                        reqtrans.setPostBody(new String(buf, encoding));
                        reqtrans.setPostParams(spparam); 
                        parameters.putAll(formMap);
                    } else if (isJson) {
                        String json = new String(buf, encoding);
                        Scriptable post = (Scriptable) cx.evaluateString(scope, 
                                "eval(" + json + ")", "", 0, null);
                        reqtrans.setPostBody(post);
                        Object[] ids = post.getIds();
                        int idslen = ids.length;
                        for (int i = 0; i < idslen; i++) {
                            parameters.put(ids[i], post.get((String) ids[i], post)); 
                        }
                    } else if (isXml) {
                        String xml = new String(buf, encoding);
                        int startProlog = xml.indexOf("<?xml version="), endProlog;
                        if (startProlog > -1 && (endProlog = xml.indexOf(">", startProlog + 1)) > -1) {
                        	xml = new StringBuffer(xml).replace(startProlog, endProlog + 1, "").toString();
                        }
                        xml = xml.replaceAll("\\\"", "\\\\\"").replaceAll("\n", "").replaceAll("\r", "");
                        Scriptable post = (Scriptable) cx.evaluateString(scope, 
                                "new XML(\"" + xml + "\");", "", 0, null); 
                        reqtrans.setPostBody(post);
                    }
                } catch (Exception e) {
                	throw e;
                }
            }

            Scriptable sreqdata = cx.newObject(scope);
            for (Iterator i = parameters.entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();
                String key = (String) entry.getKey();
                Object val = entry.getValue();

                if (val != null) {
                    if (val instanceof String[]) {
                        String[] values = (String[]) val;
                        if (values.length > 0) {
                            if (values.length == 1) {
                                sreqdata.put(key, sreqdata, values[0]);
                            } else {
                            	NativeArray na = new NativeArray(values);
                                ScriptRuntime.setObjectProtoAndParent(na, scope);
                                sreqdata.put(key, sreqdata, na);
                            }
                        }
                    } else {
                        sreqdata.put(key, sreqdata, val);
                    }
                }

            }
            reqtrans.setData(sreqdata); 

        } catch (Exception ex) { 
        	ex.printStackTrace();
            throw ex; 
        } finally {
            Context.exit();
        }
    }
    
    protected void parseCookies(HttpServletRequest request, RequestTrans reqtrans, String encoding)
    throws Exception {
    	try {
    		Context cx = Context.enter();
    		cx.setClassShutter(new ClassShutter() {
    			public boolean visibleToScripts(String fullClassName) {
    				return false;
    			}
    		});

    		ImporterTopLevel scope = new ImporterTopLevel(cx, true);

    		// read cookies
    		Cookie[] reqCookies = request.getCookies();
    		Scriptable cookies = cx.newObject(scope);
    		
    		if (reqCookies != null) {
    			for (int i = 0; i < reqCookies.length; i++){
    				try {
    					// get Cookies
    					String nextKey = reqCookies[i].getName();
    					String nextPart = reqCookies[i].getValue();

    					if (sessionCookieName.equals(nextKey)) {
    						reqtrans.setSession(nextPart);
    					} else {
    						cookies.put(nextKey, cookies, nextPart);
    					}               
    				} catch (Exception badCookie) {
    					// ignore
    				}
    			}
    		}
    		
    		reqtrans.setCookies(cookies);
    	} catch (Exception ex) {
    		ex.printStackTrace();
    		throw ex; 
    	} finally {
    		Context.exit();
    	}
    }

    /**
     * Append request parameters from the specified String to the specified
     * Map.  It is presumed that the specified Map is not accessed from any
     * other thread, so no synchronization is performed.
     * <p>
     * <strong>IMPLEMENTATION NOTE</strong>:  URL decoding is performed
     * individually on the parsed name and value elements, rather than on
     * the entire query string ahead of time, to properly deal with the case
     * where the name or value includes an encoded "=" or "&" character
     * that would otherwise be interpreted as a delimiter.
     *
     * NOTE: byte array data is modified by this method.  Caller beware.
     *
     * @param map Map that accumulates the resulting parameters
     * @param data Input string containing request parameters
     * @param encoding Encoding to use for converting hex
     *
     * @exception UnsupportedEncodingException if the data is malformed
     */
    public static void parseParameters(Map map, byte[] data, String encoding, boolean isPost)
                                throws UnsupportedEncodingException {
        if ((data != null) && (data.length > 0)) {
            int ix = 0;
            int ox = 0;
            String key = null;
            String value = null;

            while (ix < data.length) {
                byte c = data[ix++];

                switch ((char) c) {
                    case '&':
                        value = new String(data, 0, ox, encoding);

                        if (key != null) {
                            putMapEntry(map, key, value);
                            key = null;
                        }

                        ox = 0;

                        break;

                    case '=':
                        key = new String(data, 0, ox, encoding);
                        ox = 0;

                        break;

                    case '+':
                        data[ox++] = (byte) ' ';

                        break;

                    case '%':
                        data[ox++] = (byte) ((convertHexDigit(data[ix++]) << 4) +
                                     convertHexDigit(data[ix++]));

                        break;

                    default:
                        data[ox++] = c;
                }
            }
            if (key != null) {
                //The last value does not end in '&'.  So save it now.
                value = new String(data, 0, ox, encoding);
                putMapEntry(map, key, value);
            } else if (ox > 0) {
	            // Store any residual bytes in req.data.http_post_remainder
	            value = new String(data, 0, ox, encoding);
	            if (isPost) {
	                putMapEntry(map, "http_post_remainder", value);
	            } else {
	                putMapEntry(map, "http_get_remainder", value);
	            }
	        }
        }
    }

    /**
     * Convert a byte character value to hexidecimal digit value.
     *
     * @param b the character value byte
     */
    private static byte convertHexDigit(byte b) {
        if ((b >= '0') && (b <= '9')) {
            return (byte) (b - '0');
        }

        if ((b >= 'a') && (b <= 'f')) {
            return (byte) (b - 'a' + 10);
        }

        if ((b >= 'A') && (b <= 'F')) {
            return (byte) (b - 'A' + 10);
        }

        return 0;
    }

    boolean isOneDotOne(String protocol) {
        if (protocol == null) {
            return false;
        }

        if (protocol.endsWith("1.1")) {
            return true;
        }

        return false;
    }

    String getPathInfo(HttpServletRequest req)
            throws UnsupportedEncodingException {
        StringTokenizer t = new StringTokenizer(req.getContextPath(), "/");
        int prefixTokens = t.countTokens();

        t = new StringTokenizer(req.getServletPath(), "/");
        prefixTokens += t.countTokens();

        String uri = req.getRequestURI();
        t = new StringTokenizer(uri, "/");

        int uriTokens = t.countTokens();
        StringBuffer pathbuffer = new StringBuffer();

        String encoding = getApplication().getCharset();

        for (int i = 0; i < uriTokens; i++) {
            String token = t.nextToken();

            if (i < prefixTokens) {
                continue;
            }

            if (i > prefixTokens) {
                pathbuffer.append('/');
            }
            
            pathbuffer.append(UrlEncoded.decode(token, encoding));
        }

        // append trailing "/" if it is contained in original URI
        if (uri.endsWith("/"))
            pathbuffer.append('/');

        return pathbuffer.toString();
    }
    
    /**
     *
     *
     * @return ...
     */
    public String getServletInfo() {
        return new String("Axiom Servlet Client");
    }
}
