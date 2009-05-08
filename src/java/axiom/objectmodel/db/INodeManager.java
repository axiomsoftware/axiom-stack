/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile: NodeManager.java,v $
 * $Author: hannes $
 * $Revision: 1.146 $
 * $Date: 2006/03/21 16:52:46 $
 */

/* 
 * Modified by:
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */
package axiom.objectmodel.db;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import axiom.framework.ErrorReporter;
import axiom.framework.core.Application;
import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.*;
import axiom.objectmodel.dom.LuceneDatabase;
import axiom.scripting.rhino.AxiomObject;
import axiom.util.ResourceProperties;

/**
 * The NodeManager is responsible for fetching Nodes from the internal or
 * external data sources, caching them in a least-recently-used Hashtable,
 * and writing changes back to the databases.
 */
abstract class INodeManager {
    protected Application app;
	private IDGenerator idgen;
    private ArrayList<NodeChangeListener> listeners = new ArrayList<NodeChangeListener>();

	public static ObjectCache cache;
    public static final String DEFAULT_DB = "axiom.objectmodel.dom.LuceneDatabase";

    // a wrapper that catches some Exceptions while accessing this NM
    public final WrappedNodeManager safe;

    /**
     *  Create a new NodeManager for Application app.
     */
    public INodeManager(Application app) {
        this.app = app;
        safe = new WrappedNodeManager(this);
    }
    
    /**
     * Initialize the NodeManager for the given dbHome and 
     * application properties.
     */
    abstract void init(File dbHome, Properties props)
            throws DatabaseException, ClassNotFoundException,
                   IllegalAccessException, InstantiationException;
    
    /**
     * Setup the databases used in the NodeManager
     * 
     * @param dbHome - Location of the databases on the file system
     */
    abstract void setupDbs(File dbHome);

    /**
     * Gets the application's root node.
     */
    abstract Node getRootNode() throws Exception;
    
    /**
     * Checks if the given node is the application's root node.
     */
    abstract boolean isRootNode(Node node);

    /**
     *  app.properties file has been updated. Reread some settings.
     */
    abstract void updateProperties(Properties props);

    /**
     *  Shut down this node manager. This is called when the application 
     *  using this node manager is stopped.
     */
    abstract void shutdown() throws DatabaseException;

    /**
     *  Delete a node from the database.
     */
    abstract void deleteNode(Node node) throws Exception;
    
    /**
     * Get a Node based on it's Key
     * 
     * @param key - Key that identifies the node.
     * @return Node
     * @throws Exception
     */
    abstract Node getNode(Key key) throws Exception;
    
    /**
     *  Get a node by key. This is called from a node that already holds
     *  a reference to another node via a NodeHandle/Key.
     */
    abstract Node getNode(Key key, boolean multiLayers) throws Exception;

    /**
     *  Get a node by relation, using the home node, the relation and a key to apply.
     *  In contrast to getNode (Key key), this is usually called when we don't yet know
     *  whether such a node exists.
     */
    abstract Node getNode(Node home, String kstr, Relation rel) throws Exception;
    
    /**
     * Retrieve the key of a child Node
     * 
     * @param field
     * @param key
     * @param parentid
     * @param mode
     * @return
     * @throws Exception
     */
    abstract Key getChildKey(String field, String key, String parentid, int mode) throws Exception;

    /**
     * Register a Node in the node cache.
     */
    abstract void registerNode(Node node);


    /**
     * Register a node in the node cache using the key argument.
     */
    abstract void registerNode(Node node, Key key);

    /**
     * Remove a node from the node cache. If at a later time it is accessed again,
     * it will be refetched from the database.
     */
    abstract void evictNode(Node node);

    /**
     * Remove a node from the node cache. If at a later time it is accessed again,
     * it will be refetched from the database.
     * 
     * TODO: refactor method signature
     */
    abstract void evictNodeByKey(Key key);

    /**
     * Used when a key stops being valid for a node. The cached node itself
     * remains valid, if it is present in the cache by other keys.
     */
    abstract void evictKey(Key key);

    
    ////////////////////////////////////////////////////////////////////////
    // methods to do the actual db work
    ////////////////////////////////////////////////////////////////////////

    /**
     *  Insert a new node in the embedded database or a relational database table,
     *  depending on its db mapping.
     */
    abstract void insertNode(IDatabase db, ITransaction txn, Node node) throws IOException, SQLException, ClassNotFoundException;
    
    /**
     *  calls onPersist function for the AxiomObject
     */
    abstract void invokeOnPersist(Node node);

