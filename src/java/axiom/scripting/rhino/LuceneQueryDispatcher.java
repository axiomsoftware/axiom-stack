/*
 * Axiom Stack Web Application Framework
 * Copyright (C) 2008  Axiom Software Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */
package axiom.scripting.rhino;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RangeQuery;
import org.apache.lucene.search.SimpleQueryFilter;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import axiom.framework.ErrorReporter;
import axiom.framework.core.Application;
import axiom.framework.core.Prototype;
import axiom.framework.core.RequestEvaluator;
import axiom.framework.core.TypeManager;
import axiom.objectmodel.INode;
import axiom.objectmodel.IProperty;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.db.DbMapping;
import axiom.objectmodel.db.Key;
import axiom.objectmodel.db.Node;
import axiom.objectmodel.db.NodeManager;
import axiom.objectmodel.dom.LuceneDataFormatter;
import axiom.objectmodel.dom.LuceneManager;
import axiom.scripting.rhino.extensions.filter.AndFilterObject;
import axiom.scripting.rhino.extensions.filter.FilterObject;
import axiom.scripting.rhino.extensions.filter.IFilter;
import axiom.scripting.rhino.extensions.filter.NativeFilterObject;
import axiom.scripting.rhino.extensions.filter.NotFilterObject;
import axiom.scripting.rhino.extensions.filter.OpFilterObject;
import axiom.scripting.rhino.extensions.filter.OrFilterObject;
import axiom.scripting.rhino.extensions.filter.QuerySortField;
import axiom.scripting.rhino.extensions.filter.RangeFilterObject;
import axiom.scripting.rhino.extensions.filter.SearchFilterObject;
import axiom.scripting.rhino.extensions.filter.SortObject;
import axiom.scripting.rhino.extensions.filter.SearchFilterObject.SearchProfile;
import axiom.util.ResourceProperties;

public class LuceneQueryDispatcher extends QueryDispatcher {
  
    public static final String PATH_FIELD = "path";
    static final String RECURSE_PATH_MARKER = "**";

	public LuceneQueryDispatcher(){
		super();
	}
	
	public LuceneQueryDispatcher(Application app, String name) throws Exception {
    	super(app, name);
    }
    
    public ArrayList executeQuery(ArrayList prototypes, IFilter filter,
			Object options) throws Exception {
		SortObject sort = getSortObject((Scriptable)options);
		ArrayList opaths = getPaths((Scriptable)options);
		int _layer = getLayer((Scriptable) options);
		
		ArrayList results = new ArrayList();
		IndexSearcher searcher = null;
		try {
			int maxResults = getMaxResults(options);
			boolean unique = getUnique(options);
	        String field = getField(options);

	        searcher = this.lmgr.getIndexSearcher();
			Object hits = this.luceneHits(prototypes, filter, sort, maxResults,
					opaths, searcher, null, _layer);

			if (hits == null || hits instanceof Boolean) {
				return results;
			}

			HashSet idSet = null;
			final int opaths_size;
			if ((opaths_size = opaths.size()) > 0) {
				for (int i = 0; i < opaths_size; i++) {
					String path = (String) opaths.get(i);
					ArrayList ids;
					if (path.endsWith("/*")) {
						path = path.substring(0, path.length() - 1);
						INode n = (INode) AxiomObject.traverse(path, this.app);
						ids = this.lmgr.getChildrenIds(n);
					} else {
						if (path.endsWith("/**")) {
							path = path.substring(0, path.length() - 2);
						}
						RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
						int layer = DbKey.LIVE_LAYER;
						if (reqeval != null) {
							layer = reqeval.getLayer();
						}
						ids = this.pindxr.getIds(path, layer);
					}
					if (idSet == null) {
						idSet = new HashSet(ids);
					} else {
						idSet.addAll(ids);
					}
				}
			}
			if (field == null) {
				if (hits instanceof Hits) {
					luceneResultsToNodes((Hits) hits, results, maxResults,
							idSet, _layer);
				} else if (hits instanceof TopDocs) {
					luceneResultsToNodes((TopDocs) hits, searcher, results,
							maxResults, idSet, _layer);
				}
			} else {
				if (hits instanceof Hits) {
					luceneResultsToFields((Hits) hits, results, maxResults,
							idSet, field, unique, _layer);
				} else if (hits instanceof TopDocs) {
					luceneResultsToFields((TopDocs) hits, searcher, results,
							maxResults, idSet, field, unique, _layer);
				}
			}
		} catch (Exception ex) {
			results.clear();
			app.logError(ErrorReporter.errorMsg(this.getClass(), "executeQuery"), ex);
		} finally {
			this.lmgr.releaseIndexSearcher(searcher);
		}

		return results;
	} 

    private Object luceneHits(ArrayList prototypes, IFilter filter,
			SortObject sort, int maxResults, ArrayList opaths,
			IndexSearcher searcher, LuceneQueryParams params, 
			int _layer) throws Exception {
		BooleanQuery primary = new BooleanQuery();
		final String PROTO = LuceneManager.PROTOTYPE;
		final BooleanClause.Occur SHOULD = BooleanClause.Occur.SHOULD;
		final BooleanClause.Occur MUST = BooleanClause.Occur.MUST;
		final TypeManager tmgr = this.app.typemgr;
		final ResourceProperties combined_props = new ResourceProperties();
		Sort lsort = null;

		final int length;
		if (prototypes != null && (length = prototypes.size()) > 0) {
			BooleanQuery proto_query = new BooleanQuery();
			for (int i = 0; i < length; i++) {
				String prototype = (String) prototypes.get(i);
				proto_query.add(new TermQuery(new Term(PROTO, prototype)), SHOULD);

				Prototype proto = tmgr.getPrototype(prototype);
				Stack protos = new Stack();
				while (proto != null) {
					protos.push(proto);
					proto = proto.getParentPrototype();
				}
				final int protoChainSize = protos.size();
				for (int j = 0; j < protoChainSize; j++) {
					proto = (Prototype) protos.pop();
					combined_props.putAll(proto.getTypeProperties());
				}
			}
			primary.add(proto_query, MUST);
		} else {
			ArrayList protoarr = app.getSearchablePrototypes();
			BooleanQuery proto_query = new BooleanQuery();
			for (int i = protoarr.size() - 1; i > -1; i--) {
				String protoName = (String) protoarr.get(i);
				proto_query.add(new TermQuery(new Term(PROTO, protoName)), SHOULD);

				Prototype proto = tmgr.getPrototype(protoName);
				Stack protos = new Stack();
				while (proto != null) {
					protos.push(proto);
					proto = proto.getParentPrototype();
				}
				final int protoChainSize = protos.size();
				for (int j = 0; j < protoChainSize; j++) {
					proto = (Prototype) protos.pop();
					combined_props.putAll(proto.getTypeProperties());
				}
			}
			primary.add(proto_query, MUST);
		}

		parseFilterIntoQuery(filter, primary, combined_props);
		RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
		int layer = _layer;
		if (layer == -1) {
			layer = DbKey.LIVE_LAYER;
			if (reqeval != null) {
				layer = reqeval.getLayer();
			}
		}
		BooleanQuery layerQuery = new BooleanQuery();
		for (int i = 0; i <= layer; i++) {
			layerQuery.add(new TermQuery(new Term(LuceneManager.LAYER_OF_SAVE,
					i + "")), BooleanClause.Occur.SHOULD);
		}
		primary.add(layerQuery, BooleanClause.Occur.MUST);

		BooleanClause[] clauses = primary.getClauses();
		if (clauses == null || clauses.length == 0) {
			throw new Exception(
					"QueryBean.executeQuery(): The lucene query doesn't have any clauses!");
		}

		if (filter.isCached()) {
			SimpleQueryFilter sqf = (SimpleQueryFilter) this.cache.get(primary);
			if (sqf == null) {
				sqf = new SimpleQueryFilter(primary);
				this.cache.put(primary, sqf);
			}
		}
		
		Object ret = null;
		try {
			if (sort != null && (lsort = getLuceneSort(sort)) != null) {
				if (maxResults == -1 || opaths.size() > 0) {
					ret = searcher.search(primary, lsort);
				} else {
					ret = searcher.search(primary, null, maxResults, lsort);
				}
			} else {
				if (maxResults == -1 || opaths.size() > 0) {
					ret = searcher.search(primary);
				} else {
					ret = searcher.search(primary, null, maxResults);
				}
			}
		} catch (Exception ex) {
			app.logError(ErrorReporter.errorMsg(this.getClass(), "luceneHits") 
					+ "Occured on query = " + primary, ex);
		}

		if (ret == null) {
			ret = (maxResults == -1 || opaths.size() > 0) ? new Boolean(true)
					: new Boolean(false);
		}

		if (params != null) {
			params.query = primary;
			params.max_results = maxResults;
			params.sort = lsort;
			params.rprops = combined_props;
		}
		return ret;
	}

