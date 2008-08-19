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
package axiom.objectmodel.dom.convert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import axiom.framework.core.Application;
import axiom.framework.core.Prototype;
import axiom.objectmodel.INode;
import axiom.objectmodel.IProperty;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.dom.LuceneDataFormatter;
import axiom.objectmodel.dom.LuceneManager;
import axiom.util.ResourceProperties;

public class LuceneVersion4Convertor extends LuceneConvertor {

	public LuceneVersion4Convertor(Application app){
		super(app);
        super.recordNodes = true;
	}
	
	protected void updateSQL(Connection conn) throws Exception {
        if (super.allNodes == null) {
            return;
        }
        
        PreparedStatement pstmt = null;
        Iterator iter = super.allNodes.keySet().iterator();
        
        try {
            pstmt = conn.prepareStatement("DELETE FROM PathIndices ");
            pstmt.executeUpdate();
            pstmt.close();
            pstmt = null;
            
            pstmt = conn.prepareStatement("INSERT INTO PathIndices(id, path)" +
            								" VALUES(?,?)");
            while (iter.hasNext()) {
                String id = (String) iter.next();
                String path = (String) super.allNodes.get(id);
                pstmt.setString(1, id);
                pstmt.setString(2, path);
                if (pstmt.executeUpdate() < 1) {
                    throw new Exception("id " + id + " was not in the db, so couldnt update it");
                }
            }
            pstmt.close();
            pstmt = null;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            super.allNodes.clear();
            if (pstmt != null) {
                try { 
                    pstmt.close();
                } catch (Exception ignore) {
                }
                pstmt = null;
            }
        }
	}
	
    public Document convertDocument(Document doc) {
    	
    	Document ndoc = new Document();
    	Enumeration e = doc.fields();
    	
    	final String prototype = doc.get("_prototype");
        final Prototype proto = app.getPrototypeByName(prototype);
                
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
        	final int type = this.getTypeForProperty(proto, name);
        	if (type == IProperty.DATE) {
        		value = formatDate(value);
        	} else if (type == IProperty.TIME) {
        		value = formatTime(value);
        	} 
        	ndoc.add(new Field(name, value, currstore, curridx));                    
        }
        
        return ndoc;
    }
    
    private int getTypeForProperty(Prototype prototype, String property) {
        if (prototype == null) {
            return IProperty.STRING;
        }
        ResourceProperties props = prototype.getAllProps();
        String strtype = (String) props.get(property + ".type");
        return LuceneManager.stringToType(strtype);
    }
    
    public String formatDate(String v) {
        long u = System.currentTimeMillis();
        try {
        	u = Long.parseLong(v);
        	int len = (u+"").length();
            if (len == 10 || len == 9) {
                u *= 1000;
            } else if(len == 7){
            	u *= 1000000;
            }
    
        } catch (Exception ex) {
            u = System.currentTimeMillis();
        }
        double l = LuceneDataFormatter.roundUpDouble(u / LuceneDataFormatter.DATE_DIVIDER);
        return Long.toString((long) l);
    }
    
    public String formatTime(String v) {
        long u = System.currentTimeMillis();

        try {
        	u = Long.parseLong(v);
        	int len = (u+"").length();
            if (len == 10 || len == 9) {
                u *= 1000;
            } else if(len == 7){
            	u *= 1000000;
            }
        } catch (Exception ex) {
            u = System.currentTimeMillis();
        }
        double l = LuceneDataFormatter.roundUpDouble(u / 1000d);
        return Long.toString((long) l);
    }

}