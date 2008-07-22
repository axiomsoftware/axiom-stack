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
 * $RCSfile: Transactor.java,v $
 * $Author: hannes $
 * $Revision: 1.28 $
 * $Date: 2006/05/11 18:36:56 $
 */

package axiom.objectmodel.db;


import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

//TODO:import org.apache.lucene.index.IndexDelta;
import org.apache.lucene.index.SegmentInfos;

import axiom.cluster.ClusterCommunicator;
//TODO:import axiom.cluster.HeadUpdateMsg;
import axiom.extensions.trans.TransactionManager;
import axiom.framework.ErrorReporter;
import axiom.framework.RequestTrans;
import axiom.framework.core.Application;
import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.DatabaseException;
import axiom.objectmodel.IDatabase;
import axiom.objectmodel.INode;
import axiom.objectmodel.ITransaction;
import axiom.objectmodel.dom.LuceneDatabase;
import axiom.objectmodel.dom.LuceneDatabase.LuceneTransaction;
import axiom.scripting.ScriptingEngine;
import axiom.scripting.rhino.*;

/**
 * A subclass of thread that keeps track of changed nodes and triggers
 * changes in the database when a transaction is commited.
 */
public class Transactor extends Thread {

    // The associated node manager
    NodeManager nmgr;

    // List of nodes to be updated
    private HashMap dirtyNodes;

    // List of visited clean nodes
    private HashMap cleanNodes;
    
    private ConcurrentHashMap txnNodes;

    // List of nodes whose child index has been modified
    private HashSet parentNodes;

    // Is a transaction in progress?
    private volatile boolean active;
    private volatile boolean killed;

    // Transactions for the databases
    protected HashMap<IDatabase,ITransaction> txns; 

    // Transactions for SQL data sources
    private HashMap sqlConnections;

    // Set of SQL connections that already have been verified
    private HashSet testedConnections;

    // when did the current transaction start?
    private long tstart;

    // a name to log the transaction. For HTTP transactions this is the rerquest path
    private String tname;

    boolean alreadyreported = false;
    
    // the associated TransactionManager
    protected TransactionManager tmgr;
    // Transaction for path indexing
    protected PathIndexingTransaction pitxn; 
    // Cluster Communicator if the app for this transactor is in a clustered environment
    private ClusterCommunicator clusterComm;
    
    private QueryBean qbean;
    
    private HashSet<Key> keysToEvict;
    
    /**
     * Creates a new Transactor object.
     *
     * @param runnable ...
     * @param group ...
     * @param nmgr ...
     */
    public Transactor(Runnable runnable, ThreadGroup group, NodeManager nmgr) {
        super(group, runnable, group.getName());
        this.nmgr = nmgr;

        dirtyNodes = new HashMap();
        cleanNodes = new HashMap();
        txnNodes = new ConcurrentHashMap();
        parentNodes = new HashSet();
        keysToEvict = new HashSet<Key>();
        
        txns = new HashMap<IDatabase,ITransaction>();

        sqlConnections = new HashMap();
        testedConnections = new HashSet();
        active = false;
        killed = false;
        
        tmgr = TransactionManager.newInstance(nmgr.app.getTransSource()); 
        
        this.clusterComm = nmgr.app.getClusterCommunicator();
    }
    
    public void shutdown() {
        if (this.qbean != null) {
            this.qbean.shutdown();
            this.qbean = null;
        }
    }

    /**
     * Mark a Node as modified/created/deleted during this transaction
     *
     * @param node ...
     */
    public void visitNode(Node node) {
        if (node != null) {
            Key key = node.getKey();

            if (!dirtyNodes.containsKey(key)) {
                dirtyNodes.put(key, node);
                txnNodes.put(key, node);
            }
        }
    }

    /**
     * Unmark a Node that has previously been marked as modified during the transaction
     *
     * @param node ...
     */
    public void dropNode(Node node) {
        if (node != null) {
            Key key = node.getKey();

            dirtyNodes.remove(key);
            txnNodes.remove(key);
        }
    }

    /**
     * Keep a reference to an unmodified Node local to this transaction
     *
     * @param node ...
     */
    public void visitCleanNode(Node node) {
        if (node != null) {
            Key key = node.getKey();

            if (!cleanNodes.containsKey(key)) {
                cleanNodes.put(key, node);
                txnNodes.put(key, node);
            }
        }
    }

