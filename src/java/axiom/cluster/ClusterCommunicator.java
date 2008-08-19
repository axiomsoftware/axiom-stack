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

package axiom.cluster;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.lucene.index.DeletedInfos;
//TODO:import org.apache.lucene.index.IndexDelta;
//TODO:import org.apache.lucene.index.IndexInfo;
import org.apache.lucene.index.IndexObjectsFactory;
import org.apache.lucene.index.IndexOptimizer;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
//TODO:import org.jgroups.blocks.*;
//TODO:import org.jgroups.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import axiom.extensions.trans.TransactionException;
import axiom.extensions.trans.TransactionManager;
import axiom.framework.core.Application;
import axiom.framework.repository.FileResource;
import axiom.framework.repository.Repository;
import axiom.framework.repository.Resource;
import axiom.objectmodel.DatabaseException;
import axiom.objectmodel.ITransaction;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.db.NodeManager;
import axiom.objectmodel.db.PathIndexingTransaction;
import axiom.objectmodel.db.TransSource;
import axiom.objectmodel.dom.LuceneManager;
import axiom.objectmodel.dom.LuceneOptimizer;

public class ClusterCommunicator /*TODO:implements MessageListener*/ {

    private Application app;
    /*TODO:private PullPushAdapter adapter;
    private Address address;
    private boolean isHead = false;*/
        
    public static final int SUCCESS = 0;
    public static final int ERROR = -1;

    public static final Integer INVALIDATION = new Integer(1);
    public static final Integer HEAD_SYNCH = new Integer(2); 
    public static final Integer INDEX_DELTA = new Integer(3);
    public static final Integer LUCENE_OPTIMIZED = new Integer(4);
    public static final Integer HEAD_UPDATE = new Integer(5);
    
    public ClusterCommunicator(Application app) 
    /*TODO:throws ChannelException, ChannelClosedException*/ {
        
        this.app = app;
        /*TODO:ClusterConfig config = new ClusterConfig(app);
        Channel channel = new JChannel(config.getJGroupsProps());
        channel.setOpt(Channel.AUTO_RECONNECT, Boolean.TRUE);
        
        channel.connect(app.getProperty("cluster.name", app.getName()) + "_axiom_cluster");
        
        this.adapter = new PullPushAdapter(channel);
        this.adapter.start();
        this.adapter.registerListener(INVALIDATION, this);
        this.adapter.registerListener(HEAD_SYNCH, this);
        this.adapter.registerListener(INDEX_DELTA, this);
        this.adapter.registerListener(LUCENE_OPTIMIZED, this);
        this.address = channel.getLocalAddress();*/
    }
    
    public void finalize() throws Throwable {
        super.finalize();
        this.shutdown();
    }
    
    public synchronized void shutdown() {
        /*TODO:if (this.adapter != null) {
            this.adapter.unregisterListener(INVALIDATION);
            this.adapter.unregisterListener(HEAD_SYNCH);    
            this.adapter.unregisterListener(INDEX_DELTA);
            this.adapter.unregisterListener(LUCENE_OPTIMIZED);
            
            Channel channel = (Channel) this.adapter.getTransport();
            if (channel.isConnected()) {
                channel.disconnect();
            }
            if (channel.isOpen()) {
                channel.close();
            }
            this.adapter.stop();
        }*/
    }
    
