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
 * $RCSfile: RhinoCore.java,v $
 * $Author: hannes $
 * $Revision: 1.90 $
 * $Date: 2006/05/12 13:30:47 $
 */

package axiom.scripting.rhino;

import org.mozilla.javascript.*;

import com.sun.org.apache.xalan.internal.xsltc.compiler.sym;

import axiom.framework.ErrorReporter;
import axiom.framework.core.*;
import axiom.framework.repository.Resource;
import axiom.objectmodel.*;
import axiom.objectmodel.db.DbMapping;
import axiom.objectmodel.db.DbSource;
import axiom.scripting.*;
import axiom.scripting.rhino.extensions.*;
import axiom.scripting.rhino.extensions.activex.ActiveX;
import axiom.util.CacheMap;
import axiom.util.ResourceProperties;
import axiom.util.SystemMap;
import axiom.util.TALTemplate;
import axiom.util.WeakCacheMap;
import axiom.util.WrappedMap;

import java.io.*;
import java.text.*;
import java.util.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * This is the implementation of ScriptingEnvironment for the Mozilla Rhino EcmaScript interpreter.
 */
public final class RhinoCore implements ScopeProvider {
    // the application we're running in
    public final Application app;

    // the global object
    GlobalObject global;

    // caching table for JavaScript object wrappers
    CacheMap wrappercache;

    // table containing JavaScript prototypes
    Hashtable prototypes;

    // timestamp of last type update
    volatile long lastUpdate = 0;

    // the wrap factory
    WrapFactory wrapper;

    // the prototype for AxiomObject
    ScriptableObject axiomObjectProto;
    
    // the prototype for FileObject
    ScriptableObject fileObjectProto;
    
    // the prototype for ImageObject
    ScriptableObject imageObjectProto;
    
    // Any error that may have been found in global code
    String globalError;

    // dynamic portion of the type check sleep that grows
    // as the app remains unchanged
    long updateSnooze = 500;
    
    // Prevent prototype resource checks from occuring
    // upon every request, but rather, the resource update checks should occur by demand
    private boolean isInitialized = false;
    
    /*  tal migration
     * 
     */
    static HashMap templates = new HashMap();
    private static HashMap cache = new HashMap();

    protected static String DEBUGGER_PROPERTY = "rhino.debugger";
    