    /**
     * Keep a reference to an unmodified Node local to this transaction
     *
     * @param key ...
     * @param node ...
     */
    public void visitCleanNode(Key key, Node node) {
        if (node != null) {
            if (!cleanNodes.containsKey(key)) {
            	cleanNodes.put(key, node);
                txnNodes.put(key, node);
            }
        }
    }
    
    public void evictAtTxnCompletion(Key key) {
    	if (key != null) {
    		this.keysToEvict.add(key);
    	}
    }

    /**
     * Get a reference to an unmodified Node local to this transaction
     *
     * @param key ...
     *
     * @return ...
     */
    public Node getVisitedNode(Object key) {
        return (key == null) ? null : (Node) cleanNodes.get(key);
    }
    
    public void removeVisitedNode(Object key) {
    	if (key != null) {
    		this.cleanNodes.remove(key);
    	}
    }

    /**
     *
     *
     * @param node ...
     */
    public void visitParentNode(Node node) {
        parentNodes.add(node);
    }


    /**
     *
     *
     * @return ...
     */
    public boolean isActive() {
        return active;
    }

    /**
     *
     *
     * @param src ...
     * @param con ...
     */
    public void registerConnection(DbSource src, Connection con) {
        sqlConnections.put(src, con);
        // we assume a freshly created connection is ok.
        testedConnections.add(src);
    }

    /**
     *
     *
     * @param src ...
     *
     * @return ...
     */
    public Connection getConnection(DbSource src) {
        Connection con = (Connection) sqlConnections.get(src);
        if (con != null && !testedConnections.contains(src)) {
            // Check if the connection is still alive by executing a simple statement.
            try {
                Statement stmt = con.createStatement();
                stmt.execute("SELECT 1");
                stmt.close();
                testedConnections.add(src);
            } catch (SQLException sx) {
                try {
                    con.close();
                } catch (SQLException ignore) { }
                return null;
            }
        }
        return con;
    }

    /**
     * Start a new transaction with the given name.
     *
     * @param name The name of the transaction. This is usually the request
     * path for the underlying HTTP request.
     *
     * @throws Exception ...
     */
    public synchronized void begin(String name, final int mode) throws Exception {
        if (killed) {
            throw new DatabaseException("Transaction started on killed thread");
        }

        if (active) {
            abort();
        }

        dirtyNodes.clear();
        cleanNodes.clear();
        txnNodes.clear();
        parentNodes.clear();
        txns.clear();
        keysToEvict.clear();
        
        tmgr.clearTransactionUnits();
        tmgr.startTransaction();
        // managing all sub transactions through the transaction mgr
        ArrayList<IDatabase> dbs = this.nmgr.getDatabases();
        final int size = dbs.size();
        for (int i = 0; i < size; i++) {
        	IDatabase db = dbs.get(i);
        	ITransaction txn = db.beginTransaction(mode);
        	tmgr.addTransactionUnit(txn);
        	txns.put(db, txn);
        }
        pitxn = new PathIndexingTransaction(nmgr.app.getPathIndexer());
        tmgr.addTransactionUnit(pitxn);
        
        active = true;
        tstart = System.currentTimeMillis();
        tname = name;
        testedConnections.clear();
        alreadyreported = false;
    }

    public synchronized void commit(final int mode) throws Exception {
        this.commit(mode, nmgr.app.getCurrentRequestEvaluator());
    }
    
