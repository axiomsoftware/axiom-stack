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

import java.util.*;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.mozilla.javascript.*;

import axiom.framework.ErrorReporter;
import axiom.framework.core.Application;
import axiom.framework.core.Prototype;
import axiom.framework.core.RequestEvaluator;
import axiom.framework.core.TypeManager;
import axiom.objectmodel.INode;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.db.DbMapping;
import axiom.objectmodel.db.DbSource;
import axiom.objectmodel.db.Node;
import axiom.scripting.rhino.extensions.filter.*;
import axiom.util.ResourceProperties;


public final class QueryBean  {
   
    private RhinoCore core;
    private Application app;
    private LuceneQueryDispatcher lqd = null;
    private RelationalQueryDispatcher rqd = null;
    
    private HashMap<String, QueryDispatcher> querydispatchers = new HashMap<String, QueryDispatcher>();
    
    public QueryBean(Application app, String name) throws Exception {
        this.core = null;
        this.app = app;
        lqd = new LuceneQueryDispatcher(app, name + "lqd");
        rqd = new RelationalQueryDispatcher(app, name + "rqd");

		ArrayList names = app.getDbNames();
		for(int i = 0; i < names.size(); i++){
			try{
				String dbname = names.get(i).toString();
				DbSource dbsource = app.getDbSource(dbname);
				String initClass = dbsource.getProperty("queryDispatcherClass", null);
				if(initClass != null){
			        Class[] parameters = { Application.class, String.class };
			        QueryDispatcher qd = (QueryDispatcher)Class.forName(initClass)
					.getConstructor(parameters).newInstance(new Object[] {this.app, name + dbname + ""});
			        querydispatchers.put(dbsource.getType(), qd);
				}
			}
			catch(Exception e){
				app.logError(ErrorReporter.errorMsg(this.getClass(), "ctor") 
						+ "Error during " + names.get(i) + "'s initialization", e);
			}
		}
    }
    
    public void finalize() throws Throwable {
        super.finalize();
    	lqd.shutdown();
    	rqd.shutdown();
    	Iterator<QueryDispatcher> i = querydispatchers.values().iterator();
    	while(i.hasNext()){
    		i.next().shutdown();
    	}
    }

    public void shutdown() {
    	lqd.shutdown();
    	rqd.shutdown();
    	Iterator<QueryDispatcher> i = querydispatchers.values().iterator();
    	while(i.hasNext()){
    		i.next().shutdown();
    	}
    }
    
    public String toString() {
        return "[Query]";
    }
    
    public void setRhinoCore(RhinoCore core) {
        this.core = core;
        lqd.setRhinoCore(this.core);
        rqd.setRhinoCore(this.core);
    	Iterator<QueryDispatcher> i = querydispatchers.values().iterator();
    	while(i.hasNext()){
    		i.next().setRhinoCore(this.core);
    	}
    }

    public static IFilter getFilterFromObject(Object filter) throws Exception {
    	IFilter theFilter = null;
        if (filter == null || filter == Undefined.instance) {
            theFilter = new FilterObject();
        } else if (!(filter instanceof IFilter)) {
            if (filter instanceof String) {
                theFilter = new NativeFilterObject(new Object[] {filter});
            } else if (filter instanceof Scriptable) {
                Scriptable s = (Scriptable) filter;
                if (s.getClassName().equals("String")) {
                    theFilter = new NativeFilterObject(new Object[] {filter});
                } else {
                    theFilter = new FilterObject(filter, null, null);
                }
            }
        } else {
            theFilter = (IFilter) filter;
        }
        return theFilter;
    }
        
    private static void setEmptyEmbeddedArray(Application app, ArrayList embeddedDbProtos) throws Exception{
    	ArrayList specialPrototypes = app.getSearchablePrototypes();
		Collection c = app.getPrototypes();
		Iterator i = c.iterator();
		while(i.hasNext()){
			String proto  = ((Prototype)i.next()).getName();
            DbMapping dbmap = app.getDbMapping(proto);
            String type = dbmap != null ? dbmap.getType() : null;
			if(type.equalsIgnoreCase("lucene") && specialPrototypes.contains(proto)){
                embeddedDbProtos.add(proto);    				
			}
		}
    }
    
