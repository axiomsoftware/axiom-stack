package axiom.objectmodel.dom;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.apache.lucene.index.DeletedInfos;
//TODO:import org.apache.lucene.index.IndexDelta;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.FSDirectory;

import axiom.cluster.ClusterCommunicator;
import axiom.extensions.trans.TransactionException;
import axiom.extensions.trans.TransactionManager;
import axiom.framework.ErrorReporter;
import axiom.framework.core.Application;
import axiom.objectmodel.DatabaseException;
import axiom.objectmodel.IDatabase;
import axiom.objectmodel.INode;
import axiom.objectmodel.ITransaction;
import axiom.objectmodel.ObjectNotFoundException;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.db.Key;
import axiom.objectmodel.db.Node;
import axiom.objectmodel.db.NodeManager;
import axiom.objectmodel.db.PathIndexingTransaction;


public final class LuceneDatabase implements IDatabase {

    protected File dbHomeDir;
    protected Application app;
    protected NodeManager nmgr;
    protected LuceneManager lmgr;
    protected LuceneId id;
    protected boolean counterDirty = false;
    protected boolean firstTime = false;
    protected ClusterCommunicator clusterComm = null;
    
    public void init(File dbHome, Application app) {
        
        this.app = app;
        nmgr = app.getNodeManager();
        dbHomeDir = dbHome;
        this.clusterComm = app.getClusterCommunicator();
        
        if (!dbHomeDir.exists() && !dbHomeDir.mkdir()) {
            throw new DatabaseException("Can't find or create database directory " + dbHomeDir);
        }
      
        FSDirectory.setDisableLocks(true); // disable Lucene locks
        //TODO:IndexWriter.setSegmentsPrefix(app.getProperty("cluster.host", null));
 
        try { 
            lmgr = LuceneManager.getInstance(app);
        } catch (Throwable ex) {
        	ex.printStackTrace();
            if (ex instanceof DatabaseException &&
                    (this.clusterComm == null || this.clusterComm.isMaster())) {
                // the lucene database versions do not match, try converting the existing db
                // to the new lucene version
                String msg = ex.getMessage();
                int version = 0;
                try {
                    version = Integer.parseInt(msg.substring(msg.lastIndexOf(" ") + 1));
                } catch (Exception e) {
                    version = 0;
                }
                
                try {
                    lmgr = new LuceneManager(app, false, false);
                } catch (Exception ex2) {
                    ex2.printStackTrace();
                    throw new DatabaseException("Could not create temporary LuceneManager");
                } 
                
                convertLuceneDb(version);
                
                try {
                    lmgr = LuceneManager.getInstance(app);
                } catch (Exception ex2) {
                    ex2.printStackTrace();
                    throw new DatabaseException("Could not create the LuceneManager: " + ex.getMessage());
                }
            } else {
                throw new DatabaseException("Error initializing db -- Could not create LuceneManager");
            }
        }
        
        // get the initial id generator value
        long idBaseValue;
        try {
            idBaseValue = Long.parseLong(app.getProperty("idBaseValue", "2"));
            // 0 and 1 are reserved for root nodes, 2 is reserved for the session space
            idBaseValue = Math.max(2L, idBaseValue);
        } catch (NumberFormatException ignore) {
            idBaseValue = 2L;
        }

        ITransaction txn = beginTransaction(DbKey.LIVE_LAYER);
        ITransaction pitxn = new PathIndexingTransaction(app.getPathIndexer());
        TransactionManager tmgr = TransactionManager.newInstance(app.getTransSource());
        tmgr.startTransaction();
        tmgr.addTransactionUnit(txn);
        tmgr.addTransactionUnit(pitxn);
        
        try {
            
            Node node = null;
            
            try {
               this.id = setupId(txn);

                if (this.id.idCounter < idBaseValue) {
                    this.id.idCounter = idBaseValue;
                }
            } catch (ObjectNotFoundException notfound) {
                // will start with idBaseValue+1
                this.id = new LuceneId(idBaseValue, this.app.getClusterHost());
                counterDirty = true;
                firstTime = true;
            }
            
            try { 
               getNode(txn, "0");
            } catch (ObjectNotFoundException onfe) {
                node = new Node("root", "0", "Root", nmgr.safe);
                node.setDbMapping(app.getDbMapping("root"));
                insertNode(txn, node.getID(), node);
                // register node with nodemanager cache
                nmgr.registerNode(node);      
                pitxn.addResource(node, ITransaction.ADDED);
            }
            
            try { 
                getNode(txn, "1");
            } catch (ObjectNotFoundException onfe) {
                node = new Node("users", "1", null, nmgr.safe);
                node.setDbMapping(app.getDbMapping("__userroot__"));
                insertNode(txn, node.getID(), node);
                // register node with nodemanager cache
                nmgr.registerNode(node);    
                pitxn.addResource(node, ITransaction.ADDED);
            }
            
            try { 
                getNode(txn, "2");
            } catch (ObjectNotFoundException onfe) {
                node = new Node("sessions", "2", null, nmgr.safe);
                node.setDbMapping(app.getDbMapping("__sessionroot__"));
                insertNode(txn, node.getID(), node);
                // register node with nodemanager cache
                nmgr.registerNode(node);    
                pitxn.addResource(node, ITransaction.ADDED);
            }
            
            commitTransaction(tmgr);
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                tmgr.abortTransaction();
            } catch (Exception abex) {
                app.logError(ErrorReporter.errorMsg(this.getClass(), "init") 
                		+ "Error aborting the transaction", abex);
            }

            throw new DatabaseException("Error initializing db");        
        }
        
