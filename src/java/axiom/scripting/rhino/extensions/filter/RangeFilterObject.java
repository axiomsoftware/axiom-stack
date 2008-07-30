package axiom.scripting.rhino.extensions.filter;

import java.lang.reflect.Method;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * The JavaScript RangeFilter class, for passing in as a filter parameter to the Query API.
 * The RangeFilter searches the input field between the input start and end ranges.  
 * For example, <br><br>
 * 
 * <code>
 * var rf = new RangeFilter('count', 1, 10, true); <br>
 * </code>
 * <br>
 * 
 * This filter would search all objects which have a property of count whose values 
 * range from 1 to 10, inclusive.
 * 
 * @jsconstructor RangeFilter
 * @param {String} property The property to apply the range filter to
 * @param {Number|String} start The start value in the range to search
 * @param {Number|String} end The end value in the range to search
 * @param {Boolean} [inclusive] Whether the start and end ranges are inclusive, defaults to 
 *                              <code> true </code>.
 */
public class RangeFilterObject extends ScriptableObject implements IFilter {

    private String analyzer = null;
    private boolean cached = false;
    
    private String field = null;
    private Object begin = null;
    private Object end = null;
    private boolean inclusive = true;

    protected RangeFilterObject() {
        super();
    }
    
    protected RangeFilterObject(final Object[] args) throws Exception {
        super();
        field = args[0].toString();
        begin = args[1];
        end = args[2];
        
        if(args.length == 4){
	        if(args[3] != null && args[3] instanceof Boolean){
	        	inclusive = ((Boolean)args[3]).booleanValue();
	        }
	        else if(args[3] != null && !(args[3] instanceof Boolean)){
	        	throw new Exception("Fourth arguments to RangeFilter must be of type boolean");        	
	        }
        }
    }
    
    public String getClassName() {
        return "RangeFilter";
    }
    
    public String toString() {
        return "[RangeFilter]";
    }

    public void jsFunction_setAnalyzer(Object analyzer) {
        if (analyzer instanceof String) {
            this.analyzer = (String) analyzer;
        }
    }

    public String jsFunction_getAnalyzer() {
        return this.analyzer;
    }

    public boolean isCached() {
        return this.cached;
    }

    public static RangeFilterObject filterObjCtor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) 
    throws Exception {
        if (args.length == 0) {
            throw new Exception("No arguments specified to the RangeFilter constructor.");
        }
        else if (args.length < 3){
            throw new Exception("Not enough arguments specified to the RangeFilter constructor.");        	
        }
        else if (args.length > 4){
            throw new Exception("Too arguments specified to the RangeFilter constructor.");        	
        }
        else if(args[0] == null || args[1] == null || args[2] == null){
        	throw new Exception("Arguments to RangeFilter may not be null");
        }

        return new RangeFilterObject(args);
    }

    public static void init(Scriptable scope) {
        Method[] methods = RangeFilterObject.class.getMethods();
        ScriptableObject proto = new RangeFilterObject();
        proto.setPrototype(getObjectPrototype(scope));
        final int attributes = READONLY | DONTENUM | PERMANENT;
        
        final int length = methods.length;
        for (int i = 0; i < length; i++) {
            String methodName = methods[i].getName();
            if ("filterObjCtor".equals(methodName)) {
                FunctionObject ctor = new FunctionObject("RangeFilter", methods[i], scope);
                ctor.addAsConstructor(scope, proto);
            } else if (methodName.startsWith("jsFunction_")) {
                methodName = methodName.substring(11);
                FunctionObject func = new FunctionObject(methodName, methods[i], proto);
                proto.defineProperty(methodName, func, attributes);
            }
        }
    }
    
    public String getField(){
    	return field;
    }

    public Object getBegin(){
    	return begin;
    }
    
    public Object getEnd(){
    	return end;
    }
    
    public boolean isInclusive(){
    	return inclusive;
    }
}