    /*TODO:public int sendNodeInvalidationMessage(ArrayList<DbKey> keys) {
        if (keys == null || keys.size() == 0) {
            return SUCCESS;
        }
        
        try {
            Message msg = new Message(null, this.address, new InvalidationListMsg(keys));
            this.adapter.send(INVALIDATION, msg);
        } catch (Exception ex) {
            return ERROR;
        }
        
        return SUCCESS;
    }
    
    public int sendIndexDeltaMessage(IndexDelta delta) {
        try {
            Message msg = new Message(null, this.address, delta);
            this.adapter.send(INDEX_DELTA, msg);
        } catch (Exception ex) {
            return ERROR;
        }
        
        return SUCCESS;
    }
    
    public int sendHeadUpdateMessage(HeadUpdateMsg hum) {
        try {
            Message msg = new Message(null, this.address, hum);
            this.adapter.send(HEAD_UPDATE, msg);
        } catch (Exception ex) {
            return ERROR;
        }
        
        return SUCCESS;
    }
    
    public int sendTransactionUpdateMessage(long id, IndexDelta delta, 
                                            String[] ids, String[] paths, int[] ops) {
        
        if (this.isHead) {
            return SUCCESS;
        }
        
        TransactionUpdateMsg tu = new TransactionUpdateMsg(app.getClusterHost(), id, delta, 
                                                            ids, paths, ops);
        
        try {
            Channel channel = (Channel) this.adapter.getTransport();
            View view = channel.getView();
            Address head = (Address) view.getMembers().firstElement();
            Message msg = new Message(head, this.address, tu);
            this.adapter.send(HEAD_SYNCH, msg);
        } catch (Exception ex) {
            return ERROR;
        }
        
        return SUCCESS;
    }
    
    public int sendLuceneOptimizedMessage(DeletedInfos dinfos, String[] segments_to_del,
                                            SegmentInfos infos_to_add) {
        
        LuceneOptimizedMsg lomsg = new LuceneOptimizedMsg(dinfos, segments_to_del, 
                infos_to_add);
        
        try {
            Message msg = new Message(null, this.address, lomsg);
            this.adapter.send(LUCENE_OPTIMIZED, msg);
        } catch (Exception ex) {
            ex.printStackTrace();
            return ERROR;
        }
        
        return SUCCESS;
    }*/
    
    /*TODO:public void receive(Message message) {
        if (this.address.equals(message.getSrc())) {
            // we sent this message, so ignore it
            return;
        }
        
        Object obj = message.getObject();
        if (obj instanceof InvalidationListMsg) {
            evictNodesFromCache((InvalidationListMsg) obj, this.app.getNodeManager());
        } else if (obj instanceof TransactionUpdateMsg) {
            executeTransactionalUpdate((TransactionUpdateMsg) obj, this.app);
        } else if (obj instanceof IndexDelta) {
            applyLuceneChanges((IndexDelta) obj, this.app);
        } else if (obj instanceof LuceneOptimizedMsg) {
            handleOptimizedIndex((LuceneOptimizedMsg) obj, this.app);
        }
    }*/

    public byte[] getState() {
        // doesn't implement state transfer
        return null;
    }

    public void setState(byte[] bytes) {
        // doesn't implement state transfer
    }
    
    public boolean isMaster() {
        /*TODO:if (this.adapter != null) {
            try {
                Channel channel = (Channel) this.adapter.getTransport();
                View view = channel.getView();
                Address address = channel.getLocalAddress();
                return address.equals(view.getMembers().firstElement());
            } catch (Exception x) {
                this.app.logError("ClusterCommunicator.isMaster()", x);
            }
        }*/
        return false;
    }    
    
