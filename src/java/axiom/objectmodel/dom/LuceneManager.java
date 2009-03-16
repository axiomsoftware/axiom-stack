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
package axiom.objectmodel.dom;

import java.util.*;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.apache.lucene.search.*;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.*;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import axiom.extensions.trans.TransactionException;
import axiom.framework.ErrorReporter;
import axiom.framework.IPathElement;
import axiom.framework.core.Application;
import axiom.framework.core.Prototype;
import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.*;
import axiom.objectmodel.db.*;
import axiom.scripting.ScriptingEngine;
import axiom.scripting.rhino.FileObject;
import axiom.scripting.rhino.MultiValue;
import axiom.scripting.rhino.Reference;
import axiom.scripting.rhino.XhtmlUtils;
import axiom.scripting.rhino.extensions.XMLSerializer;
import axiom.util.FileUtils;
import axiom.util.ResourceProperties;
import axiom.util.UrlEncoded;
import axiom.util.XmlUtils;


public class LuceneManager{

    private static HashMap _lmMap = new HashMap();
    
	private Application app;
	private Directory directory;
	private IndexWriterManager writerManager;
	private LuceneDataFormatter dataFormatter;
    private IndexOptimizingRunner/*TODO:LuceneOptimizer*/ optimizerThread;
    private IndexSearcher searcher;
    private volatile boolean isSearcherValid = true;
    
	public static final String ID = "_id";
	public static final String PROTOTYPE = "_prototype";
	public static final String NAME = "_name"; 
	public static final String CREATED = "_created";
	public static final String LASTMODIFIED = "_lastmodified";
	public static final String PARENTID = "_parentid";
	public static final String PARENTPROTOTYPE = "_parentproto";
	public static final String ISCHILD = "_ischild";
    public static final String LAYER_OF_SAVE = "_mode";
	public static final String REF_LIST_FIELD = "_REFS";
	public static final String RELATIONAL_CHILDREN = "_relch";
	public static final String LOCATION = "_location";
	public static final String ACCESSNAME = "_accessname";
    public static final String IDENTITY = "_d";

	public static final String DOCTYPE = "doctype";
	public static final String TYPE_PROPS = "PropertiesTypes";

	public static final String NULL_DELIM = "\u0000"; // the null character

	public static final String NODE_MARKER = "__ROBJ__";
	static final String NULL_PARENT = "_NULL_";

	protected static final Field.Store DEFAULT_STORE = Field.Store.YES;
	protected static final Field.Index DEFAULT_INDEX = Field.Index.TOKENIZED;

	protected static final String YES_STORE = "YES";
	protected static final String NO_STORE = "NO";
	protected static final String COMPRESSED_STORE = "COMPRESS";

	public static final String NO_INDEX = "NO";
	public static final String NO_NORMS_INDEX = "NO_NORMS";
	public static final String TOKENIZED_INDEX = "TOKENIZED";
	public static final String UNTOKENIZED_INDEX = "UNTOKENIZED";

	public static final String BOOLEAN_FIELD = "boolean";
	public static final String DATE_FIELD = "date";
	public static final String TIME_FIELD = "time";
    public static final String TIMESTAMP_FIELD = "timestamp";
    public static final String FLOAT_FIELD = "float";
	public static final String SMALL_FLOAT_FIELD = "smallfloat";
    public static final String SMALL_INT_FIELD = "smallint";
    public static final String INTEGER_FIELD = "integer";
	public static final String NUMBER_FIELD = "number";
	public static final String NODE_FIELD = "node";
	public static final String REL_FIELD = "reference";
	public static final String JAVAOBJ_FIELD = "javaobject";
	public static final String STRING_FIELD = "string";
	public static final String MULTI_FIELD = "multivalue";
	public static final String FILE_FIELD = "file";
	public static final String IMAGE_FIELD = "image";
	public static final String XML_FIELD = "xml";
	public static final String XHTML_FIELD = "xhtml";

	private static final int LIVE_MODE = DbKey.LIVE_LAYER;
    
	public static final int LUCENE_VERSION = 8;
    public static final String MIGRATION_CLASS = "axiom.objectmodel.dom.H2Migrator";

	public static synchronized LuceneManager getInstance(Application app) throws Exception {
		if (!_lmMap.containsKey(app.getName())) {
			LuceneManager lm = new LuceneManager(app);
			_lmMap.put(app.getName(), lm);
		}
		LuceneManager lm = (LuceneManager) _lmMap.get(app.getName());
        return lm;
	}
	
	public static synchronized void clearLuceneInstances() {
		_lmMap.clear();
	}
	
    public String dump(){
    	return "dump test";
    }
	
	public synchronized void shutdown() {
        if (this.optimizerThread != null) {
            //TODO:this.optimizerThread.stopOptimizer();
        }
        
        if (this.searcher != null) {
            try {
                this.searcher.close();
            } catch (Exception ex) {
                this.app.logError(ErrorReporter.errorMsg(this.getClass(), "shutdown"), ex);
            }
            this.searcher = null;
        }
        
        IndexObjectsFactory.removeDeletedInfos(this.directory);
        //TODO:IndexObjectsFactory.removeFSSegmentsInfos(this.directory);
        
        if (this.directory != null) {
			try {
				this.directory.close();
			} catch (Exception ignore) {
				ignore.printStackTrace();
			}
			this.directory = null;
		}
        
        _lmMap.remove(app.getName());
	}
    
    public LuceneManager(Application app, boolean checkVersion, boolean initDelInfos) 
    throws Exception {
        this.app = app;  
        this.writerManager = new IndexWriterManager(app, buildAnalyzer(), checkVersion, initDelInfos);
        this.dataFormatter = new LuceneDataFormatter(app.getAppDir());
        this.directory = this.writerManager.getDirectory();
    }
	
	private LuceneManager(Application app) throws Exception { 
		this.app = app;  
		this.writerManager = new IndexWriterManager(app, buildAnalyzer(), true, true);
		this.dataFormatter = new LuceneDataFormatter(app.getAppDir());
		this.directory = this.writerManager.getDirectory();
        try {
            this.searcher = new IndexSearcher(this.directory);
        } catch (IOException ignore) {
            // db doesn't exist yet, so create the searcher the first time a search is done
        }
	}
	
	public Directory getDirectory() {
		return this.directory;
	}
    
	public static PerFieldAnalyzerWrapper buildAnalyzer() {
		PerFieldAnalyzerWrapper analyzer = new PerFieldAnalyzerWrapper(new StandardAnalyzer());
		analyzer.addAnalyzer(REF_LIST_FIELD, new ReferenceAnalyzer());
		return analyzer;
	}

	public IndexWriter updateLucene(File fsdir, ArrayList sNodes, ArrayList uNodes, ArrayList dNodes, final int currentMode) 
	throws DatabaseException {
		IndexWriter fsWriter = null;
        
		try {
			int savesize = sNodes.size();
			ArrayList docids = new ArrayList(savesize);
			ArrayList doclist = new ArrayList(savesize);

			HashMap analyzerMap = new HashMap();
			for (int i = 0; i < savesize; i++) {
				INode node = (INode) sNodes.get(i);
				// have to save the file/image objects first b/c certain properties are 
				// set on these nodes at save time before the Lucene document is 
				// created out of them
				createDocumentFromNode(docids, doclist, node, analyzerMap);
				node = null;
			}

			int updatesize = uNodes.size();
			ArrayList updateids = new ArrayList(updatesize);
			ArrayList updatelist = new ArrayList(updatesize);
            int[] modes = new int[updatesize];
			for (int i = 0; i < updatesize; i++) {
				INode node = (INode) uNodes.get(i);
				createDocumentFromNode(updateids, updatelist, node, analyzerMap);
                if (node instanceof Node) {
                    modes[i] = ((Node) node).getKey().getLayer();
                } else {
                    modes[i] = DbKey.LIVE_LAYER;
                }
				node = null;
			}

			savesize = doclist.size();
			updatesize = updatelist.size();            
			int delsize = dNodes.size();

			if (savesize + updatesize + delsize > 0) {
				try {
					fsWriter = writerManager.getWriter();
					for (int i = 0; i < savesize; i++) {
						String docid = (String) docids.get(i);
						Analyzer analyzer = (Analyzer) analyzerMap.get(docid);
						if (analyzer != null) {
							fsWriter.addDocument(docid, (Document) doclist.get(i), analyzer);
						} else {
							fsWriter.addDocument(docid, (Document) doclist.get(i));
						}
					}
					doclist = null;
					docids = null;

					for (int i = 0; i < updatesize; i++) {
						String docid = (String) updateids.get(i);
                        Analyzer analyzer = (Analyzer) analyzerMap.get(docid);
                        try {
							if (analyzer != null) {
								fsWriter.update(docid, (Document) updatelist.get(i), analyzer);
							} else {
								fsWriter.update(docid, (Document) updatelist.get(i));
							}
						} catch (IOException ioex) {
							if (currentMode > LIVE_MODE || modes[i] > LIVE_MODE) {     
								if (analyzer != null) {
									fsWriter.addDocument(docid, (Document) updatelist.get(i), analyzer);
								} else {
									fsWriter.addDocument(docid, (Document) updatelist.get(i));
								}
							} else {
								throw ioex;
							}
						}
					}

					analyzerMap.clear();
					analyzerMap = null;
					updatelist = null;
					updateids = null;

					for (int i = 0; i < delsize; i++) {
                    	String id = dNodes.get(i).toString();
						try {
                            fsWriter.delete(id);
                        } catch (Exception delex) {
                            if (currentMode != LIVE_MODE && getLayerPart(id) != LIVE_MODE) {
                                throw delex; // only throw if we arent in preview
                            }
                        }
					}

					fsWriter = writerManager.releaseWriter(fsWriter);
				} catch (Exception ex) {
					fsWriter = writerManager.abort(fsWriter);
					throw ex;
				} 
			} 
		} catch (Exception ex) {
			app.logError(ErrorReporter.errorMsg(this.getClass(), "updateLucene"), ex);
			throw new DatabaseException("Error writing the new/modified axiom objects to the Lucene Index");
		} 

		return fsWriter;
	}

	public Node retrieveFromLuceneIndex(String key, final int mode) 
	throws IOException, ObjectNotFoundException {
		return retrieveFromIndex(ID, key, mode);
	}