    public static void setPrototypeArrays(Application app, Object prototype, ArrayList embeddedDbProtos, 
        	ArrayList relationalDbProtos, HashMap<String, ArrayList> extDbProtos) throws Exception {
    	if (prototype == null || prototype == Undefined.instance) {
    		setEmptyEmbeddedArray(app, embeddedDbProtos);
        } else if (prototype instanceof String) {
            String proto = prototype.toString();
            DbMapping dbmap = app.getDbMapping(proto);
            String type = dbmap != null ? dbmap.getType() : null;
            if(dbmap != null && app.getExtDBTypes().contains(type)){
            	ArrayList<DbMapping> externalDbProtos = new ArrayList<DbMapping>();
            	externalDbProtos.add(dbmap);
            	extDbProtos.put(type, externalDbProtos);
        	} else if (dbmap != null && dbmap.isRelational()) {
                relationalDbProtos.add(dbmap);
            } else {
                embeddedDbProtos.add(proto);
            }
        } else if (prototype instanceof NativeArray) {
            final NativeArray na = (NativeArray) prototype;
            final int length = (int) na.getLength();
            if(length > 0){
	            for (int i = 0; i < length; i++) {
	                Object o = na.get(i, na);
	                if (o instanceof String) {
	                    String proto = o.toString();
	                    DbMapping dbmap = app.getDbMapping(proto);
	                    String type = dbmap != null ? dbmap.getType() : null;
	                    if(dbmap != null && app.getExtDBTypes().contains(type)){
	                    	ArrayList externalDbProtos = extDbProtos.get(type);
	                    	if(externalDbProtos == null){
	                    		externalDbProtos = new ArrayList();
	                    	} else {
	                        	extDbProtos.remove(type);
	                    	}
	                    	externalDbProtos.add(dbmap);
	                    	extDbProtos.put(type, externalDbProtos);
	                	} else if (dbmap != null && dbmap.isRelational()) {
	                        relationalDbProtos.add(dbmap);
	                    } else {
	                        embeddedDbProtos.add(proto);
	                    }
	                } else {
	                    throw new Exception("query: The prototypes array contains an invalid entry at index " + i);
	                }
	            }
            }
            else{
        		setEmptyEmbeddedArray(app, embeddedDbProtos);
            }
        }
    }
    
    public static boolean validExtDb(HashMap<String, ArrayList> hm){
    	int aggCount = 0;
    	int extCount = 0;
    	Iterator<ArrayList> i = hm.values().iterator();
    	while(i.hasNext()){
    		ArrayList al = i.next();
    		if(al.size() > 0){
    			extCount = al.size();
    			aggCount++;
    		}
    	}    	
    	return !(aggCount > 1) && extCount > 0;    	
    	
    }
    
    public static String getQueryDispatcher(HashMap<String, ArrayList> hm){
    	String name = null;
    	Iterator i = hm.keySet().iterator();
    	while(i.hasNext()){
    		String key = i.next().toString();
    		ArrayList value = hm.get(key);
    		if(value.size() > 0){
    			name = key;
    		}
    	}    	
    	return name;   	
    }
    
    public Scriptable fields(Object field, Object prototype, Object filter) 
    throws Exception {
        return this.fields(field, prototype, filter, null);
    }
    
    public Scriptable fields(Object field, Object prototype, Object filter, Object optional1) 
    throws Exception {
        
        if (field == null || field == Undefined.instance) {
            throw new Exception("query.fields(): Must specify the field as the first parameter.");
        }        

        IFilter theFilter = getFilterFromObject(filter);        
        if (theFilter == null) {
            throw new Exception("query.fields(): Could not determine the query filter.");
        }

        ArrayList ldbProtos = new ArrayList();
        ArrayList rdbProtos = new ArrayList();
        HashMap<String, ArrayList> extDbProtos = new HashMap<String, ArrayList>();
        setPrototypeArrays(this.app, prototype, ldbProtos, rdbProtos, extDbProtos);
        
        Scriptable options = null;
        if(optional1 != null && optional1 instanceof Scriptable){
        	options = (Scriptable)optional1;
        } else{
        	options = Context.getCurrentContext().newObject(this.core.global);
        }
        options.put("field", options, (String)field);
        
        boolean validExtDb = validExtDb(extDbProtos);
        ArrayList embeddedResults = null;
        if(ldbProtos.size() > 0 && (rdbProtos.size() > 0 || validExtDb) ||
        		rdbProtos.size() > 0 && (ldbProtos.size() > 0 || validExtDb) ||
        		validExtDb && (rdbProtos.size() > 0 || ldbProtos.size() > 0)){
        	this.app.logError(ErrorReporter.errorMsg(this.getClass(), "fields") 
        			+ "Does not support aggregate data stores");
        	throw new Exception("app.getFields does not support aggregate data stores");
        } else if(validExtDb){
        	String queryDispatcher = getQueryDispatcher(extDbProtos);
        	QueryDispatcher qd = querydispatchers.get(queryDispatcher);
        	if(qd != null){
        		ArrayList qdDbProtos = extDbProtos.get(queryDispatcher);
        		embeddedResults = qd.executeQuery(qdDbProtos, theFilter, options);
        	}
        } else{
        	embeddedResults = this.lqd.executeQuery(ldbProtos, theFilter, options);
        }

        return Context.getCurrentContext().newArray(this.core.global, embeddedResults.toArray());
    }
    
    
    public Scriptable objects(Object prototype, Object filter) 
    throws Exception {
        return objects(prototype, filter, null);
    }
    