    /**
     * Commit the current transaction, persisting all changes to DB.
     *
     * @throws Exception ...
     */
    public synchronized void commit(final int mode, RequestEvaluator reqeval) 
    throws Exception {
        if (killed) {
            abort();
            return;
        }

        int inserted = 0;
        int updated = 0;
        int deleted = 0;

        ArrayList insertedNodes = null;
        ArrayList updatedNodes = null;
        ArrayList deletedNodes = null;
        ArrayList modifiedParentNodes = null;
        ArrayList<DbKey> invalidationList = null;
        // if nodemanager has listeners collect dirty nodes
        final boolean hasListeners = nmgr.hasNodeChangeListeners();
        ScriptingEngine engine = reqeval.getScriptingEngine();
        final boolean hasOnCommit = engine.hasFunction(null, "onCommit");
        final boolean hasPostCommit = engine.hasFunction(null, "postCommit");
        
        if (hasListeners || hasOnCommit) {
            insertedNodes = new ArrayList();
            updatedNodes = new ArrayList();
            deletedNodes = new ArrayList();
            modifiedParentNodes = new ArrayList();
        }
        
        if (!dirtyNodes.isEmpty()) {
            Object[] dirty = dirtyNodes.values().toArray();

            // the set to collect DbMappings to be marked as changed
            HashSet dirtyDbMappings = new HashSet();

            /*
             * Reworked to do recursive deletes and deleting of references
             * if the node to be committed does not have a parent.
             * 
             */
            HashMap commitNodes = new HashMap(); 
            
            for (int i = 0; i < dirty.length; i++) {
                Node node = (Node) dirty[i];
                final String nodeId = node.getID();
                
                if (node.dbmap != null && node.dbmap.isRelational()) {
                    // not doing this for relational db nodes, b/c there is no
                    // strict parent child mapping
                    commitNodes.put(nodeId + ":" + node.getPrototype(), node);
                    continue; 
                }
                
                int nstate = node.getState();
                INode parent = node.getParent();
                
                if (parent == null && !isSpecialNode(nodeId)
                        && (node.dbmap == null || !node.dbmap.isRelational())) {
                    
                    if (nstate == Node.MODIFIED || nstate == Node.NEW || nstate == Node.DELETED) {
                    	HashMap additionalDeletes = this.recursiveDeletes(node, mode);
                        Iterator iter = additionalDeletes.values().iterator();
                        
                        while (iter.hasNext()) {
                            Node n = (Node) iter.next();
                            
                            QueryBean qb = this.getQueryBean();
                            Object[] refs = (Object[]) qb.sources(n);

                            final String nId = n.getID();
                            int refslen = refs.length;
                            for (int z = 0; z < refslen; z++) {
                                Node curr = (Node) refs[z];
                                AxiomObject currobj = AxiomObject.createAxiomObject(curr.getPrototype(), curr);
                                
                                Enumeration e = curr.properties();
                                while (e.hasMoreElements()) {
                                    Property p = (Property) curr.get(e.nextElement().toString());
                                    final int ptype = p.getType();
                                    if (ptype == Property.REFERENCE) {
                                        Reference ref = p.getReferenceValue();
                                        if (ref != null && ref.getTargetKey().getID().equals(nId)) {
                                            currobj.put(p.getName(), currobj, null);
                                        }
                                    } else if (ptype == Property.MULTI_VALUE) {
                                        MultiValue mv = p.getMultiValue();
                                        if (mv != null && mv.getValueType() == Property.REFERENCE) {
                                            Object[] mvvalues = mv.getValues();
                                            ArrayList newvalues = new ArrayList();
                                            for (int y = 0; y < mvvalues.length; y++) {
                                                Reference ref = (Reference) mvvalues[y];
                                                if (!ref.getTargetKey().getID().equals(nId)) {
                                                    newvalues.add(ref);
                                                }
                                            }
                                            curr.lastmodified = System.currentTimeMillis();
                                            mv.setValues(newvalues);
                                            currobj.put(p.getName(), currobj, mv);
                                        }
                                    } else if (ptype == Property.XHTML) {
                                    	Object xhtml = p.getXHTMLValue();
                                    	xhtml = removeRefsFromXhtml(xhtml, n);
                                    	currobj.put(p.getName(), currobj, xhtml);
                                    }
                                }
                                
                                if (curr.getState() != Node.DELETED) {
                                    curr.setState(Node.MODIFIED);
                                }
                                commitNodes.put(curr.getID(), curr);
                            }
                            
                            if (nstate == Node.MODIFIED || nstate == Node.DELETED) {
                                n.setState(Node.DELETED);
                                commitNodes.put(n.getID(), n);
                            }
                        }
                    }
                } else if (nstate == Node.NEW || nstate == Node.MODIFIED || nstate == Node.DELETED) {
                    // If the parent of a node has changed, find all XHTML references
                	// pointing to the node and all children of the node and update
                	// those references to the new URL
                	if (nstate == Node.MODIFIED && node.hasPathChanged()) {
                		ArrayList<Node> parentChangedNodes = node.getSubChildren();
                		parentChangedNodes.add(0, node);
                		final int sizeOfList = parentChangedNodes.size();
                		for (int a = 0; a < sizeOfList; a++) {
                			Node n = parentChangedNodes.get(a);
                            
                            QueryBean qb = this.getQueryBean();
                            HashMap options = new HashMap();
                            options.put(QueryDispatcher.LAYER, new Integer(node.getKey().getLayer()));
                            Object[] refs = (Object[]) qb.sources(n, null, null, options);

                            int refslen = refs.length;
                            for (int z = 0; z < refslen; z++) {
                                Node curr = (Node) refs[z];
                                AxiomObject currobj = AxiomObject.createAxiomObject(curr.getPrototype(), curr);
                                
                                Enumeration e = curr.properties();
                                while (e.hasMoreElements()) {
                                    Property p = (Property) curr.get(e.nextElement().toString());
                                    if (p.getType() == Property.XHTML) {
                                    	Object xhtml = p.getXHTMLValue();
                                    	xhtml = updateRefsInXhtml(xhtml, n);
                                    	currobj.put(p.getName(), currobj, xhtml);
                                    }
                                }
                                
                                if (curr.getState() != Node.DELETED) {
                                    curr.setState(Node.MODIFIED);
                                }
                                commitNodes.put(curr.getID(), curr);
                            }
                        }
                	} 
                	
                	// only add these nodes to the list of commit nodes if they dont conflict with
                	// any of the update/remove procedures executed above in the case of a recursive
                	// delete
                	if (commitNodes.get(nodeId) == null) {
                		commitNodes.put(nodeId, node);
                	}
                }
            }
            
            dirty = commitNodes.values().toArray();
            final int dirtylength = dirty.length;
            
            if (this.clusterComm != null) {
                invalidationList = new ArrayList<DbKey>(dirtylength);
            }
            
            for (int i = 0; i < dirtylength; i++) {
                Node node = (Node) dirty[i];
                           
                IDatabase db = this.nmgr.getDatabaseForMapping(node.dbmap);
                ITransaction txn = this.txns.get(db);
                
                // update nodes in db
                int nstate = node.getState();
                
                if (nstate == Node.NEW) {
                    pitxn.addResource(node, ITransaction.ADDED);

                    nmgr.insertNode(db, txn, node);
                    dirtyDbMappings.add(node.getDbMapping());
                    node.setState(Node.CLEAN);
                    if (node.getLayer() > node.getLayerInStorage()) {
                    	node.setLayerInStorage(node.getLayer());
                    }

                    // register node with nodemanager cache
                    nmgr.registerNode(node);

                    if (hasListeners || hasOnCommit) {
                        insertedNodes.add(node);
                    }

                    inserted++;
                    nmgr.app.logEvent("inserted: Node " + node.getPrototype() + "/" +
                                  node.getID() + "/" + node.getKey().getLayer());
                } else if (nstate == Node.MODIFIED) {
                    // update the node's path in the path index db
                    if (node.hasPathChanged()) {
                        pitxn.addResource(node, ITransaction.UPDATED);
                        ArrayList<Node> children = node.getSubChildren();
                        final int size = children.size();
                        for (int j = 0; j < size; j++) {
                        	Node subchild = children.get(j);
                        	final int sstate = subchild.getState();
                        	if (sstate == Node.CLEAN) {
                        		pitxn.addResource(subchild, ITransaction.UPDATED);
                        	}
                        }
                    }
                    
                    // only mark DbMapping as dirty if updateNode returns true
                    if (nmgr.updateNode(db, txn, node)) {
                        dirtyDbMappings.add(node.getDbMapping());
                    }
                    
                    if (invalidationList != null) {
                        invalidationList.add((DbKey)node.getKey());
                    }
                    
                    node.setState(Node.CLEAN);

                    // update node with nodemanager cache
                    nmgr.registerNode(node);

                    if (hasListeners || hasOnCommit) {
                        updatedNodes.add(node);
                    }

                    updated++;
                    nmgr.app.logEvent("updated: Node " + node.getPrototype() + "/" +
                                      node.getID() + "/" + node.getKey().getLayer());
                    
                    deleted += deletePreviewNodes(node, dirtyDbMappings, invalidationList, deletedNodes, hasListeners, hasOnCommit);
                } else if (nstate == Node.DELETED) {
                    nmgr.app.logEvent("deleting: Node " + node.getPrototype() + "/" +
                                      node.getID() + "/" + node.getKey().getLayer());
                    // delete a node's path from path indexer upon deletion
                    pitxn.addResource(node.getKey(), ITransaction.DELETED);
                    nmgr.deleteNode(db, txn, node);
                    dirtyDbMappings.add(node.getDbMapping());

                    if (invalidationList != null) {
                        invalidationList.add((DbKey)node.getKey());
                    }
                    
                    // remove node from nodemanager cache
                    nmgr.evictNode(node);

                    if (hasListeners || hasOnCommit) {
                        deletedNodes.add(node);
                    }

                    deleted++;
                    
                    deleted += deletePreviewNodes(node, dirtyDbMappings, invalidationList, deletedNodes, hasListeners, hasOnCommit);
                }

                node.clearWriteLock();
            }

            // set last data change times in db-mappings
            long now = System.currentTimeMillis();
            for (Iterator i = dirtyDbMappings.iterator(); i.hasNext(); ) {
                DbMapping dbm = (DbMapping) i.next();
                if (dbm != null) {
                    dbm.setLastDataChange(now);
                }
            }
        }

        long now = System.currentTimeMillis();

        if (!parentNodes.isEmpty()) {
            // set last subnode change times in parent nodes
            for (Iterator i = parentNodes.iterator(); i.hasNext(); ) {
                Node node = (Node) i.next();
                node.setLastSubnodeChange(now);
                if (hasListeners) {
                    modifiedParentNodes.add(node);
                }
            }
        }

        if (hasListeners) {
            nmgr.fireNodeChangeEvent(insertedNodes, updatedNodes,
                                     deletedNodes, modifiedParentNodes);
        }

        if (active) {
            active = false;
            /*
             * Commit the JDBC transactions, enables us to have jdbc transactionality
             */
            try {
                commitTransactions();
            } catch (SQLException sqle) {
                nmgr.app.logError(ErrorReporter.errorMsg(this.getClass(), "commit") 
                		+ "Could not commit the transactions on the JDBC connections", 
                		sqle);
            }
            
            if (hasOnCommit) {
                this.onCommit(insertedNodes, updatedNodes, deletedNodes, reqeval);
            }
            
            this.commitTransactionsDb(invalidationList);
            
            if (hasPostCommit) {
                this.postCommit(reqeval);
            }

            this.txns.clear();
        }
        
        String remoteAddr = "", sessionId = "", requestPath = tname; 
        int cacheCount = this.nmgr.getCurrentCacheSize(), sessionCount = this.nmgr.app.countSessions();
        if (reqeval != null) {
            RequestTrans reqtrans = reqeval.getRequest();
            sessionId = reqtrans.getSession();
            HttpServletRequest srequest = reqtrans.getServletRequest();
            if (srequest != null) {
                remoteAddr = srequest.getRemoteAddr();
            }
        }
        nmgr.app.logAccess(requestPath + " [" + remoteAddr + "], sid = " + sessionId +
                           ", " + inserted +
                           " ins, " + updated +
                           " upd, " + deleted + " del in " +
                           (now - tstart) + " ms, cc = " + cacheCount + 
                           ", sc = " + sessionCount + ", re = " + 
                           this.nmgr.app.countEvaluators() + "," +
                           this.nmgr.app.countActiveEvaluators() + "," +
                           this.nmgr.app.countFreeEvaluators());

        this.nmgr.evictKeys(this.keysToEvict);
        
        // clear the node collections
        dirtyNodes.clear();
        cleanNodes.clear();
        txnNodes.clear();
        parentNodes.clear();
        testedConnections.clear();
        keysToEvict.clear();
        
        // unset transaction name
        tname = null;
    }
    