    /**
     *  Create a Rhino evaluator for the given application and request evaluator.
     */
    public RhinoCore(Application app) {
        this.app = app;
        wrappercache = new WeakCacheMap(500);
        prototypes = new Hashtable();

        Context context = Context.enter();

        context.setLanguageVersion(170);
        context.setCompileFunctionsWithDynamicScope(true);
        context.setApplicationClassLoader(app.getClassLoader());
        wrapper = new WrapMaker();
        wrapper.setJavaPrimitiveWrap(false);
        context.setWrapFactory(wrapper);

        // Set default optimization level according to whether debugger is on
        int optLevel = "true".equals(app.getProperty(DEBUGGER_PROPERTY)) ? 0 : -1;

        String opt = app.getProperty("rhino.optlevel");
        if (opt != null) {
            try {
                optLevel = Integer.parseInt(opt);
            } catch (Exception ignore) {
                app.logError(ErrorReporter.errorMsg(this.getClass(), "ctor") 
                		+ "Invalid rhino optlevel: " + opt);
            }
        }

        context.setOptimizationLevel(optLevel);

        try {
            // create global object
            global = new GlobalObject(this, app, false);
            // call the initStandardsObject in ImporterTopLevel so that
            // importClass() and importPackage() are set up.
            global.initStandardObjects(context, false);
            global.init();

            axiomObjectProto =  AxiomObject.init(this); 
            
            fileObjectProto = FileObject.initFileProto(this); 
            
            imageObjectProto = ImageObject.initImageProto(this); 
            
            // Reference initialization
            try {
               new ReferenceObjCtor(this, Reference.init(this));
            } catch (Exception x) {
                app.logError(ErrorReporter.fatalErrorMsg(this.getClass(), "ctor") 
                		+ "Could not add ctor for Reference", x);
            } 
            
            // MultiValue initialization
            try {
                new MultiValueCtor(this, MultiValue.init(this));
            } catch (Exception x) {
                app.logError(ErrorReporter.fatalErrorMsg(this.getClass(), "ctor") 
                		+ "Could not add ctor for MultiValue", x);
            } 
            
            // use lazy loaded constructors for all extension objects that
            // adhere to the ScriptableObject.defineClass() protocol
            /*
             * Changes made, namely:
             * 
             * 1) Removed the File and Image objects in preference to the File/Image objects
             *      we built in house
             * 2) Added the following objects to the Rhino scripting environment:
             *      LdapClient
             *      Filter (for specifying queries)
             *      Sort (for specifying the sort order of queries)
             *      DOMParser (for parsing xml into a dom object)
             *      XMLSerializer (for writing a dom object to an xml string)
             * 
             */
            new LazilyLoadedCtor(global, "FtpClient",
                    "axiom.scripting.rhino.extensions.FtpObject", false);
            new LazilyLoadedCtor(global, "LdapClient",
                    "axiom.scripting.rhino.extensions.LdapObject", false);
            new LazilyLoadedCtor(global, "Filter",
                    "axiom.scripting.rhino.extensions.filter.FilterObject", false);
            new LazilyLoadedCtor(global, "NativeFilter",
                    "axiom.scripting.rhino.extensions.filter.NativeFilterObject", false);
            new LazilyLoadedCtor(global, "AndFilter",
                    "axiom.scripting.rhino.extensions.filter.AndFilterObject", false);
            new LazilyLoadedCtor(global, "OrFilter",
                    "axiom.scripting.rhino.extensions.filter.OrFilterObject", false);
            new LazilyLoadedCtor(global, "NotFilter",
                    "axiom.scripting.rhino.extensions.filter.NotFilterObject", false);
            new LazilyLoadedCtor(global, "RangeFilter",
                    "axiom.scripting.rhino.extensions.filter.RangeFilterObject", false);
            new LazilyLoadedCtor(global, "SearchFilter",
                    "axiom.scripting.rhino.extensions.filter.SearchFilterObject", false);
            new LazilyLoadedCtor(global, "Sort",
                    "axiom.scripting.rhino.extensions.filter.SortObject", false);

            ArrayList names = app.getDbNames();
    		for(int i = 0; i < names.size(); i++){
    			try{
    				String dbname = names.get(i).toString();
    				DbSource dbsource = app.getDbSource(dbname);
    				String hitsObject = dbsource.getProperty("hitsObject", null);
    				String hitsClass = dbsource.getProperty("hitsClass", null);
    				if(hitsObject != null && hitsClass != null){
    		            new LazilyLoadedCtor(global, hitsObject,
    		            		hitsClass, false);
    				}
    				String filters = dbsource.getProperty("filters", null);
    				if(filters != null){
	    				StringBuffer sb = new StringBuffer(filters);
	        			int idx;
	        			while ((idx = sb.indexOf("{")) > -1) {
	        				sb.deleteCharAt(idx);
	        			}
	        			while ((idx = sb.indexOf("}")) > -1) {
	        				sb.deleteCharAt(idx);
	        			}

	        			filters = sb.toString().trim();
	        			String[] pairs = filters.split(",");
	        			for (int j = 0; j < pairs.length; j++) {
	        				String[] pair = pairs[j].split(":");
	    		            new LazilyLoadedCtor(global, pair[0].trim(),
	    		            		pair[1].trim(), false);
	        			}
    				}

    			}
    			catch(Exception e){
    				app.logError("Error during LazilyLoadedCtor initialization for external databases");
    			}
    		}
            
            new LazilyLoadedCtor(global, "LuceneHits",
                    "axiom.scripting.rhino.extensions.LuceneHitsObject", false);
            new LazilyLoadedCtor(global, "TopDocs",
                    "axiom.scripting.rhino.extensions.TopDocsObject", false);
            new LazilyLoadedCtor(global, "DOMParser",
                    "axiom.scripting.rhino.extensions.DOMParser", false);
            new LazilyLoadedCtor(global, "XMLSerializer",
                    "axiom.scripting.rhino.extensions.XMLSerializer", false);     
            if ("true".equalsIgnoreCase(app.getProperty(RhinoCore.DEBUGGER_PROPERTY))) {
            	new LazilyLoadedCtor(global, "Debug",
                        "axiom.scripting.rhino.debug.Debug", false);
            }
            
            MailObject.init(global, app.getProperties());
            // defining the ActiveX scriptable object for exposure of its API in Axiom
            ScriptableObject.defineClass(global, ActiveX.class);
            
            // add some convenience functions to string, date and number prototypes
            Scriptable stringProto = ScriptableObject.getClassPrototype(global, "String");
            stringProto.put("trim", stringProto, new StringTrim());

            Scriptable dateProto = ScriptableObject.getClassPrototype(global, "Date");
            dateProto.put("format", dateProto, new DateFormat());

            Scriptable numberProto = ScriptableObject.getClassPrototype(global, "Number");
            numberProto.put("format", numberProto, new NumberFormat());

            initialize();
            loadTemplates(app);
        } catch (Exception e) {
            System.err.println("Cannot initialize interpreter");
            System.err.println("Error: " + e);
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        } finally {
            Context.exit();
        }
    }

    /**
     *  Initialize the evaluator, making sure the minimum type information
     *  necessary to bootstrap the rest is parsed.
     */
    private synchronized void initialize() {
        Collection protos = app.getPrototypes();

        for (Iterator i = protos.iterator(); i.hasNext();) {
            Prototype proto = (Prototype) i.next();

            initPrototype(proto);
//            getPrototype(proto.getName());
        }

        // always fully initialize global prototype, because
        // we always need it and there's no chance to trigger
        // creation on demand.
        getPrototype("global");
    }

