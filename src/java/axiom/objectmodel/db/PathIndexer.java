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
package axiom.objectmodel.db;

import java.sql.*;
import java.util.ArrayList;

import axiom.framework.ErrorReporter;
import axiom.framework.core.Application;
import axiom.framework.core.RequestEvaluator;


public final class PathIndexer {

    private Application app;
    private TransSource transSource;
    private Connection conn;
    private String appName;
    
    private static final String TABLENAME = "PathIndices";
    private static final String WILDCARD = "%";
    
    public PathIndexer(Application app) throws Exception {
        this.app = app;
        this.transSource = app.getTransSource();
        this.appName = app.getName();
        
        checkConnection();
    }
    
    public void finalize() throws Throwable {
        closeConnection();
    }
    
    public synchronized String getId(final String path, final int layer) {
        String id = null;
        
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            checkConnection();
            
            StringBuffer sql = new StringBuffer("SELECT id FROM ");
            sql.append(TABLENAME).append(" WHERE path = ? AND layer = ?");
            
            pstmt = conn.prepareStatement(sql.toString());
            
            pstmt.setString(1, path);
            pstmt.setInt(2, layer);
             
            rs = pstmt.executeQuery();
            if (rs.next()) {
                id = rs.getString(1);
            }
            
            if (rs.next()) {
                app.logError(ErrorReporter.errorMsg(this.getClass(), "getId") 
                		+ "More than one path index entry for [" + path + "]");
            }
        } catch (Exception ex) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "getId"), ex);
        } finally {
            if (rs != null) {
                try { 
                	rs.close(); 
                } catch (SQLException sqle) { 
                	app.logError(ErrorReporter.errorMsg(this.getClass(), "getId"), sqle);
                }
                rs = null;
            }
            if (pstmt != null) {
                try { 
                	pstmt.close(); 
                } catch (SQLException sqle) { 
                	app.logError(ErrorReporter.errorMsg(this.getClass(), "getId"), sqle);
                }
                pstmt = null;
            }
        }
        
        return id;
    }
    
    public synchronized ArrayList getIds(final String path, final int layer) {
        ArrayList ids = new ArrayList();
        
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            checkConnection();
            
            StringBuffer sql = new StringBuffer("SELECT id FROM ");
            sql.append(TABLENAME).append(" WHERE path LIKE ? AND layer = ?");

            pstmt = conn.prepareStatement(sql.toString());
            
            pstmt.setString(1, path + "%");
            pstmt.setInt(2, layer);
             
            rs = pstmt.executeQuery();
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
        } catch (Exception ex) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "getIds"), ex);
        } finally {
            if (rs != null) {
                try { 
                	rs.close(); 
                } catch (SQLException sqle) { 
                	app.logError(ErrorReporter.errorMsg(this.getClass(), "getIds"), sqle);
                }
                rs = null;
            }
            if (pstmt != null) {
                try { 
                	pstmt.close(); 
                } catch (SQLException sqle) { 
                	app.logError(ErrorReporter.errorMsg(this.getClass(), "getIds"), sqle);
                }
                pstmt = null;
            }
        }
        
        return ids;
    }
    
    public synchronized String getPath(final String id, final int layer) {
        String path = null;
        
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            checkConnection();
            
            StringBuffer sql = new StringBuffer("SELECT path FROM ");
            sql.append(TABLENAME).append(" WHERE id = ? AND layer = ?");
            
            pstmt = conn.prepareStatement(sql.toString());
            
            pstmt.setString(1, id);
            pstmt.setInt(2, layer);
             
            rs = pstmt.executeQuery();
            if (rs.next()) {
                path = rs.getString(1);
            }
            
            if (rs.next()) {
                app.logError(ErrorReporter.errorMsg(this.getClass(), "getPath") 
                		+ "More than one path index entry for [" + id + "]");
            }
        } catch (Exception ex) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "getPath"), ex);
        } finally {
            if (rs != null) {
                try { 
                	rs.close(); 
                } catch (SQLException sqle) {
                	app.logError(ErrorReporter.errorMsg(this.getClass(), "getPath"), sqle);
                }
                rs = null;
            }
            if (pstmt != null) {
                try { 
                	pstmt.close(); 
                } catch (SQLException sqle) { 
                	app.logError(ErrorReporter.errorMsg(this.getClass(), "getPath"), sqle);
                }
                pstmt = null;
            }
        }
        
        return path;
    }
    
    public void updatePathIndices(Connection conn, ArrayList nodes)
    throws Exception {
        PreparedStatement pstmt = null;
        
        final int size = nodes.size();
        if (size == 0) {
            return;
        }
        
        try {
            if (conn.getAutoCommit()) {
                throw new Exception("Cannot do batch path index updates when auto-commit is set to true.");
            }
            
            StringBuffer sql = new StringBuffer("UPDATE ");
            sql.append(TABLENAME).append(" SET path = ? WHERE id = ? AND layer = ?");
            
            pstmt = conn.prepareStatement(sql.toString());
            
            for (int i = 0; i < size; i++) {
                Object item = nodes.get(i); 
                
                if (item instanceof Node) {
                    Node node = (Node) item;
                    
                    pstmt.setString(1, getNodeHref(node, this.app));
                    pstmt.setString(2, node.getID());
                    pstmt.setInt(3, node.getKey().getLayer());
                } else {
                    String[] values = (String[]) item;
                    
                    pstmt.setString(1, values[1]);
                    pstmt.setString(2, values[0]);
                }
                
                pstmt.addBatch();
            }
            
            int[] rows = pstmt.executeBatch();
            
            final int length = rows.length;
            if (length != size) {
                throw new Exception("Error executing path updates.");
            }
        } catch (Exception ex) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "updatePathIndices"), ex);
            throw ex;
        } finally {
            if (pstmt != null) {
                try { 
                	pstmt.close(); 
                } catch (SQLException sqle) { 
                	app.logError(ErrorReporter.errorMsg(this.getClass(), "updatePathIndices"), sqle);
                }
                pstmt = null;
            }
        }
    }
    
    public void addPathIndices(Connection conn, ArrayList nodes)
    throws Exception {
        PreparedStatement pstmt = null;

        final int size = nodes.size();
        if (size == 0) {
            return;
        }
        
        try {
            if (conn.getAutoCommit()) {
                throw new Exception("Cannot do batch path index adds when auto-commit is set to true.");
            }
            
            StringBuffer sql = new StringBuffer("INSERT INTO ");
            sql.append(TABLENAME).append(" (id, layer, path) VALUES (?,?,?)");
            
            pstmt = conn.prepareStatement(sql.toString());
            
            for (int i = 0; i < size; i++) {
                Object item = nodes.get(i);
                
                if (item instanceof Node) {
                    Node node = (Node) item;
                    
                    pstmt.setString(1, node.getID());
                    pstmt.setInt(2, node.getKey().getLayer());
                    pstmt.setString(3, getNodeHref(node, this.app));
                } else {
                    String[] values = (String[]) item;
                    
                    pstmt.setString(1, values[0]);
                    pstmt.setString(2, values[1]);
                }
                
                pstmt.addBatch();
            }
            
            int[] rows = pstmt.executeBatch();
            
            final int length = rows.length;
            if (length != size) {
                throw new Exception("Error executing path adds.");
            }
        } catch (Exception ex) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "addPathIndices"), ex);
            throw ex;
        } finally {
            if (pstmt != null) {
                try { 
                	pstmt.close(); 
                } catch (SQLException sqle) { 
                	app.logError(ErrorReporter.errorMsg(this.getClass(), "addPathIndices"), sqle);
                }
                pstmt = null;
            }
        }
    }
    
    public void deletePathIndices(Connection conn, ArrayList nodes)
    throws Exception {
        PreparedStatement pstmt = null;
        
        final int size = nodes.size();
        if (size == 0) {
            return;
        }
        
        try {
            if (conn.getAutoCommit()) {
                throw new Exception("Cannot do batch path index deletes when auto-commit is set to true.");
            }
            
            StringBuffer sql = new StringBuffer("DELETE FROM ");
            sql.append(TABLENAME).append(" WHERE id = ? AND layer = ? ");
            
            pstmt = conn.prepareStatement(sql.toString());
            
            for (int i = 0; i < size; i++) {
                Object item = nodes.get(i); 
                
                if (item instanceof Key) {
                	Key key = (Key) item;
                	pstmt.setString(1, key.getID());
                	pstmt.setInt(2, key.getLayer());
                } else if (item instanceof Node) {
                	Node node = (Node) item;
                    pstmt.setString(1, node.getID());
                    pstmt.setInt(2, node.getKey().getLayer());
                } else {
                    pstmt.setString(1, ((String[]) item)[0]);
                }
                
                pstmt.addBatch();
            }

            int[] rows = pstmt.executeBatch();
            
            final int length = rows.length;
            if (length != size) {
                throw new Exception("Error executing path adds.");
            }
        } catch (Exception ex) {
            app.logError(ErrorReporter.errorMsg(this.getClass(), "deletePathIndices"), ex);
            throw ex;
        } finally {
            if (pstmt != null) {
                try { 
                	pstmt.close(); 
                } catch (SQLException sqle) { 
                	app.logError(ErrorReporter.errorMsg(this.getClass(), "deletePathIndices"), sqle);
                }
                pstmt = null;
            }
        }
    }
    
    protected void checkConnection() throws Exception {
        if (!transSource.checkConnection(this.conn)) {
            closeConnection();
            
            this.conn = transSource.getConnection(true);
        }
    }
    
    public static String getNodeHref(Node n, Application app) throws Exception {
        String href = app.getNodeHref(n, null);    
        if (app.getBaseURI().equals("/")) {
            return href;
        }
        
        int a = href.indexOf('/', 1);
        if (a == -1) { 
            return null; 
        }       
        
        return href.substring(a);
    }
    
    public void shutdown() throws Exception {
        this.closeConnection();
    }
    
    private void closeConnection() throws Exception {
        if (this.conn != null) {
            try {
                if (!this.conn.getAutoCommit()) {
                    this.conn.rollback();
                }
                this.conn.close();
            } catch (SQLException sqle) { 
            } finally {
                this.conn = null;
            }
        }
    }
    
}