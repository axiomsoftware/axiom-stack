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
 * $RCSfile: GlobalObject.java,v $
 * $Author: hannes $
 * $Revision: 1.42 $
 * $Date: 2006/04/26 15:52:25 $
 */

package axiom.scripting.rhino;

import org.mozilla.javascript.*;
import org.mozilla.javascript.serialize.*;

import axiom.framework.ErrorReporter;
import axiom.framework.core.*;
import axiom.objectmodel.db.*;
import axiom.scripting.rhino.extensions.*;
import axiom.util.HtmlEncoder;
import axiom.util.MimePart;
import axiom.util.XmlUtils;

import java.util.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.*;
import java.io.*;

/**
 * Axiom global object defines a number of custom global functions.
 * @jsglobal
 */
public class GlobalObject extends ImporterTopLevel implements PropertyRecorder {
    Application app;
    RhinoCore core;
    GlobalObject sharedGlobal = null;

    // fields to implement PropertyRecorder
    private volatile boolean isRecording = false;
    private volatile HashSet changedProperties;

    /**
     * Creates a new GlobalObject object.
     *
     * @param core ...
     * @param app ...
     */
    public GlobalObject(RhinoCore core, Application app, boolean perThread) {
        this.core = core;
        this.app = app;
        if (perThread) {
            sharedGlobal = core.global;
            setPrototype(sharedGlobal);
            setParentScope(null);
        }
    }

    /**
     * Initializes the global object. This is only done for the shared
     * global objects not the per-thread ones.
     *
     * @throws PropertyException ...
     */
    public void init() throws PropertyException {
        String[] globalFuncs = {
                                   "getProperty", "authenticate", "format", "encode",
                                   "encodeXml", "encodeForm", "stripTags", "formatParagraphs",
                                   "getXmlDocument", "seal",
                                   "getDBConnection", "getURL", "write", "writeln",
                                   "serialize", "deserialize", "defineLibraryScope",
                                   "wrapJavaMap", "unwrapJavaMap", "toJavaObject"
                               };

        defineFunctionProperties(globalFuncs, GlobalObject.class, 0);
        put("app", this, Context.toObject(new ApplicationBean(app), this));
        // put("Xml", this, Context.toObject(new XmlObject(core), this));
        put("global", this, this);
        // Define dontEnum() on Object prototype
        String[] objFuncs = { "dontEnum" };
        ScriptableObject objproto = (ScriptableObject) getObjectPrototype(this);
        objproto.defineFunctionProperties(objFuncs, GlobalObject.class,
                    DONTENUM | READONLY | PERMANENT);
    }

    /**
     * Get the global object's class name
     *
     * @return the class name for the global object
     */
    public String getClassName() {
        return "GlobalObject";
    }

    /**
     * Override ScriptableObject.put() to implement PropertyRecorder interface
     * and to synchronize method.
     *
     * @param name
     * @param start
     * @param value
     */
    public void put(String name, Scriptable start, Object value) {
        // register property for PropertyRecorder interface
        if (isRecording) {
            changedProperties.add(name);
        }
        super.put(name, start, value);
    }

    /**
     * Override ScriptableObject.get() to use the per-thread scope if possible,
     * and return the per-thread scope for "global".
     *
     * @param name
     * @param start
     * @return the property for the given name
     */
    public Object get(String name, Scriptable start) {
        // register property for PropertyRecorder interface
        if (isRecording) {
            changedProperties.add(name);
        }
        Context cx = Context.getCurrentContext();
        GlobalObject scope = (GlobalObject) cx.getThreadLocal("threadscope");
        if (scope != null) {
            // make thread scope accessible as "global"
            if ("global".equals(name)) {
                return scope;
            }
            // use synchronized get on fast changing per-thread scopes just to be sure
            Object obj = scope.getSynchronized(name);
            if (obj != null && obj != NOT_FOUND) {
                return obj;
            }
        }
        if (sharedGlobal != null) {
            // we're a per-thread scope
            return sharedGlobal.getInternal(name);
        } else {
            // we are the shared scope
            return super.get(name, start);
        }
    }

