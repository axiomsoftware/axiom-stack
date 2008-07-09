package axiom.scripting.rhino.extensions.filter;

public class QuerySortField {
    private String field = null;
    private int order = SortObject.ASCENDING;
    
    public QuerySortField(final String field, final int order) {
        this.field = field;
        this.order = order;
    }
    
    public String getField() {
        return this.field;
    }
    
    public int getOrder() {
        return this.order;
    }
    
    public boolean isAscending() {
        return order == SortObject.ASCENDING;
    }
}