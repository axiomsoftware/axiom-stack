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
 * $RCSfile: ListViewWrapper.java,v $
 * $Author: hannes $
 * $Revision: 1.4 $
 * $Date: 2006/04/18 11:08:58 $
 */
package axiom.scripting.rhino;


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;
import org.mozilla.javascript.ScriptRuntime;

import axiom.objectmodel.INode;
import axiom.objectmodel.db.Key;
import axiom.objectmodel.db.NodeHandle;
import axiom.objectmodel.db.OrderedSubnodeList;
import axiom.objectmodel.db.WrappedNodeManager;


public class ListViewWrapper extends ScriptableObject implements Wrapper, Scriptable {
    final List list;
    final RhinoCore core;
    final WrappedNodeManager wnm;
    final AxiomObject axObj;
    INode node;

    static ListViewWrapper listViewProto;

    /**
     * Private constructor used to create the object prototype.
     */
    private ListViewWrapper() {
        list = null;
        core = null;
        wnm = null;
        node = null;
        axObj = null;
    }

    /**
     * Create a JS wrapper around a subnode list.
     * @param list
     * @param core
     * @param wnm
     * @param axObj
     */
    ListViewWrapper (List list, RhinoCore core, WrappedNodeManager wnm, AxiomObject axObj) {
        if (list == null) {
            throw new IllegalArgumentException ("ListWrapper unable to wrap null list.");
        }
        this.core = core;
        this.list = list;
        this.wnm = wnm;
        this.axObj = axObj;
        this.node = axObj.node;
        if (listViewProto == null) {
            listViewProto = new ListViewWrapper();
            listViewProto.init();
        }
        setPrototype(listViewProto);
    }

    /**
     * Init JS functions from methods.
     */
    void init() {
        int attributes = READONLY | DONTENUM | PERMANENT;
        
        Method[] methods = getClass().getDeclaredMethods();
        for (int i=0; i<methods.length; i++) {
            String methodName = methods[i].getName();

            if (methodName.startsWith("jsFunction_")) {
                methodName = methodName.substring(11);
                FunctionObject func = new FunctionObject(methodName,
                                                         methods[i], this);
                this.defineProperty(methodName, func, attributes);
            }
        }
    }

    public Object unwrap() {
        return list;
    }

    public Object jsFunction_get(Object idxObj) {
        if (idxObj instanceof Number)
            return jsFunction_get(((Number) idxObj).intValue());
        else // fallback to this View's AxiomObject's get-function
            return axObj.jsFunction_get(idxObj);
    }
    
    public Object jsFunction_get(int idx) {
        // return null if given index is out of bounds
        if (list.size() <= idx)
            return null;
        Object obj = list.get(idx);
        // return null if indexed object is null
        if (obj == null)
            return null;
        
        // return AxiomObject in case of a NodeHandle
        if (obj instanceof NodeHandle) {
            return Context.toObject(((NodeHandle) obj).getNode(wnm), core.global);
        } else if (!(obj instanceof Scriptable)) {
            // do NOT wrap primitives - otherwise they'll be wrapped as Objects,
            // which makes them unusable for many purposes (e.g. ==)
            if (obj instanceof String ||
                    obj instanceof Number ||
                    obj instanceof Boolean) {
                return obj;
            }

            return Context.toObject(obj, core.global);
        }
        return obj;
    }
    
    public Object jsFunction_getById(Object id) {
        return axObj.jsFunction_getById(id);
    }

    /**
     *  Prefetch child objects from (relational) database.
     */
    public void jsFunction_prefetchChildren(Object startArg, Object lengthArg)
                            throws Exception {
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
        if (!(node instanceof axiom.objectmodel.db.Node))
            return;
        checkNode();
        start = Math.max(start, 0);
        length = Math.min(list.size() - start, length);
        if (length < 1)
            return;
        Key[] keys = new Key[length];
        for (int i = start; i < start+length; i++) {
            keys[i - start] = ((NodeHandle) list.get(i)).getKey();
        }
        try {
            ((axiom.objectmodel.db.Node) node).prefetchChildren(keys);
        } catch (Exception x) {
            System.err.println("Error in AxiomObject.prefetchChildren(): " + x);
        }
    }

    public int jsFunction_size() {
        if (list==null)
            return 0;
        return list.size();
    }
    
    public int jsFunction_count() {
        return jsFunction_size();
    }

    public void jsFunction_add(Object child) {
        if (this.axObj==null)
            throw new RuntimeException("ListWrapper has no knowledge about any AxiomObject or collection");
        axObj.jsFunction_add(child);
    }
    