    /**
     *   Initialize a prototype info without compiling its script files.
     *
     *  @param prototype the prototype to be created
     */
    private synchronized void initPrototype(Prototype prototype) {

        String name = prototype.getName();
        String lowerCaseName = prototype.getLowerCaseName();

        TypeInfo type = (TypeInfo) prototypes.get(lowerCaseName);

        // check if the prototype info exists already
        ScriptableObject op = (type == null) ? null : type.objProto;

        // if prototype info doesn't exist (i.e. is a standard prototype
        // built by AxiomExtension), create it.
        if (op == null) {
            if ("global".equals(lowerCaseName)) {
                op = global;
            } else if ("axiomobject".equals(lowerCaseName)) {
                op = axiomObjectProto;
            } else if ("file".equals(lowerCaseName)) {
                op = fileObjectProto;
            } else if ("image".equals(lowerCaseName)) { 
                op = imageObjectProto;
            } else {
                op = new AxiomObject(name, this);
            }
            registerPrototype(prototype, op);
        }

        // Register a constructor for all types except global.
        // This will first create a new prototyped AxiomObject and then calls
        // the actual (scripted) constructor on it.
        if (!"global".equals(lowerCaseName)) {
            if ("file".equals(lowerCaseName)) {
                try {
                    new FileObjectCtor(this, op);
                    op.setParentScope(global);
                } catch (Exception x) {
                    app.logError(ErrorReporter.fatalErrorMsg(this.getClass(), "initPrototype") 
                    		+ "Could not add ctor for " + name, x);
                }
            } else if ("image".equals(lowerCaseName)) {
                try {
                    new ImageObjectCtor(this, op);
                    op.setParentScope(global);
                } catch (Exception x) {
                	app.logError(ErrorReporter.fatalErrorMsg(this.getClass(), "initPrototype") 
                    		+ "Could not add ctor for " + name, x);
                } 
            } else {
                try {
                    new AxiomObjectCtor(name, this, op);
                    op.setParentScope(global);
                } catch (Exception x) {
                	app.logError(ErrorReporter.fatalErrorMsg(this.getClass(), "initPrototype") 
                    		+ "Could not add ctor for " + name, x);
                }
            }
        }
    }

    /**
     *  Set up a prototype, parsing and compiling all its script files.
     *
     *  @param type the info, containing the object proto, last update time and
     *         the set of compiled functions properties
     */
    private synchronized void evaluatePrototype(TypeInfo type) {

        type.prepareCompilation();
        Prototype prototype = type.frameworkProto;

        // set the parent prototype in case it hasn't been done before
        // or it has changed...
        setParentPrototype(prototype, type);

        type.error = null;
        if ("global".equals(prototype.getLowerCaseName())) {
            globalError = null;
        }

        // loop through the prototype's code elements and evaluate them
        ArrayList code = prototype.getCodeResourceList();
        final int size = code.size();
        for (int i = 0; i < size; i++) {
            evaluate(type, (Resource) code.get(i));
        }
        loadCompiledCode(type, prototype.getName());
        type.commitCompilation();
    }
    
    private void loadTemplates(Application app) throws Exception {
		HashMap templSources = getAllTemplateSources();

		Iterator keys = templSources.keySet().iterator();
		while (keys.hasNext()) {
			String name = keys.next().toString();
			retrieveDocument(app, name);
		}
	}

    public Object retrieveDocument(Application app, String name) 
	throws Exception {
		HashMap map = retrieveTemplatesForApp(app);
		Object xml = null;      
		Resource templateSource = (Resource) findTemplateSource(name, app);
		if (templateSource == null) {
			// throw new Exception("ERROR in TAL(): Could not find the TAL template file indicated by '" + name + "'.");
			return null;
		}
		TALTemplate template = null;

		synchronized (map) {
			if ((template = getTemplateByName(map, name)) != null && template.getLastModified() >= templateSource.lastModified()) { 
				xml = template.getDocument();
			} else {
				xml = ((Resource) templateSource).getContent().trim();
				if (template != null) {
					template.setDocument(xml);
					template.setLastModified(templateSource.lastModified());
				} else {
					map.put(name, new TALTemplate(name, xml, templateSource.lastModified()));
				}
			}    
		}
		return xml;
	}
	
	private static TALTemplate getTemplateByName(HashMap map, String name) {
        return (TALTemplate) map.get(name);
    }
	
	private static HashMap retrieveTemplatesForApp(Application app) {
        HashMap map = null;
        synchronized (templates) {
            Object ret = templates.get(app);
            if (ret != null)
                map = (HashMap) ret;
            else {
                map = new HashMap();
                templates.put(app, map);
            }
        }
        return map;
    }
	
