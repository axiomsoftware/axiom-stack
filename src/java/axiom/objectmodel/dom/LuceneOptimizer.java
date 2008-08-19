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

import java.io.IOException;

import org.apache.lucene.index.*;
import org.apache.lucene.store.*;

import axiom.cluster.ClusterCommunicator;
import axiom.extensions.trans.TransactionManager;
import axiom.framework.core.Application;
import axiom.objectmodel.db.TransSource;

public class LuceneOptimizer implements Runnable {
    
    private Application app;
    private IndexOptimizer optimizer;
    private Directory directory;
    private String indexPath;
    private TransactionManager tmgr;
    private LuceneManager lmgr;
    private volatile boolean isActive = true;
    
    public LuceneOptimizer(Directory d, Application app, LuceneManager lmgr) 
    throws Exception {
        this.app = app;
        this.directory = d;
        this.indexPath = d.toString();
        this.optimizer = new IndexOptimizer(d);
        this.optimizer.setUseCompoundFile(true);
        this.optimizer.setMergeFactor(Integer.MAX_VALUE);
        this.tmgr = TransactionManager.newInstance(app.getTransSource());
        this.lmgr = lmgr;
    }
    
    public void run() { 
        /*TODO:TransSource transSource = this.app.getTransSource();
        
        // keep running the optimizer in a seperate thread, until the server terminates
        while (this.isActive) {  
            try {
                long DEFAULT_SLEEP_VALUE;
                int DEFAULT_SEGMENT_COUNT; 
                
                try {
                    DEFAULT_SLEEP_VALUE = Long.parseLong(
                            this.app.getProperty("optimizer.sleepDuration", "60")) * 1000L;
                } catch (Exception pe) {
                    DEFAULT_SLEEP_VALUE = 60000L;
                }
                
                try {
                    DEFAULT_SEGMENT_COUNT = Integer.parseInt(
                            this.app.getProperty("optimizer.segmentThreshold", "30"));
                } catch (Exception pe) {
                    DEFAULT_SEGMENT_COUNT = 30;
                }
                
                Thread.sleep(DEFAULT_SLEEP_VALUE);
                
                if (!this.isActive) {
                    break;
                }
                ClusterCommunicator clusterComm = this.app.getClusterCommunicator();
                if (clusterComm != null && !clusterComm.isMaster()) {
                    continue;
                }
                if (this.optimizer.getSegmentCount() < DEFAULT_SEGMENT_COUNT) {
                    continue;
                }
                synchronized (transSource) {
                    this.optimizer.setup();
                }
                if (!this.isActive) {
                    break;
                }
                
                int mergedCount = this.optimizer.optimize();
                if (!this.isActive) {
                    break;
                }
                
                if (mergedCount > 1) {
                    this.tmgr.startTransaction();
                    
                    synchronized (transSource) {
                        this.optimizer.optimizeSegmentInfos();
                        LuceneManager.commitSegments(this.tmgr.getConnection(), 
                                this.tmgr.getApplication(), this.directory);
                        this.tmgr.commitTransaction();
                        this.optimizer.updateIndexUponFinalize();
                        this.lmgr.setSearcherDirty();
                        
                        if (clusterComm != null) {
                            DeletedInfos dinfos = IndexObjectsFactory.getDeletedInfos(this.directory);
                            clusterComm.sendLuceneOptimizedMessage(dinfos, 
                                    this.optimizer.getDeletedSegments(), 
                                    this.optimizer.getAddedInfos());
                        }
                    }
                    
                    this.optimizer.cleanup();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                this.tmgr.abortTransaction();
                this.app.logError("Optimizer for " + this.indexPath + 
                        " encountered a fatal error, caused by exception: " + ex.getMessage());
            } 
        }*/
    }
    
    public void runOptimizingThread() {
        Thread chod = new Thread(null, this, "OptimizerThread");
        chod.setDaemon(false);
        chod.start();
    }
    
    /*TODO:public boolean isRunning() {
        return this.optimizer.isRunning();
    }*/
    
    public void stopOptimizer() {
        this.isActive = false;
    }
    
    public IndexOptimizer getOptimizer() {
        return this.optimizer;
    }
    
}