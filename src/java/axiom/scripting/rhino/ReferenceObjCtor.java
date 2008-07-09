package axiom.scripting.rhino;

import java.lang.reflect.Method;

import org.mozilla.javascript.*;

public class ReferenceObjCtor extends FunctionObject {

    RhinoCore core;

    static Method refObjCtor;

    static {
        try {
            refObjCtor = ReferenceObjCtor.class.getMethod("jsConstructor", new Class[] {
                Context.class, Object[].class, Function.class, Boolean.TYPE });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Error getting ReferenceObjCtor.jsConstructor()");
        }
    }
    
    /**
     * Create and install a Reference constructor.
     * Part of this is copied from o.m.j.FunctionObject.addAsConstructor().
     *
     * @param prototype
     */
    public ReferenceObjCtor(RhinoCore core, Scriptable prototype) {
        super("Reference", refObjCtor, core.global);
        this.core = core;
        addAsConstructor(core.global, prototype);
    }

    /**
     *  This method is used as Reference constructor from JavaScript.
     */
    public static Object jsConstructor(Context cx, Object[] args, 
                                        Function ctorObj, boolean inNewExpr)
                         throws Exception {
        ReferenceObjCtor ctor = (ReferenceObjCtor) ctorObj;
        return new Reference(ctor.core, args);
    }
    
}