    protected void onCommit(ArrayList insertedNodes, ArrayList updatedNodes, ArrayList deletedNodes, RequestEvaluator reqeval) 
    throws Exception {
        ScriptingEngine engine = reqeval.getScriptingEngine();
        Object[] args = new Object[3];
        args[0] = insertedNodes.toArray();
        args[1] = updatedNodes.toArray();
        args[2] = deletedNodes.toArray();
        engine.invoke(null, "onCommit", args, RhinoEngine.ARGS_WRAP_DEFAULT, false);
    }
    
    protected void postCommit(RequestEvaluator reqeval) throws Exception {
        ScriptingEngine engine = reqeval.getScriptingEngine();
        Object[] args = new Object[0];
        engine.invoke(null, "postCommit", args, RhinoEngine.ARGS_WRAP_DEFAULT, false);
    }
    
    protected void commitTransactionsDb(ArrayList<DbKey> invalidationList) {
        boolean isMaster = this.clusterComm != null ? this.clusterComm.isMaster() : false;
        if (this.clusterComm == null || isMaster) {
            this.tmgr.executeIndividualTransactions();

            TransSource tsource = this.nmgr.app.getTransSource();
            synchronized (tsource) {
                this.tmgr.executeTransactionCommits();
                this.tmgr.commitTransaction();
                this.tmgr.postTransaction();
            }
            
            if (this.clusterComm != null) {
            	ITransaction txn = this.getTransaction(this.nmgr.defaultDb);
                //TODO:this.clusterComm.sendIndexDeltaMessage(((LuceneTransaction) txn).getIndexDelta());
            }
        } else { // clustered environment and we are the slave servers
        	/*TODO:LuceneDatabase ldb = (LuceneDatabase) this.nmgr.defaultDb;
        	ITransaction txn = this.getTransaction(ldb);
            
            long id = -1L;
            if (ldb.isCounterDirty()) {
                id = ldb.getID();
            }

            String[] ids = null, paths = null;
            int[] ops = null;
            ArrayList toAdd = this.pitxn.getAddList();
            ArrayList toUpdate = this.pitxn.getUpdateList();
            ArrayList toDelete = this.pitxn.getDeleteList();
            final int toAddSize = toAdd.size();
            final int toUpdateSize = toUpdate.size();
            final int toDeleteSize = toDelete.size();
            ids = new String[toAddSize + toUpdateSize + toDeleteSize];
            paths = new String[toAddSize + toUpdateSize + toDeleteSize];
            ops = new int[toAddSize + toUpdateSize + toDeleteSize];
            for (int i = 0; i < toAddSize; i++) {
            	Node n = (Node) toAdd.get(i);
            	ids[i] = n.getID();
            	try {
            		paths[i] = PathIndexer.getNodeHref(n, this.nmgr.app);
            	} catch (Exception ex) {
            		paths[i] = null;
            	}
            	ops[i] = ITransaction.ADDED;
            }
            for (int i = 0; i < toUpdateSize; i++) {
            	Node n = (Node) toUpdate.get(i);
            	ids[toAddSize + i] = n.getID();
            	try {
            		paths[toAddSize + i] = PathIndexer.getNodeHref(n, this.nmgr.app);
            	} catch (Exception ex) {
            		paths[toAddSize + i] = null;
            	}
            	ops[toAddSize + i] = ITransaction.UPDATED;
            }
            for (int i = 0; i < toDeleteSize; i++) {
            	Node n = (Node) toDelete.get(i);
            	ids[toAddSize + toUpdateSize + i] = n.getID();
            	try {
            		paths[toAddSize + toUpdateSize + i] = PathIndexer.getNodeHref(n, this.nmgr.app);
            	} catch (Exception ex) {
            		paths[toAddSize + toUpdateSize + i] = null;
            	}
            	ops[toAddSize + toUpdateSize + i] = ITransaction.DELETED;
            }

            this.pitxn.abort();

            TransSource tsource = this.nmgr.app.getTransSource();
            synchronized (tsource) {
                txn.commit();
                txn.commitToTransaction();
                txn.postSubTransaction();
            }
            
            IndexDelta delta = ((LuceneTransaction) txn).getIndexDelta();
            if (id != -1L || ids != null || delta != null) {
                this.clusterComm.sendTransactionUpdateMessage(id, delta, ids, paths, ops);
            }
            
            ldb.resetCounterDirty();*/
        }
        
        // clustered environment, so send message node invalidation message to all servers 
        // in the cluster
        if (this.clusterComm != null) {
            //TODO:this.clusterComm.sendNodeInvalidationMessage(invalidationList);
        }
    }