    public Scriptable objects(Object prototype, Object filter, Object optional1) 
    throws Exception {
        IFilter theFilter = getFilterFromObject(filter);        
        if (theFilter == null) {
            throw new Exception("query.objects(): Could not determine the query filter.");
        }
        
        ArrayList ldbProtos = new ArrayList();
        ArrayList rdbProtos = new ArrayList();
        HashMap<String, ArrayList> extDbProtos = new HashMap<String, ArrayList>();
        setPrototypeArrays(this.app, prototype, ldbProtos, rdbProtos, extDbProtos);
        boolean validExtDb = validExtDb(extDbProtos);
        ArrayList embeddedResults = new ArrayList();
        if(ldbProtos.size() > 0 && (rdbProtos.size() > 0 || validExtDb) ||
        		rdbProtos.size() > 0 && (ldbProtos.size() > 0 || validExtDb) ||
        		validExtDb && (rdbProtos.size() > 0 || ldbProtos.size() > 0)){
        	this.app.logError(ErrorReporter.errorMsg(this.getClass(), "objects") 
        			+ "Does not support aggregate data stores");
        	throw new Exception("app.getObjects does not support aggregate data stores");
        } else if(validExtDb){
        	String queryDispatcher = getQueryDispatcher(extDbProtos);
        	QueryDispatcher qd = querydispatchers.get(queryDispatcher);
        	if(qd != null){
        		ArrayList qdDbProtos = extDbProtos.get(queryDispatcher);
        		embeddedResults = qd.executeQuery(qdDbProtos, theFilter, optional1);
        	}
        } else if(ldbProtos.size() > 0){
	        embeddedResults = this.lqd.executeQuery(ldbProtos, theFilter, optional1);
        } else if(rdbProtos.size() > 0) {
        	embeddedResults = this.rqd.executeQuery(rdbProtos, theFilter, optional1);
        }

        return Context.getCurrentContext().newArray(this.core.global, embeddedResults.toArray());
    }
    
    public int getHitCount(Object prototype, Object filter, Object options) 
    throws Exception {

        IFilter theFilter = getFilterFromObject(filter);        
        if (theFilter == null) {
            throw new Exception("query.objects(): Could not determine the query filter.");
        }

        ArrayList ldbProtos = new ArrayList();
        ArrayList rdbProtos = new ArrayList();
        HashMap<String, ArrayList> extDbProtos = new HashMap<String, ArrayList>();
        setPrototypeArrays(this.app, prototype, ldbProtos, rdbProtos, extDbProtos);
        boolean validExtDb = validExtDb(extDbProtos);

        int length = 0;
        if(ldbProtos.size() > 0){
        	length = lqd.getHitCount(ldbProtos, theFilter, options);
        } else  if(rdbProtos.size() > 0) {
            length = rqd.getHitCount(rdbProtos, theFilter, options);
        } else if(validExtDb){
        	String queryDispatcher = getQueryDispatcher(extDbProtos);
        	QueryDispatcher qd = querydispatchers.get(queryDispatcher);
        	if(qd != null){
        		ArrayList qdDbProtos = extDbProtos.get(queryDispatcher);
            	length = qd.getHitCount(qdDbProtos, theFilter, options);
        	}
        }

        return length;
    }

    public Object hits(Object prototype, Object filter) 
    throws Exception {
        return hits(prototype, filter, null);
    }
    