	public static Object findTemplateSource(String name, Application app) throws Exception {
    	Resource resource = (Resource) cache.get(name);
        if (resource == null || !resource.exists()) {
            resource = null;
            try {
                int pos = name.indexOf(':');
                Prototype prototype = app.getPrototypeByName(name.substring(0, pos));
                name = name.substring(pos + 1) + ".tal";
                if (prototype != null) {
                    resource = scanForResource(prototype, name);
                }
            } catch (Exception ex) {
                throw new Exception("Unable to resolve source: " + name + " " + ex);
            }
        }
        return resource;
    }
	
	private static Resource scanForResource(Prototype prototype, String name) throws Exception {
		Resource resource = null;
		// scan for resources with extension .tal:
		Resource[] resources = prototype.getResources();
		for (int i = 0; i < resources.length; i++) {
			Resource res = resources[i];
			if (res.exists() && res.getShortName().equals(name)) {
				cache.put(name, res);
				resource = res;
				break;
			}
		}
		return resource;
	}
	
	public HashMap getAllTemplateSources() throws Exception {
        HashMap templSources = new HashMap();
        ArrayList mylist = new ArrayList(app.getPrototypes());
        final int size = mylist.size();
        for (int i = 0; i < size; i++) {
            Prototype prototype = (Prototype) mylist.get(i);
            String proto = prototype.getName() + ":";
            Resource[] resources = prototype.getResources();
            final int length = resources.length;
            for (int j = 0; j < length; j++) {
                if (resources[j].exists() && resources[j].getShortName().endsWith(".tal")) {
                    templSources.put(proto + resources[j].getBaseName(), resources[j]);
                }
            }
        }
        return templSources;
    }

/*
 * 
 * 
 * 
 * 
 * 
 */
    /**
     *  Set the parent prototype on the ObjectPrototype.
     *
     *  @param prototype the prototype spec
     *  @param type the prototype object info
     */
    private void setParentPrototype(Prototype prototype, TypeInfo type) {
        String name = prototype.getName();
        String lowerCaseName = prototype.getLowerCaseName();

        if (!"global".equals(lowerCaseName) && !"axiomobject".equals(lowerCaseName)) {

            // get the prototype's prototype if possible and necessary
            TypeInfo parentType = null;
            Prototype parent = prototype.getParentPrototype();

            if (parent != null) {
                // see if parent prototype is already registered. if not, register it
                parentType = getPrototypeInfo(parent.getName());
            }

            if (parentType == null && !app.isJavaPrototype(name)) {
                // FIXME: does this ever occur?
                parentType = getPrototypeInfo("axiomobject");
            }

            type.setParentType(parentType);
        }
    }

    /**
     *  This method is called before an execution context is entered to let the
     *  engine know it should update its prototype information. The update policy
     *  here is to check for update those prototypes which already have been compiled
     *  before. Others will be updated/compiled on demand.
     */
    public synchronized void updatePrototypes(boolean forceUpdate) throws IOException {
        if ((System.currentTimeMillis() - lastUpdate) < 1000L + updateSnooze) {
            return;
        }
        // We are no longer checking for prototype updates on a
        // per request basis, but rather only when there is an explicit request to update
        // the prototype resource mappings
        if (!forceUpdate && this.isInitialized) {
        	return;
        }

        // init prototypes and/or update prototype checksums
        app.typemgr.checkPrototypes();

        // get a collection of all prototypes (code directories)
        Collection protos = app.getPrototypes();

        // in order to respect inter-prototype dependencies, we try to update
        // the global prototype before all other prototypes, and parent
        // prototypes before their descendants.

        HashSet checked = new HashSet(protos.size() * 2);

        TypeInfo type = (TypeInfo) prototypes.get("global");

        if (type != null) {
            updatePrototype(type, checked);
        }

        for (Iterator i = protos.iterator(); i.hasNext();) {
            Prototype proto = (Prototype) i.next();
            if (checked.contains(proto)) {
                continue;
            }

            type = (TypeInfo) prototypes.get(proto.getLowerCaseName());

            if (type == null) {
                // a prototype we don't know anything about yet. Init local update info.
                initPrototype(proto);
            } 
            
            type = (TypeInfo) prototypes.get(proto.getLowerCaseName());
            if (type != null && (type.lastUpdate > -1 || !this.isInitialized)) {
                // only need to update prototype if it has already been initialized.
                // otherwise, this will be done on demand.
            	updatePrototype(type, checked);
            }  
        }
        
        this.isInitialized = true;
        //lastUpdate = System.currentTimeMillis();
        // max updateSnooze is 4 seconds, reached after 66.6 idle minutes
        //long newSnooze = (lastUpdate - app.typemgr.getLastCodeUpdate()) / 1000;
        //updateSnooze = Math.min(4000, Math.max(0, newSnooze));
    }