    /*TODO:private static void evictNodesFromCache(InvalidationListMsg list, NodeManager nmgr) {
        DbKey[] keys = list.getKeys();
        for (int i = 0; i < keys.length; i++) {
            nmgr.evictNodeByKey(keys[i]);
        }
    }
    
    private static void executeTransactionalUpdate(TransactionUpdateMsg tu, Application app) {
        TransSource tsource = app.getTransSource();
        TransactionManager tmgr = TransactionManager.newInstance(tsource);
        tmgr.startTransaction();
        
        if (tu.paths_modified != null) {
            PathIndexingTransaction pitxn = new PathIndexingTransaction(app.getPathIndexer());
            tmgr.addTransactionUnit(pitxn);

            final int length = tu.paths_modified.length;
            for (int i = 0; i < length; i++) {
                pitxn.addResource(new String[]{ tu.ids_modified[i], tu.paths_modified[i] }, tu.modifying_ops[i]);
            }
        }
        
        if (tu.id != -1L) {
            IdGenTransaction idtxn = new IdGenTransaction(tu.id, tu.host);
            tmgr.addTransactionUnit(idtxn);
        }
        
        if (tu.delta != null) {
            Directory dir = null;
            try {
                dir = FSDirectory.getDirectory(app.getDbDir(), false);
            } catch (Exception ex) {
            }

            LuceneSegmentsTransaction lstxn = 
                new LuceneSegmentsTransaction(dir, tu.delta);
            tmgr.addTransactionUnit(lstxn);
        }
        
        tmgr.executeIndividualTransactions();

        synchronized (tsource) {
            tmgr.executeTransactionCommits();
            tmgr.commitTransaction();
            tmgr.postTransaction();
        }
    }
    
    private static void applyLuceneChanges(IndexDelta delta, Application app) {
        TransSource tsource = app.getTransSource();
        TransactionManager tmgr = TransactionManager.newInstance(tsource);
        tmgr.startTransaction();
        
        if (delta != null) {
            Directory dir = null;
            try {
                dir = FSDirectory.getDirectory(app.getDbDir(), false);
            } catch (Exception ex) {
            }

            LuceneChangesTransaction lstxn = 
                new LuceneChangesTransaction(dir, delta);
            tmgr.addTransactionUnit(lstxn);
        }
        
        tmgr.executeIndividualTransactions();

        synchronized (tsource) {
            tmgr.executeTransactionCommits();
            tmgr.commitTransaction();
            tmgr.postTransaction();
        }
    }
    
    private static void handleOptimizedIndex(LuceneOptimizedMsg lomsg, Application app) {
        TransSource tsource = app.getTransSource();
        Directory dir = null;
        SegmentInfos sinfos = null, copy;
        try {
            dir = FSDirectory.getDirectory(app.getDbDir(), false);
            sinfos = IndexObjectsFactory.getFSSegmentInfos(dir);
        } catch (Exception ex) {
            throw new RuntimeException("ClusterCommunicator.handleOptimizedIndex() FAILED " +
                    "to retrieve directory, " + ex.getMessage());
        }
        
        synchronized (sinfos) {
            copy = sinfos.copy();
        }

        synchronized (tsource) {
            DeletedInfos new_del_infos = lomsg.dinfos, dinfos = null;
            try {
                dinfos = IndexObjectsFactory.getDeletedInfos(dir);
            } catch (Exception ex) {
                throw new RuntimeException("ClusterCommunicator.handleOptimizedIndex() " +
                        "FAILED to retrieve DeletedInfos, " + ex.getMessage());
            }
            
            int ncount = dinfos.getDocCount();
            int mergedCount = 0;
            for (int i = 0; i < lomsg.segments_to_remove.length; i++) {
                String segment = lomsg.segments_to_remove[i];
                if (segment != null) {
                    final int infoslen = copy.size();
                    for (int j = 0; j < infoslen; j++) {
                        SegmentInfo si = copy.info(j);
                        if (segment.equals(si.name)) {
                            mergedCount += si.docCount;
                            copy.remove(j);
                            break;
                        }
                    }
                }
            }
            ncount -= mergedCount;
                
            final int nsize = lomsg.infos_to_add.size();
            for (int i = nsize - 1; i > -1; i--) {
                SegmentInfo si = (SegmentInfo) lomsg.infos_to_add.remove(i);
                ncount += si.docCount;
                si.dir = dir;
                copy.add(0, si);
            }
            
            synchronized (dinfos) {
                IndexObjectsFactory.setFSSegmentInfos(dir, copy);
                dinfos.putAll(new_del_infos);
                BitSet bs = dinfos.getBitSet();
                bs.clear(0, mergedCount + 1);
                dinfos.setDocCount(ncount);
            }
        }
    }
    
    static void applyLuceneChanges(IndexDelta delta, Directory dir, LuceneOptimizer optimizer) {
        final boolean isOptimizerRunning = optimizer.isRunning();
        IndexOptimizer optzer = optimizer.getOptimizer();
        DeletedInfos dinfos;
        try {
            dinfos = IndexObjectsFactory.getDeletedInfos(dir);
        } catch (Exception ex) {
            throw new TransactionException("ClusterCommunicator.applyLuceneChanges, deleted infos is null.");
        }
        
        for (int i = 0; i < delta.obs_ids.length; i++) {
            Integer pos = (Integer) dinfos.get(delta.obs_ids[i]);
            if (pos != null) {
                if (!isOptimizerRunning) {
                    dinfos.setDocDeletion(pos.intValue());
                } else {
                    optzer.handleChange(delta.obs_ids[i], pos.intValue(), IndexInfo.OBSOLETE);
                }
            }
        }
        
        final int dcount = dinfos.getDocCount();
        Iterator iter = delta.add_ids.keySet().iterator();
        while (iter.hasNext()) {
            String id = (String) iter.next();
            int pos = delta.add_ids.get(id).intValue();
            if (!isOptimizerRunning) {
                dinfos.put(id, new Integer(pos + dcount));
            } else {
                optzer.handleChange(id, pos + dcount, IndexInfo.INSERT);
            }
        }
        
        iter = delta.del_ids.keySet().iterator();
        while (iter.hasNext()) {
            String id = (String) iter.next();
            Integer pos = delta.del_ids.get(id);
            if (pos != null) {
                if (!isOptimizerRunning) {
                    dinfos.setDocDeletion(pos.intValue() + dcount);
                    dinfos.remove(id);
                } else {
                    optzer.handleChange(id, pos.intValue() + dcount, IndexInfo.DELETE);
                }
            }
        }
        
        dinfos.setDocCount(dcount + delta.added_count + 1);
    }
    
    
    class ClusterConfig { 
        
        private String jGroupsProps = 
            "UDP(mcast_addr=224.0.0.132;mcast_port=22024;ip_ttl=32;" +
            "bind_port=48848;port_range=1000;" +
            "mcast_send_buf_size=150000;mcast_recv_buf_size=80000):" +
            "PING(timeout=2000;num_initial_members=3):" +
            "MERGE2(min_interval=5000;max_interval=10000):" +
            "FD_SOCK:" +
            "VERIFY_SUSPECT(timeout=1500):" +
            "pbcast.NAKACK(gc_lag=50;retransmit_timeout=300,600,1200,2400,4800):" +
            "UNICAST(timeout=5000):" +
            "pbcast.STABLE(desired_avg_gossip=20000):" +
            "FRAG(frag_size=8096;down_thread=false;up_thread=false):" +
            "pbcast.GMS(join_timeout=5000;join_retry_timeout=2000;shun=false;print_local_addr=true):" +
            "pbcast.STATE_TRANSFER";

        public ClusterConfig(Application app) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Resource res = null;
            String conf = app.getProperty("cluster.conf");

            if (conf != null) {
                res = new FileResource(new File(conf));
            } else {
                Iterator reps = app.getRepositories().iterator();
                while (reps.hasNext()) {
                    Repository rep = (Repository) reps.next();
                    res = rep.getResource("cluster.conf");
                    if (res != null)
                        break;
                }
            }

            if (res == null || !res.exists()) {
                app.logEvent("Resource \"" + conf + "\" not found, using defaults");
                return;
            }

            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(res.getInputStream());
                Element root = document.getDocumentElement();
                NodeList nodes = root.getElementsByTagName("jgroups-stack");

                if (nodes.getLength() == 0) {
                    app.logEvent("No JGroups stack found in cluster.conf, using defaults");
                } else {
                    NodeList jgroups = null;

                    String stackName = app.getProperty("cluster.jgroups.stack", "udp");
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Element elem = (Element) nodes.item(i);
                        if (stackName.equalsIgnoreCase(elem.getAttribute("name"))) {
                            jgroups = elem.getChildNodes();
                            break;
                        }
                    }

                    if (jgroups == null) {
                        app.logEvent("JGroups stack \"" + stackName +
                            "\" not found in cluster.conf, using first element");
                        jgroups = nodes.item(0).getChildNodes();
                    }

                    StringBuffer buffer = new StringBuffer();
                    for (int i = 0; i < jgroups.getLength(); i++) {
                        Node node = jgroups.item(i);
                        if ("#text".equals(node.getNodeName())) {
                            String str = node.getTextContent();
                            if (str == null) 
                                continue;
                            str = str.trim();
                            if (str.equals("") || str.startsWith("<!--"))
                                continue;

                            for (int j = 0; j < str.length(); j++) {
                                char c = str.charAt(j);
                                if (!Character.isWhitespace(c)) {
                                    buffer.append(c);
                                }
                            }
                        }
                    }
                    if (buffer.length() > 0) {
                        jGroupsProps = buffer.toString();
                    }
                }

            } catch (Exception e) {
                app.logError("ClusterCommunicator: Error reading config from " + res, e);
            }
        }

        public String getJGroupsProps() {
            return jGroupsProps;
        }
    }
    
    
    static class IdGenTransaction implements ITransaction {
        TransactionManager tmgr = null;
        long id;
        String host;
        
        public IdGenTransaction(long id, String host) {
            this.id = id;
            this.host = host;
        }
        
        public void commit() throws DatabaseException { }
        public void abort() throws DatabaseException { }
        public void addResource(Object res, int status) throws DatabaseException { }
        
        public void setId(long id) {
            this.id = id;
        }
        
        public void setHost(String host) {
            this.host = host;
        }

        public void commitToTransaction() throws TransactionException {
            boolean exceptionOccured = false;
            Connection conn = this.tmgr.getConnection();
            PreparedStatement pstmt = null;
            
            try {
                final String appName = this.tmgr.getApplication().getName();
                String sql = "SELECT COUNT(*) FROM IdGen WHERE cluster_host = ? AND app_name = ?";
                pstmt = conn.prepareStatement(sql);
                pstmt.setString(1, this.host);
                pstmt.setString(2, appName);
                ResultSet rs = pstmt.executeQuery();
                boolean firstTime = false;
                if (rs.next()) {
                    firstTime = rs.getInt(1) == 0;
                } else {
                    firstTime = true;
                }
                rs.close();
                rs = null;
                pstmt.close();
                pstmt = null;
                
                if (firstTime) {
                    sql = "INSERT INTO IdGen (id, cluster_host, app_name) VALUES (?,?,?)";
                    firstTime = false;
                } else {
                    sql = "UPDATE IdGen SET id = ? WHERE cluster_host = ? AND app_name = ?";
                }
                pstmt = conn.prepareStatement(sql);
                pstmt.setLong(1, this.id);
                pstmt.setString(2, this.host);
                pstmt.setString(3, appName);

                int rows = pstmt.executeUpdate();
                if (rows < 1) {
                    throw new Exception("IdGenTransaction.commitToTransaction(): Failed to " +
                            "INSERT/UPDATE IdGen with id = " + this.id + 
                            ", cluster_host = " + this.host);
                }
                
                pstmt.close();
                pstmt = null;
            } catch (Exception ex) {
                exceptionOccured = true;
                throw new TransactionException(ex.getMessage());
            } finally {            
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

        public void setTransactionManager(TransactionManager tmgr)
                throws TransactionException {
           this.tmgr = tmgr;
        }

        public TransactionManager getTransactionManager()
                throws TransactionException {
            return this.tmgr;
        }

        public void postSubTransaction() throws TransactionException { }
    }
    
    
    static class LuceneSegmentsTransaction implements ITransaction {
        TransactionManager tmgr = null;
        Directory dir = null;
        IndexDelta delta = null;
        
        public LuceneSegmentsTransaction(Directory dir, IndexDelta delta) {
            this.dir = dir;
            this.delta = delta;
        }
        
        public void commit() throws DatabaseException { }
        public void abort() throws DatabaseException { }
        public void addResource(Object res, int status) throws DatabaseException { }
        
        public void commitToTransaction() throws TransactionException {
            SegmentInfos sinfos = IndexObjectsFactory.getFSSegmentInfos(this.dir);
            SegmentInfos newinfos = new SegmentInfos();
            for (int i = 0; i < this.delta.segments.length; i++) {
                SegmentInfo si = new SegmentInfo(this.delta.segments[i], 
                        this.delta.doc_counts[i], this.dir);
                newinfos.add(si);
            }

            SegmentInfos copy;
            synchronized (sinfos) {
                copy = sinfos.copy();
            }
            
            copy.addAll(newinfos);
            
            try {
                copy.write(this.dir);
            } catch (IOException ioex) {
                throw new TransactionException("LuceneSegmentsTransaction.commitToTransaction()::" + ioex.getMessage());
            }
            
            LuceneManager.commitSegments(this.tmgr.getConnection(), 
                    this.tmgr.getApplication(), this.dir);
            
            copy.clear();
            copy = null;
            
            LuceneManager lmgr = null;
            try {
                lmgr = LuceneManager.getInstance(this.tmgr.getApplication());
            } catch (Exception ex) {
            }
            
            synchronized (sinfos) {
                sinfos.addAll(newinfos);
                applyLuceneChanges(this.delta, this.dir, lmgr.getOptimizer());
            }
        }

        public void setTransactionManager(TransactionManager tmgr)
                throws TransactionException {
           this.tmgr = tmgr;
           this.tmgr.getApplication().logEvent("*** INDEX DELTA\n:" + delta);
        }

        public TransactionManager getTransactionManager()
                throws TransactionException {
            return this.tmgr;
        }

        public void postSubTransaction() throws TransactionException { 
            try {
                LuceneManager.getInstance(this.tmgr.getApplication()).setSearcherDirty();
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new TransactionException("LuceneSegmentsTransaction.postSubTransaction() failed, " + ex.getMessage());
            }
        }
    }
    
    static class LuceneChangesTransaction implements ITransaction {
        TransactionManager tmgr = null;
        Directory dir = null;
        IndexDelta delta = null;
        
        public LuceneChangesTransaction(Directory dir, IndexDelta delta) {
            this.dir = dir;
            this.delta = delta;
        }
        
        public void commit() throws DatabaseException { }
        public void abort() throws DatabaseException { }
        public void addResource(Object res, int status) throws DatabaseException { }
        
        public void commitToTransaction() throws TransactionException {
            SegmentInfos sinfos = IndexObjectsFactory.getFSSegmentInfos(this.dir);
            SegmentInfos newinfos = new SegmentInfos();
            for (int i = 0; i < this.delta.segments.length; i++) {
                SegmentInfo si = new SegmentInfo(this.delta.segments[i], 
                        this.delta.doc_counts[i], this.dir);
                newinfos.add(si);
            }
            
            LuceneManager lmgr = null;
            try {
                lmgr = LuceneManager.getInstance(this.tmgr.getApplication());
            } catch (Exception ex) {
            }
            
            synchronized (sinfos) {
                sinfos.addAll(newinfos);
                applyLuceneChanges(this.delta, this.dir, lmgr.getOptimizer());
            }
        }

        public void setTransactionManager(TransactionManager tmgr)
                throws TransactionException {
           this.tmgr = tmgr;
           this.tmgr.getApplication().logEvent("INDEX DELTA:\n"+delta);
        }

        public TransactionManager getTransactionManager()
                throws TransactionException {
            return this.tmgr;
        }

        public void postSubTransaction() throws TransactionException { 
            try {
                LuceneManager.getInstance(this.tmgr.getApplication()).setSearcherDirty();
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new TransactionException("LuceneSegmentsTransaction.postSubTransaction() failed, " + ex.getMessage());
            }
        }
    }
    
    
    static class InvalidationListMsg implements Serializable {
        DbKey[] keys;
        
        public InvalidationListMsg(ArrayList<DbKey> keys) {
            final int size = keys.size();
            this.keys = new DbKey[size];
            for (int i = 0; i < size; i++) {
                this.keys[i] = keys.get(i);
            }
        }
        
        public DbKey[] getKeys() {
            return this.keys;
        }
    }
    
    
    static class TransactionUpdateMsg implements Serializable {
        String host;
        long id;
        IndexDelta delta;
        String[] ids_modified;
        String[] paths_modified;
        int[] modifying_ops;
        
        public TransactionUpdateMsg(String host, long id, IndexDelta delta,
                String[] ids, String[] paths, int[] ops) {
            this.host = host;
            this.id = id;
            this.delta = delta;
            this.ids_modified = ids;
            this.paths_modified = paths;
            this.modifying_ops = ops;
        }
    }
    
    static class LuceneUpdateMsg implements Serializable {
        String[] ids_to_remove;
        HashMap ax_id_to_location_map;
        int[] bit_positions_to_set;
        
        public LuceneUpdateMsg(String[] ids, HashMap map, int[] bits) {
            this.ids_to_remove = ids;
            this.ax_id_to_location_map = map;
            this.bit_positions_to_set = bits;
        }
    }
    
    static class LuceneOptimizedMsg implements Serializable {
        DeletedInfos dinfos;
        String[] segments_to_remove;
        SegmentInfos infos_to_add;
        
        public LuceneOptimizedMsg(DeletedInfos dinfos, String[] segs, SegmentInfos infos) {
            this.dinfos = dinfos;
            this.segments_to_remove = segs;
            this.infos_to_add = infos;
        }
    }*/
    
}