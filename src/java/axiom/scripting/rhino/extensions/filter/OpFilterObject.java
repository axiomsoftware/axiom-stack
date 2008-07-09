package axiom.scripting.rhino.extensions.filter;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.*;

import org.mozilla.javascript.*;

abstract public class OpFilterObject extends ScriptableObject implements IFilter {
    
    IFilter[] filters = null;
    private String analyzer = null;
    boolean cached = false; 
    
    public IFilter[] getFilters() {
        return filters;
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