	public Node retrieveFromIndex(String field, String key, final int mode) 
	throws IOException, ObjectNotFoundException {
		IndexSearcher searcher = null;
		Document doc = null;
		int layerInStorage = mode;
		
        BooleanQuery query = null;
        try {
            searcher = this.getIndexSearcher();
            
            query = new BooleanQuery();
            Query mainQuery = new TermQuery(new Term(field, key));
            query.add(mainQuery, BooleanClause.Occur.MUST); 

            for (int i = mode; i >= LIVE_MODE; i--) {
            	if (i != LIVE_MODE && !isDraftNode(key, i)) {
            		continue;
            	}
            	query = new BooleanQuery();
            	query.add(mainQuery, BooleanClause.Occur.MUST);
            	query.add(new TermQuery(new Term(LuceneManager.LAYER_OF_SAVE, i + "")), BooleanClause.Occur.MUST);
            	Hits hits = searcher.search(query);
            	if (hits.length() > 0) {
            		doc = hits.doc(0);
            		layerInStorage = i;
            		break;
            	}
            }
        } catch (IOException ioex) { 
            if (!("_id".equals(field) && ("0".equals(key) || "1".equals(key) || "2".equals(key)))) {
                app.logError(ErrorReporter.errorMsg(this.getClass(), "retrieveFromIndex") +
                		"Occured on query " + query, ioex);
            }
            doc = null;
        } catch (Exception ex) {
            if (!("_id".equals(field) && ("0".equals(key) || "1".equals(key) || "2".equals(key)))) {
            	app.logError(ErrorReporter.errorMsg(this.getClass(), "retrieveFromIndex") +
                		"Occured on query " + query, ex);
            }
		} finally {
			this.releaseIndexSearcher(searcher);
		}

		if (doc == null) {
			if (this.app.debug()) 
				app.logEvent("LuceneManager.retrieveFromIndex("	+ field + "," + key + "," 
						+ mode + ") executed query [" + query + "] and retrieved 0 documents");
			
			throw new ObjectNotFoundException("No documents exist for key '" + key + "'");
		}
		
		/*if (this.app.debug()) 
			app.logEvent("LuceneManager.retrieveFromIndex(" + field + "," + key + "," 
					+ mode + ") executed query [" + query + "] and got document on layer " 
					+ layerInStorage);*/

		Node node = null;
		try {
			node = docToNode(doc, mode, layerInStorage);
		} catch (IOException ioex) {
			throw ioex;
		} catch (ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception ex) {
			throw new ObjectNotFoundException("Error searching for children of '" + key + "'");
		}

		return node;

	}
	
