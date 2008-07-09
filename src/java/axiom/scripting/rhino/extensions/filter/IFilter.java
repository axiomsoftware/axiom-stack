package axiom.scripting.rhino.extensions.filter;

import org.mozilla.javascript.Scriptable;

public interface IFilter {
    
    public void jsFunction_setAnalyzer(Object analyzer);
    public String jsFunction_getAnalyzer();
    public boolean isCached();
    
}