    /**
     *  Updates a modified node in the data source
     *
     * @return true if the DbMapping of the updated Node is to be marked as updated via
     *              DbMapping.setLastDataChange
     */
    abstract boolean updateNode(IDatabase db, ITransaction txn, Node node) throws IOException, SQLException, ClassNotFoundException;

    /**
     *  Performs the actual deletion of a node from either the embedded or an external
     *  SQL database.
     */
    abstract void deleteNode(IDatabase db, ITransaction txn, Node node) throws Exception;


    /**
     * Generate a new ID for a given type, delegating to our IDGenerator if set.
     */
    protected String generateID(DbMapping map) throws Exception {
        if (idgen != null) {
            // use our custom IDGenerator
            return idgen.generateID(map);
        } else {
            return doGenerateID(map);
        }
    }

    /**
     * Generates an ID, using a method matching the given DbMapping.
     */
    abstract String doGenerateID(DbMapping map) throws Exception;

    /**
     * Gererates an ID for use with the embedded database.
     */
    abstract String generateEmbeddedID(DbMapping map) throws Exception;

    /**
     * Set the the id for the embedded data source.
     */
    abstract void setEmbeddedID(String _id);
    
    abstract boolean missingFromCache(Key[] keys);

    ///////////////////////////////////////////////////////////////////////////////////////
    // getNode methods
    ///////////////////////////////////////////////////////////////////////////////////////
    private Node getNodeByKey(ITransaction txn, DbKey key, boolean multiLayers) throws Exception {
    	return this.getNodeByKey(txn, key, null, multiLayers);
    }
    
    abstract Node getNodeByKey(ITransaction txn, DbKey key, String type, boolean multiLayers) throws Exception;

    /**
     *  Get an array of the the keys currently held in the object cache
     */
    public Object[] getCacheEntries() {
        return null;
    }

    /**
     * Get the number of elements in the object cache
     */
    public int countCacheEntries() {
        return cache.size();
    }

