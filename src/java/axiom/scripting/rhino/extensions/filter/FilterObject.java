package axiom.scripting.rhino.extensions.filter;

import java.lang.reflect.*;
import java.util.*;
    
import org.mozilla.javascript.*;

/**
 * The JavaScript Filter class, for passing in as a filter parameter to the Query API.
 * The Filter class encapsulates the basic type of filtering passed to the Query API.
 * Query parameters are specified as a series of key/value pairs indicating the property
 * names and their respective values to query against.  Objects that meet the specified
 * criterion are returned by the Query API.  For example, <br><br>
 * <code>
 * app.getObjects("Post", new Filter({id: "company-picnic"}));
 * </code>
 * <br>
 * 
 * This will return all Axiom Objects of the Post prototype, with an id exactly 
 * matching "company-picnic".
 * 
 * @jsconstructor Filter
 * @param {Object} filter A JavaScript object of key/value pairs indicating the property 
 *                        names and values to query against in the search
 */
public class FilterObject extends ScriptableObject implements IFilter {
    
    private HashMap filters = new HashMap();
    private String analyzer = null;
    boolean cached = false;
    private Scriptable filterProfile = null;
        
    public FilterObject() {
        super();
    }
    
    public FilterObject(final Object arg, final Object optional1, final Object optional2) 
    throws Exception {
        super();
        if (arg instanceof ScriptableObject) {
            final ScriptableObject sobj = (ScriptableObject) arg;
            final Object[] ids = ScriptableObject.getPropertyIds(sobj);
            final int idslength = ids.length;
            
            for (int i = 0; i < idslength; i++) {
                final String id = ids[i].toString();
                final Object currval = ScriptableObject.getProperty(sobj, id);
                
                Object[] arrayelems = null;
                if (currval instanceof NativeArray) {
                    final NativeArray na = (NativeArray) currval;
                    final int nalength = (int) na.getLength();
                    arrayelems = new Object[nalength];
                    for (int j = 0; j < nalength; j++) {
                        Object currelem = NativeArray.getProperty(na, j);
                        if (currelem != null) {
                            arrayelems[j] = currelem;
                        } else {
                            arrayelems[j] = null;
                        }
                    }
                } else if(currval instanceof Number){
            		Number num = (Number)currval;
            		String number = num.toString();
                    arrayelems = new Object[1];
            		arrayelems[0] = number.endsWith(".0") ? number.substring(0, number.length() -2) : number;
                } else {
                    arrayelems = new Object[1];
                    arrayelems[0] = currval;
                }
                
                filters.put(id, arrayelems);
            }            
        } else {
            throw new Exception("Could not create the Filter object, improper constructor arguments specified.");
        }
        
        if (optional1 != null && optional1 instanceof String) {
            this.analyzer = (String) analyzer;
        } else if (optional1 != null && optional1 instanceof Boolean) {
            this.cached = ((Boolean) optional1).booleanValue();
        }
        
        if (optional2 != null && optional2 instanceof Boolean) {
            this.cached = ((Boolean) optional2).booleanValue();
        }
    }
    
    public String getClassName() {
        return "Filter";
    }
    
    public String toString() {
        return "[Filter]";
    }
    
    public static FilterObject filterObjCtor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) 
    throws Exception {
        if (args.length == 0) {
            throw new Exception("No arguments specified to the Filter constructor.");
        }
        
        if (args[0] == null) {
            throw new Exception("Null argument passed to the Filter constructor.");
        }
        
        return new FilterObject(args[0], args.length > 1 ? args[1] : null, 
                args.length > 2 ? args[2] : null);
    }
    
    public static void init(Scriptable scope) {
        Method[] methods = FilterObject.class.getDeclaredMethods();
        ScriptableObject proto = new FilterObject();
        proto.setPrototype(getObjectPrototype(scope));
        final int attributes = READONLY | DONTENUM | PERMANENT;
        
        final int length = methods.length;
        for (int i = 0; i < length; i++) {
            String methodName = methods[i].getName();
            
            if ("filterObjCtor".equals(methodName)) {
                FunctionObject ctor = new FunctionObject("Filter", methods[i], scope);
                ctor.addAsConstructor(scope, proto);
            } else if (methodName.startsWith("jsFunction_")) {
                methodName = methodName.substring(11);
                FunctionObject func = new FunctionObject(methodName, methods[i], proto);
                proto.defineProperty(methodName, func, attributes);
            }
        }
    }
    
    public Set getKeys() {
        return filters.keySet();
    }
    
    public Object getValueForKey(Object key) {
        return filters.get(key);
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

}