    /**
     * Check one prototype for updates. Used by <code>upatePrototypes()</code>.
     *
     * @param type the type info to check
     * @param checked a set of prototypes that have already been checked
     */
    private void updatePrototype(TypeInfo type, HashSet checked) {
        // first, remember prototype as updated
        checked.add(type.frameworkProto);

        if (type.parentType != null &&
                !checked.contains(type.parentType.frameworkProto)) {
            updatePrototype(type.getParentType(), checked);
        }

        // let the prototype check if its resources have changed
        type.frameworkProto.checkForUpdates();

        // and re-evaluate if necessary
        if (type.needsUpdate() || !this.isInitialized) {
            evaluatePrototype(type);
        }
    }

    /**
     * A version of getPrototype() that retrieves a prototype and checks
     * if it is valid, i.e. there were no errors when compiling it. If
     * invalid, a ScriptingException is thrown.
     */
    public Scriptable getValidPrototype(String protoName) {
        if (globalError != null) {
            throw new EvaluatorException(globalError);
        }
        TypeInfo type = getPrototypeInfo(protoName);
        if (type != null) {
            if (type.hasError()) {
                throw new EvaluatorException(type.getError());
            }
            return type.objProto;
        }
        return null;
    }

    /**
     *  Get the object prototype for a prototype name and initialize/update it
     *  if necessary. The policy here is to update the prototype only if it
     *  hasn't been updated before, otherwise we assume it already was updated
     *  by updatePrototypes(), which is called for each request.
     */
    public Scriptable getPrototype(String protoName) {
        TypeInfo type = getPrototypeInfo(protoName);
        return type == null ? null : type.objProto;
    }

    /**
     * Get an array containing the property ids of all properties that were
     * compiled from scripts for the given prototype.
     *
     * @param protoName the name of the prototype
     * @return an array containing all compiled properties of the given prototype
     */
    public Map getPrototypeProperties(String protoName) {
        TypeInfo type = getPrototypeInfo(protoName);
        SystemMap map = new SystemMap();
        Iterator it =    type.compiledProperties.iterator();
        while(it.hasNext()) {
            Object key = it.next();
            if (key instanceof String)
                map.put(key, type.objProto.get((String) key, type.objProto));
        }
        return map;
    }

    /**
     *  Private helper function that retrieves a prototype's TypeInfo
     *  and creates it if not yet created. This is used by getPrototype() and
     *  getValidPrototype().
     */
    private TypeInfo getPrototypeInfo(String protoName) {
        if (protoName == null) {
            return null;
        }

        TypeInfo type = (TypeInfo) prototypes.get(protoName.toLowerCase());

        // if type exists and hasn't been evaluated (used) yet, evaluate it now.
        // otherwise, it has already been evaluated for this request by updatePrototypes(),
        // which is called before a request is handled.
        if ((type != null) && (type.lastUpdate == -1)) {
            type.frameworkProto.checkForUpdates();

            if (type.needsUpdate()) {
                evaluatePrototype(type);
            }
        }

        return type;
    }

    /**
     * Register an object prototype for a prototype name.
     */
    private TypeInfo registerPrototype(Prototype proto, ScriptableObject op) {
        TypeInfo type = new TypeInfo(proto, op);
        prototypes.put(proto.getLowerCaseName(), type);
        return type;
    }

    /**
    * Check if an object has a function property (public method if it
    * is a java object) with that name.
    */
    public boolean hasFunction(String protoname, String fname) {
        // throws EvaluatorException if type has a syntax error
        Scriptable op = getValidPrototype(protoname);

        // if this is an untyped object return false
        if (op == null) {
            return false;
        }

        return ScriptableObject.getProperty(op, fname) instanceof Function;
    }

    /**
     *  Convert an input argument from Java to the scripting runtime
     *  representation.
     */
    public Object processXmlRpcArgument (Object what) throws Exception {
        if (what == null)
            return null;
        if (what instanceof Vector) {
            Vector v = (Vector) what;
            Object[] a = v.toArray();
            for (int i=0; i<a.length; i++) {
                a[i] = processXmlRpcArgument(a[i]);
            }
            return Context.getCurrentContext().newArray(global, a);
        }
        if (what instanceof Hashtable) {
            Hashtable t = (Hashtable) what;
            for (Enumeration e=t.keys(); e.hasMoreElements(); ) {
                Object key = e.nextElement();
                t.put(key, processXmlRpcArgument(t.get(key)));
            }
            return Context.toObject(new SystemMap(t), global);
        }
        if (what instanceof String)
            return what;
        if (what instanceof Number)
            return what;
        if (what instanceof Boolean)
            return what;
        if (what instanceof Date) {
            Date d = (Date) what;
            Object[] args = { new Long(d.getTime()) };
            return Context.getCurrentContext().newObject(global, "Date", args);
        }
        return Context.toObject(what, global);
    }