    /**
     * Directly get a property, bypassing the extra stuff in get(String, Scriptable).
     *
     * @param name
     * @return the property for the given name
     */
    protected Object getInternal(String name) {
        return super.get(name, this);
    }

    /**
     * Directly get a property, bypassing the extra stuff in get(String, Scriptable),
     * and synchronizing in order to prevent cache read errors on multiprocessor systems.
     * TODO: we need extensive testing in order to tell whether this is really necessary.
     *
     * @param name
     * @return the property for the given name
     */
    protected synchronized Object getSynchronized(String name) {
        return super.get(name, this);
    }

    /**
     *
     *
     * @param propname ...
     * @param defvalue ...
     *
     * @return ...
     */
    public String getProperty(String propname, Object defvalue) {
        if (defvalue == Undefined.instance) {
            return app.getProperty(propname);
        } else {
            return app.getProperty(propname, toString(defvalue));
        }
    }

    /**
     *
     *
     * @param user ...
     * @param pwd ...
     *
     * @return ...
     */
    public boolean authenticate(String user, String pwd) {
        return app.authenticate(user, pwd);
    }

    /**
     * Get a Axiom DB connection specified in db.properties
     *
     * @param dbsource the db source name
     *
     * @return a DatabaseObject for the specified DbConnection
     */