    /**
     * Abort the current transaction, rolling back all changes made.
     */
    public synchronized void abort() {
        Object[] dirty = dirtyNodes.values().toArray();

        // evict dirty nodes from cache
        for (int i = 0; i < dirty.length; i++) {
            Node node = (Node) dirty[i];

            // Declare node as invalid, so it won't be used by other threads
            // that want to write on it and remove it from cache
            if (!"session".equalsIgnoreCase(node.getPrototype()))  
                nmgr.evictNode(node);
            
            node.clearWriteLock();
        }

        long now = System.currentTimeMillis();

        // set last subnode change times in parent nodes
        for (Iterator i = parentNodes.iterator(); i.hasNext(); ) {
            Node node = (Node) i.next();
            node.setLastSubnodeChange(now);
        }

        // clear the node collections
        dirtyNodes.clear();
        cleanNodes.clear();
        txnNodes.clear();
        parentNodes.clear();
        testedConnections.clear();
        
        // Abort the TransactionManager's transactions and abort all
        // jdbc connections
        tmgr.abortTransaction();
        // rollback the changes made to all JDBC connections
        try {
            rollbackTransactions();
        } catch (SQLException sqle) {
            nmgr.app.logError(ErrorReporter.errorMsg(this.getClass(), "abort") 
            		+ "Could not rollback the JDBC connections", sqle);
        }
        // close any JDBC connections associated with this transactor thread
        closeConnections();

        if (active) {
            active = false;

            this.txns.clear();
            pitxn = null;

            nmgr.app.logAccess(tname + " aborted after " +
                               (System.currentTimeMillis() - tstart) + " millis");
        }

        // unset transaction name
        tname = null;
    }
    