    public Object hits(Object prototype, Object filter, Object optional1)
    throws Exception {
        IFilter theFilter = getFilterFromObject(filter);        
        if (theFilter == null) {
            throw new Exception("query.hits(): Could not determine the query filter.");
        }
        
        ArrayList embeddedDbProtos = new ArrayList();
        ArrayList relationalDbProtos = new ArrayList();
        HashMap<String, ArrayList> extDbProtos = new HashMap<String, ArrayList>();
        setPrototypeArrays(this.app, prototype, embeddedDbProtos, relationalDbProtos, extDbProtos);
        boolean validExtDb = validExtDb(extDbProtos);
        Object results = null;
        if(embeddedDbProtos.size() > 0 && (relationalDbProtos.size() > 0 || validExtDb) ||
        		relationalDbProtos.size() > 0 && (embeddedDbProtos.size() > 0 || validExtDb) ||
        		validExtDb && (relationalDbProtos.size() > 0 || embeddedDbProtos.size() > 0)){
        	this.app.logError(ErrorReporter.errorMsg(this.getClass(), "hits") 
        			+ "Does not support aggregate data stores");
        	throw new Exception("app.getHits() does not support aggregate data stores");
        }        
        else if (relationalDbProtos.size() > 0){
        	throw new Exception("Relational Prototype do not support the Hits Object");
        } else if(validExtDb){
        	String queryDispatcher = getQueryDispatcher(extDbProtos);
        	QueryDispatcher qd = querydispatchers.get(queryDispatcher);
        	if(qd != null){
        		ArrayList qdDbProtos = extDbProtos.get(queryDispatcher);
        		results = qd.hits(qdDbProtos, theFilter, optional1);
        	}
        } else{
        	results = lqd.hits(embeddedDbProtos, theFilter, optional1);   
        }
        return results;
    }
    
    public Object targets(Object source) throws Exception {
        return lqd.getDirectionalNodesFor(source, 0, null, null, null);
    }
    
    public Object targets(Object source, Object prototypes) throws Exception {
        return lqd.getDirectionalNodesFor(source, 0, prototypes, null, null);
    }
    
    public Object targets(Object source, Object prototypes, Object filter) 
    throws Exception {
        return lqd.getDirectionalNodesFor(source, 0, prototypes, filter, null);
    }
    
    public Object targets(Object source, Object prototypes, Object filter, Object options) 
    throws Exception {
        return lqd.getDirectionalNodesFor(source, 0, prototypes, filter, options);
    }
    
    public Object sources(Object target) throws Exception {
        return lqd.getDirectionalNodesFor(target, 1, null, null, null);
    }
    
    public Object sources(Object target, Object prototypes) throws Exception {
        return lqd.getDirectionalNodesFor(target, 1, prototypes, null, null);
    }
    
    public Object sources(Object target, Object prototypes, Object filter) 
    throws Exception {
        return lqd.getDirectionalNodesFor(target, 1, prototypes, filter, null);
    }
    
    public Object sources(Object target, Object prototypes, Object filter, Object options) 
    throws Exception {
        return lqd.getDirectionalNodesFor(target, 1, prototypes, filter, options);
    }
    
    public int targetCount(Object source) throws Exception {
        return lqd.getDirectionalNodesForCount(source, 0, null, null, null);
    }
    
    public int targetCount(Object source, Object prototypes) throws Exception {
        return lqd.getDirectionalNodesForCount(source, 0, prototypes, null, null);
    }
    
    public int targetCount(Object source, Object prototypes, Object filter) 
    throws Exception {
        return lqd.getDirectionalNodesForCount(source, 0, prototypes, filter, null);
    }
    
    public int targetCount(Object source, Object prototypes, Object filter, Object options) 
    throws Exception {
        return lqd.getDirectionalNodesForCount(source, 0, prototypes, filter, options);
    }
    
    public int sourceCount(Object target) throws Exception {
        return lqd.getDirectionalNodesForCount(target, 1, null, null, null);
    }
    
    public int sourceCount(Object target, Object prototypes) throws Exception {
        return lqd.getDirectionalNodesForCount(target, 1, prototypes, null, null);
    }
    
    public int sourceCount(Object target, Object prototypes, Object filter) 
    throws Exception {
        return lqd.getDirectionalNodesForCount(target, 1, prototypes, filter, null);
    }
    
    public int sourceCount(Object target, Object prototypes, Object filter, Object options) 
    throws Exception {
        return lqd.getDirectionalNodesForCount(target, 1, prototypes, filter, options);
    }
    