    public Key retrieveKeyFromIndex(String field, String key, String parentid, 
            final int mode) throws IOException, ObjectNotFoundException {
        IndexSearcher searcher = null;
        Document doc = null;
        BooleanQuery query = null;

        try {
            searcher = this.getIndexSearcher();
        	Query t1 = new TermQuery(new Term(field, key));
        	Query t2 = new TermQuery(new Term(LuceneManager.PARENTID, parentid));
        	for (int i = mode; i >= LIVE_MODE; i--) {
        		query = new BooleanQuery();
        		query.add(t1, BooleanClause.Occur.MUST);
            	query.add(t2, BooleanClause.Occur.MUST);
            	query.add(new TermQuery(new Term(LuceneManager.LAYER_OF_SAVE, i + "")), BooleanClause.Occur.MUST);
            	Hits hits = searcher.search(query);
            	int length = hits.length();
            	if (length > 0) {
            		for (int j = 0; j < length; j++) {
            			Document d = hits.doc(j);
            			String id = d.get(LuceneManager.ID);
            			if ((i == LIVE_MODE || isDraftNode(id, i))
            					&& !idExistsOnHigherLayers(id, i, mode)) {
            				doc = d;
            				break;
            			} 
            		}
            	}
            	if (doc != null) {
            		break;
            	}
        	}
        } catch (Exception ex) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "retrieveKeyFromIndex") +
            		"Occured on query " + query, ex);
        } finally {
            this.releaseIndexSearcher(searcher);
        }

        if (doc == null) {
        	if (this.app.debug()) 
        		app.logEvent("LuceneManager.retrieveKeyFromIndex(" + field + "," + key + "," 
        				+ parentid + "," + mode + ") executed query [" + query 
        				+ "] and didnt find a key");
        	
            throw new ObjectNotFoundException("No documents exist for key '" + key + "'");
        }
        
        DbMapping dbmap = this.app.getNodeManager().getDbMapping(doc.get(PROTOTYPE));
        String id = doc.get(ID);
        DbKey dbkey = new DbKey(dbmap, id, mode);
        
        /*if (this.app.debug()) 
        	app.logEvent("LuceneManager.retrieveKeyFromIndex(" + field + "," + key + "," 
        			+ parentid + "," + mode + ") executed query [" + query 
        			+ "] and got key = " + dbkey);*/
        
        return dbkey;
    }
    
    public Node retrieveFromIndexFixedMode(String key, final int mode) 
	throws IOException, ObjectNotFoundException {
		return this.retrieveFromIndexFixedMode(key, mode, false);
	}
    
    public Node retrieveFromIndexFixedMode(String key, final int mode, final boolean ignoreDraftSettings) 
	throws IOException, ObjectNotFoundException {
		IndexSearcher searcher = null;
		Document doc = null;
		int layerInStorage = mode;

        BooleanQuery query = null;
        try {
        	final boolean isDraftOn = isDraftNode(key, mode);
        	if (isDraftOn || ignoreDraftSettings) {
        		query = new BooleanQuery();
        		Query mainQuery = new TermQuery(new Term(ID, key));
        		query.add(mainQuery, BooleanClause.Occur.MUST); 
        		query.add(new TermQuery(new Term(LuceneManager.LAYER_OF_SAVE, mode + "")), BooleanClause.Occur.MUST);

        		searcher = this.getIndexSearcher();
        		Hits hits = searcher.search(query);

        		if (hits.length() > 0) {
        			doc = hits.doc(0);
        		}
        	}
        } catch (IOException ioex) { 
            if (!("_id".equals(ID) && ("0".equals(key) || "1".equals(key) || "2".equals(key)))) {
                app.logError(ErrorReporter.errorMsg(this.getClass(), "retrieveNodeFromIndexFixedMode") 
                		+ "Occured on query " + query, ioex);
            }
            doc = null;
        } catch (Exception ex) {
            if (!("_id".equals(ID) && ("0".equals(key) || "1".equals(key) || "2".equals(key)))) {
            	app.logError(ErrorReporter.errorMsg(this.getClass(), "retrieveNodeFromIndexFixedMode") 
                 		+ "Occured on query " + query, ex);
            }
		} finally {
			this.releaseIndexSearcher(searcher);
		}

		if (doc == null) {
			if (this.app.debug()) 
				app.logEvent("LuceneManager.retrieveFromIndexFixedMode(" + key + "," + mode 
						+ "," + ignoreDraftSettings + ") executed query [" + query 
						+ "] and retrieved 0 documents");
			
			throw new ObjectNotFoundException("No documents exist for key '" + key + "'");
		}

		/*if (this.app.debug()) 
			app.logEvent("LuceneManager.retrieveFromIndexFixedMode(" + key + "," + mode 
					+ "," + ignoreDraftSettings + ") executed query [" + query 
					+ "] and got document on layer " + layerInStorage);*/
		
		Node node = null;
		try {
			node = docToNode(doc, mode, layerInStorage);
		} catch (IOException ioex) {
			throw ioex;
		} catch (ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception ex) {
			throw new ObjectNotFoundException("Error searching for children of '" + key + "'");
		}

		return node;
	}
    
    private boolean idExistsOnHigherLayers(final String id, final int layer, final int highest) {
    	for (int i = layer + 1; i <= highest; i++) {
    		try {
    			if (this.retrieveFromIndexFixedMode(id, i) != null) {
    				return true;
    			}
    		} catch (Exception ex) {
    			continue;
    		} 
    	}
    	return false;
    }

    protected boolean isDraftNode(String id, final int mode) {
    	RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
    	if (reqeval == null || reqeval.getRequest() == null 
    			|| reqeval.getRequest().getServletRequest() == null) {
    		return false;
    	}
    	String server = reqeval.getRequest().getServletRequest().getServerName();
        if (mode > LIVE_MODE && !isSpecialNode(id) 
                && reqeval.getSession().isDraftIdOn(id, server, mode)) {
            return true;
        }
        return false;
    }

	public static HashMap luceneDocumentToMap(Document doc) {
		HashMap map = new HashMap();

		Enumeration e = doc.fields();
		while (e.hasMoreElements()) {
			Field f = (Field) e.nextElement();
			String fieldname = f.name();
			if (LuceneManager.isSearchOnlyField(fieldname)) {
				map.put(fieldname, undoFields(doc.getFields(fieldname)));
			} else {
				map.put(fieldname, f.stringValue());
			}
		}

		return map;
	}

	public static Object[] undoFields(Field[] fields) {
		final int length = fields.length;
		Object[] arr = new Object[length];
		for (int i = 0; i < length; i++) {
			String[] fielddata = new String[2];
			fielddata[0] = fields[i].name();
			fielddata[1] = fields[i].stringValue();
			arr[i] = fielddata;
		}
		return arr;
	}

	public Node docToNode(final Document doc, final int mode, final int layerInStorage) 
	throws Exception {
		return this.mapToNode(luceneDocumentToMap(doc), mode, layerInStorage);
	}

	public Node mapToNode(final HashMap map) throws Exception {
		return mapToNode(map, DbKey.LIVE_LAYER, DbKey.LIVE_LAYER);
	}

	public Node mapToNode(final HashMap map, final int mode, final int layerInStorage) 
	throws Exception {
		Node node = null;
		String docid = null, name = null, prototype = null, refid = null, refprototype = null;
		long created = -1L, lastModified = -1L;
		Hashtable propMap = null;
		final NodeManager nmgr = this.app.getNodeManager();

		docid = (String) map.remove(ID); 
		name = (String) map.remove(NAME);  
		if (name == null)
			name = docid;
		prototype = (String) map.remove(PROTOTYPE);      

		try {
			created = Long.parseLong((String) map.remove(CREATED));
		} catch (Exception ex) {
			created = -1L;
		}

		try {
			lastModified = Long.parseLong((String) map.remove(LASTMODIFIED));
		} catch (Exception ex) {
			lastModified = -1L;
		}

		refid = (String) map.remove(PARENTID);        
		refprototype = (String) map.remove(PARENTPROTOTYPE);

		if (docid != null && name != null && prototype != null) {
			if (created != -1 && lastModified != -1) {
				node = new Node(name, docid, prototype, nmgr.safe, created, lastModified);
			} else {
				node = new Node(name, docid, prototype, nmgr.safe);  
			}
			
			if (refid != null && refprototype != null && !NULL_PARENT.equals(refid)) {
				node.setParentHandle(makeNodeHandle(nmgr, refid, refprototype, mode));
			}

			getTheChildren(node, (String) map.remove(RELATIONAL_CHILDREN), nmgr, mode, layerInStorage);

            node.setLayer(mode);
			node.setLayerInStorage(layerInStorage);
        }

		if (node == null)
			throw new IOException("Could not create Node object from Lucene Document");

		Iterator iter = map.keySet().iterator();
		Prototype proto = app.typemgr.getPrototype(prototype);

		while (iter.hasNext()) {
			String fieldname = (String) iter.next();
			// if this field has already been read by some other property that needed it,
			// then dont repeat over it
			if (isSearchOnlyField(fieldname)) { 
				continue;
			}

			String fieldvalue = (String) map.get(fieldname);
			axiom.objectmodel.db.Property prop = new axiom.objectmodel.db.Property(fieldname, node);
			boolean add = true;
			String type = getDataType(proto, fieldname);

			switch (stringToType(type)) {
            
			case IProperty.BOOLEAN: 
				prop.setBooleanValue(Boolean.parseBoolean(fieldvalue)); 
				break;
			
            case IProperty.DATE: 
				prop.setDateValue(strToDate(fieldvalue)); 
				break;
            
            case IProperty.TIME:
                prop.setDateValue(strToTime(fieldvalue));
                break;
            
            case IProperty.TIMESTAMP:
                prop.setDateValue(strToTimestamp(fieldvalue));
                break;
			
            case IProperty.FLOAT: 
            case IProperty.SMALLFLOAT:
                double d = 0.0;
                try {
                    d = Double.parseDouble(fieldvalue);
                } catch (Exception nfe) {
                    this.app.logError(ErrorReporter.errorMsg(this.getClass(), "mapToNode") 
                    		+ "Could not assign " + fieldname 
                            + " to " + fieldvalue + " because could not be parsed into a double", nfe);
                    d = 0.0;
                }
				prop.setFloatValue(d); 
				break;
			
            case IProperty.INTEGER: 
            case IProperty.SMALLINT:
                long l = 0L;
                try {
                    l = Long.parseLong(fieldvalue);
                } catch (Exception nfe) {
                    this.app.logError(ErrorReporter.errorMsg(this.getClass(), "mapToNode") 
                    		+ "Could not assign " + fieldname 
                            + " to " + fieldvalue + " because could not be parsed into a long", nfe);
                    l = 0L;
                }
				prop.setIntegerValue(l); 
				break;
			
            case IProperty.NODE: 
				NodeHandle handle = generateNodeProp(fieldvalue, nmgr, mode);
				if (handle != null) {
					prop.setNodeHandle(handle); 
				} else {
					add = false;
				}
				break;

            case IProperty.STRING: 
				prop.setStringValue(fieldvalue); 
				break;
			
            case IProperty.REFERENCE:
				Reference rel = generateRefProp(fieldvalue, mode);
				if (rel != null) {
					prop.setReferenceValue(rel);   
				} else {
					add = false;
				}
				break;
			
            case IProperty.MULTI_VALUE:       
				MultiValue mv = generateMultiValueProp(proto, fieldname, fieldvalue, mode);
				if (mv != null) {
					prop.setMultiValue(mv);
				} else {
					add = false;
				}
				break;
			case IProperty.XML:
            case IProperty.XHTML:
				prop.setXMLValue(fieldvalue);
				break;
			
            case IProperty.JAVAOBJECT:
                break;
                
            default: 
				prop.setStringValue(fieldvalue); 
			    break;
			}            

			if (propMap == null) {
				propMap = new Hashtable();
				node.setPropMap(propMap);
			}

			if (add) {
				propMap.put(fieldname, prop);
			}
		}  

		return node;
	}

	public void setPropertiesOnNode(Node node, HashMap props) throws Exception {
		Prototype proto = app.typemgr.getPrototype(node.getPrototype());
		NodeManager nmgr = app.getNodeManager();
		final int mode;
		if (app.getCurrentRequestEvaluator() != null) {
			mode = app.getCurrentRequestEvaluator().getLayer();
		} else {
			mode = DbKey.LIVE_LAYER;
		}

		Iterator iter = props.keySet().iterator();

		while (iter.hasNext()) {
			final String fieldname = (String) iter.next();
			if (isSearchOnlyField(fieldname) || isIgnoredOnSet(fieldname)) {
				continue;
			}

			String fieldvalue = (String) props.get(fieldname);
			String type = getDataType(proto, fieldname);

			switch (stringToType(type)) {
			case IProperty.BOOLEAN: 
				node.setBoolean(fieldname, Boolean.parseBoolean(fieldvalue)); 
				break;
			case IProperty.DATE: 
				node.setDate(fieldname, strToDate(fieldvalue)); 
				break;
			case IProperty.FLOAT: 
				node.setFloat(fieldname, Double.parseDouble(fieldvalue)); 
				break;
			case IProperty.INTEGER: 
				node.setInteger(fieldname, Long.parseLong(fieldvalue)); 
				break;
			case IProperty.JAVAOBJECT: 
				break; // lucene doesnt store java object types
			case IProperty.NODE: 
				Node n = generateNode(fieldvalue, nmgr, mode);
				if (n != null) {
					node.setNode(fieldname, n); 
				}
				break;
			case IProperty.STRING: 
				node.setString(fieldname, fieldvalue); 
				break;
			case IProperty.REFERENCE:
				Reference rel = generateRefProp(fieldvalue, mode);
				if (rel != null) {
					node.setReference(fieldname, rel);   
				}
				break;
			case IProperty.MULTI_VALUE:                
				MultiValue mv = generateMultiValueProp(proto, fieldname, fieldvalue, mode);
				if (mv != null) {
					node.setMultiValue(fieldname, mv);
				}
				break;
			case IProperty.XML:
				node.setXML(fieldname, fieldvalue);
				break;
			default: 
				node.setString(fieldname, fieldvalue); 
			break;
			}
		}
	}

	public boolean isSpecialNode(String id) {
		return "0".equals(id) || "1".equals(id);
	}

	public void runOptimizer() throws Exception {
        String optimizeSwitch = this.app.getProperty("lucene.optimizer", "on");
        if ("off".equalsIgnoreCase(optimizeSwitch)) {
            return;
        }
		/*TODO:this.optimizerThread = new LuceneOptimizer(writerManager.getDirectory(), app, this);
        this.writerManager.setOptimizer(this.optimizerThread.getOptimizer());
        this.optimizerThread.runOptimizingThread();*/
        this.optimizerThread = new IndexOptimizingRunner(this.directory, this.app, this);
        this.optimizerThread.runOptimizingThread();
	}

	public static String[] nodePropEval(String value) {
		try {
			String remainder = value.substring(NODE_MARKER.length());
			return remainder.split(NULL_DELIM);
		} catch (Exception ex) {
			return new String[0];
		}
	}

	private Node generateNode(String nodePropId, NodeManager nmgr, int mode) 
	throws ObjectNotFoundException {
		Node node = null;
		String[] nodeDesc = nodePropEval(nodePropId);

		if (nodeDesc.length == 2) {
			try {
				//node = retrieveFromLuceneIndex(actualId, nmgr, mode);
				DbMapping dbmap = app.typemgr.getPrototype(nodeDesc[1]).getDbMapping();
				node = nmgr.getNode(new DbKey(dbmap, nodeDesc[0], mode));
			} catch (Exception ex) {
				throw new ObjectNotFoundException("No documents exist for key '" + nodeDesc[0] + "'");
			}
		}

		return node;
	}

	private NodeHandle generateNodeProp(String nodePropId, NodeManager nmgr, int mode) 
	throws ObjectNotFoundException {
		NodeHandle handle = null;
		String[] nodeDesc = nodePropEval(nodePropId);
		if (nodeDesc.length == 2) {
			handle = this.makeNodeHandle(nmgr, nodeDesc[0], nodeDesc[1], mode);
		}
		return handle;
	}

	public static boolean isSearchOnlyField(String fieldname) {
		if (fieldname != null) {
			if (fieldname.equalsIgnoreCase(REF_LIST_FIELD) ||
                    fieldname.equalsIgnoreCase(ISCHILD) ||
                    fieldname.equalsIgnoreCase(IDENTITY) ||
                    fieldname.equalsIgnoreCase(LAYER_OF_SAVE)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isIgnoredOnSet(String fieldname) {
		if (fieldname != null) {
			if (fieldname.equals(ID) || fieldname.equals(CREATED) 
					|| fieldname.equals(PROTOTYPE)) {
				return true;
			}
		}
		return false;
	}

	private Reference generateRefProp(final String id, final int mode) throws Exception {
		return new Reference(null, new Object[] { new DbKey(null, id, mode) });
	}

	private MultiValue generateMultiValueProp(Prototype proto, final String fieldname,
			final String fieldvalue, final int mode) 
	throws Exception {
		String typeStr = "string";
		while (proto != null) {
			ResourceProperties props = proto.getTypeProperties();
			typeStr = props.getProperty(fieldname + ".type");
			if (typeStr != null) {
				typeStr = typeStr.substring(typeStr.indexOf("(") + 1, typeStr.indexOf(")")).trim();
				break;
			}
			proto = proto.getParentPrototype();
		}         
		if (typeStr == null) {
			typeStr = STRING_FIELD;
		}
		int type = stringToType(typeStr);

		String[] values = fieldvalue.split(NULL_DELIM);
		int len = values.length;
		ArrayList list = new ArrayList();
		for (int i = 0; i < len; i++) {
            values[i] = values[i].trim();
            if (values[i].length() == 0) {
                continue;
            }
            
			switch (type) {
			case IProperty.BOOLEAN:
				list.add(new Boolean(values[i]));
				break;
			case IProperty.DATE:
				list.add(strToDate(values[i]));
				break;
			case IProperty.FLOAT:
				list.add(new Double(values[i]));
				break;
			case IProperty.INTEGER:
				list.add(new Integer(values[i]));
				break;
			case IProperty.REFERENCE:
				Reference r = generateRefProp(values[i], mode);
				/*if (ref_fields != null) {
                    int length = ref_fields.length;
                    for (int j = 0; j < length; j++) {
                        String[] fdata = (String[]) ref_fields[j];
                        String[] ref_info = fdata[1].split(NULL_DELIM);
                        if (ref_info.length == 4 && values[i].equals(ref_info[0]) 
                                && fieldname.equals(ref_info[1])) {
                            r.setSourceXPath(ref_info[3]);
                            break;
                        }
                    }
                }*/
				list.add(r);
				break;
			case IProperty.STRING:
				list.add(values[i]);
			default:
				break;
			}
		}

		MultiValue mv = null;
		try {
			mv = new MultiValue(list.toArray());
			mv.setValueType(type);
		} catch (Exception ex) {
			mv = null;
		}

		return mv;
	}

	private NodeHandle makeNodeHandle(NodeManager nmgr, String refid, String refprototype, int mode) {
		DbMapping dbmap = null;
		if (refprototype != null) {
			dbmap = nmgr.getDbMapping(refprototype);
		}
		return new NodeHandle(new DbKey(dbmap, refid, mode));

	}

	private static String getFieldValue(String fieldname, Document doc, HashSet unchecked) {
		String value = null;
		Field field = doc.getField(fieldname);
		if (field != null) {
			value = field.stringValue();
			unchecked.remove(field);
		}        
		return value;
	}

	private void getTheChildren(Node parent, String relChildren, NodeManager nmgr, 
			int mode, int layerInStorage)
	throws Exception {
        
		IndexSearcher searcher = null;
        BooleanQuery bq = null;
        
		try {
			searcher = this.getIndexSearcher();
			String pid = parent.getID();

			HashMap ids = new HashMap();
			int length;
			Query query1 = new TermQuery(new Term(ISCHILD, "true"));
			Query query2 = new TermQuery(new Term(PARENTID, pid));
			for (int i = LIVE_MODE; i <= mode; i++) {
				bq = new BooleanQuery();
				bq.add(query1, BooleanClause.Occur.MUST);
				bq.add(query2, BooleanClause.Occur.MUST);
				bq.add(new TermQuery(new Term(LAYER_OF_SAVE, i + "")), BooleanClause.Occur.MUST);
				Hits hits = searcher.search(bq);
				length = hits.length();

				/*if (app.debug()) 
					app.logEvent("LuceneManager.getTheChildren(), parent = " + parent.getKey() 
							+ ", layer = " + mode + ", layerInStorage = " + layerInStorage 
							+ ", executed query " + bq + " which produced " + length 
							+ " results");*/
				
				for (int j = 0; j < length; j++) {
					Document doc = hits.doc(j);
					ids.put(doc.getField(ID).stringValue(), doc.getField(PROTOTYPE).stringValue());
				}
			}
			
			Collection<NodeHandle> subnodes = null;
			length = ids.size();
			if (length > 0) {
				subnodes = parent.createSubnodeList();
				Iterator iter = ids.keySet().iterator();
				while (iter.hasNext()) {
					String id = (String) iter.next();
					NodeHandle handle = makeNodeHandle(nmgr, id, (String) ids.get(id), mode);
					if(subnodes instanceof SubnodeList){
						((SubnodeList)subnodes).addSorted(handle);
					} else {
						subnodes.add(handle);
					}
				}
			}
			ids.clear();
			ids = null;

			if (relChildren != null) {
				String[] charr = relChildren.split(NULL_DELIM);
				if (charr.length > 0 && charr.length % 2 == 0) {
					if (subnodes == null) {
						subnodes = parent.createSubnodeList();
					}
					for (int i = 0; i < charr.length; i += 2) {
						if(subnodes instanceof SubnodeList){
							((SubnodeList)subnodes).addSorted(makeNodeHandle(nmgr, charr[i], charr[i+1], mode));
						} else {
							subnodes.add(makeNodeHandle(nmgr, charr[i], charr[i+1], mode));							
						}
					}
				}
			}
		} catch (IOException ioe) {
			throw new Exception("Searcher failed when attempting to retrieve children of " +
                    "id '" + parent.getID() + "', query = " + bq);
		} finally {
            this.releaseIndexSearcher(searcher);
		}
	}

	private void saveFile(INode node) throws Exception {
	    // its a file or image, commit the contents to storage
	    Object val = node.getJavaObject(FileObject.SELF);
	    if (val instanceof FileObject) {
	        FileObject fobj = (FileObject) val;
            if (node instanceof Node) {
                ((Node) node).unsetInternalProperty(FileObject.SELF);
            }
	        final String tmpPath = fobj == null ? null : fobj.getTmpPath();
	        if (tmpPath != null) {
	            if (!commitToStorage(node, tmpPath)) {
	                throw new Exception("Could not write the File/Image to storage: " + tmpPath);
	            }
	        }
	    }
	}

	public Document createDocument(INode node, HashMap analyzerMap) throws Exception {
		if (node == null)
			throw new Exception("The node to be saved is null");

		String prototype = node.getPrototype();
		if (prototype == null)
			prototype = "AxiomObject";  
        
        if (prototype.equals("File") || prototype.equals("Image")) {
            saveFile(node);
        }

		Enumeration e = null;
		if (node instanceof axiom.objectmodel.db.Node) {
			// a newly constructed db.Node doesn't have a propMap,
			// but returns an enumeration of all it's db-mapped properties
			Hashtable props = ((Node) node).getPropMap();

			if (props != null)
				e = props.keys();
		} else {
			e = node.properties();
		}

		Prototype proto = app.typemgr.getPrototype(prototype);
		ResourceProperties rprops = new ResourceProperties();
		Stack protos = new Stack();
		while (proto != null) {
			protos.push(proto);
			proto = proto.getParentPrototype();
		}
		final int protoChainSize = protos.size();
		for (int i = 0; i < protoChainSize; i++) {
			proto = (Prototype) protos.pop();
			rprops.putAll(proto.getTypeProperties());
		}

		Field.Store store = DEFAULT_STORE;
		Field.Index index = Field.Index.UN_TOKENIZED;

		String idstr = node.getID();
        
		Document doc = new Document();

		doc.add(new Field(ID, idstr, store, index));
		doc.add(new Field(NAME, node.getName(), store, index));

		doc.add(new Field(PROTOTYPE, prototype, store, index));

		doc.add(new Field(CREATED, serializeInt(node.created()), store, index));
		doc.add(new Field(LASTMODIFIED, serializeInt(node.lastModified()), store, index));

		String parentid, parentprototype, accessprop = null;
		INode parent = node.getParent();
		boolean isChild;
		if (parent != null) {
			parentid = parent.getID();
			parentprototype = parent.getPrototype();
			if (parent.get(node.getName()) != null) {
				isChild = false;
			} else {
				isChild = true;
			}
			DbMapping dbmap = app.getPrototypeByName(parent.getPrototype()).getDbMapping();
			if (dbmap != null) {
				Relation rel = dbmap.getSubnodeRelation();
				if (rel != null) {
					accessprop = rel.getAccessName();
				}
			}
 		} else {
			parentid = NULL_PARENT;
			parentprototype = null;
			isChild = false;
		}   

		doc.add(new Field(PARENTID, parentid, store, index));
		if (parentprototype != null) {
			doc.add(new Field(PARENTPROTOTYPE, parentprototype, store, index));
		}
        
		if (isChild) {
			doc.add(new Field(ISCHILD, "true", store, index));
		}
        
        int mode;
        if (node instanceof Node) {
            mode = ((Node) node).getKey().getLayer();
        } else {
            mode = LIVE_MODE;
        }
        doc.add(new Field(LAYER_OF_SAVE, mode + "", store, index));
        
        // this is kind of a hack - lucene requires any NOT query to include a must have
        // query term (e.g. you can search "X" NOT "Y" but you can not search NOT "Y" 
        // by itself).  to overcome this, every single object document in lucene will 
        // have the IDENTITY field set to 1, so any not filter can be proceeded by a 
        // search for IDENTITY equal to 1, since every lucene document in the system will have
        // the IDENTITY field set to 1.  
        doc.add(new Field(IDENTITY, "1", store, index));
        
		if (node instanceof axiom.objectmodel.db.Node) {
			axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) node;
			if (n.relationalNodeAdded()) {
				addRelationalChildrenField(doc, node, store, index);
				n.resetRelationalNodeAdded();
			}
		}

		ArrayList ref_list = new ArrayList();
		if (e != null) {
			while (e.hasMoreElements()) {
				String key = (String) e.nextElement();
				IProperty prop = node.get(key);
				if (prop != null) {
					addToDoc(doc, key, prop, rprops, ref_list, analyzerMap, accessprop);
				}
			}
		}

		int ref_list_size = ref_list.size();
		for (int i = 0; i < ref_list_size; i++) {
			doc.add(new Field(REF_LIST_FIELD, ref_list.get(i).toString(), store, Field.Index.TOKENIZED));
		}

		return doc;
	}

	private void addRelationalChildrenField(Document doc, INode node, 
			Field.Store store, Field.Index index) {
		Enumeration e = node.getSubnodes();
		StringBuffer sb = new StringBuffer();
		while (e.hasMoreElements()) {
			Node n = (Node) e.nextElement();
			if (n.isRelational() && n.isAnonymous()) {
				sb.append(n.getID()).append(NULL_DELIM).append(n.getPrototype()).append(NULL_DELIM);
			}
		}
		if (sb.length() > 0) {
			doc.add(new Field(RELATIONAL_CHILDREN, sb.toString(), store, index));
		}
	}

	private void createDocumentFromNode(ArrayList ids, ArrayList docs, INode node,
			HashMap analyzerMap) 
	throws Exception {
		Document doc = this.createDocument(node, analyzerMap);
		if (doc != null) {
			String key = doc.getField(ID).stringValue() 
						+ DeletedInfos.KEY_SEPERATOR 
						+ doc.getField(LAYER_OF_SAVE).stringValue();
			ids.add(key);
			docs.add(doc);   
		}
	}

	private void addToDoc(Document doc, String key, IProperty prop, 
			ResourceProperties rprops, ArrayList ref_list,
			HashMap analyzerMap, String accessprop) 
	throws Exception {
		if (prop.getValue() == null) {
			return;
		}

		final Field.Store store; 
		final Field.Index index; 
		if (accessprop != null && (key.equals(accessprop) || 
				(this.app.isPropertyFilesIgnoreCase() && key.equalsIgnoreCase(accessprop)))) {
			store = Field.Store.YES;
			index = Field.Index.UN_TOKENIZED;
		} else if (key.startsWith("_") && !key.equals(FileObject.CONTENT)) { 
			store = Field.Store.YES;
			index = Field.Index.UN_TOKENIZED;
		} else {
			store = getStore(rprops, key);
			index = getIndex(rprops, key);
		}
		
		int type = getType(rprops, key);
		if (type < 0) {
			type = prop.getType();
		}

		final Analyzer analyzer = (type == IProperty.STRING) ? getAnalyzer(rprops, key) : null;
		final float boost = getBoost(rprops, key);
		Field f;
		
		switch (type) {        

		case IProperty.BOOLEAN: 
			f = new Field(key, serializeBoolean(prop.getBooleanValue()), store, index);
			if (boost > -1f) {
				f.setBoost(boost);
			}
			doc.add(f);
			break;

		case IProperty.DATE: 
			f = new Field(key, serializeDate(prop.getDateValue()), store, index);
			if (boost > -1f) {
				f.setBoost(boost);
			}
			doc.add(f);
			break;

        case IProperty.TIME:
        	f = new Field(key, serializeTime(prop.getDateValue()), store, index);
        	if (boost > -1f) {
				f.setBoost(boost);
			}
            doc.add(f);
            break;
            
        case IProperty.TIMESTAMP:
        	f = new Field(key, serializeTimestamp(prop.getDateValue()), store, index);
        	if (boost > -1f) {
				f.setBoost(boost);
			}
            doc.add(f);
            break;
            
		case IProperty.FLOAT:
			f = new Field(key, serializeFloat(prop.getFloatValue()), store, index);
			if (boost > -1f) {
				f.setBoost(boost);
			}
			doc.add(f);
			break;
            
        case IProperty.SMALLFLOAT:
        	f = new Field(key, serializeSmallFloat(prop.getFloatValue()), store, index);
        	if (boost > -1f) {
				f.setBoost(boost);
			}
            doc.add(f);
            break;

		case IProperty.INTEGER:
			f = new Field(key, serializeInt(prop.getIntegerValue()), store, index);
			if (boost > -1f) {
				f.setBoost(boost);
			}
			doc.add(f);
			break;

        case IProperty.SMALLINT:
        	f = new Field(key, serializeSmallInt(prop.getIntegerValue()), store, index);
        	if (boost > -1f) {
				f.setBoost(boost);
			}
            doc.add(f);
            break;

		case IProperty.STRING:
			f = new Field(key, prop.getStringValue(), store, index);
			if (boost > -1f) {
				f.setBoost(boost);
			}
			doc.add(f);
			if (analyzer != null) {
				String docid = doc.getField(ID).stringValue() + DeletedInfos.KEY_SEPERATOR + doc.getField(LAYER_OF_SAVE).stringValue();
				PerFieldAnalyzerWrapper ret = 
					(PerFieldAnalyzerWrapper) analyzerMap.get(docid);
				if (ret == null) {
					ret = buildAnalyzer();
					analyzerMap.put(docid, ret);
				}
				ret.addAnalyzer(key, analyzer);
			}
			break;

		case IProperty.NODE:
			INode propNode = prop.getNodeValue();
			if (propNode != null) {
				String value = serializeNodeProp(propNode.getID(), propNode.getPrototype());
				f = new Field(key, value, store, index);
				if (boost > -1f) {
					f.setBoost(boost);
				}
				doc.add(f);
			} 
			break;

		case IProperty.REFERENCE:
			if (prop instanceof axiom.objectmodel.db.Property) {
				axiom.objectmodel.db.Property p = 
					(axiom.objectmodel.db.Property) prop;
				Reference relobj = p.getReferenceValue();
				if (relobj != null) {
					String serialized_ref = serializeReference(relobj);
					f = new Field(key, serialized_ref, store, index);
					if (boost > -1f) {
						f.setBoost(boost);
					}
					doc.add(f);

					StringBuffer sb = new StringBuffer(serialized_ref);
					sb.append(NULL_DELIM).append(key);
					ref_list.add(sb.toString());
				}
			}
			break;

		case IProperty.MULTI_VALUE:
			if (prop instanceof axiom.objectmodel.db.Property) {
				axiom.objectmodel.db.Property p = 
					(axiom.objectmodel.db.Property) prop;
				MultiValue mvobj = p.getMultiValue();
				if (mvobj != null) {
					addMultiValueToDoc(doc, key, mvobj, store, index, ref_list);
				}
			}
			break;

		case IProperty.XML:
			if (prop instanceof axiom.objectmodel.db.Property) {
				axiom.objectmodel.db.Property p = 
					(axiom.objectmodel.db.Property) prop;
				Object xml = p.getXMLValue();
				if (xml != null) {
					f = new Field(key, XmlUtils.objectToXMLString(xml), store, index);
					if (boost > -1f) {
						f.setBoost(boost);
					}
					doc.add(f);
				}
			}
        case IProperty.XHTML:
            if (prop instanceof axiom.objectmodel.db.Property) {
                axiom.objectmodel.db.Property p = 
                    (axiom.objectmodel.db.Property) prop;
                Object xml = p.getXHTMLValue();
                if (xml != null) {
                	f = new Field(key, XmlUtils.objectToXMLString(xml), store, index);
                	if (boost > -1f) {
        				f.setBoost(boost);
        			}
                    doc.add(f);
                    addXhtmlRefs(key, xml, ref_list);
                }
            }
		default:
			break;        
		}
	}
    
    private void addXhtmlRefs(String name, Object xml, ArrayList ref_list) {
        RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
        if (reqeval != null) {
            ScriptingEngine se = reqeval.getScriptingEngine();
            final String DELIM = NULL_DELIM;
            
            String[] links = XhtmlUtils.getXhtmlLinks(xml);
            final int length = links.length;
            for (int i = 0; i < length; i++) {
            	String link = links[i];
            	if (link.indexOf("://") > 0) {
            		// TODO: Store external links in the relational db to be able to
            		// determine broken links to external sites
            		continue; 
            	}

            	INode n = getNodeFromHref(link, se);
            	if (n != null) {
            		StringBuffer sb = new StringBuffer(n.getID());
            		sb.append(DELIM).append(name);
            		ref_list.add(sb.toString());
            	}
            }
        }
    }
    
    private INode getNodeFromHref(String link, ScriptingEngine se) {
        if (link == null) {
            return null;
        }
        
        link = this.app.resolveUrlToPath(link);
        
        String[] pathItems = link.split("/");
        final int length = pathItems.length;

        IPathElement currentElement = null;
        try {
            currentElement = app.getNodeManager().getRootNode();
        } catch (Exception ex) {
            currentElement = null;
        }
        
        for (int i = 0; i < length; i++) {
            if (currentElement == null) {
                return null;
            }
            if (pathItems[i].length() == 0) {
                continue;
            }

            if ((i != length - 1) || !(se.hasFunction(currentElement, pathItems[i]))) {
                currentElement = currentElement.getChildElement(pathItems[i]);
            }
        }
        
        return currentElement instanceof INode ? (INode) currentElement : null;
    }
    
    private String getPathFromLink(String link) {
        StringBuffer sb = new StringBuffer();
        String charset = this.app.getCharset();
        String[] tokens = link.split("/");
        
        int base_uri_tokens = 0;
        String base_uri = this.app.getBaseURI();
        if (base_uri != null && !base_uri.equals("/")) {
            base_uri_tokens = base_uri.split("/").length;
        }
        
        for (int i = 0; i < tokens.length; i++) {
            if (i < base_uri_tokens) {
                continue;
            }
            if (tokens[i].equals("")) {
                continue;
            }
            
            String path_elem;
            try {
                path_elem = UrlEncoded.decode(tokens[i], charset);
            } catch (Exception ex) {
                path_elem = tokens[i];
            }
            
            sb.append("/").append(path_elem);
        }
        
        // append trailing "/" if it is contained in original URI
        if (link.endsWith("/")) {
            sb.append('/');
        }
        
        return sb.toString();
    }

	public String serializeBoolean(boolean bool) {        
		return bool ? "true" : "false";
	}

	public String serializeDate(Date date) {   
        if (date == null) {
            date = new Date();
        }
		return dataFormatter.formatDate(date);
	}
    
    public String serializeTime(Date date) {
        if (date == null) {
            date = new Date();
        }
        return dataFormatter.formatTime(date);
    }
    
    public String serializeTimestamp(Date date) {
        if (date == null) {
            date = new Date();
        }
        return dataFormatter.formatTimestamp(date);
    }

	public String serializeFloat(double fl) {        
		return dataFormatter.formatFloat(fl);
	}

	public String serializeInt(long l) { 
		return dataFormatter.formatInt(l);
	}
    
    public String serializeSmallFloat(double fl) {
        return dataFormatter.formatSmallFloat(fl);
    }
    
    public String serializeSmallInt(long l) {
        return dataFormatter.formatSmallInt(l);
    }

	public String serializeReference(Reference relobj) {
		return relobj.getTargetKey().getID();
	}

	public static String serializeNodeProp(final String id, final String proto) {
		return NODE_MARKER + id + NULL_DELIM + proto;
	}

	private Date strToDate(String str) {
		try {
			return dataFormatter.strToDate(str);
		} catch (Exception ex) {
			return null;
		}
	}
    
    private Date strToTime(String str) {
        try {
            return dataFormatter.strToTime(str);
        } catch (Exception ex) {
            return null;
        }
    }

    private Date strToTimestamp(String str) {
        try {
            return dataFormatter.strToTimestamp(str);
        } catch (Exception ex) {
            return null;
        }
    }
    
	public void addMultiValueToDoc(Document doc, String key, MultiValue mvobj, 
			Field.Store store, Field.Index index, ArrayList ref_list) {
		final int type = mvobj.getValueType();
		final Object[] values = mvobj.getValues();
		final int len = values.length;
		StringBuffer valueBuffer = new StringBuffer();
		final String DELIM = NULL_DELIM;

		for (int i = 0; i < len; i++) {
            if (values[i] == null) {
                continue;
            }
            
			switch (type) {        
			case IProperty.BOOLEAN: 
				valueBuffer.append(serializeBoolean(((Boolean) values[i]).booleanValue()));
				valueBuffer.append(DELIM);
				break;
			case IProperty.DATE: 
				valueBuffer.append(serializeDate((Date) values[i]));
				valueBuffer.append(DELIM);
				break;
			case IProperty.FLOAT:
				valueBuffer.append(serializeFloat(((Float) values[i]).floatValue()));
				valueBuffer.append(DELIM);
				break;
			case IProperty.INTEGER:
				valueBuffer.append(serializeInt(((Integer) values[i]).intValue()));
				valueBuffer.append(DELIM);
				break;
			case IProperty.STRING:
				valueBuffer.append((String) values[i]).append(DELIM);
				break;
			case IProperty.REFERENCE:
				Reference r = (Reference) values[i];
				String serialized_ref = serializeReference(r);
				valueBuffer.append(serialized_ref).append(DELIM);

				StringBuffer sb = new StringBuffer(serialized_ref);
				sb.append(DELIM).append(key).append(DELIM).append(i);
				String xpath;
				if ((xpath = r.getSourceXPath()) != null) {
					sb.append(DELIM).append(xpath);
				}
				ref_list.add(sb.toString());
				break;
			default:
				break;        
			}
		}

		int endBuff = valueBuffer.length();
		if (valueBuffer.length() > 0) {
			endBuff -= NULL_DELIM.length();
		}

		String v;
		if (endBuff == 0) {
			v = "";
		} else {
			v = valueBuffer.substring(0, endBuff);
		}
		doc.add(new Field(key, v, store, Field.Index.TOKENIZED));
	}

	public Key[] getTargetNodeIds(final String id, final int mode, ArrayList protos, 
			BooleanQuery append, Sort sort) 
	throws Exception {
		IndexSearcher searcher = null;
		Document doc = null;
		BooleanQuery query = null;

		try {
			searcher = this.getIndexSearcher();
            String idvalue = id;
            Query id_query = new TermQuery(new Term(ID, idvalue));

            for (int i = mode; i >= LIVE_MODE; i--) {
            	if (i != LIVE_MODE && !isDraftNode(id, mode)) {
            		continue;
            	}
            	query = new BooleanQuery();
            	query.add(id_query, BooleanClause.Occur.MUST);
            	query.add(new TermQuery(new Term(LAYER_OF_SAVE, i + "")), BooleanClause.Occur.MUST);
            	Hits hits = searcher.search(query);
            	
            	/*if (app.debug()) 
            		app.logEvent("LuceneManager.getTargetNodeIds(): id=" + id 
            				+ ",layer=" + mode + " executed query [" + query 
            				+ "] which resulted in " + hits.length() + " hits");*/
            	
            	if (hits.length() > 0) {
            		doc = hits.doc(0);
            		break;
            	}
            }
		} catch (Exception ex) {
			app.logError(ErrorReporter.errorMsg(this.getClass(), "getSourceReferences") 
					+ "Could not retrieve document "
                    + id + " from Lucene index with query = " + (query != null ? query : "null"), ex);
			throw ex;
		} finally {
            this.releaseIndexSearcher(searcher);
		}

		if (doc == null) {
			return new Key[0];
		}

		Field[] fields = doc.getFields(REF_LIST_FIELD);
		int len;
		if ((fields == null) || ((len = fields.length) == 0)) {
			return new Key[0];
		}

		ArrayList<Key> keys = new ArrayList<Key>();
		doc = null;
		for (int i = 0; i < len; i++) {
			doc = null;
			query = new BooleanQuery();
			String refid = getIdFromRefListField(fields[i]);
			query.add(new TermQuery(new Term(ID, refid)), BooleanClause.Occur.MUST);
			BooleanQuery proto_query = null;
			final int sizeOfProtos;
			if ((sizeOfProtos = protos.size()) > 0) {
				proto_query = new BooleanQuery();
				for (int j = 0; j < sizeOfProtos; j++) {
					proto_query.add(new TermQuery(new Term(PROTOTYPE, 
							(String) protos.get(j))), BooleanClause.Occur.SHOULD);
				}
				query.add(proto_query, BooleanClause.Occur.MUST);
			}
			if (append != null && append.getClauses().length > 0) {
	        	query.add(append, BooleanClause.Occur.MUST);
	        }
			for (int j = mode; j >= LIVE_MODE; j--) {
            	if (j != LIVE_MODE && !isDraftNode(refid, mode)) {
            		continue;
            	}
            	query.add(new TermQuery(new Term(LAYER_OF_SAVE, j + "")), BooleanClause.Occur.MUST);
            	Hits hits = searcher.search(query);
            	
            	/*if (app.debug()) 
            		app.logEvent("LuceneManager.getTargetNodeIds() [for retrieving target " +
            				"keys]: id=" + id + ",layer=" + mode + " executed query [" + query 
            				+ "] which resulted in " + hits.length() + " hits");*/
            	
            	if (hits.length() > 0) {
            		doc = hits.doc(0);
            		break;
            	}
            }
			if (doc != null) {
				keys.add(new DbKey(this.app.getDbMapping(doc.get(PROTOTYPE)), doc.get(ID), mode));
			}
		}

		Key[] key_arr = new Key[keys.size()];
		return keys.toArray(key_arr);
	}

	public static String getIdFromRefListField(Field f) {
		String value = f.stringValue();
		return value.substring(0, value.indexOf(NULL_DELIM));
	}

	public Key[] getSourceNodeIds(final String id, final int mode, ArrayList protos, 
			BooleanQuery append, Sort sort) 
	throws Exception {
		IndexSearcher searcher = null;
		Hits hits = null;
		Key[] keys = null;
        BooleanQuery query = null;

		try {
			searcher = this.getIndexSearcher();
			query = new BooleanQuery();
			final int sizeOfProtos;
			if ((sizeOfProtos = protos.size()) > 0) {
				BooleanQuery proto_query = new BooleanQuery();
				for (int i = 0; i < sizeOfProtos; i++) {
					proto_query.add(new TermQuery(new Term(PROTOTYPE, 
							(String) protos.get(i))), BooleanClause.Occur.SHOULD);
				}
				query.add(proto_query, BooleanClause.Occur.MUST);
			}
            
			query.add(new TermQuery(new Term(REF_LIST_FIELD, id)), 
					BooleanClause.Occur.MUST);
            
			if (append != null && append.getClauses().length > 0) {
				query.add(append, BooleanClause.Occur.MUST);
			}
			
			hits = searcher.search(query, sort);
			
			/*if (app.debug())
				app.logEvent("LuceneManager.getSourceNodeIds(): id=" + id + ",layer=" + mode
						+ " executed query [" + query + " which resulted in " 
						+ hits.length() + " hits");*/
			
			int size = hits.length();
			ArrayList<Key> list = new ArrayList<Key>();
			for (int i = 0; i < size; i++) {
				Document doc = hits.doc(i);
				
				if (!isIdInDocumentRefs(doc, id)) {
					continue;
				}
				
				Field id_field = doc.getField(ID);
				Field proto_field = doc.getField(PROTOTYPE);
				Field layer_field = doc.getField(LAYER_OF_SAVE);
				if (layer_field != null) {
					try {
						if (mode < Integer.parseInt(layer_field.stringValue())) {
							continue;
						}
					} catch (Exception nfe) {
					}
				}
				if (id_field != null && proto_field != null) {
					list.add(new DbKey(this.app.getDbMapping(proto_field.stringValue()), 
							id_field.stringValue(), mode));
				}
			}
			
			keys = new Key[list.size()];
			list.toArray(keys);
		} catch (Exception ex) {
			app.logError(ErrorReporter.errorMsg(this.getClass(), "getSourceNodeIds") 
					+ "Could not retrieve document "
                    + id + " from Lucene index with query = " + (query != null ? query : "null"), ex);
			throw ex;
		} finally {
            this.releaseIndexSearcher(searcher);
		}

		return keys;
	}
	
	public static boolean isIdInDocumentRefs(Document doc, String id) {
		Field[] ref_fields = doc.getFields(REF_LIST_FIELD);
		final int ref_length = ref_fields != null ? ref_fields.length : 0;
		for (int i = 0; i < ref_length; i++) {
			String[] values = ref_fields[i].stringValue().split(NULL_DELIM);
			if (id.equals(values[0])) {
				return true;
			}
		}
		return false;
	}

	public static String getDataType(Prototype proto, String fieldname) {
		if (ID.equalsIgnoreCase(fieldname) || PROTOTYPE.equalsIgnoreCase(fieldname)
				|| NAME.equalsIgnoreCase(fieldname) || PARENTID.equalsIgnoreCase(fieldname) 
				|| PARENTPROTOTYPE.equalsIgnoreCase(fieldname)) {

			return STRING_FIELD;
		} else if (CREATED.equalsIgnoreCase(fieldname) || LASTMODIFIED.equalsIgnoreCase(fieldname)) {
			return INTEGER_FIELD;
		} else {
			while (proto != null) {
				ResourceProperties props = proto.getTypeProperties();
				String type = props.getProperty(fieldname + ".type");
				if (type != null) {
					type = type.toLowerCase();
					if (type.startsWith("object") || type.startsWith("collection")) {
						return NODE_FIELD;
					} else if (type.startsWith("multivalue")) {
						return MULTI_FIELD; 
					}                    

					return type;
				}

				proto = proto.getParentPrototype();
			}
		}

		return STRING_FIELD;
	}

	private Field.Store getStore(ResourceProperties rprops, String prop) {
		if (FileObject.CONTENT.equals(prop)) {
			return Field.Store.YES;
		}

		if (rprops == null)
			return DEFAULT_STORE;

		String store = rprops.getProperty(prop + ".store");
		if (store != null) {
			store = store.toUpperCase();
			if (store.equals(YES_STORE)) 
				return Field.Store.YES;
			else if (store.equals(NO_STORE))
				return Field.Store.NO;
			else if (store.equals(COMPRESSED_STORE))
				return Field.Store.COMPRESS;
		}

		return DEFAULT_STORE;
	}

	private Analyzer getAnalyzer(ResourceProperties rprops, String prop) {
		if (rprops == null) {
			return null;
		}

		String analyzer = rprops.getProperty(prop + ".analyzer");
		if (analyzer != null) {
			return this.getAnalyzer(analyzer);
		}

		return null;
	}
	
	private float getBoost(ResourceProperties rprops, String prop) {
		if (rprops == null) {
			return -1f;
		}

		String analyzer = rprops.getProperty(prop + ".boost");
		if (analyzer != null) {
			try {
				return Float.parseFloat(analyzer);
			} catch (Exception ex) {
				return -1f;
			}
		}

		return -1f;
	}

	private Field.Index getIndex(ResourceProperties rprops, String prop) {
		if (FileObject.CONTENT.equals(prop)) {
			return Field.Index.TOKENIZED;
		}

		if (rprops == null)
			return DEFAULT_INDEX;

		String index = rprops.getProperty(prop + ".index");
		if (index != null) {
			index = index.toUpperCase();
			if (index.equals(NO_INDEX))
				return Field.Index.NO;
			else if (index.equals(NO_NORMS_INDEX))
				return Field.Index.NO_NORMS;
			else if (index.equals(TOKENIZED_INDEX))
				return Field.Index.TOKENIZED;
			else if (index.equals(UNTOKENIZED_INDEX))
				return Field.Index.UN_TOKENIZED;
		}

		return DEFAULT_INDEX;
	}

	private int getType(ResourceProperties rprops, String prop) {
		if (rprops == null)
			return -1;

		String type = rprops.getProperty(prop + ".type");
		if (type != null) {
			type = type.toLowerCase();
			if (type.equals(BOOLEAN_FIELD))
				return IProperty.BOOLEAN;
			else if (type.equals(DATE_FIELD)) 
				return IProperty.DATE;
            else if (type.equals(TIME_FIELD))
                return IProperty.TIME;
            else if (type.equals(TIMESTAMP_FIELD)) 
                return IProperty.TIMESTAMP;
			else if (type.equals(FLOAT_FIELD)) 
				return IProperty.FLOAT;
			else if (type.equals(NUMBER_FIELD)) 
				return IProperty.FLOAT;
			else if (type.equals(INTEGER_FIELD))
				return IProperty.INTEGER;
			else if (type.equals(JAVAOBJ_FIELD))
				return IProperty.JAVAOBJECT;
			else if (type.equals(NODE_FIELD))
				return IProperty.NODE;
			else if (type.equals(STRING_FIELD))
				return IProperty.STRING;
			else if (type.equals(XML_FIELD)) 
				return IProperty.XML;
            else if (type.equals(XHTML_FIELD)) 
                return IProperty.XHTML;
            else if (type.equals(SMALL_FLOAT_FIELD))
                return IProperty.SMALLFLOAT;
            else if (type.equals(SMALL_INT_FIELD))
                return IProperty.SMALLINT;
		}

		return -1;
	}

	public static int stringToType(String strType) {
		int type = IProperty.STRING;

		if (strType != null) {
			strType = strType.toLowerCase();
			if (strType.equals(BOOLEAN_FIELD)) { 
				type = IProperty.BOOLEAN;
			} else if (strType.equals(DATE_FIELD)) { 
				type = IProperty.DATE;
			} else if (strType.equals(TIME_FIELD)) { 
			    type = IProperty.TIME;
            } else if (strType.equals(TIMESTAMP_FIELD)) { 
                type = IProperty.TIMESTAMP;
            } else if (strType.equals(FLOAT_FIELD)) {
				type = IProperty.FLOAT;
			} else if (strType.equals(NUMBER_FIELD)) {
				type = IProperty.FLOAT;
			} else if (strType.equals(INTEGER_FIELD)) {
				type = IProperty.INTEGER;
			} else if (strType.equals(JAVAOBJ_FIELD)) {
				type = IProperty.JAVAOBJECT;
			} else if (strType.equals(NODE_FIELD)) {
				type = IProperty.NODE;   
			} else if (strType.equals(REL_FIELD)) {
				type = IProperty.REFERENCE;
			} else if (strType.startsWith(MULTI_FIELD)) {
				type = IProperty.MULTI_VALUE;
			} else if (strType.equals(XML_FIELD)) {
				type = IProperty.XML;
			} else if (strType.equals(XHTML_FIELD)) { 
			    type = IProperty.XHTML;
            } else if (strType.equals(FILE_FIELD)) {
				return IProperty.NODE;
			} else if (strType.equals(IMAGE_FIELD)) {
				return IProperty.NODE;
			} else if (strType.equals(SMALL_FLOAT_FIELD)) {
			    return IProperty.SMALLFLOAT;         
            } else if (strType.equals(SMALL_INT_FIELD)) {
                return IProperty.SMALLINT;
            }
		}

		return type;
	}

	public final static String intToStr(int type) {
		switch (type) {
		case IProperty.BOOLEAN: 
			return LuceneManager.BOOLEAN_FIELD;
		case IProperty.DATE:
			return LuceneManager.DATE_FIELD;
        case IProperty.TIME:
            return LuceneManager.TIME_FIELD;
        case IProperty.TIMESTAMP:
            return LuceneManager.TIMESTAMP_FIELD;
		case IProperty.FLOAT: 
			return LuceneManager.FLOAT_FIELD;
		case IProperty.INTEGER:
			return LuceneManager.INTEGER_FIELD;
		case IProperty.JAVAOBJECT:
			return LuceneManager.JAVAOBJ_FIELD;
		case IProperty.MULTI_VALUE:
			return LuceneManager.MULTI_FIELD;
		case IProperty.NODE:
			return LuceneManager.NODE_FIELD;
		case IProperty.REFERENCE:
			return LuceneManager.REL_FIELD;
		case IProperty.STRING:
			return LuceneManager.STRING_FIELD;
		case IProperty.XML:
			return LuceneManager.XML_FIELD;
        case IProperty.XHTML:
            return LuceneManager.XHTML_FIELD;
        case IProperty.SMALLINT:
            return LuceneManager.SMALL_INT_FIELD;
        case IProperty.SMALLFLOAT:
            return LuceneManager.SMALL_FLOAT_FIELD;
		}
		return null;
	} 

	public boolean commitToStorage(final INode node, final String tmpPath) {   
		String oldRelPath = tmpPath;
		String repos = this.app.getBlobDir();

		if (oldRelPath != null) {
			String newPath = repos;
			if (newPath != null && !newPath.endsWith(File.separator)) {
				newPath += File.separator;
			}
			if (node instanceof Node) {
				newPath += node.getID(); 
			} else {
				return false;
			}

			oldRelPath = normalizePath(oldRelPath);
			newPath = normalizePath(newPath);
			createDirectories(newPath.substring(0, newPath.lastIndexOf(File.separator)));

			File oldFile = new File(oldRelPath);
			File newFile = new File(newPath);

			if (oldFile.equals(newFile)) {
				return true;
			}

			if (newFile.exists()) {
				newFile.delete();
			}

            String upload = node.getString(FileObject.FILE_UPLOAD);
            if (upload != null && "false".equals(upload)) {
            	return FileUtils.copy(oldFile, newFile);
            } else {
                if (oldFile.renameTo(newFile)) {
                    return true;
                } else {
                    return FileUtils.copy(oldFile, newFile);
                }
            }
		}

		return false;
	}

	private void createDirectories(String path) {
		File dir = new File(path);
		if (dir.exists() && dir.isDirectory()) {
			return;
		}

		char separator = File.separatorChar;
		StringBuffer walker = new StringBuffer(path.length());

		String[] pathItems;
		int count = 0;
		if (separator == '\\') {
			pathItems = path.split("\\"+separator);
			walker.append(pathItems[count++]).append(separator);
		} else {
			pathItems = path.split(""+separator);
			walker.append(separator);
		}

		int length = pathItems.length;

		do {
			walker.append(pathItems[count++]).append(separator);
			dir = new File(walker.toString());
			if (!dir.exists()) {
				dir.mkdir();
			}
		} while (count < length);
	}

	private String normalizePath(String path) {
		String separator = File.separator;
		String wrong;
		if (separator.equals("\\")) {
			wrong = "/";
		} else {
			wrong = "\\";
		}

		StringBuffer pathBuffer = new StringBuffer(path);
		int index = -1;
		while ((index = pathBuffer.indexOf(wrong, index)) > -1) {
			pathBuffer.replace(index, index + 1, separator);
		}

		return pathBuffer.toString();
	}

	protected boolean moveFile(File src, File dst) {
		boolean success = false;
		java.io.InputStream in = null;
		java.io.OutputStream out = null;

		try {
			in = new FileInputStream(src);
			out = new FileOutputStream(dst);

			byte[] buf = new byte[2048];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}

			success = true;
		} catch (Exception ex) {
			app.logError(ErrorReporter.errorMsg(this.getClass(), "moveFile") 
					+ "Could not move file " + src.getName() + " to " + dst.getName(), ex);
			success = false;
		} finally {
			if (in != null) {
				try { 
                    in.close(); 
                } catch (Exception e) { 
                    app.logError(ErrorReporter.errorMsg(this.getClass(), "moveFile"), e);
                }
				in = null;
			}
			if (out != null) {
				try { 
                    out.close(); 
                } catch (Exception e) { 
                    app.logError(ErrorReporter.errorMsg(this.getClass(), "moveFile"), e);
                }
				out = null;
			}
		}

		src.delete();

		return success;
	}

	public boolean deleteFromStorage(INode node) {
		StringBuffer path = new StringBuffer(35);

		ResourceProperties rprops = app.getProperties();
		final String repos = rprops.getProperty(node.getPrototype().toLowerCase()+".repository");
		path.append(repos);
		path.append(node.getID());

		File file = new File(path.toString());
		return file.exists() ? file.delete() : false;
	}

	public static void commitSegments(Connection conn, Application app, Directory dir) {
		commitSegments(null, conn, app, dir);
	}
	
	public static void commitSegments(String segmentsNew, Connection conn, Application app, Directory dir) {
        byte[] segmentContents = null;
		if (segmentsNew == null) {
			segmentsNew = TransFSDirectory.SEGMENTS_NEW;//TODO:IndexFileNames.getSegmentsNewFileName();
		}
		IndexInput input = null;
		try {
			input = dir.openInput(segmentsNew);
			int length = (int) input.length();
			segmentContents = new byte[length];
			try {
				input.readBytes(segmentContents, 0, length);
			} catch (IOException ioe) {
				segmentContents = null;
			}
		} catch (Exception ex) {
			app.logError(ErrorReporter.errorMsg(LuceneManager.class, "commitSegments"), ex);
			throw new TransactionException("LuceneTransaction.executeSubTransaction(): " + ex.getMessage());
		} finally {
			if (input != null) {
				try { 
                    input.close(); 
                } catch (Exception ignore) {
                }
				input = null;
			}
		}

		if (segmentContents == null || segmentContents.length == 0) {
			throw new TransactionException("LuceneTransaction.executeSubTransaction(): " +
			"The segments.new file does not contain any data to save.");
		}

		PreparedStatement pstmt = null;
		ByteArrayInputStream bais = null;
		boolean exceptionOccured = false;

		try {
			String sql = "UPDATE Lucene SET valid = ?, version = ? " +
						 "WHERE valid = ? AND db_home = ?";
			pstmt = conn.prepareStatement(sql);
			int count = 1;
			pstmt.setBoolean(count++, false);
            pstmt.setInt(count++, getLuceneVersion());
			pstmt.setBoolean(count++, true);
			pstmt.setString(count++, app.getDbDir().getName());
			pstmt.executeUpdate();
			pstmt.close();
			pstmt = null;

			sql = "INSERT INTO Lucene (valid, db_home, segments, version) " +
					"VALUES (?,?,?,?)";
			pstmt = conn.prepareStatement(sql);
			count = 1;
			pstmt.setBoolean(count++, true);
			pstmt.setString(count++, app.getDbDir().getName());
			bais = new ByteArrayInputStream(segmentContents);
			pstmt.setBinaryStream(count++, bais, segmentContents.length);
            pstmt.setInt(count++, getLuceneVersion());
			int rows = pstmt.executeUpdate();
			if (rows < 1) {
				throw new Exception("LuceneTransactionManager.executeTransaction(): update didn't affect any rows in the database");
			}
		} catch (Exception ex) {
			exceptionOccured = true;
			throw new TransactionException(ex.getMessage());
		} finally {
			try {
				dir.deleteFile(segmentsNew);
			} catch (IOException ioex) {
				// i guess its okay if a random segments.new file is lying around, itll 
				// get overwritten on the next lucene write operation anyway
				app.logEvent(ErrorReporter.warningMsg(LuceneManager.class, "commitSegments") 
						+ "Could not delete " + segmentsNew);
			}

			if (bais != null) {
				try { 
                    bais.close(); 
                } catch (Exception ignoreit) {
                }
				bais = null;
			}
            segmentContents = null;
            
			if (pstmt != null) {
				try { 
					pstmt.close(); 
				} catch (SQLException sqle) {
					if (!exceptionOccured) {
						throw new TransactionException(sqle.getMessage());
					}
				}
				pstmt = null;
			}
		}
	}
	
	public static void commitSegments(String segmentsNew, Connection conn, File dbhome, Directory dir) {
        byte[] segmentContents = null;
		if (segmentsNew == null) {
			segmentsNew = TransFSDirectory.SEGMENTS_NEW;//TODO:IndexFileNames.getSegmentsNewFileName();
		}
		IndexInput input = null;
		try {
			input = dir.openInput(segmentsNew);
			int length = (int) input.length();
			segmentContents = new byte[length];
			try {
				input.readBytes(segmentContents, 0, length);
			} catch (IOException ioe) {
				segmentContents = null;
			}
		} catch (Exception ex) {
			throw new TransactionException("LuceneTransaction.executeSubTransaction(): " + ex.getMessage());
		} finally {
			if (input != null) {
				try { 
                    input.close(); 
                } catch (Exception ignore) {
                }
				input = null;
			}
		}

		if (segmentContents == null || segmentContents.length == 0) {
			throw new TransactionException("LuceneTransaction.executeSubTransaction(): " +
			"The segments.new file does not contain any data to save.");
		}

		PreparedStatement pstmt = null;
		ByteArrayInputStream bais = null;
		boolean exceptionOccured = false;

		try {
			String sql = "UPDATE Lucene SET valid = ?, version = ? " +
						 "WHERE valid = ? AND db_home = ?";
			pstmt = conn.prepareStatement(sql);
			int count = 1;
			pstmt.setBoolean(count++, false);
            pstmt.setInt(count++, getLuceneVersion());
			pstmt.setBoolean(count++, true);
			pstmt.setString(count++, dbhome.getName());
			pstmt.executeUpdate();
			pstmt.close();
			pstmt = null;

			sql = "INSERT INTO Lucene (valid, db_home, segments, version) " +
					"VALUES (?,?,?,?)";
			pstmt = conn.prepareStatement(sql);
			count = 1;
			pstmt.setBoolean(count++, true);
			pstmt.setString(count++, dbhome.getName());
			bais = new ByteArrayInputStream(segmentContents);
			pstmt.setBinaryStream(count++, bais, segmentContents.length);
            pstmt.setInt(count++, getLuceneVersion());
			int rows = pstmt.executeUpdate();
			System.out.println("EXECUTE update was a SUCCESS!!");
			if (rows < 1) {
				throw new Exception("LuceneTransactionManager.executeTransaction(): update didn't affect any rows in the database");
			}
		} catch (Exception ex) {
			exceptionOccured = true;
			throw new TransactionException(ex.getMessage());
		} finally {
			try {
				dir.deleteFile(segmentsNew);
			} catch (IOException ioex) {
				// i guess its okay if a random segments.new file is lying around, itll 
				// get overwritten on the next lucene write operation anyway
			}

			if (bais != null) {
				try { 
                    bais.close(); 
                } catch (Exception ignoreit) {
                }
				bais = null;
			}
            segmentContents = null;
            
			if (pstmt != null) {
				try { 
					pstmt.close(); 
				} catch (SQLException sqle) {
					if (!exceptionOccured) {
						throw new TransactionException(sqle.getMessage());
					}
				}
				pstmt = null;
			}
		}
	}

	public static Analyzer getAnalyzer(String analyzerName) {
        if (analyzerName == null) {
            return null;
        }
        
		String analyzer = analyzerName.toLowerCase();
		if ("whitespaceanalyzer".equals(analyzer)) {
			return new WhitespaceAnalyzer();
		} else if ("simpleanalyzer".equals(analyzer)) {
			return new SimpleAnalyzer();
		} else if ("stopanalyzer".equals(analyzer)) {
			return new StopAnalyzer();
		} else if ("standardanalyzer".equals(analyzer)) {
			return new StandardAnalyzer();
		} else {
			try {
				return (Analyzer) Class.forName(analyzerName).newInstance();
			} catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch(ClassNotFoundException e){
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return null;
	}
    
    public static int getLuceneVersion() {
        return LUCENE_VERSION;
    }
    
    public int getCurrentLuceneVersion() {
        return this.writerManager.getCurrentVersion();
    }
    
    public ArrayList getChildrenIds(INode node) throws Exception {
        ArrayList childrenIds = new ArrayList();
        IndexSearcher searcher = null;
        BooleanQuery bq = new BooleanQuery();
        
        try {
            searcher = this.getIndexSearcher();
            String id = node.getID();
            final Query query1 = new TermQuery(new Term(PARENTID, isSpecialNode(id) ? id : node.getID())); 
            final Query query2 = new TermQuery(new Term(ISCHILD, "true"));
            bq.add(query1, BooleanClause.Occur.MUST);
            bq.add(query2, BooleanClause.Occur.MUST);

            final Hits hits = searcher.search(bq);
            
            /*if (app.debug())
            	app.logEvent("LuceneManager.getChildrenIds() executed query [" + bq 
            			+ " which resulted in " + hits.length() + " hits");*/

            final int length = hits.length();
            for (int i = 0; i < length; i++) {
                Document doc = hits.doc(i);
                childrenIds.add(doc.getField(ID).stringValue());
            }
        } catch (IOException ioe) {
            throw new Exception("Searcher failed when attempting to retrieve children of " +
                    "id '" + node.getID() + "', query = " + bq);
        } finally {
            this.releaseIndexSearcher(searcher);
        }
        
        return childrenIds;
    }

	public void logSegInfo() throws Exception {
		IndexWriter iw = this.writerManager.getWriter();
		SegmentInfosWrapper sisw = iw.getSegmentInfosWrapper();
        StringBuffer logStr = new StringBuffer();
		logStr.append("Start Lucene Segments Log\n");
		for (int i = 0; i < sisw.size(); i++){
			logStr.append(sisw.getSegmentInfos(i));
		}
		logStr.append("\nEnd Lucene Segments Log\n");
        app.logEvent(logStr.toString());
	}
    
    public void printSegInfo() throws Exception {
        IndexWriter iw = this.writerManager.getWriter();
        SegmentInfosWrapper sisw = iw.getSegmentInfosWrapper();
        StringBuffer logStr = new StringBuffer();
        logStr.append("Start Lucene Segments Log\n");
        for (int i = 0; i < sisw.size(); i++){
            logStr.append(sisw.getSegmentInfos(i));
        }
        logStr.append("\nEnd Lucene Segments Log\n");
        System.out.println(logStr.toString());
    }
    
    public synchronized IndexSearcher getIndexSearcher() throws IOException {
        if (!this.isSearcherValid || this.searcher == null) {
            this.isSearcherValid = true;
            try {
                this.searcher = new IndexSearcher(this.directory);
            } catch (Exception ex) {
                throw new IOException("FATAL ERROR::LuceneManager.getIndexSearcher(), Could not create IndexSearcher");
            }
        }
        this.searcher.refCount++;
        return this.searcher;
    }
    
    public synchronized void releaseIndexSearcher(IndexSearcher searcher) {
        if (searcher == null) {
            return;
        }
        searcher.refCount--;
        if (this.searcher != searcher && searcher.refCount <= 0) {
            try {
                searcher.close();
            } catch (Exception ex) {
                this.app.logError(ErrorReporter.errorMsg(this.getClass(), "releaseIndexSearcher") 
                		+ "Could not close " + searcher, ex);
            }
            searcher = null;
        }
    }
    
    public synchronized void setSearcherDirty() {
        this.isSearcherValid = false;
        if (this.searcher != null && this.searcher.refCount <= 0) {
            try {
                this.searcher.close();
            } catch (Exception ex) {
                this.app.logError(ErrorReporter.errorMsg(this.getClass(), "setSearcherDirty") 
                		+ "Could not close " + searcher, ex);
            }
            this.searcher = null;
        }
    }
    
    public void logIndexContents() {
        /*TODO:try {
            synchronized (this.directory) {
                SegmentInfos sinfos = IndexObjectsFactory.getFSSegmentInfos(this.directory);
                DeletedInfos delInfos = IndexObjectsFactory.getDeletedInfos(this.directory);
                BitSet bs = delInfos.getBitSet();
                IndexObjectsFactory.setDeletedInfos(this.directory, new DeletedInfos());
                IndexReader reader = IndexReader.open(this.directory);
                final int numdocs = reader.numDocsTotal();
                StringBuffer sb = new StringBuffer("\n");
                sb.append("TOTAL DOCS: ").append(numdocs).append("\n");
                sb.append("SEGMENTS: ");
                final int sinfossize = sinfos.size();
                for (int i = 0; i < sinfossize; i++) {
                    SegmentInfo si = sinfos.info(i);
                    sb.append(si.name).append("(").append(si.docCount).append(") ");
                }
                sb.append("\n");
                sb.append("ID\tPrototype\tPID\tParent Prototype\tObsolete?\tDeleted?\tStatus\n");
                sb.append("------------------------------------------------------------------\n");
                for (int i = 0; i < numdocs; i++) {
                    Document d = reader.document(i);
                    String id = d.get(ID);
                    String prototype = d.get(PROTOTYPE);
                    String parentid = d.get(PARENTID);
                    String parentproto = d.get(PARENTPROTOTYPE);
                    boolean isObsolete = bs.get(i);
                    boolean isDeleted = d.get(DeletedInfos.DELETED) != null;
                    String status = d.get(STATUS);
                    sb.append(id).append("\t").append(prototype).append("\t");
                    sb.append(parentid).append("\t").append(parentproto).append("\t");
                    sb.append(isObsolete).append("\t").append(isDeleted).append("\t");
                    sb.append(status).append("\n");
                }
                this.app.logEvent(sb.toString());
                IndexObjectsFactory.setDeletedInfos(this.directory, delInfos);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }*/
    }
    
    /*TODO:public LuceneOptimizer getOptimizer() {
        return this.optimizerThread;
    }*/
    
    public ArrayList<Node> getPreviewNodes(String id, final int mode) throws Exception {
    	ArrayList<Node> list = new ArrayList<Node>();
    	IndexSearcher searcher = null;
    	final int highestMode = app.getHighestPreviewLayer();
    	
    	try {
    		for (int cmode = mode + 1; cmode <= highestMode; cmode++) {
    			try {
    				Node n = this.retrieveFromIndexFixedMode(id, cmode);
    				if (n != null) {
    					list.add(n);
    				}
    			} catch (Exception x) {
    			}
    		}
    	} catch (Exception ex) {
    		app.logError(ErrorReporter.errorMsg(this.getClass(), "getPreviewNodes"), ex);
    		throw ex;
    	} finally {
    		this.releaseIndexSearcher(searcher);
    	}

    	return list;
    }
    
    private static final String getIDPart(String id) {
		return id.substring(0, id.indexOf(DeletedInfos.KEY_SEPERATOR));	
    }
    
    private static final int getLayerPart(String id) {
    	final int idx = id.indexOf(DeletedInfos.KEY_SEPERATOR);
    	return Integer.parseInt(id.substring(idx + DeletedInfos.KEY_SEPERATOR.length()));
    }
    
    public LuceneDataFormatter getDataFormatter(){
    	return dataFormatter;
    }
    
}