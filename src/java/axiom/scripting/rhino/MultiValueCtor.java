package axiom.scripting.rhino;

import java.lang.reflect.Method;

import org.mozilla.javascript.*;

public class MultiValueCtor extends FunctionObject {

    RhinoCore core;
    
    static Method mvObjCtor;

    static {
        try {
            mvObjCtor = MultiValueCtor.class.getMethod("jsConstructor", new Class[] {
                Context.class, Object[].class, Function.class, Boolean.TYPE });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Error getting MultiValueCtor.jsConstructor()");
        }
    }
    
    /**
     * Create and install a MultiValue constructor.
     * Part of this is copied from o.m.j.FunctionObject.addAsConstructor().
     *
     * @param prototype
     */
    public MultiValueCtor(RhinoCore core, Scriptable prototype) {
        super("MultiValue", mvObjCtor, core.global);
        this.core = core;
        addAsConstructor(core.global, prototype);
    }

    /**
     *  This method is used as ReferenceList constructor from JavaScript.
     */
    public static Object jsConstructor(Context cx, Object[] args, 
                                        Function ctorObj, boolean inNewExpr)
                         throws JavaScriptException {
        MultiValueCtor ctor = (MultiValueCtor) ctorObj;
        return new MultiValue(args);
    }
    
}