    public Object getDBConnection(Object option1, Object option2, Object option3, Object option4, Object option5) throws Exception {
        DatabaseObject db = null;
        if( (option1 != null && (option2 == null || option2 == Undefined.instance) && 
        	(option3 == null || option3 == Undefined.instance) && (option4 == null || option4 == Undefined.instance)) || 
        	(option1 != null && (option2 != null || option2 != Undefined.instance) && 
       		(option3 == null || option3 == Undefined.instance) && (option4 == null || option4 == Undefined.instance)) ){
        	if(option1 instanceof String){
        		String dbsource = (String)option1;
                DbSource dbsrc = app.getDbSource(dbsource);
                if (dbsrc == null){
                    throw new EvaluatorException("DbSource " + dbsource + " does not exist");
                }
                if (option2 != null && option2 != Undefined.instance) {
                	db = new DatabaseObject (dbsrc, option2);
                } else {
                	db = new DatabaseObject (dbsrc);
                }
        	}
        	else{
        		throw new EvaluatorException("Invalid arguements, proper use of getDBConnection(String), getDBConnection(String, boolean), getDBConnection(String, String, String, String) or getDBConnection(String, String, String, String, boolean)");
        	}
        } else if((option1 != null && option1 != Undefined.instance) && (option2 != null && option2 != Undefined.instance) && 
        		(option3 != null && option3 != Undefined.instance) && (option4 != null && option4 != Undefined.instance) ){
        	if(option1 instanceof String && option2 instanceof String && 
        			option3 instanceof String && option4 instanceof String){
        		db = new DatabaseObject(option1.toString(), option2.toString(), option3.toString(), option4.toString(), option5);
        	}
        } else{
    		throw new EvaluatorException("Invalid arguements, proper use of getDBConnection(String), getDBConnection(String, boolean), getDBConnection(String, String, String, String) or getDBConnection(String, String, String, String, boolean)");
        }

        return Context.toObject(db, this);    	
    }
    /**
     * Retrieve a Document from the specified URL.
     *
     * @param location the URL to retrieve
     * @param opt either a LastModified date or an ETag string for conditional GETs
     *
     * @return a wrapped MIME object
     */
    public Object getURL(String location, Object opt) {
        if (location ==  null) {
            return null;
        }

        try {
            URL url = new URL(location);
            URLConnection con = url.openConnection();

            // do we have if-modified-since or etag headers to set?
            if (opt != null && opt != Undefined.instance) {
                if (opt instanceof Scriptable) {
                    Scriptable scr = (Scriptable) opt;
                    if ("Date".equals(scr.getClassName())) {
                        Date date = new Date((long) ScriptRuntime.toNumber(scr));

                        con.setIfModifiedSince(date.getTime());

                        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",
                                                                   Locale.UK);

                        format.setTimeZone(TimeZone.getTimeZone("GMT"));
                        con.setRequestProperty("If-Modified-Since", format.format(date));
                    }else {
                        con.setRequestProperty("If-None-Match", scr.toString());
                    }
                } else {
                    con.setRequestProperty("If-None-Match", opt.toString());
                }
            }

            String httpUserAgent = app.getProperty("httpUserAgent");

            if (httpUserAgent != null) {
                con.setRequestProperty("User-Agent", httpUserAgent);
            }

            con.setAllowUserInteraction(false);

            String filename = url.getFile();
            String contentType = con.getContentType();
            long lastmod = con.getLastModified();
            String etag = con.getHeaderField("ETag");
            int length = con.getContentLength();
            int resCode = 0;

            if (con instanceof HttpURLConnection) {
                resCode = ((HttpURLConnection) con).getResponseCode();
            }

            ByteArrayOutputStream body = new ByteArrayOutputStream();

            if ((length != 0) && (resCode != 304)) {
                InputStream in = new BufferedInputStream(con.getInputStream());
                byte[] b = new byte[1024];
                int read;

                while ((read = in.read(b)) > -1)
                    body.write(b, 0, read);

                in.close();
            }

            MimePart mime = new MimePart(filename, body.toByteArray(), contentType);

            if (lastmod > 0) {
                mime.lastModified = new Date(lastmod);
            }

            mime.eTag = etag;

            return Context.toObject(mime, this);
        } catch (Exception xcept) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "getURL") 
            		+ "Error getting URL " + location, xcept);
        }

        return null;
    }

    /**
     *  Try to parse an object to a XML DOM tree. The argument must be
     *  either a URL, a piece of XML, an InputStream or a Reader.
     */
    public Object getXmlDocument(Object src) {
        try {
            Object p = src;
            if (p instanceof Wrapper) {
                p = ((Wrapper) p).unwrap();
            }
            Object doc = XmlUtils.parseXml(p);

            return Context.toObject(doc, this);
        } catch (Exception noluck) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "getXmlDocument"), noluck);
        }

        return null;
    }

    /**
     * Creates a global object, optionally initializing it with standard
     * JavaScript objects. This can be used for script libraries that want
     * to extend standard JavaScript functionality, but leave the original
     * prototypes alone.
     *
     * @param name the name of the new scope
     */
    public void defineLibraryScope(final String name, boolean initStandardObjects) {
        Object obj = get(name, this);
        if (obj != NOT_FOUND) {
            // put the property again to fool PropertyRecorder
            // into believing it has been renewed
            put(name, this, obj);
            return;
        }
        ScriptableObject scope = new NativeObject() {
            public String getClassName() {
                return name;
            }
        };
        scope.setPrototype(ScriptableObject.getObjectPrototype(this));
        if (initStandardObjects) {
            Context cx = Context.getCurrentContext();
            cx.initStandardObjects(scope, false);
        }
        put(name, this, scope);
    }

    /**
     * Wrap a java.util.Map so that it looks and behaves like a native JS object.
     * @jsfunction
     * @param {Object} obj A map.
     * @returns {String} A wrapper that makes the map look like a JS object.
     */
    public Object wrapJavaMap(Object obj) {
        if (obj instanceof Wrapper) {
            obj = ((Wrapper) obj).unwrap();
        }
        if (!(obj instanceof Map)) {
            throw ScriptRuntime.constructError("TypeError",
                "Invalid argument to wrapMap(): " + obj);
        }
        return new MapWrapper((Map) obj, core);
    }

    /**
     * Unwrap a map previously wrapped using {@link #wrapJavaMap(Object)}.
     * @param obj the wrapped map
     * @return the map exposed as java object
     */
    public Object unwrapJavaMap(Object obj) {
        if (!(obj instanceof MapWrapper)) {
            throw ScriptRuntime.constructError("TypeError",
                "Invalid argument to unwrapMap(): " + obj);
        }
        obj = ((MapWrapper) obj).unwrap();
        return new NativeJavaObject(core.global, obj, Map.class);
    }

    /**
     *
     *
     * @param obj ...
     *
     * @return ...
     */
    public String encode(Object obj) {
        return HtmlEncoder.encodeAll(toString(obj));
    }

    /**
     *
     *
     * @param obj ...
     *
     * @return ...
     */
    public String encodeXml(Object obj) {
        return HtmlEncoder.encodeXml(toString(obj));
    }

    /**
     *
     *
     * @param obj ...
     *
     * @return ...
     */
    public String encodeForm(Object obj) {
        return HtmlEncoder.encodeFormValue(toString(obj));
    }

    /**
     *
     *
     * @param obj ...
     *
     * @return ...
     */
    public String format(Object obj) {
        return HtmlEncoder.encode(toString(obj));
    }

    /**
     *
     *
     * @param obj ...
     *
     * @return ...
     */
    public String formatParagraphs(Object obj) {
        String str = toString(obj);

        if (str == null) {
            return null;
        }

        int l = str.length();

        if (l == 0) {
            return "";
        }

        // try to make stringbuffer large enough from the start
        StringBuffer buffer = new StringBuffer(Math.round(l * 1.4f));

        HtmlEncoder.encode(str, buffer, true, null);

        return buffer.toString();
    }

    /**
     *
     *
     * @param str ...
     */
    public void write(String str) {
        System.out.print(str);
    }

    /**
     *
     *
     * @param str ...
     */
    public void writeln(String str) {
        System.out.println(str);
    }

     /**
     * The seal function seals all supplied arguments.
     */
    public static void seal(Context cx, Scriptable thisObj, Object[] args,
                            Function funObj)
    {
        for (int i = 0; i != args.length; ++i) {
            Object arg = args[i];
            if (!(arg instanceof ScriptableObject) || arg == Undefined.instance)
            {
                if (!(arg instanceof Scriptable) || arg == Undefined.instance)
                {
                    throw new EvaluatorException("seal() can only be applied to Objects");
                } else {
                    throw new EvaluatorException("seal() can only be applied to Objects");
                }
            }
        }

        for (int i = 0; i != args.length; ++i) {
            Object arg = args[i];
            ((ScriptableObject)arg).sealObject();
        }
    }

    /**
     * (Try to) strip all HTML/XML style tags from the given string argument
     *
     * @param str a string
     * @return the string with tags removed
     */
    public String stripTags(String str) {
        if (str == null) {
            return null;
        }

        char[] c = str.toCharArray();
        boolean inTag = false;
        int i;
        int j = 0;

        for (i = 0; i < c.length; i++) {
            if (c[i] == '<') {
                inTag = true;
            }

            if (!inTag) {
                if (i > j) {
                    c[j] = c[i];
                }

                j++;
            }

            if (c[i] == '>') {
                inTag = false;
            }
        }

        if (i > j) {
            return new String(c, 0, j);
        }

        return str;
    }

    /**
     * Serialize a JavaScript object to a file.
     */
    public static void serialize(Context cx, Scriptable thisObj,
                                 Object[] args, Function funObj)
        throws IOException
    {
        if (args.length < 2) {
            throw Context.reportRuntimeError(
                "Expected an object to serialize and a filename to write " +
                "the serialization to");
        }
        Object obj = args[0];
        String filename = Context.toString(args[1]);
        FileOutputStream fos = new FileOutputStream(filename);
        Scriptable scope = ScriptableObject.getTopLevelScope(thisObj).getPrototype();
        // use a ScriptableOutputStream that unwraps Wrappers
        ScriptableOutputStream out = new ScriptableOutputStream(fos, scope) {
            protected Object replaceObject(Object obj) {
                if (obj instanceof Wrapper)
                    obj = ((Wrapper) obj).unwrap();
                return super.replaceObject(obj);
            }
        };
        out.writeObject(obj);
        out.close();
    }

    /**
     * Read a previously serialized JavaScript object from a file.
     */
    public static Object deserialize(Context cx, Scriptable thisObj,
                                     Object[] args, Function funObj)
        throws IOException, ClassNotFoundException
    {
        if (args.length < 1) {
            throw Context.reportRuntimeError(
                "Expected a filename to read the serialization from");
        }
        String filename = Context.toString(args[0]);
        FileInputStream fis = null;
        ObjectInputStream in = null;
        Object deserialized = null;
        Scriptable scope = null;
        try{
        	fis = new FileInputStream(filename);
            scope = ScriptableObject.getTopLevelScope(thisObj).getPrototype();
            in = new ScriptableInputStream(fis, scope);
            deserialized = in.readObject();
        } catch(IOException e){
        	e.printStackTrace();
        } finally{
        	in.close();
        }

        return Context.toObject(deserialized, scope);
    }

    /**
     * Set DONTENUM attrubutes on the given properties in this object. 
     * This is set on the JavaScript Object prototype.
     */
    public static Object dontEnum (Context cx, Scriptable thisObj, 
                                   Object[] args, Function funObj) {
        if (!(thisObj instanceof ScriptableObject)) {
            throw new EvaluatorException("dontEnum() called on non-ScriptableObject");
        }
        ScriptableObject obj = (ScriptableObject) thisObj;
        for (int i=0; i<args.length; i++) {
            if (!(args[i] instanceof String)) {
                throw new EvaluatorException("dontEnum() called with non-String argument");
            }
            String str = (String) args[i];
            if (obj.has(str, obj)) {
                obj.setAttributes(str, obj.getAttributes(str) | DONTENUM);
            }
        }
        return null;
    }

    private static String toString(Object obj) {
        if (obj == null || obj == Undefined.instance) {
            // Note: we might return "" here in order
            // to handle null/undefined as empty string
            return null;
        }
        return Context.toString(obj);
    }

    /**
     * Tell this PropertyRecorder to start recording changes to properties
     */
    public void startRecording() {
        changedProperties = new HashSet();
        isRecording = true;
    }

    /**
     * Tell this PropertyRecorder to stop recording changes to properties
     */
    public void stopRecording() {
        isRecording = false;
    }

    /**
     * Returns a set containing the names of properties changed since
     * the last time startRecording() was called.
     *
     * @return a Set containing the names of changed properties
     */
    public Set getChangeSet() {
        return changedProperties;
    }

    /**
     * Clear the set of changed properties.
     */
    public void clearChangeSet() {
        changedProperties = null;
    }

    /**
     * Convert an object into a wrapper that exposes the java
     * methods of the object to JavaScript. This is useful for
     * treating native numbers, strings, etc as their java
     * counterpart such as java.lang.Double, java.lang.String etc.
     * @param obj a java object that is wrapped in a special way
     * Rhino
     * @return the object wrapped as NativeJavaObject, exposing
     * the public methods of the underlying class.
     */
    public Object toJavaObject(Object obj) {
        if (obj == null || obj instanceof NativeJavaObject
                || obj == Undefined.instance) {
            return obj;
        }
        if (obj instanceof Wrapper) {
            obj = ((Wrapper) obj).unwrap();
        } else if (obj instanceof Scriptable) {
            String className = ((Scriptable) obj).getClassName();
            if ("Date".equals(className)) {
                return new NativeJavaObject(this,
                        new Date((long) ScriptRuntime.toNumber(obj)), null);
            }
        }
        return new NativeJavaObject(this, obj, null);
    }
    
}