    private Object luceneHits(ArrayList prototypes, IFilter ifilter, LuceneQueryParams params) 
    throws Exception {
        BooleanQuery primary = new BooleanQuery();
        final String PROTO = LuceneManager.PROTOTYPE;
        final BooleanClause.Occur SHOULD = BooleanClause.Occur.SHOULD;
        final BooleanClause.Occur MUST = BooleanClause.Occur.MUST;
        final TypeManager tmgr = this.app.typemgr;
        final ResourceProperties combined_props = new ResourceProperties();

        final int length;
        if (prototypes != null && (length = prototypes.size()) > 0) {
            BooleanQuery proto_query = new BooleanQuery();
            for (int i = 0; i < length; i++) {
                String prototype = (String) prototypes.get(i);
                proto_query.add(new TermQuery(new Term(PROTO, prototype)), SHOULD);
                Prototype p = tmgr.getPrototype(prototype);
                if (p != null) {
                    combined_props.putAll(p.getTypeProperties());
                }
            }
            primary.add(proto_query, MUST);
        } 

        Query mergedQuery;
        BooleanClause[] clauses = primary.getClauses();
        if (clauses.length == 0) {
            mergedQuery = params.query;
        } else {
            BooleanQuery tmpQuery = new BooleanQuery();
            tmpQuery.add(params.query, MUST);
            tmpQuery.add(primary, MUST);
            mergedQuery = tmpQuery;
        }
        
        if (params.rprops != null) {
            combined_props.putAll(params.rprops);
        }
        Filter filter = this.getQueryFilter(ifilter, combined_props);
        SimpleQueryFilter sqf = (SimpleQueryFilter) this.cache.get(mergedQuery);
        if (sqf != null) {
            mergedQuery = new TermQuery(new Term("_d", "1"));
            IndexReader reader = params.searcher.getIndexReader();
            filter = new SimpleQueryFilter(filter.bits(reader), sqf.bits(reader));
        }
        
        Object ret = null;
        try {
            IndexSearcher searcher = params.searcher;
            if (params.sort != null) {
                if (params.max_results == -1) {
                    ret = searcher.search(mergedQuery, filter, params.sort);
                } else {
                    ret = searcher.search(mergedQuery, filter, params.max_results, params.sort);
                }
            } else {
                if (params.max_results == -1) {
                    ret = searcher.search(mergedQuery, filter);
                } else {
                    ret = searcher.search(mergedQuery, filter, params.max_results);
                }
            }
        } catch (Exception ex) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "luceneHits") 
            		+ "Occured on query = " + primary, ex);
        } 

        return ret;
    }

    public void luceneResultsToNodes(Hits hits, ArrayList results, int maxResults, HashSet ids, int _layer) 
    throws Exception {
        int hitslen = hits.length();
        if (hitslen > 0) {
            if (maxResults < 0) {
                maxResults = hitslen;
            }
            
            final GlobalObject global = this.core != null ? this.core.global : null;
            final Application app = this.app;

            final int mode; 
            if (_layer != -1) {
            	mode = _layer;
            } else {
            	RequestEvaluator reqeval = app.getCurrentRequestEvaluator();
            	if (reqeval != null) {
            		mode = reqeval.getLayer();
            	} else {
            		mode = DbKey.LIVE_LAYER;
            	}
            }
            
            final String ID = LuceneManager.ID;
            HashSet retrieved = new HashSet();
            NodeManager nmgr = this.app.getNodeManager();
            
            for (int i = 0, count = 0; i < hitslen && count < maxResults; i++) {
                Document doc = hits.doc(i);
                String id = doc.get(ID);
                if (id == null || (ids != null && !ids.contains(id)) || retrieved.contains(id)) {
                	continue;
                }
                
                Key key = new DbKey(this.app.getDbMapping(doc.get(LuceneManager.PROTOTYPE)), id, mode);
                Node node = nmgr.getNode(key); 
                if (node != null) {
                	if (global != null) {
                		results.add(Context.toObject(node, global));
                	} else {
                    	results.add(node);
                	}
                	retrieved.add(id);
                	count++;
                }
            }
        }
    }
    
    public int luceneResultsToNodesLength(Hits hits, int maxResults, HashSet ids, int _layer) 
    throws Exception {
    	int length = 0;
        int hitslen = hits.length();
        if (hitslen > 0) {
            if (maxResults < 0) {
                maxResults = hitslen;
            }
            
            final Application app = this.app;
            final int mode; 
            if (_layer != -1) {
            	mode = _layer;
            } else {
            	RequestEvaluator reqeval = app.getCurrentRequestEvaluator();
            	if (reqeval != null) {
            		mode = reqeval.getLayer();
            	} else {
            		mode = DbKey.LIVE_LAYER;
            	}
            }
            
            final String ID = LuceneManager.ID;
            HashSet retrieved = new HashSet();
            NodeManager nmgr = this.app.getNodeManager();
            
            for (int i = 0, count = 0; i < hitslen && count < maxResults; i++) {
                Document doc = hits.doc(i);
                String id = doc.get(ID);
                if (id == null || (ids != null && !ids.contains(id)) || retrieved.contains(id)) {
                	continue;
                }
                
                retrieved.add(id);
                count++;
                length++;
            }
        }
        
        return length;
    }
    
    public int determineResultsLength(Hits hits) throws IOException {
    	int count = 0;
    	final String ID = LuceneManager.ID;
    	HashSet retrieved = new HashSet();
    	final int length = hits.length();
    	for (int i = 0; i < length; i++) {
    		Document d = hits.doc(i);
    		String id = d.get(ID);
    		if (id == null || retrieved.contains(id)) {
    			continue;
    		}
    		count++;
    		retrieved.add(id);
    	}

    	return count;
    }
    
    public int determineResultsLength(TopDocs hits) throws IOException {
    	return hits.totalHits;
    }

    public void luceneResultsToNodes(TopDocs docs, IndexSearcher searcher, 
                                        ArrayList results, int maxResults, HashSet ids, int _layer) 
    throws Exception {
        int hitslen = docs.scoreDocs.length;
        if (hitslen > 0) {
            if (maxResults < 0) {
                maxResults = hitslen;
            }
            
            final GlobalObject global = this.core != null ? this.core.global : null;
            final Application app = this.app;

            final int mode; 
            if (_layer != -1) {
            	mode = _layer;
            } else {
            	RequestEvaluator reqeval = app.getCurrentRequestEvaluator();
            	if (reqeval != null) {
            		mode = reqeval.getLayer();
            	} else {
            		mode = DbKey.LIVE_LAYER;
            	}
            }
            
            final String ID = LuceneManager.ID;
            NodeManager nmgr = this.app.getNodeManager();
            HashSet retrieved = new HashSet();
            
            for (int i = 0, count = 0; i < hitslen && count < maxResults; i++) {
                Document doc = searcher.doc(docs.scoreDocs[i].doc);
                String id = doc.get(ID);
                if (id == null || (ids != null && !ids.contains(id)) || retrieved.contains(id)) {
                	continue;
                }

                Key key = new DbKey(this.app.getDbMapping(doc.get(LuceneManager.PROTOTYPE)), id, mode);
                Node node = nmgr.getNode(key); 
                if (node != null) {
                	if (global != null) {
                		results.add(Context.toObject(node, global));
                	} else {
                    	results.add(node);
                	}
                	retrieved.add(id);
                	count++;
                }
            }
        }
    }

    public int luceneResultsToNodesLength(TopDocs docs, IndexSearcher searcher, 
    		int maxResults, HashSet ids, int _layer) 
    throws Exception {
    	int length = 0;
    	int hitslen = docs.scoreDocs.length;
    	if (hitslen > 0) {
    		if (maxResults < 0) {
    			maxResults = hitslen;
    		}

    		final Application app = this.app;
    		final int mode; 
    		if (_layer != -1) {
    			mode = _layer;
    		} else {
    			RequestEvaluator reqeval = app.getCurrentRequestEvaluator();
    			if (reqeval != null) {
    				mode = reqeval.getLayer();
    			} else {
    				mode = DbKey.LIVE_LAYER;
    			}
    		}

    		final String ID = LuceneManager.ID;
    		NodeManager nmgr = this.app.getNodeManager();
    		HashSet retrieved = new HashSet();

    		for (int i = 0, count = 0; i < hitslen && count < maxResults; i++) {
    			Document doc = searcher.doc(docs.scoreDocs[i].doc);
    			String id = doc.get(ID);
    			if (id == null || (ids != null && !ids.contains(id)) || retrieved.contains(id)) {
    				continue;
    			}

    			retrieved.add(id);
    			count++;
    			length++;
    		}
    	}
    	
    	return length;
    }

    public void luceneResultsToFields(Hits hits, ArrayList results,
			int maxResults, HashSet ids, String field, boolean unique, int _layer)
			throws Exception {
		int hitslen = hits.length();
		if (hitslen > 0) {
			if (maxResults < 0) {
				maxResults = hitslen;
			}
			final String ID = LuceneManager.ID;
            final int mode; 
            if (_layer != -1) {
            	mode = _layer;
            } else {
            	RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
            	if (reqeval != null) {
            		mode = reqeval.getLayer();
            	} else {
            		mode = DbKey.LIVE_LAYER;
            	}
            }
            
            NodeManager nmgr = this.app.getNodeManager();
            HashSet retrieved = new HashSet();
			for (int i = 0, count = 0; i < hitslen && count < maxResults; i++) {
				Document d = hits.doc(i);
				String id = d.get(ID);
				if (id == null || (ids != null && !ids.contains(id)) || retrieved.contains(id)) {
                	continue;
				}

				DbMapping dbmap = this.app.getDbMapping(d.getField(LuceneManager.PROTOTYPE).stringValue());
				Key key = new DbKey(dbmap, d.getField(LuceneManager.ID).stringValue(), mode);
				INode node = nmgr.getNode(key);
				String f = null;
				if (node != null) {
					f = node.getString(field);
				} 
				if (f == null) {
					f = d.getField(field).stringValue();
				}           
				if (unique) {
					if (!results.contains(f)) {
						results.add(f);
						retrieved.add(id);
						count++;
					}
				} else {
					results.add(f);
					retrieved.add(id);
					count++;
				}
			}
		}
	}

	public void luceneResultsToFields(TopDocs docs, IndexSearcher searcher,
			ArrayList results, int maxResults, HashSet ids, String field,
			boolean unique, int _layer) throws Exception {
		int hitslen = docs.scoreDocs.length;
		if (hitslen > 0) {
			if (maxResults < 0) {
				maxResults = hitslen;
			}
			final String ID = LuceneManager.ID;
            final int mode; 
            if (_layer != -1) {
            	mode = _layer;
            } else {
            	RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
            	if (reqeval != null) {
            		mode = reqeval.getLayer();
            	} else {
            		mode = DbKey.LIVE_LAYER;
            	}
            }
            
            NodeManager nmgr = this.app.getNodeManager();
			HashSet retrieved = new HashSet();
            for (int i = 0, count = 0; i < hitslen && count < maxResults; i++) {
				Document d = searcher.doc(docs.scoreDocs[i].doc);
				String id = d.get(ID);
				if (id == null || (ids != null && !ids.contains(id)) || retrieved.contains(id)) {
					continue;
				}

				DbMapping dbmap = this.app.getDbMapping(d.getField(LuceneManager.PROTOTYPE).stringValue());
				Key key = new DbKey(dbmap, d.getField(LuceneManager.ID).stringValue(), mode);
				INode node = nmgr.getNode(key);
				String f = null;
				if (node != null) {
					f = node.getString(field);
				} 
				if (f == null) {
					f = d.getField(field).stringValue();
				}           
				if (unique) {
					if (!results.contains(f)) {
						results.add(f);
						retrieved.add(id);
						count++;
					}
				} else {
					results.add(f);
					retrieved.add(id);
					count++;
				}
            }
		}
	}

	private void parseFilterIntoQuery(IFilter filter, BooleanQuery primary, 
            ResourceProperties combined_props) 
		throws Exception {
        if (filter instanceof FilterObject) {
            FilterObject fobj = (FilterObject) filter;
            Query filter_query = getFilterQuery(fobj, combined_props);
            String analyzer = filter.jsFunction_getAnalyzer();
            if (analyzer != null) {
                QueryParser qp = new QueryParser(LuceneManager.ID, 
                        this.lmgr.getAnalyzer(analyzer));
                filter_query = qp.parse(filter_query.toString());
                qp = null;
            }
            if (filter_query != null) {
                primary.add(filter_query, BooleanClause.Occur.MUST);
            }
        } else if (filter instanceof NativeFilterObject) {
            NativeFilterObject nfobj = (NativeFilterObject) filter;
            Query native_query = getNativeQuery(nfobj);
            if (native_query != null) {
                primary.add(native_query, BooleanClause.Occur.MUST);
            }
        } else if (filter instanceof OpFilterObject) {
            Query q = parseOpFilter((OpFilterObject) filter, combined_props);
            String analyzer = filter.jsFunction_getAnalyzer();
            if (analyzer != null) {
                QueryParser qp = new QueryParser(LuceneManager.ID, 
                        this.lmgr.getAnalyzer(analyzer));
                q = qp.parse(q.toString());
                qp = null;
            }
            if (q != null) {
                primary.add(q, BooleanClause.Occur.MUST);
            }
        } else if (filter instanceof RangeFilterObject){
        	RangeFilterObject rfobj = (RangeFilterObject)filter;
        	Query q = getRangeQuery(rfobj, combined_props);
            if (q != null) {
                primary.add(q, BooleanClause.Occur.MUST);
            }
        } else if (filter instanceof SearchFilterObject){
        	SearchFilterObject sfobj = (SearchFilterObject) filter;
            Query q = getSearchFilterQuery(sfobj, combined_props);
            if (q != null) {
                primary.add(q, BooleanClause.Occur.MUST);
            }
        }
    }

	private Query getRangeQuery(RangeFilterObject rfobj, ResourceProperties combined_props) throws Exception {
		Query query = null;
    	try{
        	String field = rfobj.getField();
	    	if(combined_props.containsKey(field)){
	    		String range_begin = new String();
	    		String range_end = new String();
	    		Object begin = rfobj.getBegin();
	    		Object end = rfobj.getEnd();
	    		LuceneDataFormatter ldf = lmgr.getDataFormatter();
	        	String type = combined_props.getProperty(field + ".type") == null ? LuceneManager.STRING_FIELD : combined_props.getProperty(field + ".type");
	
	        	if(type.equalsIgnoreCase(LuceneManager.DATE_FIELD)){
	        		range_begin = ldf.formatDate(new Date((long) ScriptRuntime.toNumber(begin)));
	        		range_end = ldf.formatDate(new Date((long) ScriptRuntime.toNumber(end)));
	        	} else if(type.equalsIgnoreCase(LuceneManager.TIME_FIELD)){
	        		range_begin = ldf.formatTime(new Date((long) ScriptRuntime.toNumber(begin)));
	        		range_end = ldf.formatTime(new Date((long) ScriptRuntime.toNumber(end)));
	        	} else if(type.equalsIgnoreCase(LuceneManager.TIMESTAMP_FIELD)){
	        		range_begin = ldf.formatTimestamp(new Date((long) ScriptRuntime.toNumber(begin)));
	        		range_end = ldf.formatTimestamp(new Date((long) ScriptRuntime.toNumber(end)));
	        	} else if(type.equalsIgnoreCase(LuceneManager.FLOAT_FIELD)){
	        		range_begin = ldf.formatFloat(((Number)begin).doubleValue());
	        		range_end = ldf.formatFloat(((Number)end).doubleValue());
	        	} else if(type.equalsIgnoreCase(LuceneManager.SMALL_FLOAT_FIELD)){
	        		range_begin = ldf.formatSmallFloat(((Number)begin).doubleValue());
	        		range_end = ldf.formatSmallFloat(((Number)end).doubleValue());
	        	} else if(type.equalsIgnoreCase(LuceneManager.SMALL_INT_FIELD)){
	        		range_begin = ldf.formatSmallInt(((Number)begin).longValue());
	        		range_end = ldf.formatSmallInt(((Number)end).longValue());
	        	} else if(type.equalsIgnoreCase(LuceneManager.INTEGER_FIELD)){
	        		range_begin = ldf.formatInt(((Number)begin).longValue());
	        		range_end = ldf.formatInt(((Number)end).longValue());
	        	} else if(type.equalsIgnoreCase(LuceneManager.NUMBER_FIELD)){
	        		range_begin = ldf.formatFloat(((Number)begin).doubleValue());
	        		range_end = ldf.formatFloat(((Number)end).doubleValue());
	        	} else if(type.equalsIgnoreCase(LuceneManager.STRING_FIELD)){
	        		range_begin = begin.toString();
	        		range_end = end.toString();
	        	} else {
	        		throw new Exception("Cannot use RangeFilter on fields of type " + type);
	        	}
	        	
	        	Term tbegin = new Term(field, range_begin);
	        	Term tend = new Term(field, range_end);
	        	query = new RangeQuery(tbegin, tend, rfobj.isInclusive());
	    	}
    	}
    	catch(Exception e){
    		throw e;
    	}
        return query;
	}
	
    private Sort getLuceneSort(SortObject sort) {
    	if (sort == null) {
    		return null;
    	}
        QuerySortField[] fields = sort.getSortFields();
        if (fields == null) {
            return null;
        }
        
        final int length = fields.length;
        SortField[] sortFields = new SortField[length];
        for (int i = 0; i < length; i++) {
            String s = fields[i].getField();
            if (s == null) {
                return null;
            }
            sortFields[i] = new SortField(s, SortField.STRING, !fields[i].isAscending());
        }
        
        return new Sort(sortFields);
    }

    
    public Object luceneDocumentToNode(Object d, Scriptable global,
			final int mode, final boolean executedInTransactor)
			throws Exception {
    	Document doc = (Document)d;
        NodeManager nmgr = app.getNodeManager();
        DbMapping dbmap = this.app.getDbMapping(doc.getField(LuceneManager.PROTOTYPE).stringValue());
        Key key = new DbKey(dbmap, doc.getField(LuceneManager.ID).stringValue(), mode);
        axiom.objectmodel.db.Node node = null;
		if (executedInTransactor) {
			node = nmgr.getNodeFromTransaction(key);
		}

		if (node == null) {
			node = nmgr.getNodeFromCache(key);

			if (node == null) {
                node = nmgr.getNode(key);
				if (executedInTransactor) {
					node = nmgr.conditionalCacheUpdate(node);
				}
			}

			if (executedInTransactor) {
				nmgr.conditionalNodeVisit(key, node);
			}
		}

		Object ret = null;

		if (node != null) {
			if (global != null) {
				ret = Context.toObject(node, global);
			} else {
				ret = node;
			}
		}

		return ret;
	}

    
    private Query getFilterQuery(FilterObject fobj, ResourceProperties props) 
    throws Exception {
        final LuceneManager lmgr = this.lmgr;
        final BooleanClause.Occur SHOULD = BooleanClause.Occur.SHOULD;
        final BooleanClause.Occur MUST = BooleanClause.Occur.MUST;
        Query query = null;
        
        try {
            BooleanQuery primary = null;
            Iterator iter = fobj.getKeys().iterator();
            while (iter.hasNext()) {
                if (primary == null) {
                    primary = new BooleanQuery();
                }

                BooleanQuery sub_query = new BooleanQuery();
                String key = iter.next().toString();

                Object[] values = (Object[]) fobj.getValueForKey(key);
                final int vallen = values.length;
                if (vallen > 1) {
                    for (int i = 0; i < vallen; i++) {
                        Query q = createLuceneQuery(key, values[i], lmgr, props);
                        sub_query.add(q, SHOULD);
                    }
                    primary.add(sub_query, MUST);
                } else if (vallen == 1) {
                    primary.add(createLuceneQuery(key, values[0], lmgr, props), MUST);
                }
            }
            query = primary;
        } catch (Exception ex) {
            return null;
        }
        
        return query;
    }

    private Query getNativeQuery(NativeFilterObject nfobj) 
    throws Exception {
    	Query query = null;
    	String qstr = nfobj.getNativeQuery();
    	Analyzer analyzer = nfobj.getAnalyzer();

    	try {
    		if (analyzer != null) {
    			QueryParser qp = new QueryParser(LuceneManager.ID, analyzer);
   				query = qp.parse(qstr);
    			qp = null;
    		} else {
    			synchronized (this.qparser) {
    				query = this.qparser.parse(qstr);
    			}
    		}
    	} catch (Exception ex) {
    		app.logError(ErrorReporter.errorMsg(this.getClass(), "getNativeQuery") 
    				+ "Could not parse the input query.\n\t query = " + qstr, ex);
    		throw ex;
    	}
    	return query;
    }
    
    private Query getSearchFilterQuery(SearchFilterObject sfobj, ResourceProperties props) 
    	throws Exception {
    	BooleanQuery bquery = new BooleanQuery();
    	Query query = null;
    	
    	Analyzer analyzer = sfobj.getAnalyzer();
    	SearchProfile profile = sfobj.getSearchProfile();
    	Object filter = sfobj.getFilter();
    	String qstr = filter instanceof String ? (String)filter : new String();

    	BooleanClause.Occur OCCUR = BooleanClause.Occur.MUST;
        if (profile.operator == null) {
        	OCCUR = BooleanClause.Occur.MUST;
        } else if(profile.operator.equalsIgnoreCase("and")){
            OCCUR = BooleanClause.Occur.MUST;
        } else if(profile.operator.equalsIgnoreCase("or")) {
            OCCUR = BooleanClause.Occur.SHOULD;
        } else if(profile.operator.equalsIgnoreCase("not")) {
            OCCUR = BooleanClause.Occur.MUST_NOT;
        }
        
        try {
    		if (analyzer != null) {
    			QueryParser qp = new QueryParser(LuceneManager.ID, analyzer);
    			if(profile != null && filter instanceof String) {
    				qstr = "\"" + qstr + "\"";
    				BooleanQuery bq = new BooleanQuery();
    				for (int i = 0; i < profile.fields.length; i++) {
    					Query q = qp.parse(profile.fields[i] + ":" + qstr);
    					q.setBoost(profile.boosts[i]);
    					bq.add(q, BooleanClause.Occur.SHOULD);
    				}
        			bquery.add(bq, BooleanClause.Occur.MUST);
    			} else if(profile != null && filter instanceof Scriptable){
        			IFilter spfilter = QueryBean.getFilterFromObject((Scriptable)filter);
    				
        			Query q = null; 
                    if (spfilter instanceof FilterObject) {
                        q = getFilterQuery((FilterObject) spfilter, props);
                    } else if (spfilter instanceof NativeFilterObject) {
                        q = getNativeQuery((NativeFilterObject) spfilter);
                    } else if (spfilter instanceof OpFilterObject) {
                        q = parseOpFilter((OpFilterObject) spfilter, props);
                    } else if (spfilter instanceof RangeFilterObject) {
                    	q = getRangeQuery((RangeFilterObject)spfilter, props);
                    } else if (spfilter instanceof SearchFilterObject) {
                    	q = getSearchFilterQuery((SearchFilterObject)spfilter, props);
                    }
                    
                    if(q != null){
            			bquery.add(q, BooleanClause.Occur.SHOULD);
                    }
    			}
    	    	
    	    	String profileFilter = profile.filter;
    	    	if(profileFilter != null && profileFilter.startsWith("{") && profileFilter.endsWith("}")){
    	    		profileFilter = "new Filter(" + profileFilter + ")";
    	    	}
    	    	
    			Context cx = Context.getCurrentContext();
    			Object result = null;
    			if (profileFilter != null) { 
    				result = cx.evaluateString(RhinoEngine.getRhinoCore(app).getGlobal(),
    					profileFilter, "eval", 1, null);
    			}
    			
    			IFilter spfilter = null;
    			if (result != null) {
    				spfilter = QueryBean.getFilterFromObject(result);
    			}

    			Query q = null; 
                if (spfilter instanceof FilterObject) {
                    q = getFilterQuery((FilterObject) spfilter, props);
                } else if (spfilter instanceof NativeFilterObject) {
                    q = getNativeQuery((NativeFilterObject) spfilter);
                } else if (spfilter instanceof OpFilterObject) {
                    q = parseOpFilter((OpFilterObject) spfilter, props);
                } else if (spfilter instanceof RangeFilterObject) {
                	q = getRangeQuery((RangeFilterObject)spfilter, props);
                } else if (spfilter instanceof SearchFilterObject) {
                	q = getSearchFilterQuery((SearchFilterObject)spfilter, props);
                }
                
                if(q != null){
        			bquery.add(q, OCCUR);
                }

    			query = bquery;
    	    	
    			qp = null;
    		} else {
    			synchronized (this.qparser) {
    				query = this.qparser.parse(qstr);
    			}
    		}
    	} catch (Exception ex) {
    		app.logError(ErrorReporter.errorMsg(this.getClass(), "getSearchFilterQuery") 
    				+ "Could not parse the input query.\n\t query = " + qstr, ex);
    		throw ex;
    	}
    	return query;
    }
    
    private Query _getNativeQuery(String qstr, String analyzer) throws Exception {
        Query query = null;
        try {
            if (analyzer != null) {
                QueryParser qp = new QueryParser(LuceneManager.ID, 
                        this.lmgr.getAnalyzer(analyzer));
                query = qp.parse(qstr);
                qp = null;
            } else {
                synchronized (this.qparser) {
                    query = this.qparser.parse(qstr);
                }
            }
        } catch (Exception ex) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "_getNativeQuery") 
            		+ "Could not parse the input query.\n\t query = " + qstr, ex);
            throw ex;
        }
        return query;
    }
    
    private Query parseOpFilter(OpFilterObject opf, ResourceProperties props) 
    throws Exception {
        IFilter[] filters = opf.getFilters();
        BooleanQuery bq = new BooleanQuery();
        if (filters == null) {
            return bq;
        }

        BooleanClause.Occur OCCUR = BooleanClause.Occur.MUST; // default is AND
        if (opf instanceof AndFilterObject) {
            OCCUR = BooleanClause.Occur.MUST;
        } else if (opf instanceof OrFilterObject) {
            OCCUR = BooleanClause.Occur.SHOULD;
        } else if (opf instanceof NotFilterObject) {
            OCCUR = BooleanClause.Occur.MUST_NOT;
        }
        
        final int length = filters.length;
        for (int i = 0; i < length; i++) {
            Query q = null; 
            if (filters[i] instanceof FilterObject) {
                try {
                    q = getFilterQuery((FilterObject) filters[i], props);
                } catch (Exception ex) {
                    q = null;
                }
            } else if (filters[i] instanceof NativeFilterObject) {
                q = getNativeQuery((NativeFilterObject) filters[i]);
            } else if (filters[i] instanceof OpFilterObject) {
                q = parseOpFilter((OpFilterObject) filters[i], props);
            } else if (filters[i] instanceof RangeFilterObject) {
            	q = getRangeQuery((RangeFilterObject)filters[i], props);
	        } else if (filters[i] instanceof SearchFilterObject) {
	        	q = getSearchFilterQuery((SearchFilterObject)filters[i], props);
	        }
            
            if (q != null) {
                if (OCCUR == BooleanClause.Occur.MUST_NOT) {
                    // every NOT query in lucene must be proceeded with a MUST have query,
                    // since every document in the index will have IDENTITY set to 1, we 
                    // precede the NOT query with something we know every document will have,
                    // that is, IDENTITY set to 1.  (its a hack, i know)
                    bq.add(new TermQuery(new Term(LuceneManager.IDENTITY, "1")), BooleanClause.Occur.MUST);
                }
                bq.add(q, OCCUR);
            }
        }
        
        return bq;
    }

    private Query createLuceneQuery(final String key, final Object value,
			final LuceneManager lmgr, ResourceProperties props)
			throws Exception {
		final int type = LuceneManager.stringToType((String) props.get(key + ".type"));
		final String idx = props.getProperty(key + ".index");
		Query q = null;
		if ((idx == null || LuceneManager.TOKENIZED_INDEX.equalsIgnoreCase(idx))
				&& type == IProperty.STRING && !key.startsWith("_")) {
			q = _getNativeQuery(key + ":\"" + (String) value + "\"",
					(String) props.getProperty(key + ".analyzer"));
		} else {
			q = _createLuceneQuery(key, value, lmgr, type);
		}
		return q;
	}

	private Query _createLuceneQuery(final String key, final Object value,
			final LuceneManager lmgr, final int type) throws Exception {

		if (value == null) {
			throw new Exception("QueryBean.createQuery(): The value specified to the query is null");
		}

		String normalized_value = null;
		switch (type) {

		case IProperty.BOOLEAN:
			normalized_value = lmgr.serializeBoolean(Boolean.parseBoolean(value.toString()));
			break;

		case IProperty.DATE:
			if (value instanceof java.util.Date) {
				normalized_value = lmgr.serializeDate((java.util.Date) value);
			} else if (value instanceof Scriptable) {
				Scriptable s = (Scriptable) value;
				String class_name = s.getClassName();
				if ("Date".equals(class_name)) {
					normalized_value = lmgr.serializeDate(new java.util.Date(
							(long) ScriptRuntime.toNumber(s)));
				}
			}
			break;

		case IProperty.TIME:
			if (value instanceof java.util.Date) {
				normalized_value = lmgr.serializeTime((java.util.Date) value);
			} else if (value instanceof Scriptable) {
				Scriptable s = (Scriptable) value;
				String class_name = s.getClassName();
				if ("Date".equals(class_name)) {
					normalized_value = lmgr.serializeTime(new java.util.Date(
							(long) ScriptRuntime.toNumber(s)));
				}
			}
			break;

		case IProperty.TIMESTAMP:
			if (value instanceof java.util.Date) {
				normalized_value = lmgr
						.serializeTimestamp((java.util.Date) value);
			} else if (value instanceof Scriptable) {
				Scriptable s = (Scriptable) value;
				String class_name = s.getClassName();
				if ("Date".equals(class_name)) {
					normalized_value = lmgr
							.serializeTimestamp(new java.util.Date(
									(long) ScriptRuntime.toNumber(s)));
				}
			}
			break;

		case IProperty.FLOAT:
			normalized_value = lmgr.serializeFloat(Double.parseDouble(value
					.toString()));
			break;

		case IProperty.SMALLFLOAT:
			normalized_value = lmgr.serializeSmallFloat(Double
					.parseDouble(value.toString()));
			break;

		case IProperty.INTEGER:
			normalized_value = lmgr.serializeInt(Long.parseLong(value
					.toString()));
			break;

		case IProperty.SMALLINT:
			normalized_value = lmgr.serializeSmallInt(Long.parseLong(value
					.toString()));
			break;

		case IProperty.STRING:
			if (value instanceof Number && key.equals(LuceneManager.ID)) {
				normalized_value = serializeNumber((Number) value);
			} else if (value instanceof String) {
				normalized_value = (String) value;
			} else if (value instanceof Scriptable && "String".equalsIgnoreCase(((Scriptable) value).getClassName())) {
				normalized_value = ScriptRuntime.toString(value);
			} 
			break;
		
		case IProperty.REFERENCE:
		case IProperty.MULTI_VALUE:
			if (value instanceof String) {
				normalized_value = (String) value;
			} else if (value instanceof Scriptable && "String".equalsIgnoreCase(((Scriptable) value).getClassName())) {
				normalized_value = ScriptRuntime.toString(value);
			} else if (value instanceof AxiomObject) {
				normalized_value = ((AxiomObject) value).getNode().getID();
			} else if (value instanceof INode) {
				normalized_value = ((INode) value).getID();
			}
			break;

		default:
			break;

		}

		if (normalized_value == null) {
			throw new Exception("QueryBean.createQuery(): Unable to normalize the value " + value);
		}

		Query q = null;
		if (type == IProperty.STRING
				&& (normalized_value.indexOf("*") > -1 || normalized_value
						.indexOf("?") > -1)) {
			// might be a wildcard query, so use the query parser to parse the query
			synchronized (this.qparser) {
				q = this.qparser.parse(key + ": " + normalized_value);
			}
		} else {
			q = new TermQuery(new Term(key, normalized_value));
		}

		return q;
	}
	
	private String serializeNumber(Number number) {
		String str = null;
		if (number.doubleValue() - (double) number.longValue() != 0.0) {
    		str = number.toString();
    	} else {
    		Long l = new Long(number.longValue());
    		str = l.toString();
    	}
		return str;
	}

    private Filter getQueryFilter(IFilter filter, ResourceProperties props) {
        BooleanQuery query = new BooleanQuery();
        try {
            this.parseFilterIntoQuery(filter, query, props);
            SimpleQueryFilter sqf = (SimpleQueryFilter) this.cache.get(query);
            if (sqf == null && filter.isCached()) {
                sqf = new SimpleQueryFilter(query);
                this.cache.put(query, sqf);
            } 

            return sqf;
        } catch (Exception ex) {
        }
        return null;
    }
    
    public class LuceneQueryParams {
        public Query query;
        public Sort sort;
        public int max_results;
        public IndexSearcher searcher;
        public ResourceProperties rprops;
    }
    
    private ArrayList getPaths(Object options) throws Exception{
    	try {
            ArrayList opaths = new ArrayList();
            if (options != null) {
            	Object value = null;
            	if (options instanceof Scriptable) {
            		value = ((Scriptable) options).get(PATH_FIELD, (Scriptable) options);
            	} else if (options instanceof Map) {
            		value = ((Map) options).get(PATH_FIELD);
            	}
            	if (value != null) {
            		if (value instanceof Scriptable) {
            			String className = ((Scriptable) value).getClassName();
            			if (className.equals("String")) {
            				opaths.add(ScriptRuntime.toString(value));
            			} else if (className.equals("Array")) { 
            				Scriptable arr = (Scriptable) value;
            				final int arrlen = arr.getIds().length;
            				for (int j = 0; j < arrlen; j++) {
            					opaths.add(arr.get(j, arr));
            				}
            			}
            		} else if (value instanceof String) {
            			opaths.add(value);
            		}
            	}
            }
    		return opaths;
    	}
    	catch(Exception e){
    		throw e;
    	}

    }

    public Object hits(ArrayList prototypes, IFilter filter, Object options) throws Exception {
    	Object results = null;

    	int maxResults = getMaxResults((Scriptable)options);
    	SortObject sort = getSortObject((Scriptable)options);
    	int _layer = getLayer((Scriptable) options);
    	ArrayList opaths = getPaths((Scriptable)options);
    	IndexSearcher searcher = this.lmgr.getIndexSearcher();
    	LuceneQueryParams params = new LuceneQueryParams();
    	params.searcher = searcher;
    	Object hits = luceneHits(prototypes, filter, sort, maxResults, opaths, searcher, params, _layer);

		HashSet idSet = null;
		final int opaths_size;
		if ((opaths_size = opaths.size()) > 0) {
			for (int i = 0; i < opaths_size; i++) {
				String path = (String) opaths.get(i);
				ArrayList ids;
				if (path.endsWith("/*")) {
					path = path.substring(0, path.length() - 1);
					INode n = (INode) AxiomObject.traverse(path, this.app);
					ids = this.lmgr.getChildrenIds(n);
				} else {
					if (path.endsWith("/**")) {
						path = path.substring(0, path.length() - 2);
					}
					RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
					int layer = DbKey.LIVE_LAYER;
					if (reqeval != null) {
						layer = reqeval.getLayer();
					}
					ids = this.pindxr.getIds(path, layer);
				}
				if (idSet == null) {
					idSet = new HashSet(ids);
				} else {
					idSet.addAll(ids);
				}
			}
		}

    	hits = hitsToKeys(hits, params, _layer, idSet);
    	final Object[] args = { this.core, this, hits, params, this.lmgr };
    	results = Context.getCurrentContext().newObject(this.core.getScope(), "LuceneHits", args);

    	return results;
    }


    public Object filterHits(Object prototype, Object filter, Object params) throws Exception {
        IFilter theFilter = QueryBean.getFilterFromObject(filter);  
        LuceneQueryParams lparams = (LuceneQueryParams)params;
        if (theFilter == null) {
            throw new Exception("LuceneQueryDispatcher.filterHits(): Could not determine the query filter.");
        }

    	Object results = null;

        ArrayList embeddedDbProtos = new ArrayList();
        QueryBean.setPrototypeArrays(this.app, prototype, embeddedDbProtos, new ArrayList(), new HashMap<String, ArrayList>());
    	Object hits = luceneHits(embeddedDbProtos, theFilter, lparams);
    	hits = hitsToKeys(hits, lparams, -1, null);
    	final Object[] args = { this.core, this, hits, params, this.lmgr };
        results = Context.getCurrentContext().newObject(this.core.getScope(), "LuceneHits", args);
       
        return results;
    }
    
    public Object documentToNode(Object d, Scriptable global,
			final int mode, final boolean executedInTransactor) throws Exception {
    	return this.luceneDocumentToNode(d, global, mode, executedInTransactor);
    }

    public int filterHitsLength(Object prototype, Object filter, LuceneQueryParams params)
    throws Exception {
        IFilter theFilter = QueryBean.getFilterFromObject(filter);        
        if (theFilter == null) {
            throw new Exception("LuceneQueryDispatcher.filterHits(): Could not determine the query filter.");
        }

        ArrayList embeddedDbProtos = new ArrayList();
        QueryBean.setPrototypeArrays(this.app, prototype, embeddedDbProtos, new ArrayList(), new HashMap<String, ArrayList>());

        Object hits = luceneHits(embeddedDbProtos, theFilter, params);
        return hitsToKeys(hits, params, -1, null).size();
    }

    public Scriptable getReferences(ArrayList sources, ArrayList targets, final int mode)
    throws Exception {
        final GlobalObject global = this.core != null ? this.core.global : null;
        final Application app = this.app;
        
        final String ID_FIELD = LuceneManager.ID;
        final String REF_LIST = LuceneManager.REF_LIST_FIELD;
        final BooleanClause.Occur SHOULD = BooleanClause.Occur.SHOULD;
        final BooleanClause.Occur MUST = BooleanClause.Occur.MUST;
        
        final int sources_size = sources.size();
        final int targets_size = targets.size();

        ArrayList results = new ArrayList();    
        
        BooleanQuery sub_query = new BooleanQuery();
        for (int i = 0; i < sources_size; i++) {
            sub_query.add(new TermQuery(new Term(ID_FIELD, sources.get(i).toString())), SHOULD);
        }
        
        BooleanQuery primary = new BooleanQuery();
        primary.add(sub_query, MUST);
        
        sub_query = new BooleanQuery();
        for (int i = 0; i < targets_size; i++) {
            sub_query.add(new TermQuery(new Term(REF_LIST, targets.get(i).toString())), SHOULD);
        }
        primary.add(sub_query, MUST);
        
        IndexSearcher searcher = null;   
        
        try {
            searcher = this.lmgr.getIndexSearcher();
            Hits hits = searcher.search(primary);
            
            HashSet target_set = new HashSet(targets);
            luceneResultsToReferences(hits, results, target_set, mode);
        } catch (Exception ex) {
            results.clear();
            app.logError(ErrorReporter.errorMsg(this.getClass(), "getReferences") 
            		+ "Occured on query = " + primary, ex);
        } finally {
            this.lmgr.releaseIndexSearcher(searcher);
        }
        
        return Context.getCurrentContext().newArray(global, results.toArray());
    }
    
    private ArrayList hitsToKeys(Object hits, LuceneQueryParams params, int _layer, HashSet ids) 
    throws IOException {
    	ArrayList keys = new ArrayList();
    	RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
    	
    	int layer;
    	if (_layer != -1) { 
    		layer = _layer; 
    	} else { 
    		layer = DbKey.LIVE_LAYER;
    		if (reqeval != null) {
    			layer = reqeval.getLayer();
    		}
    	}
    	
    	if (hits instanceof Hits) {
    		Hits h = (Hits) hits;
    		final int length = h.length();
    		HashSet retrieved = new HashSet();
    		for (int i = 0; i < length; i++) {
    			Document doc = h.doc(i);
    			String id = doc.get(LuceneManager.ID);
    			String prototype = doc.get(LuceneManager.PROTOTYPE);
    			
                if (id == null || (ids != null && !ids.contains(id)) || retrieved.contains(id)) {
//    			if (id == null || retrieved.contains(id)) {
    				continue;
    			}
    			
    			Key key = new DbKey(this.app.getDbMapping(prototype), id, layer);
    			keys.add(key);
    			retrieved.add(id);
    		}
    	} else if (hits instanceof TopDocs) {
    		TopDocs td = (TopDocs) hits;
    		final int length = td.scoreDocs.length;
    		HashSet retrieved = new HashSet();
    		IndexSearcher searcher = params.searcher;
    		for (int i = 0; i < length; i++) {
    			Document doc = searcher.doc(td.scoreDocs[i].doc);
    			String id = doc.get(LuceneManager.ID);
    			String prototype = doc.get(LuceneManager.PROTOTYPE);
    			
                if (id == null || (ids != null && !ids.contains(id)) || retrieved.contains(id)) {
//  			if (id == null || retrieved.contains(id)) {
    				continue;
    			}
    			
    			Key key = new DbKey(this.app.getDbMapping(prototype), id, layer);
    			keys.add(key);
    			retrieved.add(id);
    		}
    	}
    	
    	return keys;
    }

    private void luceneResultsToReferences(final Hits hits,
			final ArrayList results, final HashSet targets, final int mode)
			throws Exception {
		final int hitslen = hits.length();
		final String ID = LuceneManager.ID;
		final String REF_FIELD = LuceneManager.REF_LIST_FIELD;
		final String DELIM = LuceneManager.NULL_DELIM;
		final Context cx = Context.getCurrentContext();
		final GlobalObject global = this.core != null ? this.core.global : null;
		if (global == null) {
			return;
		}

		for (int i = 0; i < hitslen; i++) {
			Document d = hits.doc(i);
			Field f = d.getField(ID);
			if (f == null) {
				continue;
			}

			final String source_id = f.stringValue();

			Field[] ref_fields = d.getFields(REF_FIELD);
			int ref_length = ref_fields != null ? ref_fields.length : 0;
			for (int j = 0; j < ref_length; j++) {
				String[] values = ref_fields[j].stringValue().split(DELIM);
				if (targets.contains(values[0])) {
					final Object[] args = { new DbKey(null, values[0], mode) };
					Object o = cx.newObject(global, "Reference", args);
					Reference relobj = (Reference) o;
					relobj.setSourceKey(new DbKey(null, source_id,
							mode));
					relobj.setSourceProperty(values[1]);
					if (values.length > 2) {
						relobj.setSourceIndex(Integer.parseInt(values[2]));
						if (values.length > 3) {
							relobj.setSourceXPath(values[3]);
						}
					}

					results.add(relobj);
				}
			}
		}
	}
    
    public Object getDirectionalNodesFor(Object o, int direction, Object prototypes, 
    										Object filter, Object options) 
    throws Exception {
        final GlobalObject global = this.core != null ? this.core.global : null;
        final Application app = this.app;
        final LuceneManager lmgr = this.lmgr;
        INode node = null;

        ArrayList objects = null;
        if (o instanceof AxiomObject) {
            objects = new ArrayList();
            node = ((AxiomObject) o).node;
            if (node != null) {
                objects.add(node.getID());
            }
        } else if (o instanceof INode) { 
            objects = new ArrayList();
            node = (INode) o;
            objects.add(node.getID());
        } else if (o instanceof String) {
            objects = this.getNodesByPath((String) o);
        }

        final int size = objects == null ? 0 : objects.size();
        if (size == 0) {
            if (global != null) {
                return Context.getCurrentContext().newArray(global, new Object[0]);
            } else {
                return new Object[0];
            }
        }
        
        int _layer = getLayer(options);
		
        final int mode; 
        if (_layer > -1) {
        	mode = _layer;
        } else {
        	RequestEvaluator req_eval = app.getCurrentRequestEvaluator();
        	if (req_eval != null) {
        		mode = req_eval.getLayer();
        	} else {
        		mode = DbKey.LIVE_LAYER;
        	}
        }
        
        ArrayList protos = new ArrayList();
        if (prototypes != null && prototypes != Scriptable.NOT_FOUND) {
            QueryBean.setPrototypeArrays(this.app, prototypes, protos, new ArrayList(), new HashMap<String, ArrayList>());
        }
        
        IFilter theFilter = QueryBean.getFilterFromObject(filter);
        BooleanQuery primary = new BooleanQuery();
        ResourceProperties props = QueryBean.getResourceProperties(this.app, protos);
		parseFilterIntoQuery(theFilter, primary, props);

        NodeManager nmgr = app.getNodeManager();
        HashMap node_map = new HashMap();
        
        for (int i = 0; i < size; i++) {
            Key[] node_keys = null;
            try {
                node_keys = (direction == 0 ? 
                    lmgr.getTargetNodeIds(objects.get(i).toString(), mode, protos, primary, 
                            getLuceneSort(getSortObject(options))) :
                    lmgr.getSourceNodeIds(objects.get(i).toString(), mode, protos, primary, 
                            getLuceneSort(getSortObject(options))));
            } catch (Exception ex) {
                node_keys = null;
                app.logError(ErrorReporter.errorMsg(this.getClass(), "getDirectionalNodesFor") 
                	+ "Could not retrieve " + (direction == 0 ? "target" : "source") + " nodes", ex);
            }
            
            if (node_keys == null) {
                continue;
            }
            
            int length = node_keys.length;
            for (int j = 0; j < length; j++) {
                Node curr = null;
                try {
                    curr = nmgr.getNode(node_keys[j]);
                } catch (Exception ex) {
                    curr = null;
                    app.logError(ErrorReporter.errorMsg(this.getClass(), "getDirectionalNodesFor") 
                    		+ "Could not get node '" + node_keys[j].getID() + "'.", ex);
                }
                
                if (curr != null) {
                    if (global != null) {
                        node_map.put(node_keys[j], Context.toObject(curr, global));
                    } else {
                        node_map.put(node_keys[j], curr);
                    }
                }
            } 
        }
        
        if (global != null) {
            return Context.getCurrentContext().newArray(core.global, node_map.values().toArray());
        } else {
            return node_map.values().toArray();
        }
    }


    public ArrayList getNodesByPath(String path) {
        ArrayList objects;
        int layer = DbKey.LIVE_LAYER;
        RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
        if (reqeval != null) {
        	layer = reqeval.getLayer();
        }
        if (path.endsWith(RECURSE_PATH_MARKER)) {
            objects = this.pindxr.getIds(path.substring(0, path.indexOf(RECURSE_PATH_MARKER)), layer);
        } else {
            objects = new ArrayList();
            objects.add(this.pindxr.getId(path, layer));
        }
        return objects;
    }

    public int getDirectionalNodesForCount(Object o, int direction, Object prototypes, 
    										Object filter, Object options) 
    throws Exception {
        final Application app = this.app;
        final LuceneManager lmgr = this.lmgr;
        INode node = null;

        ArrayList objects = null;
        if (o instanceof AxiomObject) {
            objects = new ArrayList();
            node = ((AxiomObject) o).node;
            if (node != null) {
                objects.add(node.getID());
            }
        } else if (o instanceof INode) { 
            objects = new ArrayList();
            node = (INode) o;
            objects.add(node.getID());
        } else if (o instanceof String) {
            objects = this.getNodesByPath((String) o);
        }

        final int size = objects == null ? 0 : objects.size();
        if (size == 0) {
            return 0;
        }
        
        int _layer = getLayer((Scriptable) options);
		
        final int mode; 
        if (_layer > -1) {
        	mode = _layer;
        } else {
        	RequestEvaluator req_eval = app.getCurrentRequestEvaluator();
        	if (req_eval != null) {
        		mode = req_eval.getLayer();
        	} else {
        		mode = DbKey.LIVE_LAYER;
        	}
        }

        ArrayList protos = new ArrayList();
        if (prototypes != null && prototypes != Scriptable.NOT_FOUND) {
            QueryBean.setPrototypeArrays(this.app, prototypes, protos, new ArrayList(), new HashMap<String, ArrayList>());
        }
        
        IFilter theFilter = QueryBean.getFilterFromObject(filter);
        BooleanQuery primary = new BooleanQuery();
        ResourceProperties props = QueryBean.getResourceProperties(this.app, protos);
		parseFilterIntoQuery(theFilter, primary, props);

        int count = 0;
        for (int i = 0; i < size; i++) {
            Key[] node_keys = null;
            try {
                node_keys = (direction == 0 ? 
                    lmgr.getTargetNodeIds(objects.get(i).toString(), mode, protos, primary,
                            getLuceneSort(getSortObject(options))) :
                    lmgr.getSourceNodeIds(objects.get(i).toString(), mode, protos, primary, 
                            getLuceneSort(getSortObject(options))));
            } catch (Exception ex) {
            	ex.printStackTrace();
                node_keys = null;
                app.logError(ErrorReporter.errorMsg(this.getClass(), "getDirectionalNodesForCount") 
                    	+ "Could not retrieve " + (direction == 0 ? "target" : "source") + " nodes", ex);
            }
            
            if (node_keys == null) {
                continue;
            }
            
            count += node_keys.length;
        }
        
        return count;
    }

    public int getHitCount(ArrayList prototypes, IFilter filter, Object options) 
    throws Exception {
		ArrayList opaths = getPaths(options);
		IndexSearcher searcher = null;
		int count = 0;
		
		try {
			int maxResults = getMaxResults((Scriptable)options);
			boolean unique = getUnique((Scriptable)options);
			int _layer = getLayer((Scriptable) options);

	        searcher = this.lmgr.getIndexSearcher();
			Object hits = this.luceneHits(prototypes, filter, null, maxResults,
					opaths, searcher, null, _layer);

			if (hits == null || hits instanceof Boolean) {
				return 0;
			}

			HashSet idSet = null;
			final int opaths_size;
			if ((opaths_size = opaths.size()) > 0) {
				for (int i = 0; i < opaths_size; i++) {
					String path = (String) opaths.get(i);
					ArrayList ids;
					if (path.endsWith("/*")) {
						path = path.substring(0, path.length() - 1);
						INode n = (INode) AxiomObject.traverse(path, this.app);
						ids = this.lmgr.getChildrenIds(n);
					} else {
						if (path.endsWith("/**")) {
							path = path.substring(0, path.length() - 2);
						}
						int layer;
						if (_layer != -1) {
							layer = _layer;
						} else {
							RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
							layer = DbKey.LIVE_LAYER;
							if (reqeval != null) {
								layer = reqeval.getLayer();
							}
						}
						ids = this.pindxr.getIds(path, layer);
					}
					if (idSet == null) {
						idSet = new HashSet(ids);
					} else {
						idSet.addAll(ids);
					}
				}
			}

			if (hits instanceof Hits) {
				count = luceneResultsToNodesLength((Hits) hits, maxResults, idSet, _layer);
			} else if (hits instanceof TopDocs) {
				count = luceneResultsToNodesLength((TopDocs) hits, searcher, maxResults, idSet, _layer);
			}
		} catch (Exception ex) {
			app.logError(ErrorReporter.errorMsg(this.getClass(), "getHitCount"), ex);
		} finally {
			this.lmgr.releaseIndexSearcher(searcher);
		}
		
		return count;
	} 

    public ArrayList getVersionFields(Object obj, Object fields, ArrayList prototypes, IFilter filter, Object options) throws Exception{
    	String id = null;		
    	if (obj instanceof String) {
    		id = (String) obj;
    	} else if(obj instanceof INode) {
    		id = ((INode)obj).getID();
    	} else if (obj instanceof Scriptable) {
    		id = ScriptRuntime.toString(obj);
    	} else {
   			id = obj.toString();
    	}
    	
    	Scriptable idfilter = Context.getCurrentContext().newObject(this.core.getScope());
    	idfilter.put(LuceneManager.ID, idfilter, id);
    	IFilter newFilter = AndFilterObject.filterObjCtor(null, new Object[]{filter, idfilter}, null, false);
		
		SortObject sort = getSortObject((Scriptable)options);
		ArrayList opaths = getPaths((Scriptable)options);
		int _layer = getLayer((Scriptable) options);
    	RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
		_layer = (_layer == -1) ? (reqeval != null) ? reqeval.getLayer() : DbKey.LIVE_LAYER : _layer;
		int maxResults = getMaxResults((Scriptable)options);

		IndexSearcher searcher = new IndexSearcher(this.lmgr.getDirectory(), true);

		Object hits = this.luceneHits(prototypes, newFilter, sort, maxResults,
				opaths, searcher, null, _layer);

		ArrayList<Scriptable> versions = new ArrayList<Scriptable>();
		if(hits != null){
			int hitslength = hits instanceof Hits ? ((Hits)hits).length() : ((TopDocs)hits).scoreDocs.length;
	        if (hitslength > 0) {
	            if (maxResults < 0) {
	                maxResults = hitslength;
	            }
	        }
	        for (int i = 0, count = 0; i < hitslength && count < maxResults; i++) {
	            Document doc = hits instanceof Hits ? ((Hits)hits).doc(i) : searcher.doc(((TopDocs)hits).scoreDocs[i].doc);
	            if(doc != null){
	            	ArrayList<String> _fields = new ArrayList<String>();
	            	if(fields instanceof String){
	            		_fields.add((String)fields);
	            	} else if (fields instanceof Scriptable){
	                    String className = ((Scriptable) fields).getClassName();
	                    if (className.equals("String")) {
	                		_fields.add(fields.toString());
	                    } else if (className.equals("Array")) {
	                        Scriptable arr = (Scriptable) fields;
	                        final int arrlen = arr.getIds().length;
	                        for (int j = 0; j < arrlen; j++) {
	                        	_fields.add(arr.get(j, arr).toString());
	                        }
	                    } else{
	                		_fields.add(fields.toString());
	                    }
	            	}

	            	Scriptable version = Context.getCurrentContext().newObject(this.core.getScope());
		            for(int j = 0; j < _fields.size(); j++){
		            	String field = _fields.get(j);
		            	Object value = doc.get(field) != null ? doc.get(field): Undefined.instance;
	            		version.put(field, version, value);
		            }
		            count++;
		            versions.add(version);
	            }
			}
		}

		return versions;
    }
    
}