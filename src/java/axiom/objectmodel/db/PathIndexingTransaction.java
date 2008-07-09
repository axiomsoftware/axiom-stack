package axiom.objectmodel.db;

import java.util.ArrayList;
import java.sql.Connection;

import axiom.extensions.trans.TransactionException;
import axiom.extensions.trans.TransactionManager;
import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.DatabaseException;
import axiom.objectmodel.ITransaction;


public class PathIndexingTransaction implements ITransaction {

    private TransactionManager tmgr;
    private PathIndexer indexer;
    private ArrayList toAdd = new ArrayList();
    private ArrayList toUpdate = new ArrayList();
    private ArrayList toDelete = new ArrayList();
    
    public PathIndexingTransaction(PathIndexer indexer) {
        this.indexer = indexer;
    }
    
    public void commit() throws DatabaseException {
        // do nothing
    }

    public void abort() throws DatabaseException {
        toAdd.clear();
        toUpdate.clear();
        toDelete.clear();
    }

    public void addResource(Object res, int status) throws DatabaseException {
        if (res != null && res instanceof Node) {
            DbMapping dbm = ((Node) res).getDbMapping();
            if (dbm != null && dbm.isRelational()) {
                return;
            }
        }
        
        if (status == ITransaction.ADDED) {
            toAdd.add(res);
        } else if (status == ITransaction.UPDATED) {
            toUpdate.add(res);
        } else if (status == ITransaction.DELETED) {
            toDelete.add(res);
        }
    }

    public void commitToTransaction() throws TransactionException {
    	try {
    		Connection conn = tmgr.getConnection(); 
    		indexer.addPathIndices(conn, toAdd);
    		indexer.updatePathIndices(conn, toUpdate);
    		indexer.deletePathIndices(conn, toDelete);
    	} catch (Exception ex) {
    		throw new TransactionException(ex.getMessage());
    	} 
    }

    public void setTransactionManager(TransactionManager tmgr)
            throws TransactionException {
       this.tmgr = tmgr;
    }

    public TransactionManager getTransactionManager()
            throws TransactionException {
        return this.tmgr;
    }

    public void postSubTransaction() throws TransactionException {
        // do nothing
    }
    
    public ArrayList getAddList() {
        return this.toAdd;
    }
    
    public ArrayList getUpdateList() {
        return this.toUpdate;
    }
    
    public ArrayList getDeleteList() {
        return this.toDelete;
    }

}