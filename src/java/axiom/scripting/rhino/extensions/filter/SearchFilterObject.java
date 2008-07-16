	package axiom.scripting.rhino.extensions.filter;

import java.lang.reflect.Method;

import org.apache.lucene.analysis.Analyzer;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import axiom.framework.core.Application;
import axiom.objectmodel.dom.LuceneManager;
import axiom.scripting.rhino.RhinoEngine;
//import axiom.scripting.rhino.extensions.filter.NativeFilterObject.SearchProfile;
import axiom.util.ResourceProperties;

/**
 * @jsconstructor SearchFilter
 */
public class SearchFilterObject extends ScriptableObject implements IFilter {

    private Analyzer analyzer = null;
    private boolean cached = false;
    private String searchFilter = null;
    private String analyzerString = null;
    private SearchProfile profile = null;
    private Object filter = null;

    static final String DEFAULT_ANALYZER = "StandardAnalyzer";
    
    protected SearchFilterObject() {
        super();
    }

    public SearchFilterObject(final Object arg, final Object searchProfile) 
    throws Exception {
        super();
        if(arg instanceof String){
        	filter = (String)arg;
        } else if (arg instanceof Scriptable && ((Scriptable) arg).getClassName().equals("String")) {
        	filter = ScriptRuntime.toString(arg);
        } else if (arg instanceof Scriptable){
        	filter = (Scriptable)arg;
        } else {
            throw new Exception("First parameter of a SearchFilter must be a string literal or a javascript object literal.");
        }
        	
        if(searchProfile instanceof String){
        	searchFilter = searchProfile.toString();
        } else if (searchProfile instanceof Scriptable && ((Scriptable) searchProfile).getClassName().equals("String")) {
        	searchFilter = ScriptRuntime.toString(searchProfile);
        } else {
            throw new Exception("Second parameter of a SearchFilter must be a string");
        }
        
    	searchSetup(searchFilter);
    }

    public static SearchFilterObject filterObjCtor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) 
    throws Exception {
	    if (args.length != 2) {
	        throw new Exception("Wrong number of arguments passed to the SearchFilter constructor.");
        } else if (args[0] == null || args[1] == null) {
            throw new Exception("Null argument passed to the SearchFilter constructor.");
        }
        return new SearchFilterObject(args[0], args[1]);
    }

    public static void init(Scriptable scope) {
        Method[] methods = SearchFilterObject.class.getDeclaredMethods();
        ScriptableObject proto = new SearchFilterObject();
        proto.setPrototype(getObjectPrototype(scope));
        final int attributes = READONLY | DONTENUM | PERMANENT;
        
        final int length = methods.length;
        for (int i = 0; i < length; i++) {
            String methodName = methods[i].getName();
            
            if ("filterObjCtor".equals(methodName)) {
                FunctionObject ctor = new FunctionObject("SearchFilter", methods[i], scope);
                ctor.addAsConstructor(scope, proto);
            } else if (methodName.startsWith("jsFunction_")) {
                methodName = methodName.substring(11);
                FunctionObject func = new FunctionObject(methodName, methods[i], proto);
                proto.defineProperty(methodName, func, attributes);
            }
        }
    }

    public void jsFunction_setAnalyzer(Object analyzer) {
        if (analyzer instanceof String) {
            searchSetup((String) analyzer);
        }
    }

    public String jsFunction_getAnalyzer() {
    	return this.analyzerString;
    }

    public String getClassName() {
        return "SearchFilter";
    }

    public String toString() {
        return "[SearchFilter]";
    }

    public boolean isCached() {
        return this.cached;
    }

    public SearchProfile getSearchProfile() {
    	return this.profile;
    }
    
    public Analyzer getAnalyzer() {
        return this.analyzer;
    }
    
    public Object getFilter(){
    	return filter;
    }

    private void searchSetup(String param) {
    	this.analyzerString = param;
    	this.analyzer = LuceneManager.getAnalyzer(param);

    	if (this.analyzer == null) {
    		Application app = ((RhinoEngine) Context.getCurrentContext().getThreadLocal("engine")).getCore().app;
    		ResourceProperties sprops = app.getSearchProperties();
    		this.analyzer = LuceneManager.getAnalyzer(sprops.getProperty(param + ".analyzer", DEFAULT_ANALYZER));
    		String fields = sprops.getProperty(param + ".fields");

    		this.profile = new SearchProfile();
    		if (sprops.getProperty(param + ".filter") != null && sprops.getProperty(param + ".filter.operator") != null) {
    			this.profile.filter = sprops.getProperty(param + ".filter").trim();
        		this.profile.operator = sprops.getProperty(param + ".filter.operator").trim();
    		}
    		
    		if (fields != null && filter instanceof String) {
    			StringBuffer sb = new StringBuffer(fields);
    			int idx;
    			while ((idx = sb.indexOf("{")) > -1) {
    				sb.deleteCharAt(idx);
    			}
    			while ((idx = sb.indexOf("}")) > -1) {
    				sb.deleteCharAt(idx);
    			}
    			fields = sb.toString().trim();
    			String[] pairs = fields.split(",");
    			this.profile.fields = new String[pairs.length];
    			this.profile.boosts = new float[pairs.length];
    			for (int i = 0; i < pairs.length; i++) {
    				String[] pair = pairs[i].split(":");
    				this.profile.fields[i] = pair[0].trim();
    				try {
    					this.profile.boosts[i] = Float.parseFloat(pair[1].trim());
    				} catch (Exception ex) {
    					this.profile.boosts[i] = 1.0f;
    				}
    			}
    		}
    	}
    }
    
    public class SearchProfile {
    	public String[] fields;
    	public float[] boosts;
    	public String filter = null;
    	public String operator = null;
    }


}
