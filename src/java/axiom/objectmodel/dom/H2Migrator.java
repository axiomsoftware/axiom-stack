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

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Enumeration;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import axiom.framework.core.Application;
import axiom.main.Server;
import axiom.util.FileUtils;

public class H2Migrator extends DbMigrator {

    public H2Migrator() {
        super();
    }

    public void migrateDb(Application app) throws Exception {
        File oldDbDir = new File(new File(app.getServerDir(), "db"), "Transactions");
        if (!oldDbDir.exists()) {
            return;
        }
        
        Connection conn = null;
        Connection oldConn = null;
        PreparedStatement selectStmt = null;
        PreparedStatement insertStmt = null;
        ResultSet rs = null;
        boolean exceptionOccured = false;
        
        try {
            conn = app.getTransSource().getConnection();
            System.setProperty("derby.system.home", Server.getServer().getDbHome().getAbsolutePath());
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver").newInstance();
            oldConn = DriverManager.getConnection("jdbc:derby:Transactions;create=true");
            
            selectStmt = oldConn.prepareStatement("SELECT * FROM Lucene");
            insertStmt = conn.prepareStatement("INSERT INTO Lucene(timestamp, valid, " +
            		"db_home, segments, version) VALUES (?,?,?,?,?)");
            rs = selectStmt.executeQuery();
            while (rs.next()) {
                insertStmt.setTimestamp(1, rs.getTimestamp("timestamp"));
                insertStmt.setBoolean(2, rs.getBoolean("valid"));
                insertStmt.setString(3, rs.getString("db_home"));
                insertStmt.setBinaryStream(4, rs.getBinaryStream("segments"));
                insertStmt.setInt(5, rs.getInt("version"));
                
                insertStmt.addBatch();
            }
            
            insertStmt.executeBatch();
            rs.close();
            selectStmt.close();
            insertStmt.close();
            rs = null;
            selectStmt = null;
            insertStmt = null;
            
            selectStmt = oldConn.prepareStatement("SELECT * FROM IdGen");
            insertStmt = conn.prepareStatement("INSERT INTO IdGen(id, cluster_host) " +
            		"VALUES (?,?)");
            rs = selectStmt.executeQuery();
            while (rs.next()) {
                insertStmt.setLong(1, rs.getLong("id"));
                insertStmt.setString(2, "");
                
                insertStmt.addBatch();
            }
            
            insertStmt.executeBatch();
            rs.close();
            selectStmt.close();
            insertStmt.close();
            rs = null;
            selectStmt = null;
            insertStmt = null;
            
            selectStmt = oldConn.prepareStatement("SELECT * FROM PathIndices");
            insertStmt = conn.prepareStatement("INSERT INTO PathIndices(id, " +
            		"path) VALUES (?,?)");
            rs = selectStmt.executeQuery();
            while (rs.next()) {
                insertStmt.setString(1, rs.getString("id"));
                insertStmt.setString(2, rs.getString("path"));
                
                insertStmt.addBatch();
            }
            
            insertStmt.executeBatch();
            rs.close();
            selectStmt.close();
            insertStmt.close();
            rs = null;
            selectStmt = null;
            insertStmt = null;
            
        } catch (Exception ex) {
            ex.printStackTrace();
            exceptionOccured = true;
            throw ex;
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception ex2) {
                }
                rs = null;
            }
            if (selectStmt != null) {
                try { 
                    selectStmt.close();
                } catch (Exception ex2) {
                }
                selectStmt = null;
            }
            if (insertStmt != null) {
                try { 
                    insertStmt.close();
                } catch (Exception ex2) {
                }
                insertStmt = null;
            }
            if (oldConn != null) {
                try { 
                    oldConn.close();
                } catch (Exception ex2) {
                }
                oldConn = null;
            }
            try {
                DriverManager.getConnection("jdbc:derby:;shutdown=true");
            } catch (Exception e) {
            }
            if (conn != null) {
                try {
                    if (exceptionOccured) {
                        conn.rollback();
                    } else {
                        conn.commit();
                    }
                    conn.close();
                } catch (Exception ex2) {
                    throw ex2;
                }
                conn = null;
            }
        }
        
        FileUtils.deleteDir(oldDbDir);
        new File(new File(app.getServerDir(), "db"), "derby.log").delete();
    }

}