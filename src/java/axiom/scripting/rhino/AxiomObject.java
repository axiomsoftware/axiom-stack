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
 * $RCSfile: AxiomObject.java,v $
 * $Author: hannes $
 * $Revision: 1.65 $
 * $Date: 2006/04/10 10:07:42 $
 */

/* 
 * Modified by (and changed the name of):
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */
package axiom.scripting.rhino;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeFunction;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.PropertyException;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import axiom.extensions.tal.TALExtension;
import axiom.framework.ErrorReporter;
import axiom.framework.IPathElement;
import axiom.framework.RequestBean;
import axiom.framework.RequestTrans;
import axiom.framework.core.ActionSecurityManager;
import axiom.framework.core.Application;
import axiom.framework.core.ApplicationBean;
import axiom.framework.core.Prototype;
import axiom.objectmodel.INode;
import axiom.objectmodel.IProperty;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.db.DbMapping;
import axiom.objectmodel.db.Key;
import axiom.objectmodel.db.Node;
import axiom.objectmodel.db.NodeHandle;
import axiom.objectmodel.db.Relation;
import axiom.objectmodel.dom.LuceneManager;
import axiom.scripting.rhino.extensions.DOMParser;
import axiom.util.CacheMap;
import axiom.util.ExecutionCache;
import axiom.util.ResourceProperties;
import axiom.util.WeakCacheMap;


/**
 * This class wraps an Axiom object for accessibility on the Rhino scripting layer.
 * 
 * @jsconstructor
 */
public class AxiomObject extends ScriptableObject implements Wrapper, PropertyRecorder {

	protected String className;
	protected INode node;
	protected RhinoCore core;
	
	static HashMap dependencies = new HashMap();
	long dependenciesLastModified;
	final SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/y");
	private static final String DEPENDSALL = "#DEPENDSALL#";

	// fields to implement PropertyRecorder
	private boolean isRecording = false;
	private HashSet changedProperties;

	private CacheMap referenceCache = new WeakCacheMap(300);

	/**
	 * Creates a new AxiomObject prototype.
	 *
	 * @param className the prototype name
	 * @param core the RhinoCore
	 */
	protected AxiomObject(String className, RhinoCore core) {
		this.className = className;
		this.core = core;
		setParentScope(core.global);   
	}


	/**
	 * Creates a new AxiomObject.
	 *
	 * @param className the className
	 * @param proto the object's prototype
	 */
	protected AxiomObject(String className, RhinoCore core,
			INode node, Scriptable proto) {
		this(className, core);
		this.node = node;
		setPrototype(proto);
	}
    
     /**
     * Creates a new AxiomObject.
     *
     * @param className the className
     * @param proto the object's prototype
     */
    protected AxiomObject(String className, RhinoCore core,
                        INode node, Scriptable proto, boolean setupdefault) {
        this(className, core);
        this.node = node;
        setPrototype(proto);
        if (setupdefault) {// set up default properties
            setupDefaultProperties();
        }
    }

