package axiom.extensions.trans;

import java.sql.*;
import java.util.ArrayList;

import axiom.framework.core.Application;
import axiom.objectmodel.ITransaction;
import axiom.objectmodel.db.TransSource;

public class TransactionManager {
    
    TransSource source;
    Connection conn;
    ArrayList transactionUnits = new ArrayList();
    
    protected TransactionManager(TransSource source) throws TransactionException {
        this.source = source;
    }
    
    public static TransactionManager newInstance(TransSource source) throws TransactionException {
        return new TransactionManager(source);
    }
    
    public void startTransaction() throws TransactionException {
        try {
            if (conn == null || conn.isClosed()) {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException ignore) {
                    } finally {
                        conn = null;
                    }
                }
                conn = source.getConnection();
            }
        } catch (Exception ex) {
            throw new TransactionException(ex.getMessage());
        }
    }
    
    public void addTransactionUnit(ITransaction trans) throws TransactionException {
        if (trans.getTransactionManager() != null) {
            throw new TransactionException("TransactionManager.addTransactionUnit(): " +
                    "attempted to add a transaction unit that has already been added");
        }
        
        trans.setTransactionManager(this);
        transactionUnits.add(trans);
    }
    
    public void clearTransactionUnits() {
        this.transactionUnits.clear();
    }
    
    public void executeIndividualTransactions() throws TransactionException {
        final int size = transactionUnits.size();
        for (int i = 0; i < size; i++) {
            ITransaction trans = (ITransaction) transactionUnits.get(i);
            trans.commit();
        }
    }
    
    public void executeTransactionCommits() throws TransactionException {
        final int size = transactionUnits.size();
        for (int i = 0; i < size; i++) {
            ITransaction trans = (ITransaction) transactionUnits.get(i);
            trans.commitToTransaction();
        }
    }
    
    public void commitTransaction() throws TransactionException {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.commit();
            } else {
                throw new TransactionException("TransactionManager.commitTransaction(): Attempting to commit a transaction without a valid connection");
            }
        } catch (Exception ex) {
            throw new TransactionException(ex.getMessage());
        } 
    }
    
    public void abortTransaction() throws TransactionException {
        final int size = transactionUnits.size();
        for (int i = 0; i < size; i++) {
            ITransaction trans = (ITransaction) transactionUnits.get(i);
            trans.abort();
        }

        try {
            if (conn != null && !conn.isClosed()) {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
            }
        } catch (Exception ex) {
            source.getApplication().logError("TransactionManager.abortTransaction(): ", ex);
        }
    }
    
    public void postTransaction() throws TransactionException {
        final int size = transactionUnits.size();
        for (int i = 0; i < size; i++) {
            ITransaction trans = (ITransaction) transactionUnits.get(i);
            trans.postSubTransaction();
        }
    }
    
    public Connection getConnection() {
        return conn;
    }
    
    public Application getApplication() {
        return source.getApplication();
    }
    
}