    public Scriptable references(Object source, Object target) throws Exception {
        if (source == null || source == Undefined.instance) {
            throw new Exception("query.references(): The source parameter is not defined.");
        }
        if (target == null || target == Undefined.instance) {
            throw new Exception("query.references(): The target parameter is not defined.");
        }
        
        RhinoCore core = this.core;
        Application app = this.app;
        INode node = null;

        ArrayList source_objects = null;
        if (source instanceof AxiomObject) {
            source_objects = new ArrayList();
            node = ((AxiomObject) source).node;
            if (node != null) {
                source_objects.add(node.getID());
            }
        } else if (source instanceof Node) { 
            source_objects = new ArrayList();
            node = (Node) source;
            if (node != null) {
                source_objects.add(node.getID());
            }
        } else if (source instanceof String) {
            source_objects = lqd.getNodesByPath((String) source);
        } 

        final int source_size = source_objects == null ? 0 : source_objects.size();
        if (source_size == 0) {
            return Context.getCurrentContext().newArray(core.global, new Object[0]);
        }
        
        ArrayList target_objects = null;
        if (target instanceof AxiomObject) {
            target_objects = new ArrayList();
            node = ((AxiomObject) target).node;
            if (node != null) {
                target_objects.add(node.getID());
            }
        } else if (target instanceof Node) { 
            target_objects = new ArrayList();
            node = (Node) target;
            if (node != null) {
                target_objects.add(node.getID());
            }
        } else if (target instanceof String) {
            target_objects = lqd.getNodesByPath((String) target);
        }

        final int targets_size = target_objects == null ? 0 : target_objects.size();
        if (targets_size == 0) {
            return Context.getCurrentContext().newArray(core.global, new Object[0]);
        }
        
        final int mode; 
        RequestEvaluator req_eval = app.getCurrentRequestEvaluator();
        if (req_eval != null) {
            mode = req_eval.getLayer();
        } else {
            mode = DbKey.LIVE_LAYER;
        }
        
        return lqd.getReferences(source_objects, target_objects, mode);
    }
    
    public static ResourceProperties getResourceProperties(Application app, ArrayList prototypes) {
    	int length = 0;
    	final TypeManager tmgr = app.typemgr;
    	ResourceProperties combined_props = new ResourceProperties();
    	if (prototypes != null && (length = prototypes.size()) > 0) {
			BooleanQuery proto_query = new BooleanQuery();
			for (int i = 0; i < length; i++) {
				String prototype = (String) prototypes.get(i);

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
		} else {
			ArrayList protoarr = app.getSearchablePrototypes();
			BooleanQuery proto_query = new BooleanQuery();
			for (int i = protoarr.size() - 1; i > -1; i--) {
				String protoName = (String) protoarr.get(i);

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
		}
    	
    	return combined_props;
    }

    public Scriptable getVersionFields(Object obj, Object fields, Object prototypes, Object filter, Object options) throws Exception{
        if(obj == null){
            throw new Exception("app.getVersions(): First parameter cannot be null, please specify an object _id or an AxiomObject");
    	}

        if (fields == null || fields == Undefined.instance) {
            throw new Exception("app.getVersions(): Fields cannot be null, fields must be a string or an Javascript Object");
        }        

    	IFilter _filter = QueryBean.getFilterFromObject(filter);        
        if (_filter == null) {
            throw new Exception("LuceneQueryDispatcher.getVersions(): Could not determine the query filter.");
        }

        ArrayList ldbProtos = new ArrayList();
        ArrayList rdbProtos = new ArrayList();
        HashMap<String, ArrayList> extDbProtos = new HashMap<String, ArrayList>();
        setPrototypeArrays(this.app, prototypes, ldbProtos, rdbProtos, extDbProtos);
        boolean validExtDb = validExtDb(extDbProtos);
        ArrayList results = new ArrayList();
        if(ldbProtos.size() > 0 && (rdbProtos.size() > 0 || validExtDb) ||
        		rdbProtos.size() > 0 && (ldbProtos.size() > 0 || validExtDb) ||
        		validExtDb && (rdbProtos.size() > 0 || ldbProtos.size() > 0)){
        	throw new Exception("app.getVersionFields does not support aggregate data stores");
        } else if(validExtDb){
        	throw new Exception("app.getVersionFields does not support external data stores");
        } else if(ldbProtos.size() > 0){
        	results = this.lqd.getVersionFields(obj, fields, ldbProtos, _filter, options);
        } else if(rdbProtos.size() > 0) {
        	throw new Exception("app.getVersionFields does not support relational data stores");
        }
        return Context.getCurrentContext().newArray(this.core.global, results.toArray());
    }
}