    /**
     * convert a JavaScript Object object to a generic Java object stucture.
     */
    public Object processXmlRpcResponse (Object what) throws Exception {
        // unwrap if argument is a Wrapper
        if (what instanceof Wrapper) {
            what = ((Wrapper) what).unwrap();
        }
        if (what instanceof NativeObject) {
            NativeObject no = (NativeObject) what;
            Object[] ids = no.getIds();
            Hashtable ht = new Hashtable(ids.length*2);
            for (int i=0; i<ids.length; i++) {
                if (ids[i] instanceof String) {
                    String key = (String) ids[i];
                    Object o = no.get(key, no);
                    if (o != null) {
                        ht.put(key, processXmlRpcResponse(o));
                    }
                }
            }
            what = ht;
        } else if (what instanceof NativeArray) {
            NativeArray na = (NativeArray) what;
            Number n = (Number) na.get("length", na);
            int l = n.intValue();
            Vector retval = new Vector(l);
            for (int i=0; i<l; i++) {
                retval.add(i, processXmlRpcResponse(na.get(i, na)));
            }
            what = retval;
        } else if (what instanceof Map) {
            Map map = (Map) what;
            Hashtable ht = new Hashtable(map.size()*2);
            for (Iterator it=map.entrySet().iterator(); it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                ht.put(entry.getKey().toString(),
                       processXmlRpcResponse(entry.getValue()));
            }
            what = ht;
        } else if (what instanceof Number) {
            Number n = (Number) what;
            if (what instanceof Float || what instanceof Long) {
                what = new Double(n.doubleValue());
            } else if (!(what instanceof Double)) {
                what = new Integer(n.intValue());
            }
        } else if (what instanceof Scriptable) {
            Scriptable s = (Scriptable) what;
            if ("Date".equals(s.getClassName())) {
                what = new Date((long) ScriptRuntime.toNumber(s));
            }
        }
        return what;
    }


    /**
     * Return the application we're running in
     */
    public Application getApplication() {
        return app;
    }


    /**
     *  Get a Script wrapper for any given object. If the object implements the IPathElement
     *  interface, the getPrototype method will be used to retrieve the name of the prototype
     * to use. Otherwise, a Java-Class-to-Script-Prototype mapping is consulted.
     */
    public Scriptable getElementWrapper(Object e) {
        WeakReference ref = (WeakReference) wrappercache.get(e);
        Scriptable wrapper = ref == null ? null : (Scriptable) ref.get();

        if (wrapper == null) {
            // Gotta find out the prototype name to use for this object...
            String prototypeName = app.getPrototypeName(e);
            Scriptable op = getPrototype(prototypeName);

            if (op == null) {
                // no prototype found, return an unscripted wrapper
                wrapper = new NativeJavaObject(global, e, e.getClass());
            } else {
                wrapper = new JavaObject(global, e, prototypeName, op, this);
            }

            wrappercache.put(e, new WeakReference(wrapper));
        }

        return wrapper;
    }

    /**
     *  Get a script wrapper for an instance of axiom.objectmodel.INode
     */
    public Scriptable getNodeWrapper(INode n) {
        if (n == null) {
            return null;
        }

        AxiomObject esn = (AxiomObject) wrappercache.get(n);

        if (esn == null) {

            String protoname = n.getPrototype();

            Scriptable op = getValidPrototype(protoname);

            // no prototype found for this node
            if (op == null) {
                // maybe this object has a prototype name that has been
                // deleted, but the storage layer was able to set a
                // DbMapping matching the relational table the object
                // was fetched from.
                DbMapping dbmap = n.getDbMapping();
                if (dbmap != null && (protoname = dbmap.getTypeName()) != null) {
                    op = getValidPrototype(protoname);
                }

                // if not found, fall back to AxiomObject prototype
                if (op == null) {
                    protoname = "AxiomObject";
                    op = getValidPrototype("AxiomObject");
                }
            }

            if ("File".equals(protoname)) {
                esn = new FileObject("File", this, n, op);
            } else if ("Image".equals(protoname)) {
                esn = new ImageObject("Image", this, n, op);
            } else { 
                esn = new AxiomObject(protoname, this, n, op);
            }
            
            wrappercache.put(n, esn);
        }

        return esn;
    }

    protected String postProcessHref(Object obj, String protoName, String basicHref)
            throws UnsupportedEncodingException, IOException {
        // check if the app.properties specify a href-function to post-process the
        // basic href.
        String hrefFunction = app.getProperty("hrefFunction", null);

        if (hrefFunction != null) {

            Object handler = obj;
            String proto = protoName;

            while (handler != null) {
                if (hasFunction(proto, hrefFunction)) {

                    // get the currently active rhino engine and invoke the function
                    Context cx = Context.getCurrentContext();
                    RhinoEngine engine = (RhinoEngine) cx.getThreadLocal("engine");
                    Object result;

                    try {
                        result = engine.invoke(handler, hrefFunction,
                                               new Object[] { basicHref },
                                               ScriptingEngine.ARGS_WRAP_DEFAULT,
                                               false);
                    } catch (ScriptingException x) {
                        throw new EvaluatorException("Error in hrefFunction: " + x);
                    }

                    if (result == null) {
                        throw new EvaluatorException("hrefFunction " + hrefFunction +
                                                       " returned null");
                    }

                    basicHref = result.toString();
                    break;
                }
                handler = app.getParentElement(handler);
                proto = app.getPrototypeName(handler);

            }
        }

        return basicHref;
    }