        // start the optimizer thread for this lucene index
        try {
            lmgr.runOptimizer();
        } catch (Exception ex) {
            throw new DatabaseException("Error initializing the optimizer for index " + dbHome);
        }
        
        System.out.println("LuceneDatabase initialization completed for " + app.getName());
    }
    
    protected void commitTransaction(TransactionManager tmgr) {
        synchronized (this.app.getTransSource()) {
            tmgr.executeIndividualTransactions();
            tmgr.executeTransactionCommits();
            tmgr.commitTransaction();
            tmgr.postTransaction();
        }
    }

    public void shutdown() {
        this.lmgr.shutdown();
    }
    
    public LuceneManager getLuceneManager() {
        return this.lmgr;
    }

    public void setEmbeddedID(String _id){
    	int id = Integer.valueOf(_id).intValue();
    	if(id > this.id.idCounter){
    		this.id.idCounter = id;
    		this.counterDirty = true;
    	}
    }

    public String nextID() throws ObjectNotFoundException {
        this.id.idCounter++;
        this.counterDirty = true;
        return this.id.toString();
    }
    
    public long getID() {
        return this.id.idCounter;
    }
    
    public boolean isCounterDirty() {
        return this.counterDirty;
    }
    
    public void resetCounterDirty() {
        this.counterDirty = false;
    }
    
    public LuceneId setupId(ITransaction txn) throws ObjectNotFoundException {
        TransactionManager tmgr = txn.getTransactionManager();
        Connection conn = tmgr.getConnection();
        final String appName = app.getName();
        String clusterHost = app.getClusterHost();
        if (clusterHost == null) {
            clusterHost = "";
        }
        PreparedStatement pstmt = null;
        
        long id = -1L;
        
        String sql = "SELECT id FROM IdGen WHERE cluster_host = ?";
        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, clusterHost);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                id = rs.getLong(1);
            } 
        } catch (Exception ex) {
            throw new ObjectNotFoundException("Could not retrieve id from the db for app " + appName);
        } finally {
            if (pstmt != null) {
                try { 
                    pstmt.close(); 
                } catch (SQLException ignore) { 
                }
                pstmt = null;
            }
        }
        
        if (id == -1L) {
            throw new ObjectNotFoundException("Id doesn't exist yet in the db for app " + appName);
        }
        
        return new LuceneId(id, clusterHost);
    }
    
    public INode getNode(String key, int mode) 
    throws IOException, ObjectNotFoundException {
        return lmgr.retrieveFromLuceneIndex(key, mode);  
    }
    
    public INode getNode(ITransaction transaction, String key, int mode)
    throws IOException, ObjectNotFoundException {
        return lmgr.retrieveFromLuceneIndex(key, mode);
    }
    
    public INode getNode(ITransaction transaction, String key, int mode, boolean tryOtherModes)
    throws IOException, ObjectNotFoundException {
        if (tryOtherModes) {
        	return lmgr.retrieveFromLuceneIndex(key, mode);
        } else {
        	return lmgr.retrieveFromIndexFixedMode(key, mode);
        }
    }

    public INode getNode(ITransaction transaction, String key)
    throws IOException, ObjectNotFoundException {
        return getNode(transaction, key, DbKey.LIVE_LAYER);
    }
    
    public Key getChildKey(String accessname, String value, String parentid, int mode) 
    throws IOException, ObjectNotFoundException {
        return lmgr.retrieveKeyFromIndex(accessname, value, parentid, mode);
    }
    
    public void insertNode(ITransaction transaction, String key, INode node)
    throws IOException {
        transaction.addResource(node, ITransaction.ADDED);    
    }
    
    public void updateNode(ITransaction transaction, String key, INode node)
    throws IOException {
        transaction.addResource(node, ITransaction.UPDATED);
    }
    
    public void deleteNode(ITransaction transaction, String key)
    throws IOException {
        this.deleteNode(transaction, key, DbKey.LIVE_LAYER);
    }
    
    public void deleteNode(ITransaction transaction, String key, int mode)
    throws IOException {
        transaction.addResource(key + DeletedInfos.KEY_SEPERATOR + mode, ITransaction.DELETED);
    }

    public ITransaction beginTransaction(int mode) {
        return new LuceneTransaction(mode);
    }

    public void commitTransaction(ITransaction transaction) throws DatabaseException {
        transaction.commit();
    }

    public void abortTransaction(ITransaction transaction) throws DatabaseException {
        transaction.abort();
    }
    
    public ArrayList<Node> getPreviewNodes(String id, final int mode) throws Exception {
    	return this.lmgr.getPreviewNodes(id, mode);
    }
    
    private void convertLuceneDb(int version) throws DatabaseException {
        for (int curr = ++version; curr <= LuceneManager.LUCENE_VERSION; curr++) {
            EmbeddedDbConvertor cvtr = null;
            final String className = "axiom.objectmodel.dom.convert.LuceneVersion" + 
                                        curr + "Convertor";

            Class appclass = null;
            try {
                appclass = Class.forName("axiom.framework.core.Application");
            } catch (Exception ex) {
            }
            
            try {
                Class c = Class.forName(className);
                Constructor ctor = c.getConstructor(appclass);
                cvtr = (EmbeddedDbConvertor) ctor.newInstance(this.app);
            } catch (Exception ex) {
                ex.printStackTrace();
            	throw new DatabaseException("FATAL ERROR::Could not find the convertor " +
                        "class " + className + ", conversion failed.");
            }

            try {
                cvtr.convert(this.app, this.dbHomeDir);
                System.out.println("Converted Lucene db to version " + curr);
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new DatabaseException("Could not convert the Lucene Db to the new version: " 
                        + ex.getMessage());
            }
        }
        
        System.out.println("Successfully converted the current " + this.app.getName() + 
                " db to version " + LuceneManager.LUCENE_VERSION);
    }
    
    
    class LuceneId {
        protected long idCounter = -1L;
        protected String host = "";
        
        public LuceneId(long idCounter, String host) {
            this.idCounter = idCounter;
            if (host != null) {
                this.host = host.toLowerCase();
            }
        }
        
        public String toString() {
            if ("".equals(host)) {
                return Long.toString(this.idCounter);
            } else {
                return host + Long.toString(this.idCounter);
            }
        }
    }
    
    final public class LuceneTransaction implements ITransaction {
        ArrayList saveNodes = new ArrayList();
        ArrayList delNodes = new ArrayList();
        ArrayList updateNodes = new ArrayList();
        TransactionManager tmgr = null;
        IndexWriter writer = null;
        //TODO:IndexDelta delta = null;
        int mode = DbKey.LIVE_LAYER;
        
        public LuceneTransaction(int mode) {
            this.mode = mode;
        }
        
        public void commit() throws DatabaseException {    
            writer = lmgr.updateLucene(dbHomeDir, saveNodes, updateNodes, delNodes, mode);
            clearLists();
        }
        
        public void setTransactionManager(TransactionManager tmgr) {
            this.tmgr = tmgr;
        }
        
        public TransactionManager getTransactionManager() {
            return tmgr;
        }
        
        /*TODO:public IndexDelta getIndexDelta() {
            return this.delta;
        }*/
        
        public void commitToTransaction() throws TransactionException {
            if (tmgr == null) {
                throw new TransactionException("LuceneTransaction.executeSubTransaction(): " + 
                        "Transaction has not been added to a transaction manager.");
            }
            
            if (writer == null) {
                return; // no documents were added to lucene, so no need to update the db
            }
            
            String segmentsNew = null;
            try {
            	writer.flushCache();//TODO:segmentsNew = writer.writeSegmentsFile();
            } catch (Exception ex) {
            	throw new TransactionException(ex.getMessage());
            }
            
            if (clusterComm == null || clusterComm.isMaster()) {
            	LuceneManager.commitSegments(segmentsNew, tmgr.getConnection(), 
            			tmgr.getApplication(), writer.getDirectory());

            	commitIdgen();
            } 
        }
        
        public void commitIdgen() {
            // commit idgen stuff below
            if (counterDirty) {
                boolean exceptionOccured = false;
                Connection conn = tmgr.getConnection();
                PreparedStatement pstmt = null;
                
                try {
                    String sql;
                    if (firstTime) {
                        sql = "INSERT INTO IdGen (id, cluster_host) VALUES (?,?)";
                        firstTime = false;
                    } else {
                        sql = "UPDATE IdGen SET id = ? WHERE cluster_host = ?";
                    }
                    pstmt = conn.prepareStatement(sql);
                    int count = 1;
                    pstmt.setLong(count++, id.idCounter);
                    pstmt.setString(count++, id.host);

                    pstmt.executeUpdate();
                    //if (rows < 1) {
                    //    throw new Exception("LuceneTransaction.executeSubTransaction(): update didn't affect any rows in the database");
                    //}
                } catch (Exception ex) {
                    exceptionOccured = true;
                    throw new TransactionException(ex.getMessage());
                } finally {
                    counterDirty = false;
                    
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
        }
        
        public void postSubTransaction() throws TransactionException {
            if (writer == null) {
                return;
            }
            
            lmgr.setSearcherDirty();
            
            try {
                if (clusterComm == null) {
                    writer.finalizeTrans();
                } else {
                    //TODO:delta = writer.finalizeTrans(true);
                }
            } catch (IOException iox) {
                throw new TransactionException("postSubTransaction() failed: " + iox.getMessage());
            }
            writer = null;
        }
       
        public void abort() throws DatabaseException {
        }

        public void addResource(Object res, int status) throws DatabaseException {
            if (status == ITransaction.ADDED) {
                saveNodes.add(res);
            } else if (status == ITransaction.UPDATED) {
                updateNodes.add(res);
            } else if (status == ITransaction.DELETED) {
                delNodes.add(res);
            }
        }
        
        private void clearLists() {
            if (saveNodes != null) {
                saveNodes.clear();
                saveNodes = null;
            }
            if (updateNodes != null) { 
                updateNodes.clear(); 
                updateNodes = null; 
            }
            if (delNodes != null) { 
                delNodes.clear();
                delNodes = null; 
            }
        }
    }

}
