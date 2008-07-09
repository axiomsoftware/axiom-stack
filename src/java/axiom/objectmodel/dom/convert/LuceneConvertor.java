package axiom.objectmodel.dom.convert;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DeletedInfos;
import org.apache.lucene.index.IndexObjectsFactory;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.TransFSDirectory;

import axiom.framework.ErrorReporter;
import axiom.framework.core.Application;
import axiom.objectmodel.INode;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.db.TransSource;
import axiom.objectmodel.dom.EmbeddedDbConvertor;
import axiom.objectmodel.dom.IndexWriterManager;
import axiom.objectmodel.dom.LuceneManager;
import axiom.util.FileUtils;

public abstract class LuceneConvertor implements EmbeddedDbConvertor {
    
	protected Application app;
	protected boolean recordNodes = false;
    protected HashMap allNodes;
    
	public LuceneConvertor(Application app){
	    this.app = app;
	}

	public void convert(Application app, File dbhome) throws Exception {
	    FSDirectory indexDir = FSDirectory.getDirectory(dbhome, false); 
	    if (indexDir instanceof TransFSDirectory) {
	        FSDirectory.setDisableLocks(true);
	        TransFSDirectory d = (TransFSDirectory) indexDir;
	        TransSource source = app.getTransSource();
	        d.setDriverClass(source.getDriverClass());
	        d.setUrl(source.getUrl());
	        d.setUser(source.getUser());
	        d.setPassword(source.getPassword());
	    }
	    File ndbhome = new File(dbhome.getParentFile(), dbhome.getName() + "_tmp");
	    File olddbhome = new File(dbhome.getParentFile(), dbhome.getName() + "_old");
	    FSDirectory nindexDir = FSDirectory.getDirectory(ndbhome, true);
	    if (nindexDir instanceof TransFSDirectory) {
	        FSDirectory.setDisableLocks(true);
	        TransFSDirectory d = (TransFSDirectory) nindexDir;
	        TransSource source = app.getTransSource();
	        d.setDriverClass(source.getDriverClass());
	        d.setUrl(source.getUrl());
	        d.setUser(source.getUser());
	        d.setPassword(source.getPassword());
	    }

	    IndexSearcher searcher = null;
	    IndexWriter writer = null;
        LuceneManager lmgr = null;

	    try {
	        searcher = new IndexSearcher(indexDir);
            PerFieldAnalyzerWrapper a = LuceneManager.buildAnalyzer();
            writer = IndexWriterManager.getWriter(nindexDir, a, true);
	        final int numDocs = searcher.getIndexReader().numDocs();

	        HashSet deldocs = new HashSet();
	        HashMap infos = new HashMap();
	        for (int i = 0; i < numDocs; i++) {
	            Document doc = searcher.doc(i);
	            String delprop = doc.get(DeletedInfos.DELETED);
	            String layerStr = doc.get(LuceneManager.LAYER_OF_SAVE);
	            int layer = -1;
	            try {
	            	layer = Integer.parseInt(layerStr);
	            } catch (Exception ex) {
	            	layer = -1;
	            }
	            final String id = doc.get(LuceneManager.ID) 
	            					+ DeletedInfos.KEY_SEPERATOR 
	            					+ doc.get(LuceneManager.LAYER_OF_SAVE);
	            if (delprop != null && "true".equals(delprop)/* && layer == DbKey.LIVE_LAYER*/) {
	                deldocs.add(id);
	            } else {
	                Object v;
	                if ((v = infos.get(id)) == null) {
	                    infos.put(id, new Integer(i));
	                } else {
	                    final String lmod = doc.get(LuceneManager.LASTMODIFIED);
	                    final String lmod_prev = searcher.doc(((Integer) v).intValue()).get("_lastmodified");
	                    if (lmod_prev == null || (lmod != null && lmod.compareTo(lmod_prev) > 0)) {
	                        infos.put(id, new Integer(i));
	                    }
	                }
	            }
	        }
            
            ArrayList listOfMaps = new ArrayList();

	        for (int i = 0; i < numDocs; i++) {
	            Document doc = searcher.doc(i);
	            String delprop = doc.get(DeletedInfos.DELETED);
	            String layerStr = doc.get(LuceneManager.LAYER_OF_SAVE);
	            int layer = -1;
	            try {
	            	layer = Integer.parseInt(layerStr);
	            } catch (Exception ex) {
	            	layer = -1;
	            }
	            final String id = doc.get(LuceneManager.ID)  
	            					+ DeletedInfos.KEY_SEPERATOR 
	            					+ doc.get(LuceneManager.LAYER_OF_SAVE);
	            if (delprop != null && "true".equals(delprop)) {
	                continue;
	            } else if (id != null && deldocs.contains(id)/* && layer == DbKey.LIVE_LAYER*/) {
	                continue;
	            } 
                
	            Integer idx = (Integer) infos.get(id);
	            if (idx != null && i != idx.intValue()) {
	                continue;
	            }

	            Document ndoc = convertDocument(doc);
              
                if (this.recordNodes) {
                    listOfMaps.add(LuceneManager.luceneDocumentToMap(doc));
                }
                
	            if (ndoc != null) {
	            	writer.addDocument(ndoc);
	            }
	        }

            if (this.recordNodes) {
                lmgr = new LuceneManager(this.app, false, true);
                this.allNodes = new HashMap();
                final int size = listOfMaps.size();
                for (int i = 0; i < size; i++) {
                    HashMap m = (HashMap) listOfMaps.get(i);
                    INode n = lmgr.mapToNode(m);
                    this.allNodes.put(n.getID(), getPath(n));
                    n = null;
                }
            }
            
	    } catch (Exception ex) {
	        ex.printStackTrace();
	        throw new RuntimeException(ex);
	    } finally {
	        if (searcher != null) {
	            try {
	                searcher.close();
	            } catch (Exception ex) {
	                app.logError(ErrorReporter.errorMsg(this.getClass(), "convert"), ex);
	            }
	        }

            if (lmgr != null) {
                lmgr.shutdown();
                lmgr = null;
            }
            
	        indexDir.close();
            SegmentInfos sinfos = IndexObjectsFactory.getFSSegmentInfos(indexDir);
            sinfos.clear();
            IndexObjectsFactory.removeDeletedInfos(indexDir);
	    }

	    Connection conn = null;
	    boolean exceptionOccured = false;
	    
        try {
	        if (writer != null) {
	            TransSource ts = app.getTransSource();
	            conn = ts.getConnection();

	            DatabaseMetaData dmd = conn.getMetaData();
	            ResultSet rs = dmd.getColumns(null, null, "Lucene", "version");
	            if (!rs.next()) {
	                final String alterTbl = "ALTER TABLE Lucene ADD version INT NOT NULL DEFAULT 1";
	                PreparedStatement pstmt = null;
	                try {
	                    pstmt = conn.prepareStatement(alterTbl);
	                    pstmt.execute();
	                } catch (SQLException sqle) {
	                    app.logError(ErrorReporter.errorMsg(this.getClass(), "convert"), sqle);
	                } finally {
	                    if (pstmt != null) {
	                        pstmt.close();
	                        pstmt = null;
	                    }
	                }
	            }
	            rs.close();
	            rs = null;

	            writer.close();
	            writer.flushCache();//TODO:writer.writeSegmentsFile();
	            LuceneManager.commitSegments(conn, app, writer.getDirectory());
	            writer.finalizeTrans();

	            this.updateSQL(conn);
	        }
	    } catch (Exception ex) {
            ex.printStackTrace();
	        exceptionOccured = true;
	        throw new RuntimeException(ex);
	    } finally {
	        if (conn != null) {
	            try { 
	                if (!conn.getAutoCommit()) {
	                    if (!exceptionOccured) {
	                        conn.commit();
	                    } else {
	                        conn.rollback();
	                    }
	                }
	                conn.close();
	            } catch (Exception ex) {
	                app.logError(ErrorReporter.errorMsg(this.getClass(), "convert"), ex);
	            }
	            conn = null;
	        }

	        nindexDir.close();
            SegmentInfos sinfos = IndexObjectsFactory.getFSSegmentInfos(nindexDir);
            sinfos.clear();
            IndexObjectsFactory.removeDeletedInfos(nindexDir);
	    }

	    if (!dbhome.renameTo(olddbhome)) {
	        throw new Exception("Could not move the old version of the db into " + olddbhome);
	    }

	    if (!ndbhome.renameTo(dbhome)) {
	        throw new Exception("Could not move the newer version of the db into " + dbhome);
	    }
        
        File oldBlobDir = new File(olddbhome, "blob");
        File newBlobDir = new File(ndbhome, "blob");
        oldBlobDir.renameTo(newBlobDir);

	    if (!FileUtils.deleteDir(olddbhome)) {
	        throw new Exception("Could not delete the old version of the db at " + olddbhome);
	    }
	}

    protected HashMap getAllNodes() {
        return this.allNodes;
    }
        
    protected boolean recordNodes() {
        return this.recordNodes;
    }
    
    private String getPath(INode n) throws Exception {
        String href = this.app.getNodeHref(n, null);    
        if (this.app.getBaseURI().equals("/")) {
            return href;
        }
        
        int a = href.indexOf('/', 1);
        if (a == -1) { 
            return null; 
        }       
        
        return href.substring(a);
    }

	protected abstract Document convertDocument(Document doc) throws Exception;
	protected abstract void updateSQL(Connection conn) throws Exception;
    
}