    ////////////////////////////////////////////////
    // private evaluation/compilation methods
    ////////////////////////////////////////////////

    private synchronized void loadCompiledCode(TypeInfo type, String protoname) {
        // get the current context
        Context cx = Context.getCurrentContext();
        // unregister the per-thread scope while evaluating
        Object threadScope = cx.getThreadLocal("threadscope");
        cx.removeThreadLocal("threadscope");
        
        try{
        	Script compiled = (Script) Class.forName("axiom.compiled."+protoname).newInstance();
        	compiled.exec(cx, type.objProto);
        }catch(ClassNotFoundException e){
        	// no compiled code loaded, ignore
        }catch(Exception e){
            app.logError(ErrorReporter.errorMsg(this.getClass(), "loadCompiledCode") 
            		+ "Error loading compiled js for prototype: " + protoname, e);
            // mark prototype as broken
            if (type.error == null) {
                type.error = e.getMessage();
                if (type.error == null || e instanceof EcmaError) {
                    type.error = e.toString();
                }
                if ("global".equals(type.frameworkProto.getLowerCaseName())) {
                    globalError = type.error;
                }
                wrappercache.clear();
            }
        	        	
        }finally{
        	if (threadScope != null) {
                cx.putThreadLocal("threadscope", threadScope);
            }
        }
	}	
    