    /**
     * Creates a new AxiomObject.
     *
     * @param className the className
     * @param proto the object's prototype
     */
    protected AxiomObject(String className, RhinoCore core,
                        INode node, Scriptable proto, Scriptable data) {
        this(className, core);
        this.node = node;
        setPrototype(proto);
        ResourceProperties props = this.getResourceProperties(node.getPrototype());

        Object[] keys = data.getIds();

    	String id = (data.get(LuceneManager.ID, data) == Undefined.instance || data.get(LuceneManager.ID, data) == NOT_FOUND) ? null : data.get(LuceneManager.ID, data).toString();
    	String parentid = (data.get(LuceneManager.PARENTID, data) == Undefined.instance || data.get(LuceneManager.PARENTID, data) == NOT_FOUND) ? null : data.get(LuceneManager.PARENTID, data).toString();
    	String parentProto = data.get(LuceneManager.PARENTPROTOTYPE, data) == null ? null : data.get(LuceneManager.PARENTPROTOTYPE, data).toString();
    	
    	if(id != null && parentProto != null &&	parentid != null){
        	String mode = (data.get(LuceneManager.LAYER_OF_SAVE, data) == Undefined.instance || data.get(LuceneManager.LAYER_OF_SAVE, data) == NOT_FOUND) ? String.valueOf(DbKey.LIVE_LAYER) : data.get(LuceneManager.LAYER_OF_SAVE, data).toString();
        	String lastmodified = (data.get(LuceneManager.LASTMODIFIED, data) == Undefined.instance || data.get(LuceneManager.LASTMODIFIED, data) == NOT_FOUND) ? null : data.get(LuceneManager.LASTMODIFIED, data).toString();
    		DbKey nodeKey = new DbKey(core.app.getDbMapping(proto.getClassName()), id, Integer.valueOf(mode));
    		boolean bExists = false;

    		try{
    			Node newNode = core.app.getNodeManager().getNode(nodeKey);
    			bExists = true;
    		}
    		catch(axiom.objectmodel.ObjectNotFoundException onf){
    			bExists = false;
    		}
    		catch(Exception e){
    			throw new RuntimeException(e);    			
    		}

    		if(!bExists){
    			core.app.getNodeManager().setEmbeddedID(id);    			
	        	// set parent
	    		DbKey parentKey = new DbKey(core.app.getDbMapping(parentProto), parentid, Integer.valueOf(mode));
	    		try{
	    			Node parentNode = core.app.getNodeManager().getNode(parentKey);
	        		((axiom.objectmodel.db.Node)node).setParent(parentNode);
	        		node.setState(Node.NEW);
	    		}
	    		catch(Exception e){
	    			throw new RuntimeException(e);
	    		}
	        	
	        	// set id
		    	((axiom.objectmodel.db.Node)node).setID(id);
		    	DbKey newKey = new DbKey(core.app.getDbMapping(proto.getClassName()), id, Integer.valueOf(mode));
				((axiom.objectmodel.db.Node)node).setKey(newKey);
	
				// set everything else
		        for (Object o : keys) {
		            String k = o.toString();
		            
		            if (k.equals(LuceneManager.LOCATION) || k.equals(LuceneManager.ACCESSNAME)) {
		            	this.put(k, this, data.get(k, data));
		            }
		            else if(k.equals(LuceneManager.CREATED)){
		            	if(data.get(k, data) == null){
			            	((axiom.objectmodel.db.Node)node).setCreated(System.currentTimeMillis());		            				            		
		            	}
		            	else{
			            	((axiom.objectmodel.db.Node)node).setCreated(new Long((String)data.get(k, data)).longValue());		            		
		            	}
		            }
		            else if(k.equals(LuceneManager.LAYER_OF_SAVE)){
		                if (node instanceof axiom.objectmodel.db.Node) {
	                		((axiom.objectmodel.db.Node)node).setKey(nodeKey);
		                }
		                else{
		                    this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "ctor") 
		                    		+ "node is not an instance of axiom.objectmodel.db.Node, unable to set mode");
		            		throw new RuntimeException("node is not an instance of axiom.objectmodel.db.Node, unable to set mode");                	
		                }
		            }
		            else{
		            	this.put(k, this, data.get(k, data));     	
		            }
		        }
		        // set last modified
		        if(lastmodified != null){
		        	node.setLastModified(Long.valueOf(lastmodified));
		        }
	        }
	        else{
	    		this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "ctor") 
	    				+ "Attempt to add " + node + " was rejected because it wouldve create an id conflict.");
	        }
    	}
    	else{
            this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "ctor") 
            		+ "Missing parent info or id, cannot create axiomobject");
    		throw new RuntimeException("Missing parent info or id, cannot create axiomobject");            		        	
    	}
    }

	/**
	 * Initialize AxiomObject prototype for Rhino scope.
	 *
	 * @param core the RhinoCore
	 * @return the AxiomObject prototype
	 * @throws PropertyException
	 */
	public static AxiomObject init(RhinoCore core)
	throws PropertyException {
		final int attributes = DONTENUM;

		// create prototype object
		AxiomObject proto = new AxiomObject("AxiomObject", core);
		proto.setPrototype(getObjectPrototype(core.global));

		// install JavaScript methods and properties
		Method[] methods = AxiomObject.class.getDeclaredMethods();
		for (int i=0; i<methods.length; i++) {
			String methodName = methods[i].getName();

			if (methodName.startsWith("jsFunction_")) {
				methodName = methodName.substring(11);
				FunctionObject func = new FunctionObject(methodName,
						methods[i], proto);
				proto.defineProperty(methodName, func, attributes);

			} else if (methodName.startsWith("jsGet_")) {
				methodName = methodName.substring(6);
				proto.defineProperty(methodName, null, methods[i],
						null, attributes);
			}
		}
		return proto;
	}

	/**
	 *
	 *
	 * @return ...
	 */
	public String getClassName() {
		return className;
	}

	/**
	 * Return a primitive representation for this object.
	 * FIXME: We always return a string representation.
	 *
	 * @param hint the type hint
	 * @return the default value for the object
	 */
	public Object getDefaultValue(Class hint) {
		return toString();
	}

	/**
	 * Return the INode wrapped by this AxiomObject.
	 *
	 * @returns the wrapped INode instance
	 */
	public INode getNode() {
		if (node != null) {
			checkNode();
		}
		return node;
	}

	/**
	 * Returns the wrapped Node. Implements unwrap() in interface Wrapper.
	 *
	 */
	public Object unwrap() {
		if (node != null) {
			checkNode();
			return node;
		} else {
			return this;
		}
	}

	/**
	 * Check if the node has been invalidated. If so, it has to be re-fetched
	 * from the db via the app's node manager.
	 */
	protected void checkNode() {
		if (node != null && node.getState() == INode.INVALID) {
			if (node instanceof axiom.objectmodel.db.Node) {
				NodeHandle handle = ((axiom.objectmodel.db.Node) node).getHandle();
				node = handle.getNode(core.app.getWrappedNodeManager());
				if (node == null) {
					// we probably have a deleted node. Replace with empty transient node
					// to avoid throwing an exception.
					node = new axiom.objectmodel.TransientNode();
					// throw new RuntimeException("Tried to access invalid/removed node " + handle + ".");
				}
			}
		}
	}

	/**
	 * Get the URL of this object within the application.
	 *
	 * @param {String} [action] The action name, or null/undefined for the "main" action
	 * @returns {String} The URL
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	public String jsFunction_getURI(Object action) 
	throws UnsupportedEncodingException, IOException {

		if (node == null) {
            return null;
        }

        String act = null;

        checkNode();

        if (action != null) {
            if (action instanceof Wrapper) {
                act = ((Wrapper) action).unwrap().toString();
            } else if (!(action instanceof Undefined)) {
                act = action.toString();
            }
            
            if (act != null && act.startsWith("/")) {
                act = act.substring(1);
            }
        }

        String basicHref = core.app.getNodeHref(node, act, true);
        String href = core.postProcessHref(node, className, basicHref);

        return href;
    }
    
	/**
	 * Get the absolute URL of this object, appending the 
	 * protocol and host name to the URL to make it fully qualified.
	 *
	 * @param {String} [action] The action name, or null/undefined for the "main" action
	 * @returns {String} Absolute URL
	 */
    public String jsFunction_getAbsoluteURI(Object action) throws Exception {
    	String trailuri = this.jsFunction_getURI(action);
    	StringBuffer uri = new StringBuffer();
    	uri.append("http://").append(java.net.InetAddress.getLocalHost().getHostName());
    	if (trailuri != null && !trailuri.startsWith("/")) {
    		uri.append("/");
    	}
    	uri.append(trailuri);
    	return uri.toString();
    }
    
    /**
     * @deprecated replaced by getURI()
     */
    public Object jsFunction_href(Object action) throws UnsupportedEncodingException,
                                                        IOException {
        if (node == null) {
            return null;
        }

        String act = null;

        checkNode();

        if (action != null) {
            if (action instanceof Wrapper) {
                act = ((Wrapper) action).unwrap().toString();
            } else if (!(action instanceof Undefined)) {
                act = action.toString();
            }
        }

        String basicHref = core.app.getNodeHref(node, act, true);
        String href = core.postProcessHref(node, className, basicHref);
        
        if (!href.endsWith("/")) {
            href += "/";
        }

        return href;
    }

	/**
	 * Get a child object by accessname/id or index.
	 *
	 * @param {String|Number} id The accessname/id or index, 
	 * 							 depending if the argument is a String or Number
	 * @returns {AxiomObject} The requested child object
	 */
	public Object jsFunction_get(Object id) {
		if ((node == null) || (id == null)) {
			return null;
		}

		Object n = null;

		checkNode();

		if (id instanceof Number) {
			n = node.getSubnodeAt(((Number) id).intValue());
		} else {
			// If this is a path, then return the result of it.
			String idstr = id.toString();
			if (idstr.indexOf("/") > -1) {
				n = traverse(idstr, this.core.app);
			} else {
				n = node.getChildElement(idstr);
			}
		}

		if (n != null) {
			return Context.toObject(n, core.global);
		}

		return null;
	}

	/**
	 * Get a child object by ID.
	 *
	 * @param {String} id The child id
	 * @returns {AxiomObject} The requested object
	 */
	public Object jsFunction_getById(Object id) {
		if ((node == null) || (id == null) || id == Undefined.instance) {
			return null;
		}

		checkNode();

		String idString = (id instanceof Double) ?
				Long.toString(((Double) id).longValue()) :
					id.toString();
		Object n = node.getSubnode(idString);

		if (n == null) {
			return null;
		} else {
			return Context.toObject(n, core.global);
		}
	}

	/**
	 * The number of child objects.
	 * @type Number
	 */
	public int jsGet_length() {
		if (node == null) {
			return 0;
		}

		checkNode();

		return node.numberOfNodes();
	}

	/**
	 * Prefetch child objects from (relational) database.  This function is invoked to 
	 * perform optimizations in having child objects from relational storage prefetched
	 * for use by Axiom prior to their being needed.  Each application's logic will 
	 * determine if invoking this function is useful for optimization or not.
	 *  
	 * @param {Number} [start] The starting index for prefetching children, defaults to 0
	 * @param {Number} [length] The number of child objects to prefetch, defaults to 1000
	 */
	public void jsFunction_prefetchChildren(Object startArg, Object lengthArg) {
		// check if we were called with no arguments
		if (startArg == Undefined.instance && lengthArg == Undefined.instance) {
			prefetchChildren(0, 1000);
		} else {
			int start = (int) ScriptRuntime.toNumber(startArg);
			int length = (int) ScriptRuntime.toNumber(lengthArg);
			prefetchChildren(start, length);
		}
	}

	private void prefetchChildren(int start, int length) {
		if (!(node instanceof axiom.objectmodel.db.Node)) {
			return;
		}

		checkNode();

		try {
			((axiom.objectmodel.db.Node) node).prefetchChildren(start, length);
		} catch (Exception ignore) {
			System.err.println("Error in AxiomObject.prefetchChildren(): "+ignore);
		}
	}

	/**
	 * Return the full list of child objects in a JavaScript Array.
	 * This is called by jsFunction_getChildren() if called with no arguments.
	 *
	 * @returns A JavaScript Array containing all child objects
	 */
	private Scriptable list() {
		checkNode();

		prefetchChildren(0, 1000);

		Enumeration e = node.getSubnodes();
		ArrayList a = new ArrayList();

		if (e != null) {
			while (e.hasMoreElements()) {
				Object obj = e.nextElement();
				if (obj != null)
					a.add(Context.toObject(obj, core.global));
			}
		}

		return Context.getCurrentContext().newArray(core.global, a.toArray());
	}

	/**
	 * Return an array of child objects of this object, filtering the child objects
	 * returned by the below specified parameters, if applicable.
	 *
	 * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {FilterObject} [filter] The filter to apply to the search
     * @param {Object} [options] The optional parameters to pass in to the search. 
     *                           These are all specified in name/value pairs in a 
     *                           javascript object
     *                           
	 * 		<br><br>Possible values for the optional parameters are:
     * 		   <ul>
     * 	   	   <li>'sort' - A <code>SortObject</code> dictating the sort order of the results
     * 		   <li>'maxlength' - A Number indicating the maximum number of results to return
     *         <li>'layer' - A number indicating the layer to execute the query on, if this
     *                   is not specified, execute the query on the layer on which this
     *                   function is being invoked.
     * 		   </ul>
     *      <br>Example: { 'maxlength':50, 'sort':new Sort('propname','asc'), 'layer':1 }
     *            
	 * @returns {Array} A JavaScript Array containing the specified child objects
	 */
	public Scriptable jsFunction_getChildren(Object prototype, Object filter, Object optional) {

		Scriptable ret;
	    if (prototype == Undefined.instance && 
	            filter == Undefined.instance &&
	            optional == Undefined.instance) {

	        ret = this.list();
	    } else {
	        try {
	        	Scriptable options = null;
	        	if(optional instanceof Scriptable){
	        		options = (Scriptable)optional;
	        		options.delete(LuceneQueryDispatcher.PATH_FIELD);
	        	}
	        	else{
	        		options = Context.getCurrentContext().newObject(this.core.getScope());	        		
	        	}
	        	String path = this.jsFunction_getPath();
	        	path = path.equals("/") ? path + "*" : path + "/*";

	        	options.put(LuceneQueryDispatcher.PATH_FIELD, options, path);

				ret = this.core.app.getQueryBean().objects(prototype, filter, options);
	        } catch (Exception ex) {
	            this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "jsFunction_getChildren"), ex);
	            ret = Context.getCurrentContext().newArray(core.global, new Object[0]);
	        }
	    }

	    return ret;
	}
	
	/**
	 * The number of objects that the equivalent call to getChildren() would return.
	 *
	 * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {FilterObject} [filter] The filter to apply to the search
     * @param {Object} [options] The optional parameters to pass in to the search. 
     *                           These are all specified in name/value pairs in a 
     *                           javascript object
     *                           
	 * 		<br><br>Possible values for the optional parameters are:
     * 		   <ul>
     * 	   	   <li>'sort' - A <code>SortObject</code> dictating the sort order of the results
     * 		   <li>'maxlength' - A Number indicating the maximum number of results to return
     *         <li>'layer' - A number indicating the layer to execute the query on, if this
     *                   is not specified, execute the query on the layer on which this
     *                   function is being invoked.
     * 		   </ul>
     *      <br>Example: { 'maxlength':50, 'sort':new Sort('propname','asc'), 'layer':1 }
     *            
	 * @returns {Number} The number of child objects
	 */
	public int jsFunction_getChildCount(Object prototype, Object filter, Object optional) {

	    int ret = 0;
	    if (prototype == Undefined.instance && 
	            filter == Undefined.instance) {

	        ret = node.numberOfNodes();
	    } else {
	        try {
	        	Scriptable options = null;
	        	if(optional instanceof Scriptable) {
	        		options = (Scriptable)optional;
	        		options.delete(LuceneQueryDispatcher.PATH_FIELD);
	        	} else {
	        		options = Context.getCurrentContext().newObject(this.core.getScope());	        		
	        	}
	        	String path = this.jsFunction_getPath();
	        	path = path.equals("/") ? path + "*" : path + "/*";
				options.put(LuceneQueryDispatcher.PATH_FIELD, options, path);
				
				ret = this.core.app.getQueryBean().getHitCount(prototype, filter, options);
	        } catch (Exception ex) {
	            this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "jsFunction_getChildCount"), ex);
	            ret = 0;
	        }
	    }

	    return ret;
	}


	/**
	 * Add the input object to this object's children.  The input object will be 
	 * located under this object and it will be accessible by accessname/id.
	 *
	 * @param {AxiomObject} child The object to add to this object's children
	 * @returns {Boolean} Whether the operation was a success or not 
	 */
	public boolean jsFunction_add(Object child) {
        if ((node == null) || (child == null)) {
            return false;
        }
        
        checkNode();

        boolean success = false;
        boolean axobj = false;
        if (child instanceof AxiomObject) {
            if (node.addNode(((AxiomObject) child).node) != null) {
                success = true;
                axobj = true;
            }
        } else if (child instanceof INode) {
            if (node.addNode((INode) child) != null) {
                success = true;
            }
        }
        
        if (success) {
            String name;
            AxiomObject parent;
            if (!"axiomobject".equals(node.getPrototype().toLowerCase())) {
                this.calcComputedProps("_children");
            } else if ((name = node.getName()) != null && 
                    (parent = (AxiomObject) this.getInternalProperty("_parent")) != null) {
                parent.calcComputedProps(name);
            } 
            if (axobj) {
                ((AxiomObject) child).calcComputedProps("_parent");
            }
        }

        return success;
	}

	public boolean removeChild(Object child) { 

		checkNode();

		if (child instanceof AxiomObject) {
			AxiomObject axobj = (AxiomObject) child;
			INode axobjnode = axobj.node;

			if (axobjnode != null) { 
				node.removeNode(axobjnode);

				return true;
			}
		}

		return false;
	}

	/**
	 * Invalidate the node itself, evicting it from the object cache and causing it to 
	 * be refetched from the database.
	 *  
	 * @returns {Boolean} Whether the operation was a success or not  
	 */
	public boolean jsFunction_invalidate() {
		if (node instanceof axiom.objectmodel.db.Node) {

			if (node.getState() != INode.INVALID) {
				((axiom.objectmodel.db.Node) node).invalidate();
			}
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * Check if the input object is a child of this object.
	 * 
	 * @param {AxiomObject} object The object to check 
	 * @returns {Boolean} If the input object is a child of this object
	 */
	public boolean jsFunction_isChild(Object obj) {

		checkNode();

		if ((node != null) && obj instanceof AxiomObject) {
			checkNode();

			if (node.contains(((AxiomObject) obj).node)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Set a property in this AxiomObject, implements the JavaScript assignment operation
	 *
	 * @param name property name
	 * @param start
	 * @param value ...
	 * @throws Exception 
	 * @throws Exception 
	 */
	public void put(String name, Scriptable start, Object value) {  
		if (node == null) {
			// redirect the scripted constructor to __constructor__,
			// constructor is set to the native constructor method.
			if ("constructor".equals(name) && value instanceof NativeFunction) {
				name = "__constructor__";
			}
			// register property for PropertyRecorder interface
			if (isRecording) {
				changedProperties.add(name);
			}
			super.put(name, start, value);
		} else {
			checkNode();

			if ("subnodeRelation".equals(name)) {
				node.setSubnodeRelation(value == null ? null : value.toString());
			}

			if (value instanceof Wrapper) {
				value = ((Wrapper) value).unwrap();
			}


			boolean runTrigger = true;

			Prototype proto = core.app.typemgr.getPrototype(node.getPrototype()); 
			ResourceProperties props = null;
			while (proto != null) {
				props = proto.getTypeProperties();
				if (props.getProperty(name) != null) {
					break;
				}
				proto = proto.getParentPrototype();
			}

			if (props == null) {
				throw new RuntimeException("Could not determine the prototype of " + this);
			}

			String strType = props.getProperty(name + ".type");
			if (strType == null) {
				strType = "String";
			}
			final int type = LuceneManager.stringToType(strType);

			if ((value == null) || (value == Undefined.instance)) {
				runTrigger = false;
				node.unset(name);
			} else if (value instanceof Scriptable) {
				Scriptable s = (Scriptable) value;
				String className = s.getClassName();
				if ("Date".equals(className)) {
					node.setDate(name, new Date((long) ScriptRuntime.toNumber(s)));
				} else if ("String".equals(className)) {   
					if (type == IProperty.XML && node instanceof axiom.objectmodel.db.Node) {
						axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) this.node;
						String xml = ScriptRuntime.toString(s);
						n.setXML(name, xml);
					} else {
						String strval = ScriptRuntime.toString(s);
						validateString(name, strval, props);
						node.setString(name, strval);
					}
				} else if ("Number".equals(className)) {
					double dbl = ScriptRuntime.toNumber(s);
					validateFloat(name, dbl, props);
					node.setFloat(name, dbl);
				} else if ("Boolean".equals(className)) {
					node.setBoolean(name, ScriptRuntime.toBoolean(s));
				} else if ("Reference".equals(className)) { 
					Reference relobj = (Reference) value;
					if (this.node instanceof axiom.objectmodel.db.Node) {
						axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) this.node;
						n.setReference(name, relobj);
					}
				} else if ("MultiValue".equals(className)) {
					MultiValue mv = (MultiValue) value;
					if (this.node instanceof axiom.objectmodel.db.Node) {
						axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) this.node;
						mv.setValueType(getTypeForMultiValue(name));
						checkValidity(mv);
						n.setMultiValue(name, mv);
					}
				} else if ("XML".equals(className) || "XMLList".equals(className)) { 
					if (this.node instanceof axiom.objectmodel.db.Node) {
						axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) this.node;
						if (type == IProperty.XHTML) {
                            n.setXHTML(name, value);
                        } else {
                            n.setXML(name, value);
                        }
					}
				} else {
					node.setJavaObject(name, s);
				}
			} else if (value instanceof String) {
				if (type == IProperty.XML && node instanceof axiom.objectmodel.db.Node) {
					axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) this.node;
					String xml = value.toString();
					n.setXML(name, xml);
				} else {
					String strval = (String) value;
					validateString(name, strval, props);
					node.setString(name, strval);
				}
			} else if (value instanceof Boolean) {
				node.setBoolean(name, ((Boolean) value).booleanValue());
			} else if (value instanceof Number) {
				double dbl = ((Number) value).doubleValue();
				validateFloat(name, dbl, props);
				node.setFloat(name, dbl);
			} else if (value instanceof Date) {
				node.setDate(name, (Date) value);
			} else if (value instanceof INode) {
				node.setNode(name, (INode) value);
			} else if (value instanceof Reference) {
				Reference relobj = (Reference) value;
				if (this.node instanceof axiom.objectmodel.db.Node) {
					axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) this.node;
					n.setReference(name, relobj);
				}
			} else if (value instanceof MultiValue) { 
				MultiValue mv = (MultiValue) value;
				if (this.node instanceof axiom.objectmodel.db.Node) {
					axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) this.node;
					mv.setValueType(getTypeForMultiValue(name));
					checkValidity(mv);
					n.setMultiValue(name, mv);
				}
			} else if (value.getClass().getName().equals("org.mozilla.javascript.xmlimpl.XML")) { // added by ali
				String xml = value.toString();
				if (this.node instanceof axiom.objectmodel.db.Node) {
					axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) this.node;
					n.setXML(name, value);
				}
			} else {
				node.setJavaObject(name, value);
			}

			// calculate the computed properties for this particular assigned property
			calcComputedProps(name);

            this.jsFunction_invalidateResultsCache(null);
            
		}
	}

	/**
	 * Check if a property is set in this AxiomObject
	 *
	 * @param name the property name
	 * @param start the object in which the lookup began
	 * @return true if the property was found
	 */
	public boolean has(String name, Scriptable start) {
		if (node != null) {
			checkNode();
			return  (node.get(name) != null);
		} else {
			return super.has(name, start);
		}
	}

	/**
	 * Implements the JavaScript delete operation
	 * 
	 * @param name ...
	 */
	public void delete(String name) {
		if ((node != null)) {
			checkNode();
			node.unset(name);
		} else {
			super.delete(name);
		}
	}

	/**
	 * Implements JavaScript level value retrieval
	 *
	 * @param name ...
	 * @param start ...
	 *
	 * @return ...
	 */
	public Object get(String name, Scriptable start) {
		if (node == null) {
			return super.get(name, start);
		} else {
			return getFromNode(name);
		}
	}

	/**
	 *  Retrieve a property only from the node itself, not the underlying prototype object.
	 *  This is called directly when we call get(x) on a JS AxiomObject, since we don't want
	 *  to return the prototype functions in that case.
	 */
	private Object getFromNode(String name) {
		if (node != null && name != null && name.length() > 0) {

			checkNode();

			// Property names starting with an underscore is interpreted
			// as internal properties
			if (name.charAt(0) == '_') {
				Object value = getInternalProperty(name);
				if (value != NOT_FOUND)
					return value;
			}

			if ("subnodeRelation".equals(name)) {
				return node.getSubnodeRelation();
			}

			IProperty p = node.get(name);

			if (p != null) {
                final int ptype = p.getType();
                
				switch (ptype) {
				case IProperty.STRING:
				case IProperty.INTEGER:
				case IProperty.FLOAT:
				case IProperty.BOOLEAN:
					return p.getValue();
				}

                Context cx = Context.getCurrentContext();
                
				if (ptype == IProperty.XML || ptype == IProperty.XHTML) {
					return Context.toObject(p.getXMLValue(), core.global);
				}

				if (ptype == IProperty.DATE) {
					Date d = p.getDateValue();

					if (d == null) {
						return null;
					} else {
						Object[] args = { new Long(d.getTime()) };
						try {
							return cx.newObject(core.global, "Date", args);
						} catch (JavaScriptException nafx) {
							return null;
						}
					}
				}

				if (ptype == IProperty.NODE) {
					INode n = p.getNodeValue();

					if (n == null) {
						return null;
					} else {
						return Context.toObject(n, core.global);
					}
				}

				if (ptype == IProperty.REFERENCE 
						&& p instanceof axiom.objectmodel.db.Property) {
					axiom.objectmodel.db.Property property = (axiom.objectmodel.db.Property) p;
					Reference r = property.getReferenceValue();

					if (r == null) {
						return null;
					} else {
						Key key = r.getTargetKey();
						Object cached = referenceCache.get(key);
						if (cached != null) {
							return cached;
						}

						Object[] args = { key };
						try {
							Object o = cx.newObject(this, "Reference", args);
							Reference relobj = (Reference) o;
							relobj.setSourceKey(((axiom.objectmodel.db.Node) this.node).getKey());
							relobj.setSourceProperty(name);
							referenceCache.put(key, relobj);
							return relobj;
						} catch (Exception ex) {
							return null;
						}
					}
				}

				if (ptype == IProperty.MULTI_VALUE
						&& p instanceof axiom.objectmodel.db.Property) {
					
					axiom.objectmodel.db.Property property = (axiom.objectmodel.db.Property) p;
					MultiValue mv = property.getMultiValue();

					if (mv == null) {
						return null;
					} else {
						final int type = mv.getValueType();
						final Object[] values = mv.getValues();
						final int len = values.length;
						Object[] args = new Object[len];

						if (type == IProperty.REFERENCE) {
							int nullcount = 0;
							for (int i = 0; i < len; i++) {
								Reference r = (Reference) values[i];

								Object o = null;
								if (r == null) {
									args[i] = null;
									nullcount++;
									continue;
								}

								Key key = r.getTargetKey();
								Object cached = referenceCache.get(key);

								if (cached != null) {
									o = cached;
								} else {
									Object[] args1 = { key };
									try {
										o = cx.newObject(this, "Reference", args1);
										Reference relobj = (Reference) o;
										relobj.setSourceKey(((axiom.objectmodel.db.Node) this.node).getKey());
										relobj.setSourceProperty(name);
										relobj.setSourceIndex(i);
										referenceCache.put(key, o);
									} catch (Exception ex) {
										this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "getFromNode"), ex);
										o = null;
									}
								}

								args[i] = o;
							}

							if (nullcount > 0) {
								Object[] nargs = new Object[len - nullcount];
								int count = 0;
								for (int i = 0; i < len; i++) {
									if (args[i] != null) {
										nargs[count++] = args[i];
									}
								}
								args = nargs;
							}
						} else {
							for (int i = 0; i < len; i++) {
								args[i] = values[i];
							}
						}

						try {
							Object o = cx.newObject(this, "MultiValue", args);
							MultiValue mvobj = (MultiValue) o;
							mvobj.setValueType(type);
							return mvobj;
						} catch (Exception ex) {
							return null;
						}
					}
				}

				if (ptype == IProperty.JAVAOBJECT) {
					Object obj = p.getJavaObjectValue();

					if (obj == null) {
						return null;
					} else {
						return Context.toObject(obj, core.global);
					}
				}
			}

			DbMapping dbmap = node.getDbMapping();
			if (dbmap != null && dbmap.propertyToRelation(name) != null) {
				return NOT_FOUND;
			}
		}

		return NOT_FOUND;
	}

	private Object getInternalProperty(String name) {
		if ("__id__".equals(name) || "_id".equals(name)) {
			return node.getID();
		}
		
		if ("_layer".equals(name) && node instanceof Node) {
			return new Integer(((Node) node).getLayer());
		}
		
		if ("_persistedLayer".equals(name) && node instanceof Node) {
			return new Integer(((Node) node).getLayerInStorage());
		}

		if ("__proto__".equals(name)) {
			return getPrototype(); // prototype object
		}

		if ("__prototype__".equals(name) || "_prototype".equals(name)) {
			return node.getPrototype(); // prototype name
		}

		if ("__parent__".equals(name) || "_parent".equals(name)) {
			return core.getNodeWrapper(node.getParent());
		}

		// some more internal properties
		if ("__name__".equals(name)) {
			return node.getName();
		}

		if ("__fullname__".equals(name)) {
			return node.getFullName();
		}

		if ("__hash__".equals(name)) {
			return Integer.toString(node.hashCode());
		}

		if ("__node__".equals(name)) {
			return new NativeJavaObject(core.global, node, null);
		}

		if ("_created".equalsIgnoreCase(name) || "__created__".equalsIgnoreCase(name)) {
			return new Date(node.created());
		}

		if ("_lastmodified".equalsIgnoreCase(name) || "__lastmodified__".equalsIgnoreCase(name)) {
			return new Date(node.lastModified());
		}

        if ("_accessname".equals(name)) {
            return this.jsFunction_accessname();
        }

		return NOT_FOUND;
	}
	
	/**
	 * @deprecated replaced by <code> obj._created </code>
	 */
	public Object jsFunction_created() {
		return this.getInternalProperty("_created");
	}
	
	/**
	 * @deprecated replaced by <code> obj._lastmodified </code>
	 */
	public Object jsFunction_lastmodified() {
		return this.getInternalProperty("_lastmodified");
	}

	/**
	 * Implements Scriptable.getIds()
	 * 
	 * @return ...
	 */
	public Object[] getIds() {
		if (node == null) {
			// AxiomObject prototypes always return an empty array in order not to
			// pollute actual AxiomObjects properties. Call getAllIds() to get
			// a list of properties from a AxiomObject prototype.
			return new Object[0];
		}

		checkNode();

		Enumeration en = node.properties();
		ArrayList list = new ArrayList();

		while (en.hasMoreElements())
			list.add(en.nextElement());

		return list.toArray();
	}

	/**
	 *
	 * @param idx ...
	 * @param start ...
	 *
	 * @return ...
	 */
	public boolean has(int idx, Scriptable start) {
		if (node != null) {
			checkNode();

			return (0 <= idx && idx < node.numberOfNodes());
		}

		return false;
	}

	/**
	 *
	 * @param idx ...
	 * @param start ...
	 *
	 * @return ...
	 */
	public Object get(int idx, Scriptable start) {
		if (node != null) {
			checkNode();

			INode n = node.getSubnodeAt(idx);

			if (n != null) {
				return Context.toObject(n, core.global);
			}
		}

		return NOT_FOUND;
	}

	/**
	 * Return a string representation of this object
	 *
	 * @return ...
	 */
	public String toString() {
		return (className != null) ? ("[AxiomObject " + className + "]") : "[AxiomObject]";
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
	 * Get the Object's prototype name
	 */
	public Object prototype() {
		if (node != null) {
			checkNode();

			return node.getPrototype();
		}

		return "AxiomObject"; // the default prototype
	}

	private ArrayList getTypePropertyIds(String prototype) {
		ArrayList ids = new ArrayList();
		getTypePropertyIds(this.core.app.getPrototypeByName(prototype), ids);
		return ids;
	}

	private void getTypePropertyIds(Prototype prototype, ArrayList ids) {
		Prototype parent;
		if ((parent = prototype.getParentPrototype()) != null) {
			getTypePropertyIds(parent, ids);
		}
		ResourceProperties props = prototype.getTypeProperties();
		ids.addAll(props.keySet());
	}

	/**
	 * Default getUserName() method for any AxiomObject attempting to be used as a 
	 * User to authenticate into Axiom with.
	 * 
	 * @returns {String} the user name, or "Anonymous" if there is no user name on this object
	 */
	public String getUserName() {
		INode n = core.app.getCurrentRequestEvaluator().getSession().getUserNode();
		if (n == null) { 
			return "Anonymous"; 
		}
		Object name = n.get("username");
		if (name == null) { 
			return "Anonymous"; 
		}
		return name.toString();
	}

	/**
	 * Returns the property on this object that acts as an accessname from its parent.
	 * That is, the property on this object whose value is used by this object's parent
	 * to retrieve this object in the parent object's get() call.  The default is the _id
	 * unless the parent object's prototype.properties defines _children.accessname, 
	 * in which case it is the value of _children.accessname.
	 * 
	 * @returns {String} The accessname
	 */
	public String jsFunction_accessname() {
		String accessName = null;
		AxiomObject parent = (AxiomObject) this.getInternalProperty("_parent");

		if (parent != null) {
			String prototype = (String) parent.prototype();
			Prototype proto = this.core.app.getPrototypeByName(prototype); 
			accessName = proto.getAccessname();

			if (accessName == null) {
				String name = (String) parent.getInternalProperty("__name__");
				parent = (AxiomObject) parent.getInternalProperty("_parent");
				if (parent != null) {
					prototype = (String) parent.prototype();
					proto = this.core.app.getPrototypeByName(prototype);
					DbMapping dbmap = proto.getDbMapping();
					if (dbmap != null) {
						Relation rel = dbmap.getExactPropertyRelation(name);
						if (rel != null) {
							accessName = rel.getAccessName();
						}
					}
				}
			}
		}

		return accessName;
	}

	/**
	 * @deprecated replaced by accessvalue()
	 */
	public Object jsFunction_getAccessValue() {
		return this.jsFunction_accessvalue();
	}
	/**
	 * Returns the value of the property that serves as the accessname on this object.
	 * That is, it returns the value of <code>this[this.accessvalue()]</code>
	 * 
	 * @returns {String} The value of the accessname property
	 */
	public Object jsFunction_accessvalue() {
	    String accessname = this.jsFunction_accessname();
	    if (accessname != null) {
	        return this.get(accessname, this);
	    }
	    return null;
	}

	/**
	 * Another way of doing property assignment on objects.  Calling 
	 * <br><code> this.edit({prop1:prop1_value,prop2:prop2_value})</code><br> 
	 * is the equivalent of doing: <br>
	 * <code>this.prop1 = prop1_value; </code><br>
	 * <code>this.prop2 = prop2_value; </code><br>
	 * 
	 * @param {Object} input_data A JavaScript object of name/value pairs for assignment
	 *                            on this object
	 * @returns {Object} A JavaScript hash of the errors encountered executing this function
	 * @throws Exception
	 */
	public Scriptable jsFunction_edit(Object input_data) throws Exception {
		RequestTrans req = core.app.getCurrentRequestEvaluator().getRequest();
		ResourceProperties props = this.getResourceProperties(node.getPrototype());
		HashMap dataMap = new HashMap();
		Scriptable data;
		if (input_data == null || input_data == Undefined.instance) {
			data = req.getRequestData();
		} else {
			data = (Scriptable) input_data;
		}

		final String loc = LuceneManager.LOCATION;
		final String accessname = LuceneManager.ACCESSNAME;
		Object[] keys = data.getIds();
		for (Object o : keys) {
			String k = o.toString();
			if (this.core.app.isPropertyFilesIgnoreCase()) {
	            k = k.toLowerCase();
	        }			
			if (props.get(k) != null || k.equals(loc) || k.equals(accessname)) {
				dataMap.put(k, data.get(k, data));
			}
		}

		Scriptable emsgs = this.distill(dataMap);
		this.setProperties(dataMap, req, emsgs);

		Scriptable reterror = null;
		if (emsgs.getIds().length > 0) {
			reterror = Context.getCurrentContext().newObject(core.getScope());
			reterror.put("errors", reterror, emsgs);
		}

		return reterror;
	}

	public void setProperties(HashMap map, RequestTrans req, Scriptable emsgs) 
	throws Exception {
		if (emsgs.getIds().length > 0) {
			return;
		}

		boolean sameNode = false;
		INode oldChild = null;
		String setNameTo = null;
		Object setValueTo = null;
		boolean _idAccess = false;
        
		String aname = computeAccessname(map.get(LuceneManager.LOCATION));
		String value;
		if (aname != null) {
		    value = (String) map.remove(aname);
		} else {
		    value = (String) map.remove(LuceneManager.ACCESSNAME);
		}
		if (value != null) {
		    if (aname != null) {
		    	INode parent = this.node.getParent();
		    	if (parent != null) {
		    		oldChild = (INode) parent.getChildElement(value);
		    	}
		        if (oldChild != null && oldChild.getID().equals(this.node.getID()) 
		                && oldChild.getPrototype().equals(this.node.getPrototype())) {
		            sameNode = true;
		        }
		        if (oldChild != null && !sameNode) {
		            emsgs.put(LuceneManager.ACCESSNAME, emsgs, "Duplicate Entry Found");
		            return;
		        } else if (!value.equals(this.jsFunction_accessvalue())) {
		            setNameTo = aname;
		            setValueTo = value;
		        }
		    } else {
		        // access name is _id 
		        _idAccess = true;
		    }
		}
		
		Object setParentTo = null, v;
		if ((v = map.get(LuceneManager.LOCATION)) != null) {
			Object avalue = setValueTo != null ? setValueTo : this.jsFunction_accessvalue();

			sameNode = false;
			oldChild = null;
            AxiomObject prevChild = (AxiomObject) ((AxiomObject) v).getByAccessname(avalue);
            if (prevChild != null) {
				oldChild = prevChild.node;
				if (oldChild != null && oldChild.getID().equals(this.node.getID()) 
						&& oldChild.getPrototype().equals(this.node.getPrototype())) {
					sameNode = true;
				}
			}

			if (oldChild != null && !sameNode) {
				emsgs.put(LuceneManager.LOCATION, emsgs, "ID already in use in this location.");
				return;
			} else if (!sameNode || _idAccess) {
				setParentTo = v;
			} 
		}

		if (setNameTo != null) {
			if (this.core.app.debug()) {
				core.app.logEvent("AxiomObject.edit() -> " + ((Node)node).logString() 
						+ "setting accessname " + setNameTo + " to " + setValueTo);
			}
			this.put(setNameTo, this, setValueTo);
		}
		if (setParentTo != null) {
			try{
				this.setParent(setParentTo);
				if (this.core.app.debug()) {
					core.app.logEvent("AxiomObject.edit() -> " + ((Node)node).logString() 
							+ "setting parent to " + setParentTo);
					
				}
			} catch(Exception e){
				emsgs.put(LuceneManager.LOCATION, emsgs, e.getMessage());
				return;				
			}
            this.calcComputedProps("_parent");
		}

		Object[] pids = this.getTypePropertyIds(this.className).toArray();
		String prop = null;
		for (int i = 0; i < pids.length; i++) {
			prop = (String) pids[i];
			if (map.containsKey(prop)) {
				v = map.get(prop);
				try {
					this.put(prop, this, v);
				} catch (Exception e) {
					this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "setProperties") 
							+ "Failed for " + prop, e);
					emsgs.put(prop, emsgs, e.getMessage());
					return;
				}
			}
		}

		this.put("lastmodifiedby", this, getUserName());
	}
    
    private Object getByAccessname(Object id) {
        if ((node == null) || (id == null) || !(node instanceof Node)) {
            return null;
        }
        
        Object n = null;

        checkNode();

        String idstr = id.toString();
        if (idstr.indexOf("/") > -1) {
            n = traverse(idstr, this.core.app);
        } else {
            n = ((Node) node).getChildElement(idstr, false);
        }
        
        if (n != null) {
            return Context.toObject(n, core.global);
        }

        return null;
    }
    
	private String computeAccessname(Object object) {
	    String aname = null;
	    if (object != null && object instanceof AxiomObject) {
	        AxiomObject axobj = (AxiomObject) object;
	        Prototype p = this.core.app.getPrototypeByName(axobj.node.getPrototype());
	        aname = p.getAccessname();
	    } else { 
	        aname = this.jsFunction_accessname(); 
	    }
	    return aname;
	}

	protected Scriptable distill(HashMap map) {
		Scriptable errors = Context.getCurrentContext().newObject(this.core.getScope());
		checkNode();
		if (map == null || map == Undefined.instance) {    
			return errors;
		}

		ResourceProperties props = this.getResourceProperties(node.getPrototype());

		Iterator iter = map.keySet().iterator();
		final String loc = LuceneManager.LOCATION;
		final String aname = LuceneManager.ACCESSNAME;
		boolean errorflag = false;

		while (iter.hasNext()) {
			errorflag = false;
			final String name = (String) iter.next();
			Object newvalue = null;
			if (loc.equals(name)) {
				try {
					final String path = (String) map.get(name);
					INode n = (INode) traverse(path, this.core.app);
					if (n != null) {
						newvalue = Context.toObject(n, core.global);
					} else {
						errors.put(name, errors, "Object not found at " + path);
						errorflag = true;
					}
				} catch (Exception ex) {
					errors.put(name, errors, ex.getMessage());
					errorflag = true;
				}
			} else if (!aname.equals(name)) {
				try {
					newvalue = this.typecast(name, map.get(name), props, -1);
				} catch (Exception e) {
					errors.put(name, errors, e.getMessage());
					errorflag = true;
				}
			} else { 
				errorflag = true; 
			}

			if (!errorflag) {
				map.put(name, newvalue);
			}
		}

		return errors; 
	}

	public Scriptable distillParams(Scriptable map) {
		checkNode();
		if (map == null || map == Undefined.instance) {    
			return Context.getCurrentContext().newObject(this.core.getScope());
		}

		ResourceProperties props = this.getResourceProperties(node.getPrototype());

		Set propKeys = props.keySet();
		Object[] ids = map.getIds();
		Scriptable errors = Context.getCurrentContext().newObject(this.core.getScope());
		final String loc = LuceneManager.LOCATION;
		final String aname = LuceneManager.ACCESSNAME;
		boolean errorflag = false;

		for (int i = 0; i < ids.length; i++) {
			errorflag = false;
			final String name = (String) ids[i];
			Object newvalue = null;
			if (loc.equals(name)) {
				try {
					final String path = (String) map.get(name, map);
					INode n = (INode) traverse(path, this.core.app);
					if (n != null) {
						newvalue = Context.toObject(n, core.global);
					} else {
						errors.put(name, errors, "Object not found at " + path);
						errorflag = true;
					}
				} catch (Exception ex) {
					errors.put(name, errors, ex.getMessage());
					errorflag = true;
				}
			} else if (!aname.equals(name)) {
				try {
					newvalue = this.typecast(name, map.get(name, map), props, -1);
				} catch (Exception e) {
					errors.put(name, errors, e.getMessage());
					errorflag = true;
				}
			} else { 
				errorflag = true; 
			}

			if (!errorflag) {
				map.put(name, map, newvalue);
			}
		}

		return errors;
	}

	protected Object typecast(final String name, final Object value, ResourceProperties props,
			int type) throws Exception {
		if (value == null) {
			return null;
		}

		Object newvalue = null;
		boolean errorflag = false;

		String emsg = null;
		int mvtype = IProperty.STRING;
		boolean redirectFromMV = true;
		if (type == -1) {
			redirectFromMV = false;
			String type_ = props.getProperty(name + ".type");
			if (type_ == null) {
				throw new RuntimeException("Property " + name + " on prototype " 
						+ this.node.getPrototype() + " does not have a type specified.");
			}

			type_ = type_.trim().toLowerCase();
			final boolean isMultiValue = type_.startsWith("multivalue");
			if (isMultiValue) {
				type = IProperty.MULTI_VALUE;
				type_ = type_.substring(type_.indexOf("(") + 1, type_.indexOf(")")).trim();
				mvtype = LuceneManager.stringToType(type_);
			} else {
				if (type_.startsWith("object") || type_.startsWith("collection")) {
					type_ = "node";
				}
				type = LuceneManager.stringToType(type_);
			}
		}

		switch (type) {
		case IProperty.BOOLEAN:
			if (value instanceof Scriptable) {
				Scriptable s = (Scriptable) value;
				String className = s.getClassName();
				if ("String".equals(className)) {
					newvalue = new Boolean(ScriptRuntime.toString(s));
				} else if ("Boolean".equals(className)) {
					newvalue = value;
				}
			} else if (value instanceof String) {
				newvalue = new Boolean((String) value);
			} else if (value instanceof Boolean) {
				newvalue = value;
			}
			break;
		case IProperty.DATE:
        case IProperty.TIMESTAMP:
        case IProperty.TIME:
			String format = props.getProperty(name+".controlstring");
			if (value instanceof Scriptable) {
				Scriptable s = (Scriptable) value;
				String className = s.getClassName();
				if ("Date".equals(className)) {
					newvalue = value;
				} else if ("String".equals(className)) {
					try {
						Date d;
						String str = ScriptRuntime.toString(s);
						try {
							d = new Date(Long.parseLong(str));
						} catch (Exception nfex) {
							d = null;
						}
						if (d == null) {
							if (format != null) {
								d = new SimpleDateFormat(format).parse(str);
							} else {
								d = dateFormat.parse(str);
							}
						}
						Object[] args = { new Long(d.getTime()) };
						newvalue = Context.getCurrentContext().newObject(core.global, "Date", args);
					} catch (Exception ex) {
						emsg = ex.getMessage();
						newvalue = null;
						errorflag = true;
					}
				}
			} else if (value instanceof Date) {
				Object[] args = { new Long(((Date) value).getTime()) };
				try {
					newvalue = Context.getCurrentContext().newObject(core.global, "Date", args);
				} catch (Exception ex) {
					emsg = ex.getMessage();
					newvalue = null;
					errorflag = true;
				}
			} else if (value instanceof String) {
				try {
					Date d;
					String str = (String) value;
					try {
						d = new Date(Long.parseLong(str));
					} catch (Exception nfex) {
						d = null;
					}
					if (d == null) {
						if (format != null) {
							d = new SimpleDateFormat(format).parse(str);
						} else {
							d = dateFormat.parse(str);
						}
					}
					Object[] args = { new Long(d.getTime()) };
					newvalue = Context.getCurrentContext().newObject(core.global, "Date", args);
				} catch (Exception ex) {
					emsg = ex.getMessage();
					newvalue = null;
					errorflag = true;
				}
			}
			break;
		case IProperty.FLOAT:
		case IProperty.INTEGER:
        case IProperty.SMALLFLOAT:
        case IProperty.SMALLINT:
			if (value instanceof Scriptable) {
				Scriptable s = (Scriptable) value;
				String className = s.getClassName();
				if ("Number".equals(className)) {
					newvalue = value;
				} else if ("String".equals(className)) {
					try {
						newvalue = new Double(ScriptRuntime.toString(s));
					} catch (Exception ex) {
						emsg = ex.getMessage();
						newvalue = null;
						errorflag = true;
					}
				}
			} else if (value instanceof Number) {
				newvalue = value;
			} else if (value instanceof String) {
				try {
					newvalue = new Double((String) value);
				} catch (Exception ex) {
					emsg = ex.getMessage();
					newvalue = null;
					errorflag = true;
				}
			}
			break;
		case IProperty.JAVAOBJECT:
			newvalue = value;
			break;
		case IProperty.MULTI_VALUE:
			String delim = props.getProperty(name+".controlstring");
			if (delim == null) {
				delim = ","; // default control string for multi values
			}
			if (value instanceof MultiValue || (value instanceof Scriptable 
					&& "MultiValue".equals(((Scriptable) value).getClassName()))) {
				newvalue = value;
			}  else if ((value instanceof String) || (value instanceof Scriptable) ||
                    (value instanceof String[])) {
                String[] vals;
                if (value instanceof String[]) { 
                    vals = (String[]) value;
                } else if ((value instanceof String) || ((value instanceof Scriptable) 
                        && "String".equals(((Scriptable) value).getClassName()))) {
                    if (((String) value).length() == 0) { 
                        vals = new String[0]; 
                    } else {
                        vals = (value instanceof String ? (String) value :
                                ScriptRuntime.toString(value)).split(delim);
                    }
                } else {
                    Scriptable sarray = (Scriptable) value;
                    final int len = sarray.getIds().length;
                    vals = new String[len];
                    for (int i = 0; i < len; i++) {
                        vals[i] = ScriptRuntime.toString(sarray.get(i, sarray));
                    }
                }
                Object[] args = new Object[vals.length];
                boolean earlyExit = false;
                for (int i = 0; i < vals.length; i++) {
                    Object o = null;
                    try {
                        o = this.typecast(name, vals[i], props, mvtype);
                    } catch (Exception e) {
                        o = null;
                    }
                    if (o != null) {
                        args[i] = o;
                    } else {
                        earlyExit = true;
                        break;
                    }
                }
                if (!earlyExit) {
                    try {
                        Object o = Context.getCurrentContext().newObject(this, "MultiValue", args);
                        MultiValue mvobj = (MultiValue) o;
                        mvobj.setValueType(mvtype);
                        newvalue = mvobj;
                    } catch (Exception ex) {
                        emsg = ex.getMessage();
                        newvalue = null;
                        errorflag = true;
                    }
                }
            }
            break;
		case IProperty.NODE:
			if (value instanceof INode) {
				newvalue = value;
			} else if (value instanceof String) {
				try {
					newvalue = traverse((String) value, this.core.app);
					if (newvalue != null) {
						newvalue = Context.toObject(newvalue, core.global);
					}
				} catch (Exception ex) {
					emsg = ex.getMessage();
					newvalue = null;
					errorflag = true;
				}
			} else if (value instanceof Scriptable) {
				Scriptable s = (Scriptable) value;
				String className = s.getClassName();
				if ("String".equalsIgnoreCase(className)) {
					try {
						newvalue = traverse(ScriptRuntime.toString(s), this.core.app);
						if (newvalue != null) {
							newvalue = Context.toObject(newvalue, core.global);
						}
					} catch (Exception ex) {
						emsg = ex.getMessage();
						newvalue = null;
						errorflag = true;
					}
				} else if ("AxiomObject".equals(className) || "File".equals(className) 
						|| "Image".equals(className)) {
					newvalue = value;
				}
			}
			break;
		case IProperty.REFERENCE:
			if (value instanceof Reference || (value instanceof Scriptable 
					&& "Reference".equals(((Scriptable) value).getClassName()))) {
				newvalue = value;
			} else if (value instanceof String && ((String) value).length() == 0) {
				try {
					newvalue = null;
				} catch (Exception e) {
					emsg = e.getMessage();
					newvalue = null;
					errorflag = true;
				}
			} else if (value instanceof String || (value instanceof Scriptable 
					&& "String".equals(((Scriptable) value).getClassName()))) {
				Object[] args = { value };
				try {
					Object o = Context.getCurrentContext().newObject(this, "Reference", args);
					Reference relobj = (Reference) o;
					relobj.setSourceKey(((axiom.objectmodel.db.Node) this.node).getKey());
					relobj.setSourceProperty(name);
					newvalue = relobj;
				} catch (Exception ex) {
					emsg = ex.getMessage();
					newvalue = null;
					errorflag = true;
				}
			}
			break;
		case IProperty.STRING:
			if (value instanceof Scriptable) {
				Scriptable s = (Scriptable) value;
				if ("String".equals(s.getClassName())) {
					newvalue = ScriptRuntime.toString(s);
				}
			} else if (value instanceof String) {
				newvalue = (String) value;
			}
			break;
		case IProperty.XHTML:
		case IProperty.XML:
			if (value instanceof Scriptable) {
				Scriptable s = (Scriptable) value;
				String className = s.getClassName();
				if ("String".equals(className)) {
					try {
                        Context cx = Context.getCurrentContext();
                        String fixed = ScriptRuntime.toString(s);
                        if(type == IProperty.XHTML){
                        	fixed = DOMParser.replaceEntitiesWithChars(fixed);                        	
                        }
                        newvalue = cx.newObject(this.core.getScope(), "XMLList", new Object[]{fixed});
                        //newvalue = cx.evaluateString(this.core.getScope(), 
                        //            "new XMLList(\""+fixed.replaceAll("\"", "\\\\\"").replaceAll("\r?\n","\\\\\n") + "\");", "typecast()", 1, null); 
					} catch (Exception ex) {
						emsg = ex.getMessage();
						newvalue = null;
						errorflag = true;
					}
				} else if ("XML".equals(className) || "XMLList".equals(className)) {
				    newvalue = value;
                }
			} else if (value instanceof String) {
				try {
                    Context cx = Context.getCurrentContext();
                    String fixed = (String)value;
                    if(type == IProperty.XHTML){
                    	fixed = DOMParser.replaceEntitiesWithChars(fixed);                        	
                    }
                    newvalue = cx.newObject(this.core.getScope(), "XMLList", new Object[]{fixed});
                    //newvalue = cx.evaluateString(this.core.getScope(), 
                    //			"new XMLList(\""+fixed.replaceAll("\"", "\\\\\"").replaceAll("\r?\n","\\\\\n") + "\");", "typecast()", 1, null);
				} catch (Exception ex) {
					emsg = ex.getMessage();
					newvalue = null;
					errorflag = true;
				}
			} 
			break;
		}

		if (errorflag && !redirectFromMV) {
			if (emsg == null) {
				emsg = "Input object " + value + " of type " + value.getClass().getName() + 
				" could not be cast to the property " + name + " which is of type " +
				LuceneManager.intToStr(type);
			}
			throw new Exception(emsg);
		}

		return newvalue;
	}

	protected boolean setParent(Object parent) throws Exception{
	    checkNode();

	    INode newparent = null;
	    if (parent instanceof AxiomObject) {
	        AxiomObject axobj = (AxiomObject) parent;
	        newparent = axobj.node;
	    } else if (parent instanceof String || (parent instanceof Scriptable 
	            && ((Scriptable) parent).getClassName().equals("String"))) {
	        final String path = parent instanceof String ? parent.toString() : 
	            ScriptRuntime.toString((Scriptable) parent);
	        IPathElement pe = traverse(path, this.core.app);
	        if (pe == null) {
	            throw new RuntimeException("AxiomObject.setParent(): The path " + path 
	                    + " does not point to a traversable object.");
	        } else if (!(pe instanceof INode)) {
	            throw new RuntimeException("AxiomObject.setParent(): The path " + path 
	                    + " does not point to a Node object.");
	        }

	        newparent = (INode) pe;
	    } 

	    if (node != null && newparent != null) {
	        INode currparent;
	        boolean doTheAdd = true;
	        if ((currparent = node.getParent()) != null) {
	            if (areNodesEqual(newparent, currparent)) {
	                doTheAdd = false;
	            } else if(childAccessNameExists(newparent)){
		            throw new Exception("Adding " + node.getName() + " to " + newparent.getName() + " would cause an accessname conflict.");
	            } else {
	            	currparent.removeNode(node);
	            	((AxiomObject) core.getNodeWrapper(currparent)).calcComputedProps("_children");
	            }
	        }

	        if (doTheAdd) {
	            if (newparent.addNode(node) != null) {
	            	((AxiomObject) core.getNodeWrapper(newparent)).calcComputedProps("_children");
	            }
	        }

	        return true;
	    }

	    return false;
	}
	
	private boolean childAccessNameExists(INode newparent){
    	String aname = this.computeAccessname(this);
    	if(aname != null){
        	String anameValue = node.getString(aname);
        	if(anameValue != null){
	        	INode child = (INode) newparent.getChildElement(anameValue);
	        	if(child != null){
	        		return true;
	        	}
        	}    		
    	}
		return false;
	}
    
	protected static boolean areNodesEqual(INode n1, INode n2) {
	    if (n1 != null && n2 != null && n1.getID().equals(n2.getID())
	            && n1.getPrototype().equals(n2.getPrototype())) {
	        return true;
	    }
	    return false;
	}

	/**
	 * Returns an array of the property names assigned on this object.
	 * 
	 * @returns {Array} An array of the property names assigned on this object
	 */
	public Scriptable jsFunction_getPropertyNames() {
		ArrayList a = new ArrayList();

		if (node != null && node instanceof axiom.objectmodel.db.Node) {
			checkNode();

			axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) node;
			Enumeration e = n.getPropMap().keys();
			while (e.hasMoreElements()) {
				Object obj = e.nextElement();
				if (obj != null)
					a.add(obj.toString());
			}
		}

		return Context.getCurrentContext().newArray(core.global, a.toArray());
	}

	/**
	 * Return the first AxiomObject in this object's ancestral hierarchy that matches
	 * the given prototype.  For example, if <code>this._parent = foo</code> and 
	 * <code>foo._parent = bar</code> and <code>foo</code> is of prototype X and 
	 * <code>bar</code> is of prototype Y, then <code>this.getAncestor("Y")</code> will
	 * return <code>bar</code>.
	 * 
	 * @param {String | Array} [prototype] The prototype to look for on the ancestor hierarchy,
	 *                             if not specified, it just returns the 
	 *                             <code>_parent</code> of this object
	 * @param {Boolean} [includeThis] Whether to include this object in the ancestor
	 *                                hierarchy search, defaults to <code>false</code>
	 * @returns {AxiomObject} The first AxiomObject in the hierarchy of this object matching
	 *                       the given object prototype
	 */
	public Object jsFunction_getAncestor(Object prototype, Object includeThis) {
		if (node != null) {
			checkNode();

			if (prototype == null) {
				return Context.toObject(node.getParent(), core.global);
			}

			boolean inclThis = false;
			if (includeThis != null && includeThis instanceof Boolean) {
				inclThis = ((Boolean) includeThis).booleanValue();
			}

			INode n;
			if (inclThis) {
				n = node;
			} else {
				n = node.getParent();
			}


			ArrayList<String> prototype_array = new ArrayList<String>();
			if (prototype instanceof NativeArray) {
				final NativeArray na = (NativeArray)prototype;
	            final int length = (int)na.getLength();
	            if(length > 0) {
		            for (int i = 0; i < length; i++) {
		                Object o = na.get(i, na);
		                if (o instanceof String) {
		                    String proto = o.toString().toLowerCase();
		                    prototype_array.add(proto);
		                }
		            }
	            }
			} else if (prototype instanceof String) {
				prototype_array.add(prototype.toString().toLowerCase());
			}
	            
		    while (n != null) {
		        if (prototype_array.contains(n.getPrototype().toLowerCase())) {
		        	return Context.toObject(n, core.global);
		        }
		        n = n.getParent();
		    }
		}

		return null;
	}

	/**
	 * Get the value of a property in this object's prototype.properties.  
	 * 
	 * @param {String} name The name of the property (e.g. date_prop.type)
	 * @returns {String} The value of the property (e.g. Date)
	 */
	 public Object jsFunction_getTypePropertyValue(Object name) {
		ResourceProperties props = this.getResourceProperties(this.getClassName());
		return props.getProperty((String)name, null);
	}

	/**
	 * Returns a JavaScript object of the object's prototype schema, 
	 * including prototypes from which it extends.
	 * 
	 * @param {Boolean} [ignoreInternalProps] Whether to ignore internal properties 
	 *                  (properties starting with an underscore) when returning the 
	 *                  schema, defaults to <code>true</code>
	 * @returns {Object} A JavaScript object containing the prototype's schema
	 */
	 public Scriptable jsFunction_getSchema(Object ignoreInternalProps) throws Exception {
		 if (this.node != null) {
			 return getSchema(this.node.getPrototype(), ignoreInternalProps, this.core);
		 }
		 
		 return null;
	 }
	 
	 public static Scriptable getSchema(String prototype, Object arg, RhinoCore core) {
		 try {         
			 boolean ignoreInternalProps = true;
			 if (arg != null && arg instanceof Boolean) {
				 ignoreInternalProps = !((Boolean) arg).booleanValue();
			 }
			 
			 Scriptable schema = Context.getCurrentContext().newObject(core.global);

			 Stack protos = new Stack();
			 Prototype proto = core.app.typemgr.getPrototype(prototype);
			 while (proto != null) {
				 protos.push(proto);
				 proto = proto.getParentPrototype();
			 }

			 final int stackSize = protos.size();
			 for (int i = 0; i < stackSize; i++) {
				 proto = (Prototype) protos.pop();
				 ResourceProperties rprops = proto.getTypeProperties();
				 Enumeration e = rprops.propertyNames();

				 while (e.hasMoreElements()) {
					 String key = e.nextElement().toString();
					 if (ignoreInternalProps && key.startsWith("_")) {
						 continue;
					 }

					 String value = rprops.getProperty(key);
					 String[] split = key.split("\\.");
					 int length = split.length;

					 addPropToSchema(core.global, schema, split, length, 0, value);
				 }
			 }

			 return schema;
		 } catch (Exception x) {
			 core.app.logError(ErrorReporter.errorMsg(AxiomObject.class.getClass(), "getSchema"), x);
		 }

		 return null;
	 }

	 private static void addPropToSchema(final GlobalObject global, final Scriptable entry, 
			 final String[] hierarchy, final int length, 
			 final int count, final String value)
	 throws Exception {

		 if (count == length - 1) { 
			 Object existingValue = entry.get(hierarchy[count], entry);
			 if(existingValue == null || existingValue == Context.getUndefinedValue() 
					 || !(existingValue instanceof Scriptable)){	
				 Scriptable leaf = Context.getCurrentContext().newObject(global);
				 leaf.put("value", leaf, value);
				 entry.put(hierarchy[count], entry, leaf);
			 } else {
				 Scriptable s = (Scriptable) existingValue;
				 s.put("value", s, value);
			 }
		 } else {
			 Scriptable nextEntry = null;
			 Object retobj = null;
			 try {
				 retobj = entry.get(hierarchy[count], entry);
			 } catch (Exception ex) {
				 retobj = null;
			 }

			 if (retobj == null || retobj == Context.getUndefinedValue() || !(retobj instanceof Scriptable)) {
				 nextEntry = Context.getCurrentContext().newObject(global);
				 entry.put(hierarchy[count], entry, nextEntry);
			 } else {
				 nextEntry = (Scriptable) retobj;
			 }
			 addPropToSchema(global, nextEntry, hierarchy, length, count + 1, value);
		 }
	 }

	 private int getTypeForMultiValue(String propname) {
		 axiom.framework.core.TypeManager tmgr = core.app.typemgr;
		 Prototype proto = tmgr.getPrototype(node.getPrototype());
		 while (proto != null) {
			 ResourceProperties props = proto.getTypeProperties();
			 String typeStr = props.getProperty(propname + ".type");
			 if (typeStr != null) {
				 typeStr = typeStr.substring(typeStr.indexOf("(") + 1, typeStr.indexOf(")")).trim();
				 return LuceneManager.stringToType(typeStr);
			 } else {
				 proto = proto.getParentPrototype();
			 }
		 }

		 return IProperty.STRING;
	 }

	 private void checkValidity(MultiValue mv) throws RuntimeException {
		 int type = mv.getValueType();
		 Object[] values = mv.getValues();
		 int len = values.length;

		 for (int i = 0; i < len; i++) {
			 String className = null;
			 if (values[i] instanceof Scriptable) {
				 className = ((Scriptable) values[i]).getClassName();
			 }

			 if ((type == IProperty.BOOLEAN && !(values[i] instanceof Boolean) && !"Boolean".equals(className))
					 || (type == IProperty.DATE && !(values[i] instanceof Date) && !"Date".equals(className))
					 || (type == IProperty.FLOAT && !(values[i] instanceof Number) && !"Number".equals(className))
					 || (type == IProperty.INTEGER && !(values[i] instanceof Number) && !"Number".equals(className))
					 || (type == IProperty.REFERENCE && !(values[i] instanceof Reference) && !"Reference".equals(className))
					 || (type == IProperty.STRING && !(values[i] instanceof String) && !"String".equals(className))) {

				 throw new EvaluatorException("MultiValue object contains objects of a type " + type + " with class name "+className+", which is not specified in the prototype.properties file");
			 } else if (type == IProperty.JAVAOBJECT || type == IProperty.MULTI_VALUE 
					 || type == IProperty.NODE || type == IProperty.XML) {

				 throw new EvaluatorException("MultiValue object contains invalid objects of type " + LuceneManager.intToStr(type));
			 }
		 }
	 }

	 /**
	  * Returns a new AxiomObject with all the properties of this object copied onto the 
	  * new AxiomObject.
	  * 
	  * @param {String} [propname] Optional property name whose value should be replaced
	  *                 with propvalue
	  * @param {Object} [propvalue] The property value to set for propname
	  * @param {Boolean} [deep] Whether to do a deep copy of the object or not, that is,
	  *                  cloning and copying all child nodes as well
	  * @returns {AxiomObject} The newly created copy of this object
	  * @throws UnsupportedEncodingException
	  * @throws IOException
	  */
	 public Object jsFunction_copy(Object propname, Object propvalue, Object deep) 
	 throws UnsupportedEncodingException, IOException {
		 if (node == null) {
			 return null;
		 }

		 checkNode();

		 String protoname = node.getPrototype();
		 axiom.objectmodel.db.Node copynode = new axiom.objectmodel.db.Node(protoname, protoname,
				 core.app.getWrappedNodeManager());
		 Scriptable proto = core.getPrototype(protoname);
		 AxiomObject axobj = new AxiomObject(protoname, core, copynode, proto);

		 if (proto != null) {
			 Object f = ScriptableObject.getProperty(proto, protoname);
			 if (!(f instanceof Function)) {
				 // backup compatibility: look up function constructor
				 f = ScriptableObject.getProperty(proto, "__constructor__");
			 }
			 if (f instanceof Function) {
				 try {
					 ((Function) f).call(Context.getCurrentContext(), core.global, axobj, null);
				 } catch (JavaScriptException ex) {
					 System.err.println("Error in jsFunction_copy() -> Could not create the axiom object: " + ex.getMessage());
				 }
			 }
		 }

		 if (node instanceof axiom.objectmodel.db.Node) {
			 boolean deepCopy = false;
			 if (deep != null && deep != Undefined.instance) {
				 deepCopy = ScriptRuntime.toBoolean(deep);
			 }
			 ((axiom.objectmodel.db.Node) node).cloneNode(copynode, deepCopy);
			 String pname = null;
			 if (this.core.app.isPropertyFilesIgnoreCase() && propname != null) {
				 pname = propname.toString().toLowerCase();
		     } else if (propname != null) {
		    	 pname = propname.toString();
		     }
			 //String pname = propname != null ? propname.toString().toLowerCase() : null;
			 copynode.setString("_v_copied_code", this.getObjectCode());
			 copynode.setNode("_v_copied_parent", ((axiom.objectmodel.db.Node) node).getParent());
			 if (pname != null) {
				 if (copynode.get(pname) != null)
					 copynode.setString(pname, propvalue == null ? null : propvalue.toString());
			 }
		 }


		 return axobj;
	 }

	 // a unique code representing a node
	 protected String getObjectCode() throws UnsupportedEncodingException, IOException {
		 if (this.node.get("_v_copied_code") != null) {
			 return this.node.get("_v_copied_code").getStringValue();
		 }
		 return this.node.getPrototype() + "/" + this.node.getID();
	 }

	 /**
	  * Returns the path of this object's parent.
	  * 
	  * @returns {String} The path of <code>this._parent</code>
	  * @throws UnsupportedEncodingException
	  * @throws IOException
	  */
	 public String jsFunction_getParentPath() throws UnsupportedEncodingException, IOException {
	     INode parent = this.node.getParent();
	     if (parent == null) {
	         parent = this.node.getNode("_v_copied_parent");
	     }
	     if (parent != null) {
	         return getPath(parent, parent.getPrototype(), this.core.app);
	     }
	     return "";
	 }

	 /**
	  * @deprecated replaced with getParentPath()
	  */
	 public String jsFunction_parentPath() throws UnsupportedEncodingException, IOException {
	     INode parent = this.node.getParent();
	     if (parent == null) {
	         parent = this.node.getNode("_v_copied_parent");
	     }
	     if (parent != null) {
	         return getPath(parent, parent.getPrototype(), this.core.app);
	     }
	     return "";
	 }

	 // used by path and parentPath()
	 public static String getPath(INode n, String cName, Application app) 
	 throws UnsupportedEncodingException, IOException {
		 if (n == null) { 
			 return null; 
		 }

		 String href = app.getNodeHref(n, null);
		 href = RhinoEngine.getRhinoCore(app).postProcessHref(n, cName, href);
		 if (app.getBaseURI().equals("/")) {
			 return href;
		 }
		 int a = href.indexOf('/', 1);
		 if (a == -1) { 
			 return "/"; 
		 }		
		 return href.substring(a);
	 }

	 /**
	  * Get this object's path.  Since all objects in Axiom have a single location in 
	  * an object hierarchy, all based off of root (at path '/'), 
	  * the path is determined by parent/child relationships and the accessnames 
	  * in the parent/child relationships.  
	  * For example, if <code>root.get('foo').get('bar')</code> returns <code> this </code> 
	  * then <code>this.getPath()</code> would return '/foo/bar'.
	  *
	  * @returns {String} This object's path
	  * @throws UnsupportedEncodingException
	  * @throws IOException
	  */
	 public String jsFunction_getPath() throws UnsupportedEncodingException, IOException {
	     if (node == null) { 
	         return null; 
	     }
	     checkNode();
	     return getPath(node, className, this.core.app);
	 }

	 /**
	  * @deprecated replaced by getPath()
	  */
	 public String jsFunction_path()
	 throws UnsupportedEncodingException, IOException {
	     if (node == null) { return null; }
	     checkNode();
	     return getPath(node, className, this.core.app);
	 }

	 // object traversal
	 public static IPathElement traverse(String path, Application app) {
	     String[] ids = path.split("/");
	     IPathElement p = ((axiom.objectmodel.db.Node) app.getDataRoot());
	     final String appName = app.getName();
	     boolean traversedAppName = false;
	     for (int i = 0; i < ids.length; i++) {
	         if (p == null) {
	             break;
	         }
	         if (!ids[i].equals("")) {
	             if (ids[i].toLowerCase().equals(appName) && !traversedAppName) {
	                 traversedAppName = true;
	                 continue;
	             }
	             p = p.getChildElement(ids[i]);
	         }
	     }
	     return p;
	 }

	 /**
	  * Returns true if this object has any children.
	  * 
	  * @returns {Boolean} Whether or not this object has any children
	  */
	 public boolean jsFunction_hasChildren() {
		 checkNode();
		 return node.getSubnodes().hasMoreElements();
	 }

	 /**
	  * Remove a child object from this object's children collection.   
	  * If the child object is not inserted as a child anywhere else before the transaction is
	  * completed in Axiom, then the child object automatically gets deleted from the 
	  * database. 
	  * 
	  * @param {AxiomObject} [child] The child object to remove from this object's children
	  * @returns {Boolean} Whether the operation was a success or not
	  */ 
	 public boolean jsFunction_remove(Object child) throws Exception{
		 if(child == null || child == Undefined.instance){
			 throw new Exception(".remove requires an object to delete");
		 }

		 boolean success = this.removeChild(child);

		 if (success) { 
			 String name;
			 AxiomObject parent;
			 if (!"axiomobject".equals(node.getPrototype().toLowerCase())) {
				 this.calcComputedProps("_children");
			 } else if ((name = node.getName()) != null &&
					 (parent = (AxiomObject) this.getInternalProperty("_parent")) != null) {
				 parent.calcComputedProps(name);
			 } 
             ((AxiomObject) child).calcComputedProps("_parent");
		 }

		 return success;
	 } 
	 
	 /**
	  * Check to see if the user security roles match any of the input roles.
	  * 
	  * @param {Array} roles A JavaScript array of roles to check if the user is a part of
      * @returns {Boolean} Whether the user has any matching roles with the input list 
	  * @throws SecurityException
	  */
	 public boolean jsFunction_checkRoles(Object roles) throws SecurityException {
		 ArrayList r = new ArrayList();
		 NativeArray ra = (NativeArray)roles;
		 for (int i = 0; i < ra.getLength();i++) {
			 r.add(ra.get(i, ra));
		 }
		 return ActionSecurityManager.checkRoles(this.core.app, r.toArray());
	 }
	 
	 /**
	  * Checks to see if the currently logged in user is allowed to make a URL request
	  * that maps to the input action on this object.
	  * 
	  * @param {String} action The action on this object to check if the current session
	  *                        user is allowed to make a URL request for
	  * @param {Array} [roles] Optional list of roles to pass in.  Defaults to retrieving
	  *                        the user roles from the current session user and checking
	  *                        against those roles.  Only pass in roles if you want to 
	  *                        override the current session user's default roles
	  * @returns {Boolean} Whether the action is allowed by the current session user or not
	  */
	 public boolean jsFunction_isAllowed(String action, Scriptable roles) {
		 return this.isAllowedDefault(action, null, roles);
	 }

	 /**
	  * Used only if you are overriding the default behavior of isAllowed(), and you
	  * want to invoke the default isAllowed() behavior in your overriding function.
	  * 
	  * @param {String} action The action on this object to check if the current session
	  *                        user is allowed to make a URL request for
	  * @param {Object} data A JavaScript object that contains name/value pairs to override
	  *                      any of the name/value pairs in security.properties 
	  * @returns {Boolean} Whether the action is allowed by the current session user or not
	  */
	 public boolean jsFunction_isAllowedDefault(String action, Object data) {
		 if (data == null || data == Undefined.instance) {
			 return this.isAllowedDefault(action, null, null);
		 } else if (data instanceof Scriptable) {
			 return this.isAllowedDefault(action, (Scriptable) data, null);
		 } 
		 return false;
	 }
	 
	 /**
	  * If cache.properties specifies that the results of a specific set of functions 
	  * on an object may be cached (for optimized performance), then this function is
	  * invoked to invalidate those results and re-execute the function call the next time
	  * it is invoked.
	  * 
	  * @param {String} [func] The name of the function whose cached result should be
	  *                        invalidated.  If no function name is specified, then ALL
	  *                        cached function results for this object will be invalidated
	  */
	 public void jsFunction_invalidateResultsCache(Object func) {
	     if (this.node == null || this.node.getState() == INode.TRANSIENT) {
	         return;
	     }

	     checkNode();

	     String function = null;
	     if (func != null) {
	         if (func instanceof Wrapper) {
	             function = ((Wrapper) func).unwrap().toString();
	         } else if (!(func instanceof Undefined)) {
	             function = func.toString();
	         }
	     }

	     ExecutionCache appcache = this.core.app.getExecutionCache();
	     ExecutionCache talcache = this.core.app.getTALCache();

	     if (appcache != null) {
	         if (function != null) {
	             appcache.invalidate(this, function);
	         } else {
	             appcache.invalidateAll(this);
	         }
	     }

	     if (talcache != null) {
	         if (function != null) {
	             talcache.invalidate(this, function);
	         } else {
	             talcache.invalidateAll(this);
	         }
	     }
	 }

	 public boolean remove() throws Exception{
		 if(!this.node.getDbMapping().isRelational()){
			 throw new Exception(".remove may only be called on relational prototypes");
		 }
		 
		 boolean success = this.node.remove();

		 if (success) { 
			 String name;
			 AxiomObject parent;
			 if (!"axiomobject".equals(this.node.getPrototype().toLowerCase())) {
				 this.calcComputedProps("_children");
			 } else if ((name = node.getName()) != null &&
					 (parent = (AxiomObject) this.getInternalProperty("_parent")) != null) {
				 parent.calcComputedProps(name);
			 } 
             this.calcComputedProps("_parent");
		 }

		 return success;
	 } 

	 public void calcComputedProps(String name) {
		 Prototype proto = core.app.typemgr.getPrototype(node.getPrototype());
		 ResourceProperties props = proto.getTypeProperties();
		 // First get the HashMap from Memory ***
		 if (!dependencies.isEmpty()) {
			 //Get when Properties file was Last modified
			 long PropertieslastModified = props.lastModified();
			 //Now compare the times, if properties file was modified later, re-populate the Hashmap
			 if (PropertieslastModified > dependenciesLastModified)
				 populateHashMap();
		 } else {
			 populateHashMap();
		 }
		 //Check if the name parameter is a key in the parent hashmap, if so get the child hashmap
		 HashMap pMap = (HashMap) dependencies.get(proto.getName());
		 if (pMap != null){
			 HashMap dMap = (HashMap) pMap.get(name);
			 if (dMap != null) {
				 //Now iterate over the ChildHashmap and get the dependent property name and methods and call the methods
				 executeComputeFunctions(name, dMap);
			 }
			 dMap = (HashMap) pMap.get(DEPENDSALL);
			 if (dMap != null) {
				 executeComputeFunctions(name, dMap);
			 }
		 }
	 }

	 private void executeComputeFunctions(String name, HashMap dMap) {
		 Set dSet = dMap.keySet();
		 for (Iterator it = dSet.iterator(); it.hasNext(); ) {
			 String dProp = (String) it.next();
             if (dProp.equals(name)) {
                    continue; // Prevents infinite recursion from happening!
                }
			 String functionName = (String) dMap.get(dProp);
			 Context cx = Context.getCurrentContext();
			 Object ret = null;
			 try {
				 ret = cx.evaluateString(this, functionName, "", 0, null);
				 if (ret != null) {
					 //Assigns the value to the dependent property
					 IProperty p = this.node.get(dProp);
					 if (p == null || !ret.equals(p.getValue())) {
						 this.put(dProp, this, ret);
					 }
				 }
			 } catch (Exception ex) {
				 throw new RuntimeException("AxiomObject.executeComputeFunctions() failed: " + ex.toString());
			 }
		 }
	 }

	 public void populateHashMap(){
		 dependenciesLastModified = (new Long(System.currentTimeMillis())).longValue();
		 HashMap pMap = new HashMap();
		 final String protoName = node.getPrototype();

		 ResourceProperties props = getResourceProperties(protoName);

		 Set propSet = props.keySet();
		 String psKey = null;
		 for (Iterator it = propSet.iterator(); it.hasNext(); ){
			 psKey = (String)it.next();
			 if (psKey.indexOf(".") == -1) {
				 ResourceProperties subProps = props.getSubProperties(psKey);
				 String dStr = subProps.getProperty(".depends");
				 String cStr = subProps.getProperty(".compute");
				 if (dStr != null && cStr != null) {
					 String[] dArr=dStr.split(",");
					 for (int i = 0; i < dArr.length; i++) {
						 dArr[i] = dArr[i].trim();
						 if (this.core.app.isPropertyFilesIgnoreCase()) {
							dArr[i] = dArr[i].toLowerCase(); 
						 }
						 HashMap dMap = (HashMap) pMap.get(dArr[i]);
						 if (dMap == null) {
							 dMap = new HashMap();
							 pMap.put(dArr[i], dMap);
						 }
						 dMap.put(psKey, cStr);
					 }
				 } else if (cStr != null) {
					 HashMap dMap = (HashMap) pMap.get(DEPENDSALL);
					 if (dMap == null) {
						 dMap = new HashMap();
						 pMap.put(DEPENDSALL, dMap);
					 }
					 dMap.put(psKey, cStr);
				 }
			 }
		 }
		 dependencies.put(protoName, pMap);
	 }

	 public ResourceProperties getResourceProperties(final String protoName) {
		 Prototype proto = core.app.typemgr.getPrototype(node.getPrototype());
		 return proto.getAllProps();
	 }

	 private void validateString(String name, String value, ResourceProperties props) 
	 throws RuntimeException {
		 String min = props.getProperty(name + ".valid.min");
		 String max = props.getProperty(name + ".valid.max");

		 if (min != null || max != null) {
			 final int length = value.length();
			 if (min != null) {
				 if (Integer.parseInt(min) > length) {
					 throw new RuntimeException("Value Error: Minimum Length is " + min);
				 }
			 }
			 if (max != null) {
				 if (Integer.parseInt(max) < length) {
					 throw new RuntimeException("Value Error: Maximum Length is " + max);
				 }
			 }
		 }

		 String expr = props.getProperty(name + ".valid.expr");
		 if (expr != null) {
			 String error = null;
			 try {               
				 error = (String)Context.getCurrentContext().evaluateString(this, expr, "", 0, null);                
			 } catch (Exception e) {
				 throw new RuntimeException("Internal Error: " + e);
			 }
			 if (error != null) {
				 throw new RuntimeException(error);
			 }
		 }
	 }

	 private void validateFloat(String name, double value, ResourceProperties props) 
	 throws RuntimeException {
		 String min = props.getProperty(name + ".valid.min");
		 String max = props.getProperty(name + ".valid.max");

		 if (min != null) {
			 if (Double.valueOf(min).compareTo(value) > 0){
				 throw new RuntimeException("Value Error: Minimum Value is " + min);
			 }
		 }
		 if (min != null) {  
			 if (Double.valueOf(max).compareTo(value) < 0){
				 throw new RuntimeException("Value Error: Maximum Value is " + max);
			 }
		 }

		 String expr = props.getProperty(name + ".valid.expr");
		 if (expr != null) {
			 String error = null;
			 try {               
				 error = (String)Context.getCurrentContext().evaluateString(this, expr, "", 0, null);                
			 } catch (Exception e) {
				 throw new RuntimeException("Internal Error: " + e);
			 }
			 if (error != null) {
				 throw new RuntimeException(error);
			 }
		 }
	 }

	 /**
	  * Sets up the default properties on a axiom object by reading them from prototype.properties
	  */
	 protected void setupDefaultProperties() {
		 if (this.node == null) {
			 return;
		 }

		 Prototype prototype = core.app.getPrototypeByName(this.className);
		 if (prototype == null) {
			 return;
		 }

		 Context cx = Context.getCurrentContext();
		 ResourceProperties props = this.getResourceProperties(prototype.getName());
		 Enumeration e = props.keys();

		 while (e.hasMoreElements()) {
			 String key = (String) e.nextElement();
			 if (key.endsWith(".default")) {
				 String prop = key.substring(0, key.indexOf("."));
				 String setTo = props.getProperty(key);

				 try {
					 Object ret = cx.evaluateString(this, setTo, "", 0, null);
					 this.put(prop, this, ret);
				 } catch (Exception ex) {
					 core.app.logError(ErrorReporter.errorMsg(this.getClass(), "setupDefaultProperties") 
							 + "Could not initialize default value for property " + prop + " on AxiomObject " 
							 + this.className, ex);
				 }
			 }
		 }
	 }

	 /**
	  * Create a new AxiomObject of type protoname.
	  * @param protoname
	  * @return
	  */
	 public static AxiomObject createAxiomObject(String protoname) {
		 RhinoCore core = ((RhinoEngine) Context.getCurrentContext().getThreadLocal("engine")).getCore();
		 Node node = new axiom.objectmodel.db.Node(protoname, protoname,
				 core.app.getWrappedNodeManager());
		 Scriptable proto = core.getPrototype(protoname);
		 return new AxiomObject(protoname, core, node, proto);
	 }

	 /**
	  * Wrap a node in a AxiomObject of type protoname.
	  * @param protoname
	  * @param node
	  * @return
	  */
	 public static AxiomObject createAxiomObject(String protoname, INode node) {
		 RhinoCore core = ((RhinoEngine) Context.getCurrentContext().getThreadLocal("engine")).getCore();    	
		 Scriptable proto = core.getPrototype(protoname);
		 return new AxiomObject(protoname, core, node, proto);
	 }

	 public boolean isAllowedDefault(String action, Scriptable data, 
			 Scriptable rolesVar) {
		 if (action.equals("unauthorized") || action.equals("error") 
				 || action.equals("notfound")) { 
			 return true; 
		 }

		 checkNode();

		 if (this.node == null) {
			 return false;
		 }

		 Prototype p = this.core.app.getPrototypeByName(this.node.getPrototype());
		 HashMap securityMap = new HashMap(p.getSecurityMap());

		 try {
			 LinkedList roles;
			 if (rolesVar == null || rolesVar == Undefined.instance) {
				 INode user = this.core.app.getCurrentRequestEvaluator()
				 .getSession()
				 .getUserNode();
				 roles = (LinkedList) ActionSecurityManager.getUserRoles(user, this.core.app, 0);    
			 } else {
				 Scriptable s = (Scriptable) rolesVar;
				 Object[] ids = s.getIds();
				 final int length = ids.length;
				 roles = new LinkedList();
				 for (int i = 0; i < length; i++) {
					 roles.add(s.get(i, s));
				 }
			 }

			 ArrayList permissions = new ArrayList();
			 if (data != null && data != Undefined.instance) {
				 Object[] ids = data.getIds();
				 final int len = ids.length;
				 for (int i = 0; i < len; i++) {
					 String currId = ids[i].toString();
					 securityMap.put(currId, data.get(currId, data).toString());
				 }
			 }

			 String roleProp = (String) securityMap.get(action);
			 if (roleProp == null && "main".equalsIgnoreCase(action)) {
				 roleProp = "@Anyone";
			 }

			 if (roleProp == null) {
				 return false;
			 } else {
				 String[] actionRoles = roleProp.split(",");
				 final int len = actionRoles.length;
				 for (int i = 0; i < len; i++) {
					 addPermissions(actionRoles[i].trim(), securityMap, permissions);
				 }
			 }

			 final int size = permissions.size();
			 for (int i = 0; i < size; i++) {
				 if (roles.contains(permissions.get(i))) {
					 return true;
				 }
			 }
		 } finally {
			 securityMap.clear();
			 securityMap = null;
		 }

		 return false;
	 }

	 private void addPermissions(String var, HashMap securityMap, ArrayList permissions) {
		 if (var.startsWith("$")) {
			 String v = (String) securityMap.get(var);
			 if (v != null) {
				 String[] r = v.split(",");
				 final int len = r.length;
				 for (int i = 0; i < len; i++) {
					 addPermissions(r[i].trim(), securityMap, permissions);
				 }
			 }
		 } else {
			 permissions.add(var);
		 }
	 }

	 /*
	  * 
	  * TAL
	  * 
	  */

	 private static Object _getTALXml(String name, String proto) 
	 throws Exception {
		 Object doc = getTALDoc(proto, name);
		 return doc;
	 }

	 /**
	  * Get an XML object representation of the specified TAL file on this object.
	  * 
	  * @param {String} tal The name of the TAL file
	  * @returns {XML} A JavaScript XML object representing the TAL file
	  * @throws Exception
	  */
	 public Object jsFunction_getTALXml(Object tal) 
	 throws Exception {
		 Context cx = Context.getCurrentContext();
		 RhinoCore core = ((RhinoEngine) cx.getThreadLocal("engine")).getCore();
		 String name = tal.toString();
		 String proto = this.getClassName();
		 // separate prototype name and template name by :. this will be used again in TemplateLoader
		 Object result = _getTALXml(name, proto);
	     return (result != null) ? cx.evaluateString(core.getScope(), result.toString(), "", 1, null) : null;
	 }

	 /**
	  * Evaluate a TAL file on this object and return the rendered TAL as an XML object.
	  * 
	  * @param {String} tal The name of the TAL file
	  * @param {Object} [data] A JavaScript object encapsulating the data scope 
	  * 					   passed to the TAL evaluation engine.  Defaults to an empty
	  * 					   JavaScript object
	  * @returns {XML} A JavaScript XML object representing the rendered TAL
	  * @throws Exception
	  */
	 public Object jsFunction_renderTAL(Object tal, Scriptable data) 
	 throws Exception {
		 Object result = null;
		 RhinoCore core = ((RhinoEngine) Context.getCurrentContext().getThreadLocal("engine")).getCore();
		 Context cx = Context.getCurrentContext();
         RhinoEngine re = (RhinoEngine) core.app.getCurrentRequestEvaluator().getScriptingEngine();
		 Scriptable scope = re.global;
		 String proto = this.getClassName();
		 String name = tal.toString();
		 if (data == null || data == Undefined.instance) {
			 data = cx.newObject(scope); // empty javascript object 
		 } 
         
         ExecutionCache cache = (ExecutionCache) this.core.app.getTALCache();
         result = cache.getFunctionResult(this, name);
         if (result != null) {
             return result;
         }

		 try {
			 Object xml = null;
			 if (tal instanceof String || (tal instanceof Scriptable && ((Scriptable) tal).getClassName().equals("String"))) {
			     xml = TALExtension.stringToXmlObject((String) getTALDoc(proto, name), scope, core.app);
			 } else {
				 xml = TALExtension.stringToXmlObject(TALExtension.xmlObjectToString(((Scriptable) tal), core.app), scope, core.app);
			 }
			 if (xml == null) {
				 throw new RuntimeException("Could not find the TAL file " + name);
			 }
			 
			 synchronized (scope) {
				 data.put("this", data, this);
			     data.put("app", data, Context.toObject(new ApplicationBean(core.app), scope));
			     Object req = new RequestBean(core.app.getCurrentRequestEvaluator().getRequest());
			     data.put("req", data, Context.toObject(req, scope));
			     data.put("root", data, core.getNodeWrapper(core.app.getNodeManager().getRootNode()));
			     
			     Object f = ScriptableObject.getProperty(scope, "TAL");
                 Object[] fargs = { xml, data };
                 for (int i = 0; i < fargs.length; i++) {
                     // convert java objects to JavaScript
                     if (fargs[i] != null) {
                         fargs[i] = Context.javaToJS(fargs[i], scope);
                     }
                 }

                 Function func = null;
                 try {
                	 func = (Function) f;
                 } catch (Exception e) {
                	 throw new RuntimeException("Unable to load TAL, check that tal.js exists in the lib directory");
                 }
                 
                 Object ret = func.call(cx, scope, scope, fargs);
                 result = Context.toObject(ret, scope);
			 }
             
			 if (this.core.app.isFunctionResultCachable(this, result, name)) {
			     cache.putResultInCache(this, name, result);
			 }
		 } catch (Exception ex) {
             this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "jsFunction_renderTAL") 
            		 + "TAL execution failed on object " + this 
            		 + " with data = " + ((Function)ScriptableObject.getProperty(data, "toSource")).call(cx, scope, data, new Object[]{}), ex);
			 throw new Exception("ERROR in TAL() on " + name + ": " + ex.getMessage());
		 } 	

		 return result;
	 } 

	 private static Object getTALDoc(String proto, String name) 
	 throws Exception {
		 RhinoCore core = ((RhinoEngine) Context.getCurrentContext().getThreadLocal("engine")).getCore();
		 Prototype prototype = core.app.getPrototypeByName(proto);
		 while (prototype != null) {
			 // separate prototype name and template name by :. this will be used again in TemplateLoader
			 String curr = new StringBuffer().append(prototype.getName())
			 .append(':').append(name).toString();
			 Object d = core.retrieveDocument(core.app, curr);
			 if (d != null) {
				 return d;
			 }
			 prototype = prototype.getParentPrototype();
		 }
		 return null;
	 } 

	 public static final String getModeStr(Key k) {
	     if (k instanceof DbKey) {
	         final int mode = ((DbKey) k).getLayer();
	         if (mode == DbKey.LIVE_LAYER) {
	             return "LIVE";
	         } else {
	             return "DRAFT";
	         } 
	     }
	     return "LIVE";
	 }

	 /**
	  * Deletes the current object it's children, if this object
	  * is not inserted as a child anywhere else before the transaction is
	  * completed in Axiom, then this object and it's children are automatically deleted 
	  * from the database. 
	  *                      
	  * @returns {Boolean} Whether the operation was a success or not
	  */ 

	 public boolean jsFunction_del() throws Exception{
		 boolean success = this.node.remove();

		 if (success) { 
			 String name;
			 AxiomObject parent;
			 if (!"axiomobject".equals(this.node.getPrototype().toLowerCase())) {
				 this.calcComputedProps("_children");
			 } else if ((name = node.getName()) != null &&
					 (parent = (AxiomObject) this.getInternalProperty("_parent")) != null) {
				 parent.calcComputedProps(name);
			 } 
             this.calcComputedProps("_parent");
		 }

		 return success;
	}
	 
	 
}