    /**
     * Return the full list of child objects in a JavaScript Array.
     * This is called by jsFunction_list() if called with no arguments.
     *
     * @return A JavaScript Array containing all child objects
     */
    private Scriptable list() {
        checkNode();

        ArrayList a = new ArrayList();
        for (Iterator i = list.iterator(); i.hasNext(); ) {
            NodeHandle nh = (NodeHandle) i.next();
            if (nh!=null) {
                a.add(Context.toObject(nh.getNode(wnm), core.global));
            }
        }
        return Context.getCurrentContext().newArray(core.global, a.toArray());
    }
    
    /**
     *  Return a JS array of child objects with the given start and length.
     *
     * @return A JavaScript Array containing the specified child objects
     */
    public Scriptable jsFunction_list(Object startArg, Object lengthArg) {
        if (startArg == Undefined.instance && lengthArg == Undefined.instance) {
            return list();
        }

        int start = (int) ScriptRuntime.toNumber(startArg);
        int length = (int) ScriptRuntime.toNumber(lengthArg);

        if (start < 0 || length < 0) {
            throw new EvaluatorException("Arguments must not be negative in AxiomObject.list(start, length)");
        }

        checkNode();
        start = Math.max(start, 0);
        length = Math.min(list.size() - start, length);

        prefetchChildren(start, length);
        ArrayList a = new ArrayList();

        for (int i=start; i<start+length; i++) {
            NodeHandle nh = (NodeHandle) list.get(i);
            if (nh != null) {
                a.add(Context.toObject(nh.getNode(wnm), core.global));
            }
        }

        return Context.getCurrentContext().newArray(core.global, a.toArray());
    }

    /**
     *  Remove this object from the database.
     */
    public boolean jsFunction_remove(Object arg) {
        return jsFunction_removeChild(arg);
    }

    /**
     *  Remove a child node from this node's collection without deleting
     *  it from the database.
     */
    public boolean jsFunction_removeChild(Object child) {
        if (this.axObj==null)
            throw new RuntimeException("ListWrapper has no knowledge about any AxiomObject or collection");
        return axObj.removeChild(child);
    }

    /**
     *  Invalidate the node itself or a subnode
     */
    public boolean jsFunction_invalidate() {
        if (this.axObj==null)
            throw new RuntimeException("ListWrapper has no knowledge about any AxiomObject or collection");
        return axObj.jsFunction_invalidate();
    }

    /**
     *  Check if node is contained in subnodes
     */
    public int jsFunction_contains(Object obj) {
        if (obj instanceof AxiomObject) {
            INode n = ((AxiomObject) obj).node;
            if (n instanceof axiom.objectmodel.db.Node)
                return list.indexOf(((axiom.objectmodel.db.Node) n).getHandle());
        }
        return -1;
    }

    /**
     * This method represents the Java-Script-exposed function for updating Subnode-Collections.
     * The following conditions must be met to make a subnodecollection updateable.
     * .) the collection must be specified with collection.updateable=true
     * .) the id's of this collection must be in ascending order, meaning, that new records
     *    do have a higher id than the last record loaded by this collection
     */
    public int jsFunction_update() {
        if (!(node instanceof axiom.objectmodel.db.Node))
            throw new RuntimeException ("updateSubnodes only callabel on persistent AxiomObjects");
        checkNode();
        axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) node;
        return n.updateSubnodes();
    }

    /**
     * Retrieve a view having a different order from this Node's subnodelist.
     * The underlying OrderedSubnodeList will keep those views and updates them
     * if the original collection has been updated.
     * @param expr the order (like sql-order using the properties instead)
     * @return ListViewWrapper holding the information of the ordered view
     */
    public Object jsFunction_getOrderedView(String expr) {
        if (!(list instanceof OrderedSubnodeList))
            throw new RuntimeException ("getOrderedView only callable on persistent AxiomObjects");
        checkNode();
        
        OrderedSubnodeList osl = (OrderedSubnodeList) list;
        return new ListViewWrapper (osl.getOrderedView(expr), core, wnm, axObj);
    }
    
    public String toString() {
        if (list==null)
            return "[ListWrapper{}]";
        else
            return "[ListWrapper"+ list.toString() + "]";
    }

    /**
     * Check if the node has been invalidated. If so, it has to be re-fetched
     * from the db via the app's node manager.
     */
    private final void checkNode() {
        if (node != null && node.getState() == INode.INVALID) {
            if (node instanceof axiom.objectmodel.db.Node) {
                NodeHandle handle = ((axiom.objectmodel.db.Node) node).getHandle();
                node = handle.getNode(core.app.getWrappedNodeManager());
            }
        }
    }

    public String getClassName() {
        return "[ListWrapper]";
    }
}
