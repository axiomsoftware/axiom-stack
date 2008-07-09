package axiom.scripting.rhino.extensions.filter;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.apache.lucene.analysis.Analyzer;
import org.mozilla.javascript.*;

import axiom.framework.core.Application;
import axiom.objectmodel.dom.LuceneManager;
import axiom.scripting.rhino.RhinoEngine;
import axiom.util.ResourceProperties;

/** 
 * @jsconstructor NativeFilter
 */
public class NativeFilterObject extends ScriptableObject implements IFilter {

    private String nativeQuery = null;
    private Analyzer analyzer = null;
    private String analyzerString = null;
    boolean cached = false;
    private Scriptable filterProfile = null;
    
    static final String DEFAULT_ANALYZER = "StandardAnalyzer";
    
    protected NativeFilterObject() {
        super();
    }

    public NativeFilterObject(final Object arg, final Object optional1) 
    throws Exception {
        super();
        if (arg instanceof String) {
            nativeQuery = (String) arg;
        } else if (arg instanceof Scriptable && ((Scriptable) arg).getClassName().equals("String")) { 
            nativeQuery = ScriptRuntime.toString(arg);
        } else {
            throw new Exception("NativeFilter constructor takes only a String as its first argument.");
        }
        
        if (optional1 != null && optional1 != Undefined.instance) {
        	if (optional1 instanceof Boolean) {
                this.cached = ((Boolean) optional1).booleanValue();
            }
        }
    }

    public String getClassName() {
        return "NativeFilter";
    }
    
    public String toString() {
        return "[NativeFilter]";
    }
    
    public static NativeFilterObject filterObjCtor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) 
    throws Exception {
        if (args.length == 0 || args[0] == null) {
            throw new Exception("No arguments specified to the NativeFilter constructor.");
        }
        
        if (args[0] == null) {
            throw new Exception("Null argument passed to the NativeFilter constructor.");
        }
        
        return new NativeFilterObject(args[0], args.length > 1 ? args[1] : null);
    }
    
    public static void init(Scriptable scope) {
        Method[] methods = NativeFilterObject.class.getDeclaredMethods();
        ScriptableObject proto = new NativeFilterObject();
        proto.setPrototype(getObjectPrototype(scope));
        final int attributes = READONLY | DONTENUM | PERMANENT;
        
        final int length = methods.length;
        for (int i = 0; i < length; i++) {
            String methodName = methods[i].getName();
            
            if ("filterObjCtor".equals(methodName)) {
                FunctionObject ctor = new FunctionObject("NativeFilter", methods[i], scope);
                ctor.addAsConstructor(scope, proto);
            } else if (methodName.startsWith("jsFunction_")) {
                methodName = methodName.substring(11);
                FunctionObject func = new FunctionObject(methodName, methods[i], proto);
                proto.defineProperty(methodName, func, attributes);
            }
        }
    }
    
    public String getNativeQuery() {
        return nativeQuery;
    }
    
    public void jsFunction_setAnalyzer(Object analyzer) {
        if (analyzer instanceof String) {
            this.analyzerString = (String) analyzer;
        }
    }
    
    public String jsFunction_getAnalyzer() {
    	return this.analyzerString;
    }
    
    public Analyzer getAnalyzer() {
        return this.analyzer;
    }

    public boolean isCached() {
        return this.cached;
    }

    public void jsFunction_setFilterProfile(Object filterProfile) throws Exception{
    	if(filterProfile instanceof Scriptable){
    		this.filterProfile = (Scriptable)filterProfile;
    	} else{
    		throw new Exception("Filter Profile must be of type Scriptable");
    	}
    }

    public Scriptable getFilterProfile(){
    	return this.filterProfile;
    }
   
}