    public void commitTransactions() throws SQLException {
        if (sqlConnections != null) {
            for (Iterator i = sqlConnections.values().iterator(); i.hasNext();) {
                
                Connection con = (Connection) i.next();
                
                try {
                    if (!con.getAutoCommit() && !con.isClosed()) {
                        con.commit();
                    }
                } catch (SQLException sqle) {
                    throw new SQLException("Could not execute the commit on connection " + con.getClass().toString() + ": " + sqle.getMessage());
                }
            }

            sqlConnections.clear();
        }
    }
    
    public void rollbackTransactions() throws SQLException {
        if (sqlConnections != null) {
            StringBuffer exceptionBuffer = new StringBuffer();
            for (Iterator i = sqlConnections.values().iterator(); i.hasNext();) {
                
                Connection con = (Connection) i.next();
                
                try {
                    if (!con.getAutoCommit()) {
                        con.rollback();
                    }
                } catch (SQLException sqle) {
                    if (exceptionBuffer.length() == 0)
                        exceptionBuffer.append("Rollback Failures:\n");
                    exceptionBuffer.append(sqle.getMessage()+"\n");
                }
                nmgr.app.logEvent("Rolling back DB connection: " + con);
            }

            sqlConnections.clear();
            
            if (exceptionBuffer.length() > 0)
                throw new SQLException(exceptionBuffer.toString());
        }
    }

