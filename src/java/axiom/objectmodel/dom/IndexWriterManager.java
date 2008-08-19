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

import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.apache.lucene.analysis.*;

import axiom.framework.ErrorReporter;
import axiom.framework.core.Application;
import axiom.objectmodel.DatabaseException;
import axiom.objectmodel.db.TransSource;


public class IndexWriterManager {
    
    private Application app = null;
    private Directory indexDir = null;
    private Analyzer analyzer = null;
    private IndexOptimizer optimizer = null;
    private int version = -1;
    
    private static final boolean DEFAULT_USE_COMPOUND_FILE = true;
    private static final int DEFAULT_MERGE_FACTOR = Integer.MAX_VALUE;
    private static final int DEFAULT_MAX_BUFFERED_DOCS = Integer.MAX_VALUE;
    private static final int DEFAULT_MAX_MERGE_DOCS = Integer.MAX_VALUE;
    
    private static final String SEGMENTS = "segments";
    
    public IndexWriterManager(Application app, Analyzer analyzer, 
                                final boolean checkVersion, final boolean initDelInfos) 
    throws Exception {
        boolean exc = false;
        try {
            this.app = app;
            File dbhome = app.getDbDir();
            this.analyzer = analyzer;
            final boolean doesIndexExist = doesIndexExist(dbhome);
            this.indexDir = FSDirectory.getDirectory(dbhome, !doesIndexExist); 
            if (this.indexDir instanceof TransFSDirectory) {
                FSDirectory.setDisableLocks(true);
                TransFSDirectory d = (TransFSDirectory) this.indexDir;
                TransSource source = app.getTransSource();
                d.setDriverClass(source.getDriverClass());
                d.setUrl(source.getUrl());
                d.setUser(source.getUser());
                d.setPassword(source.getPassword());
                if (doesIndexExist && checkVersion) {
                    this.version = d.getVersion();
                    if (this.version < LuceneManager.LUCENE_VERSION) {
                        throw new DatabaseException("Out of date version: " + this.version);
                    }
                }
            }
        } catch (Exception ex) {
            exc = true;
            throw ex;
        } finally {
            if (exc && this.indexDir != null) {
                this.indexDir.close();
            }
        }
        
        if (initDelInfos) {
            IndexObjectsFactory.initDeletedInfos(this.indexDir);
        }
    }
    
    public IndexWriter getWriter() throws Exception {
        IndexWriter writer = new IndexWriter(indexDir, this.analyzer, false);
        writer.setUseCompoundFile(DEFAULT_USE_COMPOUND_FILE);
        writer.setMergeFactor(DEFAULT_MERGE_FACTOR);
        writer.setMaxBufferedDocs(DEFAULT_MAX_BUFFERED_DOCS);
        writer.setMaxMergeDocs(DEFAULT_MAX_MERGE_DOCS);
        //TODO:writer.setCurrentOptimizer(this.optimizer);
        return writer;
    }  
    
    public static IndexWriter getWriter(Directory dir, Analyzer a, boolean create)
    throws Exception {
        IndexWriter writer = new IndexWriter(dir, a, create);
        writer.setUseCompoundFile(DEFAULT_USE_COMPOUND_FILE);
        writer.setMergeFactor(DEFAULT_MERGE_FACTOR);
        writer.setMaxBufferedDocs(DEFAULT_MAX_BUFFERED_DOCS);
        writer.setMaxMergeDocs(DEFAULT_MAX_MERGE_DOCS);
        return writer;
    }
    
    public IndexWriter releaseWriter(IndexWriter writer) throws Exception {    
        if (writer != null) {            
            try {
                writer.close();
            } catch (Exception ex) {
                this.app.logError(ErrorReporter.errorMsg(this.getClass(), "releaseWriter"), ex);
                throw new DatabaseException("ERROR in IndexWriterManager.releaseWriter(): Could not release the index writer");
            } 
        }
        return writer;
    }
    
    public IndexWriter abort(IndexWriter writer) throws Exception {
        if (writer != null) {            
            try {                
                if (IndexReader.isLocked(indexDir)) {
                    IndexReader.unlock(indexDir);                
                }                
            } catch (Exception ex) {
                throw new DatabaseException("ERROR in IndexWriterManager.abort(): Could not unlock the index " + indexDir);
            } finally {
                writer = null;
            }
        }     
        return writer;
    }
    
    public Directory getDirectory() {
        return this.indexDir;
    }
    
    public boolean doesIndexExist(File dir) throws IOException {
        Connection conn = null;
        try {
            conn = this.app.getTransSource().getConnection(true);
            return TransFSDirectory.segmentsExists(conn, dir.getName());
        } catch (Exception ex) {
            this.app.logError(ErrorReporter.errorMsg(this.getClass(), "doesIndexExist"), ex);
        } finally {
            if (conn != null) {
                try { 
                    conn.close(); 
                } catch (SQLException sqle) { 
                    app.logError(ErrorReporter.errorMsg(this.getClass(), "doesIndexExist"), sqle);
                }
                conn = null;
            }
        }
        
        return false;
    }
    
    public boolean doesIndexExist(Directory dir) throws IOException {
        return dir.fileExists(SEGMENTS);    
    }
    
    public int getCurrentVersion() {
        return this.version;
    }
    
    public void setOptimizer(IndexOptimizer optimizer) {
        this.optimizer = optimizer;
    }
    
}