    private synchronized void evaluate (TypeInfo type, Resource code) {
        // get the current context
        Context cx = Context.getCurrentContext();
        // unregister the per-thread scope while evaluating
        Object threadScope = cx.getThreadLocal("threadscope");
        cx.removeThreadLocal("threadscope");

        String sourceName = code.getName();
        Reader reader = null;

        try {
            Scriptable op = type.objProto;

            // do the update, evaluating the file
            if (sourceName.endsWith(".js")) {
                reader = new InputStreamReader(code.getInputStream());
            } else if (sourceName.endsWith(".tal")) {
            	reader = new StringReader(ResourceConverter.convertTal(code));
            } 
            cx.evaluateReader(op, reader, sourceName, 1, null);
        } catch (Exception e) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "evaluate") 
            		+ "Error parsing file " + sourceName,  e);
            // mark prototype as broken
            if (type.error == null) {
                type.error = e.getMessage();
                if (type.error == null || e instanceof EcmaError) {
                    type.error = e.toString();
                }
                if ("global".equals(type.frameworkProto.getLowerCaseName())) {
                    globalError = type.error;
                }
                wrappercache.clear();
            }
            // e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                }
            }
            if (threadScope != null) {
                cx.putThreadLocal("threadscope", threadScope);
            }
        }
    }

    /**
     *  Return the global scope of this RhinoCore.
     */
    public Scriptable getScope() {
        return global;
    }
    
    public GlobalObject getGlobal(){
    	return global;
    }
    
    /**
     *  TypeInfo helper class
     */
    class TypeInfo {

        // the framework prototype object
        Prototype frameworkProto;

        // the JavaScript prototype for this type
        ScriptableObject objProto;

        // timestamp of last update. This is -1 so even an empty prototype directory
        // (with lastUpdate == 0) gets evaluated at least once, which is necessary
        // to get the prototype chain set.
        long lastUpdate = -1;

        // the parent prototype info
        TypeInfo parentType;

        // a set of property keys that were in script compilation.
        // Used to decide which properties should be removed if not renewed.
        Set compiledProperties;

        // a set of property keys that were present before first script compilation
        final Set predefinedProperties;

        String error;

        public TypeInfo(Prototype proto, ScriptableObject op) {
            frameworkProto = proto;        
            objProto = op;
            // remember properties already defined on this object prototype
            compiledProperties = new HashSet();
            predefinedProperties = new HashSet();
            Object[] keys = op.getAllIds();
            for (int i = 0; i < keys.length; i++) {
                predefinedProperties.add(keys[i].toString());
            }
        }

        /**
         * If prototype implements PropertyRecorder tell it to start
         * registering property puts.
         */
        public void prepareCompilation() {
            if (objProto instanceof PropertyRecorder) {
                ((PropertyRecorder) objProto).startRecording();
            }
        }

        /**
         * Compilation has been completed successfully - switch over to code
         * from temporary prototype, removing properties that haven't been
         * renewed.
         */
        public void commitCompilation() {
            // loop through properties defined on the prototype object
            // and remove thos properties which haven't been renewed during
            // this compilation/evaluation pass.
            if (objProto instanceof PropertyRecorder) {

                PropertyRecorder recorder = (PropertyRecorder) objProto;

                recorder.stopRecording();
                Set changedProperties = recorder.getChangeSet();
                recorder.clearChangeSet();

                // ignore all  properties that were defined before we started
                // compilation. We won't manage these properties, even
                // if they were set during compilation.
                changedProperties.removeAll(predefinedProperties);

                // remove all renewed properties from the previously compiled
                // property names so we can remove those properties that were not
                // renewed in this compilation
                compiledProperties.removeAll(changedProperties);

                Iterator it = compiledProperties.iterator();
                while (it.hasNext()) {
                    String key = (String) it.next();

                    try {
                        objProto.setAttributes(key, 0);
                        objProto.delete(key);
                    } catch (Exception px) {
                        System.err.println("Error unsetting property "+key+" on "+
                                           frameworkProto.getName());
                    }
                }

                // update compiled properties
                compiledProperties = changedProperties;
            }

            // mark this type as updated
            lastUpdate = frameworkProto.lastCodeUpdate();

            // If this prototype defines a postCompile() function, call it
            Context cx = Context.getCurrentContext();
            try {
                Object fObj = ScriptableObject.getProperty(objProto,
                                                           "onCodeUpdate");
                if (fObj instanceof Function) {
                    Object[] args = {frameworkProto.getName()};
                    ((Function) fObj).call(cx, global, objProto, args);
                }
            } catch (Exception x) {
                app.logError(ErrorReporter.errorMsg(this.getClass(), "commitCompilation")  
                		+ "Exception in "+frameworkProto.getName()+
                             ".onCodeUpdate(): " + x, x);
            }
        }

        public boolean needsUpdate() {
            return frameworkProto.lastCodeUpdate() > lastUpdate;
        }

        public void setParentType(TypeInfo type) {
            parentType = type;
            if (type == null) {
                objProto.setPrototype(null);
            } else {
                objProto.setPrototype(type.objProto);
            }
        }

        public TypeInfo getParentType() {
            return parentType;
        }

        public boolean hasError() {
            TypeInfo p = this;
            while (p != null) {
                if (p.error != null)
                    return true;
                p = p.parentType;
            }
            return false;
        }

        public String getError() {
            TypeInfo p = this;
            while (p != null) {
                if (p.error != null)
                    return p.error;
                p = p.parentType;
            }
            return null;
        }

        public String toString() {
            return ("TypeInfo[" + frameworkProto + "," + new Date(lastUpdate) + "]");
        }
    }

    /**
     *  Object wrapper class
     */
    class WrapMaker extends WrapFactory {

        public Object wrap(Context cx, Scriptable scope, Object obj, Class staticType) {
            // Wrap Nodes
            if (obj instanceof INode) {
                return getNodeWrapper((INode) obj);
            }

            // Masquerade SystemMap and WrappedMap as native JavaScript objects
            if (obj instanceof SystemMap || obj instanceof WrappedMap) {
                return new MapWrapper((Map) obj, RhinoCore.this);
            }

            // Convert java.util.Date objects to JavaScript Dates
            if (obj instanceof Date) {
                Object[] args = { new Long(((Date) obj).getTime()) };
                try {
                    return cx.newObject(global, "Date", args);
                 } catch (JavaScriptException nafx) {
                    return obj;
                }
            }

            // Wrap scripted Java objects
            if (obj != null && app.getPrototypeName(obj) != null) {
                return getElementWrapper(obj);
            }
            
            return super.wrap(cx, scope, obj, staticType);
        }

        public Scriptable wrapNewObject(Context cx, Scriptable scope, Object obj) {
            // System.err.println ("N-Wrapping: "+obj);
            if (obj instanceof INode) {
                return getNodeWrapper((INode) obj);
            }

            if (obj != null && app.getPrototypeName(obj) != null) {
                return getElementWrapper(obj);
            }

            return super.wrapNewObject(cx, scope, obj);
        }
    }

    class StringTrim extends BaseFunction {
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            String str = thisObj.toString();
            return str.trim();
        }
    }

    class DateFormat extends BaseFunction {
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            Date date = new Date((long) ScriptRuntime.toNumber(thisObj));
            SimpleDateFormat df;

            if (args.length > 0 && args[0] != Undefined.instance && args[0] != null) {
                if (args.length > 1 && args[1] instanceof NativeJavaObject) {
                    Object locale = ((NativeJavaObject) args[1]).unwrap();
                    if (locale instanceof Locale) {
                        df = new SimpleDateFormat(args[0].toString(), (Locale) locale);
                    } else {
                        throw new IllegalArgumentException("Second argument to Date.format() not a java.util.Locale: " +
                                                            locale.getClass());
                    }
                } else {
                    df = new SimpleDateFormat(args[0].toString());
                }
            } else {
                df = new SimpleDateFormat();
            }
            return df.format(date);
        }
    }

    class NumberFormat extends BaseFunction {
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            DecimalFormat df;
            if (args.length > 0 && args[0] != Undefined.instance) {
                df = new DecimalFormat(args[0].toString());
            } else {
                df = new DecimalFormat("#,##0.00");
            }
            return df.format(ScriptRuntime.toNumber(thisObj));
        }
    }

}
