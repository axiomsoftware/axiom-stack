package axiom.scripting.rhino;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.store.Directory;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import axiom.framework.core.Application;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.db.PathIndexer;
import axiom.objectmodel.dom.LuceneManager;
import axiom.scripting.rhino.extensions.filter.IFilter;
import axiom.scripting.rhino.extensions.filter.SortObject;
import axiom.util.EhCacheMap;

public abstract class QueryDispatcher {
	
    public static final String SORT_FIELD = "sort";
    public static final String MAXLENGTH_FIELD = "maxlength";
    public static final String UNIQUE_FIELD = "unique";
    public static final String FIELD = "field";
    public static final String LAYER = "layer";
    public static final String VIEW = "view";

	protected Application app;
    protected RhinoCore core;
    protected LuceneManager lmgr;
    protected Directory directory;
    protected PathIndexer pindxr;
    protected QueryParser qparser;
    protected EhCacheMap cache;

	public QueryDispatcher(){
	}

	public QueryDispatcher(Application app, String name) throws Exception {
        this.core = null;
        this.app = app;
        this.lmgr = LuceneManager.getInstance(app);
        this.directory = this.lmgr.getDirectory();
        this.pindxr = app.getPathIndexer();
        this.qparser = new QueryParser(LuceneManager.ID, this.lmgr.buildAnalyzer());
        this.cache = new EhCacheMap();
        this.cache.init(app, name);
    }

	public void finalize() throws Throwable {
		super.finalize();
		this.cache.shutdown();
	}

	public void shutdown() {
		this.cache.shutdown();
	}

	public void setRhinoCore(RhinoCore core) {
		this.core = core;
	}
	
	public RhinoCore getRhinoCore(){
		return this.core;
	}

    public ArrayList jsStringOrArrayToArrayList(Object value) {
        ArrayList list = new ArrayList();
        
        if (value == null || value == Undefined.instance) {
            return list;
        }
        
        if (value instanceof String) {
            list.add(value);
        } else if (value instanceof NativeArray) {
            final NativeArray na = (NativeArray) value;
            final int length = (int) na.getLength();
            for (int i = 0; i < length; i++) {
                Object o = na.get(i, na);
                if (o instanceof String) {
                    list.add(o);
                } 
            }
        }
        
        return list;
    }
    
    protected int getMaxResults(Object options) throws Exception {
    	try{
	    	int numResults = -1;
	    	if (options != null) {
	    		Object value = null;
	    		if (options instanceof Scriptable) {
	    			value = ((Scriptable) options).get(MAXLENGTH_FIELD, (Scriptable) options);
	    		} else if (options instanceof java.util.Map) {
	    			value = ((Map) options).get(MAXLENGTH_FIELD);
	    		}
	    		if (value != null) {
    				if (value instanceof Number) {
    					numResults = ((Number) value).intValue();
    				} else if (value instanceof String) {
    					numResults = Integer.parseInt((String)value);
    				}
    			}
	    	}
	    	return numResults;
    	} catch (Exception e) {
    		throw e;
    	}
    }
    
    protected boolean getUnique(Object options) throws Exception {
    	try {
	    	boolean unique = false;
	    	if (options != null) {
	    		Object value = null;
	    		if (options instanceof Scriptable) {
	    			value = ((Scriptable) options).get(UNIQUE_FIELD, (Scriptable) options);
	    		} else if (options instanceof Map) {
	    			value = ((Map) options).get(UNIQUE_FIELD);
	    		}
	    		if (value != null) {
    				if (value instanceof Boolean) {
    					unique = ((Boolean) value).booleanValue();
    				}
    			}
	    	}
	    	return unique;
    	} catch (Exception e) {
    		throw e;
    	}
    }

    protected String getField(Object options) throws Exception {
    	try {
	    	String field = null;
	    	if (options != null) {
	    		Object value = null;
	    		if (options instanceof Scriptable) {
	    			value = ((Scriptable) options).get(FIELD, (Scriptable) options);
	    		} else if (options instanceof Map) {
	    			value = ((Map) options).get(FIELD);
	    		}
		    	if (value != null) {
					if (value instanceof String) {
						field = (String)value;
					}
		    	}
	    	}
	    	return field;
    	} catch (Exception e) {
    		throw e;
    	}
    }
    
    protected SortObject getSortObject(Object options) throws Exception {
    	try {
    		SortObject theSort = null;
    		if (options != null) {
    			Object value = null;
    			if (options instanceof Scriptable) {
    				value = ((Scriptable) options).get(SORT_FIELD, (Scriptable) options);
    			} else if (options instanceof Map) {
    				value = ((Map) options).get(SORT_FIELD);
    			}
		    	if (value != null) {
		       		if (value instanceof Scriptable) {
		       			if (value instanceof SortObject) {
		       				theSort = (SortObject)value;
		       			} else {
		       				theSort = new SortObject(value);
		       			}
		       		}
		    	}
    		}
    		return theSort;
    	} catch (Exception e) {
    		throw e;
    	}
    }
    
    protected int getLayer(Object options) throws Exception {
		int layer = -1;
    	try {
    		if (options != null) {
    			Object value = null;
    			if (options instanceof Scriptable) {
    				value = ((Scriptable) options).get(LAYER, (Scriptable) options);
    			} else if (options instanceof Map) {
    				value = ((Map) options).get(LAYER);
    			}
		    	if (value != null) {
		       		if (value instanceof Scriptable) {
		       			layer = ScriptRuntime.toInt32(value);
		       		} else if (value instanceof Number) {
		       			layer = ((Number) value).intValue();
		       		} else if (value instanceof String) {
		       			layer = Integer.parseInt((String) value);
		       		} 
		    	}
    		}
    	} catch (Exception e) {
    		throw e;
    	}
    	
    	return layer;
    }
    
    public abstract Object documentToNode(Object d, Scriptable global,
			final int mode, final boolean executedInTransactor) throws Exception;
    
	public abstract ArrayList executeQuery(ArrayList prototypes,
			IFilter filter, Object options) throws Exception;		

    public abstract Object hits(ArrayList prototypes, IFilter filter, 
    		Object options) throws Exception ;
    
    public abstract int getHitCount(ArrayList prototypes, 
    		IFilter filter, Object options) throws Exception;

}