    /**
     * Kill this transaction thread. Used as last measure only.
     */
    public synchronized void kill() {
        killed = true;

        // The thread is told to stop by setting the thread flag in the EcmaScript
        // evaluator, so we can hope that it stops without doing anything else.
        try {
            join(500);
        } catch (InterruptedException ir) {
            // interrupted by other thread
        	ir.printStackTrace();
        }

        // Interrupt the thread if it has not noticed the flag (e.g. because it is busy
        // reading from a network socket).
        if (isAlive()) {
            interrupt();

            try {
                join(1000);
            } catch (InterruptedException ir) {
                // interrupted by other thread
            	ir.printStackTrace();
            }
        }
    }

    /**
     *
     */
    public void closeConnections() {
        if (sqlConnections != null) {
            for (Iterator i = sqlConnections.values().iterator(); i.hasNext();) {
                try {
                    Connection con = (Connection) i.next();

                    con.close();
                } catch (Exception ignore) {
                    // exception closing db connection, ignore
                }
            }

            sqlConnections.clear();
        }
    }

    /**
     * Return the name of the current transaction. This is usually the request
     * path for the underlying HTTP request.
     */
    public String getTransactionName() {
        return tname;
    }

    /**
     * Return a string representation of this Transactor thread
     *
     * @return ...
     */
    public String toString() {
        return "Transactor[" + tname + "]";
    }
    
    public void deleteFromPathIndices(Key key) {
    	this.pitxn.addResource(key, ITransaction.DELETED);
    }
    
    /*
     * recursively delete all nodes under a deleted node
     */
    protected HashMap recursiveDeletes(Node node, final int mode) {
        HashMap deletedNodes = new HashMap();
        this.deepRemove(node, deletedNodes, mode);        
        return deletedNodes;
    }
    
