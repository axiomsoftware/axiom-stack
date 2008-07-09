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
 * $RCSfile: AxiomObjectCtor.java,v $
 * $Author: hannes $
 * $Revision: 1.2 $
 * $Date: 2006/04/03 12:32:35 $
 */
package axiom.scripting.rhino;

import org.mozilla.javascript.*;

import axiom.objectmodel.INode;
import axiom.objectmodel.db.*;
import axiom.objectmodel.dom.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;


public class AxiomObjectCtor extends FunctionObject {

    // init flag to trigger prototype compilation on
    // static constructor property access
    boolean initialized;
    RhinoCore core;

    static Method axiomObjCtor;

    static {
        try {
            axiomObjCtor = AxiomObjectCtor.class.getMethod("jsConstructor", new Class[] {
                Context.class, Object[].class, Function.class, Boolean.TYPE });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Error getting AxiomObjectCtor.jsConstructor()");
        }
    }

    static final int attr = ScriptableObject.DONTENUM  |
                            ScriptableObject.PERMANENT |
                            ScriptableObject.READONLY;
    /**
     * Create and install a AxiomObject constructor.
     * Part of this is copied from o.m.j.FunctionObject.addAsConstructor().
     *
     * @param prototype
     */
    public AxiomObjectCtor(String protoName, RhinoCore core, Scriptable prototype) {
        super(protoName, axiomObjCtor, core.global);
        this.core = core;
        // Scriptable ps = prototype.getParentScope();
        addAsConstructor(core.global, prototype);
        // prototype.setParentScope(ps);
        defineProperty("getById", new GetById(), attr);
    }

    /**
     *  This method is used as AxiomObject constructor from JavaScript.
     */
    public static Object jsConstructor(Context cx, Object[] args,
                                       Function ctorObj, boolean inNewExpr)
                         throws JavaScriptException {
        AxiomObjectCtor ctor = (AxiomObjectCtor) ctorObj;
        RhinoCore core = ctor.core;
        String protoname = ctor.getFunctionName();

        // if this is a java object prototype, create a new java object
        // of the given class instead of a AxiomObject.
        if (core.app.isJavaPrototype(protoname)) {
            String classname = core.app.getJavaClassForPrototype(protoname);
            try {
                Class clazz = Class.forName(classname);
                // try to get the constructor matching our arguments
                Class[] argsTypes = new Class[args.length];
                for (int i=0; i<argsTypes.length; i++) {
                    argsTypes[i] = args[i] == null ? null : args[i].getClass();
                }
                Constructor cnst = clazz.getConstructor(argsTypes);
                // crate a new instance using the constructor
                Object obj = cnst.newInstance(args);
                return Context.toObject(obj, core.global);
            } catch (Exception x) {
                System.err.println("Error in Java constructor: "+x);
                throw new EvaluatorException(x.toString());
            }
        } else {
            INode node = new axiom.objectmodel.db.Node(protoname, protoname,
                                                    core.app.getWrappedNodeManager());
            Scriptable proto = core.getPrototype(protoname);
            AxiomObject hobj = null;

            if(args != null && args.length > 0 && args[0] instanceof Scriptable){
            	Scriptable data = (Scriptable)args[0];
            	hobj = new AxiomObject(protoname, core, node, proto, data);   
            } else {
            	hobj = new AxiomObject(protoname, core, node, proto, true);
                String uname = "";
                try {
                	uname = hobj.getUserName();
                } catch (Exception e) { 
                }
                node.setString("creator", uname);
                node.setString("lastmodifiedby", uname);
            }
            
            if (proto != null) {
                Object f = ScriptableObject.getProperty(proto, protoname);
                if (!(f instanceof Function)) {
                    // backup compatibility: look up function constructor
                    f = ScriptableObject.getProperty(proto, "__constructor__");
                }
                if (f instanceof Function) {
                    ((Function) f).call(cx, core.global, hobj, args);
                }
            }

            return hobj;
        }
    }

    public Object get(String name, Scriptable start) {
        if (!initialized  && !"prototype".equals(name)) {
            // trigger prototype compilation on static
            // constructor property access
            initialized = true;
            core.getPrototype(getFunctionName());
        }
        return super.get(name, start);
    }

    class GetById extends BaseFunction {

        /**
         * Retrieve any persistent AxiomObject by type name and id.
         *
         * @return the AxiomObject or null if it doesn't exist
         */
        public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            if (args.length < 1 || args.length > 2)
                throw new IllegalArgumentException("Wrong number of arguments in getById()");
            // If second argument is provided, use it as type name.
            // Otherwise, use our own type name.
            String type = args.length == 1 ?
                    AxiomObjectCtor.this.getFunctionName() :
                    Context.toString(args[1]);

            DbMapping dbmap = core.app.getDbMapping(type);
            if (dbmap == null)
                return null;
            Object node = null;
            try {
                DbKey key = new DbKey(dbmap, Context.toString(args[0]), core.getApplication().getCurrentRequestEvaluator().getLayer());
                node = core.app.getNodeManager().getNode(key);
            } catch (Exception x) {
                return null;
            }
            return node == null ? null : Context.toObject(node, this);
        }
    }

}