    /**
     * Clear the object cache, causing all objects to be recreated.
     */
    public void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }

    /** 
     * Add a listener that is notified each time a transaction commits 
     * that adds, modifies or deletes any Nodes.
     */
    public void addNodeChangeListener(NodeChangeListener listener) {
        listeners.add(listener);
    }
    
    /** 
     * Remove a previously added NodeChangeListener. 
     */
    public void removeNodeChangeListener(NodeChangeListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Let transactors know if they should collect and fire NodeChangeListener
     * events
     */
    protected boolean hasNodeChangeListeners() {
        return !listeners.isEmpty();
    }
    
    /**
     * Called by transactors after committing.
     */
    protected void fireNodeChangeEvent(List<Node> inserted, List<Node> updated, List<Node> deleted, List<Node> parents) {
        int l = listeners.size();

        for (int i=0; i<l; i++) {
            try {
                ((NodeChangeListener) listeners.get(i)).nodesChanged(inserted, updated, deleted, parents);
            } catch (Error e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }    
    
    private boolean isValidInCache(Key key) {
    	final int layer = key.getLayer();
    	if (layer > DbKey.LIVE_LAYER) {
    		RequestEvaluator reqeval = this.app.getCurrentRequestEvaluator();
    		if (reqeval != null) {
    			Object[] domains = this.app.getDomainsForLayer(layer);
    			final String id = key.getID();
    			for (int i = 0; i < domains.length; i++) {
    				if (!reqeval.getSession().isDraftIdOn(id, (String) domains[i], layer)) {
    					return false;
    				}
    			}
    		}
    	}
    	return true;
    }
    
    public Node getNodeFromTransaction(Key key) {
        Transactor tx = (Transactor) Thread.currentThread();
        Node node = tx.getVisitedNode(key);

        if ((node != null) && (node.getState() != Node.INVALID)) {
            return node;
        }
        
        return null;
    }
    
    public Node getNodeFromCache(Key key) {
        Node node = (Node) cache.get(key);
        
        long now = System.currentTimeMillis();
        if (node != null && node.dbmap != null && node.dbmap.timeout != -1L && node.dbmap.timeout < (now - node.created)) {
            cache.remove(node.getKey());
            return null;
        }
        
        if ((node != null) && (node.getState() != Node.INVALID)) {
            return node;
        }
        
        return null;
    }
    
    /**
     * Updates the cache with the node, if the existing node is invalidated or null
     * 
     * @param node
     * @return
     */
    public Node conditionalCacheUpdate(Node node) {
        if (node != null) {
            Key k = node.getKey();
            // synchronize with cache
            synchronized (cache) {
                Node oldnode = (Node) cache.put(k, node);

                if ((oldnode != null) && !oldnode.isNullNode() && (oldnode.getState() != Node.INVALID)) {
                    cache.put(k, oldnode);
                    node = oldnode;
                }
            }
            // end of cache-synchronized section
        }
        
        return node;
    }
    
    /**
     * Updates the cache with the new node 
     * 
     * @param node
     */
    public void cacheUpdate(Node node) {
    	// synchronize with cache
        synchronized (cache) {
            Node oldnode = (Node) cache.put(node.getKey(), node);

            if ((oldnode != null) && !oldnode.isNullNode() && (oldnode.getState() != Node.INVALID)) {
                cache.put(oldnode.getKey(), oldnode);
                node = oldnode;
            }
        }
        // end of cache-synchronized section
    }
    
    public void conditionalNodeVisit(Key key, Node node) {
        Transactor tx = (Transactor) Thread.currentThread();
        if (node != null) {
            tx.visitCleanNode(key, node);
        }
    }
    
    /**
     * Gets the current size of the cache.    
     * @return
     */
    public int getCurrentCacheSize() {
        return cache.size();
    }
    
    /**
     * Get a node in a layer
     * 
     * @param node
     * @param layer
     * @return
     */
    protected Node getNodeInLayer(Node node, final int layer) {
    	DbKey dkey = new DbKey(node.getDbMapping(), node.getID(), layer);
    	Node nnode = null;
    	try {
    		nnode = getNode(dkey, false);
    	} catch (Exception ex) {
    		nnode = null;
    	}
    	return nnode;
    }
    
    /**
     * Will retrieve or create a node from a specified layer.
     * 
     * @param node - The node you are looking for.
     * @param layer - The layer to look in.
     * @param create - Allows you to specify whether you would like to create the Node should it not exist.
     * @return The new node within the layer.
     * @throws Exception
     */
    public Node getNodeInLayer(Node node, final int layer, boolean create) throws Exception {
    	if (app.debug()) 
    		app.logEvent("NodeManager.getNodeInLayer() for " + node.logString() 
    				+ " on layer " + layer);
    	
    	Node nnode = getNodeInLayer(node, layer);
    	
    	Node parent = (Node) node.getParent();
        if (parent != null) {
        	Node nparent = getNodeInLayer(parent, layer);
        	if (nparent != null) {
        		parent = nparent;
        	}
        }
        
    	if (nnode != null) {
    		if (app.debug())
        		app.logEvent("NodeManager.getNodeInLayer(), getting node " 
        				+ nnode.logString() + ", hashcode = " + nnode.hashCode() 
        				+ ", state = " + nnode.getState()
        				+ ", id = " + nnode.getString("id")
        				+ ", layer = " + nnode.getLayer() 
        				+ ", layerinStorage = " + nnode.getLayerInStorage()
        				+ " by thread " + Thread.currentThread());
    		
    		final int state = nnode.getState();
    		if (state == Node.DELETED || (state == Node.MODIFIED && nnode.getParent() == null)) {
    			nnode.checkWriteLock();
    			nnode.markAs(Node.MODIFIED);
    			nnode.setParent(parent);
    			nnode.cloneProperties(node);
    		}
    		return nnode;
    	}
    	
    	if (nnode == null && create) {
    		nnode = this.createNodeInLayer(node, layer);
    	}
        
        cacheUpdate(nnode);
        
    	return nnode;
    }
    
    protected Node createNodeInLayer(Node node, final int mode) throws Exception {
    	Node parent = (Node) node.getParent();
        if (parent != null) {
        	Node nparent = getNodeInLayer(parent, mode);
        	if (nparent != null) {
        		parent = nparent;
        	}
        }
    	
    	Node nnode = new Node(node.getName(), node.getID(), node.getPrototype(), safe, node.created, node.lastmodified);
    	nnode.getKey(mode);
    	nnode.setLayer(mode);
    	nnode.setLayerInStorage(mode);
        nnode.setParent(parent);
        nnode.setSubnodes(node.getSubnodeList());
        nnode.cloneProperties(node);
        nnode.markAs(Node.NEW);
        
        if (app.debug())
    		app.logEvent("NodeManager.getNodeInLayer(), created new node "
    				+ nnode.logString() + ", hashcode = " + nnode.hashCode()
    				+ ", id = " + nnode.getString("id")
    				+ " by thread " + Thread.currentThread());
    	
    	return nnode;
    }
    
    public void deleteNodeInLayer(Node node, final int mode) {
    	if (app.debug())
    		app.logEvent("NodeManager.deleteNodeInLayer() for " + node.logString()
    				+ " on layer " + mode);
    	
    	DbKey dkey = new DbKey(node.getDbMapping(), node.getID(), mode);
    	Node nnode = null;
    	try {
    		nnode = getNode(dkey, false);
    	} catch (Exception ex) {
    		nnode = null;
    	}
    	
    	if (nnode != null) {
    		nnode.checkWriteLock();
    		if (app.debug())
        		app.logEvent("NodeManager.deleteNodeInLayer(), setting delete status on " 
        				+ nnode.logString() + ", hashcode = " + nnode.hashCode() 
        				+ ", state = " + nnode.getState()
        				+ ", id = " + nnode.getString("id")
        				+ " by thread " + Thread.currentThread());
        	
    		nnode.markAs(Node.DELETED);
    		INode parent = nnode.getParent();
    		if (parent != null) {
    			parent.removeNode(nnode);
    		}
    		nnode.setParentHandle(null);
    	}

    	if (app.debug())
    		app.logEvent("NodeManager.deleteNodeInLayer(), evict key = " + dkey);
    	
    	this.evictKey(dkey);
    }
    
    public void saveNodeInLayer(Node node, final int layer) {
    	Key oldkey = node.getKey();
    	Transactor tx = (Transactor) Thread.currentThread();
    	IDatabase db = this.getDatabaseForMapping(node.dbmap);
    	ITransaction txn = tx.getTransaction(db);
		
    	DbKey dkey = new DbKey(node.dbmap, node.getID(), layer);
    	node.setKey(dkey);
    	node.updateLayersOnReferences(layer);
    	tx.visitCleanNode(dkey, node);

		Node dbNode = null;
    	try {
    		if (db instanceof LuceneDatabase) {
    			dbNode = ((LuceneDatabase) db).getLuceneManager().retrieveFromIndexFixedMode(dkey.getID(), dkey.getLayer(), true);
    		}
    		String newPath = AxiomObject.getPath(node, node.getPrototype(), this.app);
    		String oldPath = null;
    		if (dbNode != null) {
    			oldPath = AxiomObject.getPath(dbNode, dbNode.getPrototype(), this.app);
    		}
    		if (newPath != null && oldPath != null && !newPath.equals(oldPath)) {
    			node.hasPathChanged = true;
    			if (this.app.debug()) 
    	        	this.app.logEvent("Node.saveNodeInLayer(): Path has changed on " 
    	        			+ node.logString());
    		}
    	} catch (Exception ignore) {
    		// object not found in current layer, ignore
    	}
    	
    	try {
    		INode oldnode = null;
    		try {
    			oldnode = ((LuceneDatabase) db).getLuceneManager().retrieveFromIndexFixedMode(oldkey.getID(), oldkey.getLayer(), true);
    		} catch (Exception ex) {
    			oldnode = null;
    		}
    		if (oldnode != null) {
    			db.deleteNode(txn, oldkey.getID(), oldkey.getLayer());
    			tx.deleteFromPathIndices(oldkey);
    		}
    	} catch (Exception ex) {
    		this.app.logError(ErrorReporter.errorMsg(this.getClass(), "saveNodeInLayer") 
    				+ ": Could not delete " + oldkey, ex);
    	}
    	
    	node.checkWriteLock();
    	if (dbNode != null) {
    		node.markAs(Node.MODIFIED);
    	} else {
    		node.markAs(Node.NEW);
    	}
    	
    	// synchronize with cache
        synchronized (cache) {
        	this.evictNodeByKey(oldkey);
            Node oldnode = (Node) cache.put(node.getKey(), node);

            if ((oldnode != null) && !oldnode.isNullNode() &&
                    (oldnode.getState() != Node.INVALID)) {
                cache.put(oldnode.getKey(), oldnode);
            }
        }
        // end of cache-synchronized section
    }
    
    public IDatabase getDatabaseForMapping(DbMapping dbm) {
    	IDatabase db = null;
    	if (dbm != null) {
    		String className = dbm.getClassName();
        	if (className != null) {
        		db = this.dbs.get(className);
        	}
    	}
    	if (db == null) {
    		db = this.defaultDb;
    	}
    	
    	return db;
    }
    
    public IDatabase getDefaultDb() {
    	return this.defaultDb;
    }
    
    public ArrayList<IDatabase> getDatabases() {
    	return new ArrayList<IDatabase>(this.dbs.values());
    }
    
    public void evictKeys(HashSet<Key> keyset) {
    	synchronized (cache) {
    		Iterator<Key> keys = keyset.iterator();
        	while (keys.hasNext()) {
        		this.evictNodeByKey(keys.next());
        	}
    	}
    }
    
} 