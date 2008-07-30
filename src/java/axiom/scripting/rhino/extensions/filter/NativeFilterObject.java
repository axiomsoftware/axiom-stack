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
 * The JavaScript NativeFilter class, for passing in as a filter parameter to the Query API.
 * A NativeFilter is used to pass in any storage specific filtering to a query.  
 * In the case of the default embedded storage, this would be a Lucene specific query that
 * the query parser would ingest.  In the case of relational storage, this would be what
 * would be found in the WHERE clause, e.g. <code> COLUMN1 == X AND COLUMN2 >= Y </code>.
 * For example, <br><br>
 * <code>
 * app.getObjects("Post", new NativeFilter("title: Company*"));
 * </code>
 * <br>
 * 
 * This will return all Axiom Objects of the Post prototype, with a title that starts 
 * with "Company". 
 * 
 * @jsconstructor NativeFilter
 * @param {String} filter A query filter native to the underlying storage
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

    public NativeFilterObject(Object[] args) throws Exception {
        super();
        if (args[0] instanceof String) {
            nativeQuery = (String) args[0];
        } else if (args[0] instanceof Scriptable && ((Scriptable) args[0]).getClassName().equals("String")) { 
            nativeQuery = ScriptRuntime.toString(args[0]);
        } else {
            throw new Exception("NativeFilter constructor takes only a String as its first argument.");
        }
        
        if (args.length > 1 && args[1] != null && args[1] != Undefined.instance) {
        	if (args[1] instanceof Boolean) {
                this.cached = ((Boolean) args[1]).booleanValue();
            } else if (args[1] instanceof String) {
            	this.analyzerString = (String) args[1];
            } else if (args[1] instanceof Scriptable && ((Scriptable) args[1]).getClassName().equals("String")) {
            	this.analyzerString = ScriptRuntime.toString(args[1]);
            }
        }
        
        if (args.length > 2 && args[2] != null && args[2] != Undefined.instance) {
        	if (args[2] instanceof Boolean) {
        		this.cached = ((Boolean) args[2]).booleanValue();
        	}
        }
        
        if (this.analyzerString == null) {
        	this.analyzerString = DEFAULT_ANALYZER;
        }
        
    	this.analyzer = LuceneManager.getAnalyzer(this.analyzerString);
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
        
        return new NativeFilterObject(args);
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
            this.analyzer = LuceneManager.getAnalyzer(this.analyzerString);
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