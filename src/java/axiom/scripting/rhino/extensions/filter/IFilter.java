package axiom.scripting.rhino.extensions.filter;

public interface IFilter {
    
    public void jsFunction_setAnalyzer(Object analyzer);
    public String jsFunction_getAnalyzer();
    public boolean isCached();
    
}