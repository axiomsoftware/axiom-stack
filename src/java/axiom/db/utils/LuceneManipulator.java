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
package axiom.db.utils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DeletedInfos;
import org.apache.lucene.index.IndexObjectsFactory;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.TransFSDirectory;

import axiom.objectmodel.db.TransSource;
import axiom.objectmodel.dom.IndexWriterManager;
import axiom.objectmodel.dom.LuceneManager;
import axiom.util.FileUtils;

public abstract class LuceneManipulator {

	private static final String DRIVER_CLASS = TransSource.DEFAULT_DRIVER;
	private static final String DEFAULT_URL = TransSource.DEFAULT_URL;
	private static final String TRANS_DB_DIR = TransSource.TRANSACTIONS_DB_DIR;
	private static final String TRANS_DB_NAME = TransSource.TRANSACTIONS_DB_NAME;
	
	public void compress(String dbDir) throws Exception {
		System.setProperty("org.apache.lucene.FSDirectory.class","org.apache.lucene.store.TransFSDirectory");
		
		File dbhome = new File(dbDir);
		String url = getUrl(dbhome);
		
		FSDirectory indexDir = FSDirectory.getDirectory(dbhome, false); 
	    if (indexDir instanceof TransFSDirectory) {
	        FSDirectory.setDisableLocks(true);
	        TransFSDirectory d = (TransFSDirectory) indexDir;
	        d.setDriverClass(DRIVER_CLASS);
	        d.setUrl(url);
	        d.setUser(null);
	        d.setPassword(null);
	    }
	    
	    File ndbhome = new File(dbhome.getParentFile(), dbhome.getName() + "_tmp");
	    File olddbhome = new File(dbhome.getParentFile(), dbhome.getName() + "_old");
	    FSDirectory nindexDir = FSDirectory.getDirectory(ndbhome, true);
	    if (nindexDir instanceof TransFSDirectory) {
	        FSDirectory.setDisableLocks(true);
	        TransFSDirectory d = (TransFSDirectory) nindexDir;
	        d.setDriverClass(DRIVER_CLASS);
	        d.setUrl(url);
	        d.setUser(null);
	        d.setPassword(null);
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
	            final String id = doc.get(LuceneManager.ID) 
	            					+ DeletedInfos.KEY_SEPERATOR 
	            					+ doc.get(LuceneManager.LAYER_OF_SAVE);
	            if (delprop != null && "true".equals(delprop)) {
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
	            } else if (id != null && deldocs.contains(id)) {
	                continue;
	            } 
                
	            Integer idx = (Integer) infos.get(id);
	            if (idx != null && i != idx.intValue()) {
	                continue;
	            }

	            Document ndoc = convertDocument(doc);
              
	            if (ndoc != null) {
	            	writer.addDocument(ndoc);
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
	        	conn = DriverManager.getConnection(url);
	        	conn.setAutoCommit(false);
	            writer.close();
	            writer.flushCache();
	            LuceneManager.commitSegments(null, conn, dbhome, writer.getDirectory());
	            writer.finalizeTrans();
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
	            	ex.printStackTrace();
	            }
	            conn = null;
	        }

	        nindexDir.close();
            SegmentInfos sinfos = IndexObjectsFactory.getFSSegmentInfos(nindexDir);
            sinfos.clear();
            IndexObjectsFactory.removeDeletedInfos(nindexDir);
	    }

	    File[] files = dbhome.listFiles();
	    for (int i = 0; i < files.length; i++) {
	    	if (!files[i].isDirectory()) {
	    		files[i].delete();
	    	}
	    }
	    
	    files = ndbhome.listFiles();
	    for (int i = 0; i < files.length; i++) {
	    	if (!files[i].isDirectory()) {
	    		File nfile = new File(dbhome, files[i].getName());
	    		files[i].renameTo(nfile);
	    	}
	    }
	    
	    if (!FileUtils.deleteDir(ndbhome)) {
	    	throw new Exception("Could not delete " + ndbhome);
	    }
	}
	
	public static String getUrl(File dbhome) {
        String url = DEFAULT_URL + dbhome.getPath();
        if (!url.endsWith(File.separator)) url += File.separator;
        url += TRANS_DB_DIR + File.separator + TRANS_DB_NAME;
        return url;
	}

	protected Document convertDocument(Document doc) {
		Document ndoc = new Document();
		Enumeration e = doc.fields();

		while (e.hasMoreElements()) {
			Field f = (Field) e.nextElement();
			Field.Store currstore = Field.Store.YES;
			if (!f.isStored()) {
				currstore = Field.Store.NO;
			} else if (f.isCompressed()) {
				currstore = Field.Store.COMPRESS;
			}
			Field.Index curridx = Field.Index.UN_TOKENIZED;
			if (!f.isIndexed()) {
				curridx = Field.Index.NO;
			} else if (f.isTokenized()) {
				curridx = Field.Index.TOKENIZED;
			}

			String name = f.name();
			String value = f.stringValue();

			ndoc.add(new Field(name, value, currstore, curridx));                    
		}

		return ndoc;
	}
	
}