    protected void deepRemove(Node node, HashMap deletedNodes, final int mode) {
        deletedNodes.put(node.getID(), node);
        
        // tell all nodes that are properties of n that they are no longer used as such
        for (Enumeration en = node.properties(); en.hasMoreElements();) {
            Property p = (Property) node.get(en.nextElement().toString());
            
            if ((p != null) && (p.getType() == Property.NODE)) {
                Node n = (Node) p.getNodeValue();
                if (n != null && !n.isRelational() && n.getParent() == node
                		&& n.getLayerInStorage() == mode) {
                    deepRemove(n, deletedNodes, mode);
                } 
            }
        }

        // cascading delete of all subnodes. This is never done for relational subnodes, because
        // the parent info is not 100% accurate for them.
        Vector v = new Vector();
        // remove modifies the Vector we are enumerating, so we are extra careful.
        for (Enumeration en = node.getSubnodes(); en.hasMoreElements();) {
            v.add(en.nextElement());
        }
        
        final int m = v.size();
        for (int i = 0; i < m; i++) {
            // getParent() is heuristical/implicit for relational nodes, so we don't base
            // a cascading delete on that criterium for relational nodes.
            Node n = (Node) v.get(i);
            
            if (!n.isRelational() && n.getParent() == node 
            		&& n.getLayerInStorage() == mode) {
                deepRemove(n, deletedNodes, mode);
            }
        }

    }
    
    public boolean isSpecialNode(String nodeId) {
        return this.nmgr.app.getRootId().equals(nodeId) || this.nmgr.app.getUserRoot().getID().equals(nodeId)
                        || this.nmgr.app.getSessionsRootId().equals(nodeId);
    }
    
    public HashMap getCleanNodes() {
        return this.cleanNodes;
    }
    
    public QueryBean getQueryBean() throws Exception {
        if (this.qbean == null) {
            this.qbean = new QueryBean(this.nmgr.app, "TX-" + System.currentTimeMillis());
        }
        return this.qbean;
    }
    
    protected ArrayList getVisitedChildren(Node parent) {
        ArrayList list = new ArrayList();
        Iterator iter = this.txnNodes.entrySet().iterator();
        Key pkey = parent.getKey();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            Object o = entry.getValue();
            if (o instanceof Node) {
                Node n = (Node) ((Node) o).getParent();
                if (n != null && pkey.equals(n.getKey())) {
                    list.add(o);
                }
            }
        }
        return list;
    }
    
    protected Object removeRefsFromXhtml(Object xhtml, Node node) {
    	try {
    		Application app = this.nmgr.app;
    		String path = app.getPathIndexer().getPath(node.getID(), node.getLayer());
    		XhtmlUtils.removeLinkFromXhtml(xhtml, app.applyUrlRewrite(path));
    	} catch (Exception ex) {
    		this.nmgr.app.logError(ErrorReporter.errorMsg(this.getClass(), "removeRefsFromXhtml") 
    				+ "Failed on " + node, ex);
    	}
    	
    	return xhtml;
    }
    
    protected Object updateRefsInXhtml(Object xhtml, Node node) {
    	try {
    		Application app = this.nmgr.app;
    		String path = app.getPathIndexer().getPath(node.getID(), node.getLayer());
    		String newHref = app.getNodeHref(node, null, true);
    		XhtmlUtils.updateLinkInXhtml(xhtml, app.applyUrlRewrite(path), newHref);
    	} catch (Exception ex) {
    		this.nmgr.app.logError(ErrorReporter.errorMsg(this.getClass(), "updateRefsInXhtml") 
    				+ "Failed on " + node, ex);
    	}
    	
    	return xhtml;
    }
    
    protected int deletePreviewNodes(Node node, HashSet dirtyDbMappings, 
    								 ArrayList<DbKey> invalidationList, ArrayList deletedNodes,
    								 final boolean hasListeners, final boolean hasOnCommit)
    throws Exception {
    	
    	int deleted = 0;
    	IDatabase db = this.nmgr.getDatabaseForMapping(node.dbmap);
    	if (db instanceof LuceneDatabase) {
        	ArrayList<Node> preview = ((LuceneDatabase) db).getPreviewNodes(node.getID(), node.getLayer());
        	for (Node n : preview) {
        		pitxn.addResource(n, ITransaction.DELETED);
        		nmgr.deleteNode(db, this.txns.get(db), n);
                dirtyDbMappings.add(n.getDbMapping());

                if (invalidationList != null) {
                    invalidationList.add((DbKey)n.getKey());
                }
                
                // remove node from nodemanager cache
                nmgr.evictNode(n);
                if (hasListeners || hasOnCommit) {
                    deletedNodes.add(n);
                }
                
                n.markAs(Node.DELETED);
                deleted++;
                
                n.clearWriteLock();
        	}
        }
    	
    	return deleted;
    }
    
    public ITransaction getTransaction(IDatabase db) {
    	return this.txns.get(db);
    }
    
}