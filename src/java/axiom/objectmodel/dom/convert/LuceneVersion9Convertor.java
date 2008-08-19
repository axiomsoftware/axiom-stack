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
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Enumeration;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import axiom.framework.core.Application;
import axiom.objectmodel.dom.LuceneManager;

public class LuceneVersion9Convertor extends LuceneConvertor {
    
    public LuceneVersion9Convertor(Application app) {
        super(app);
    }
    
    protected void updateSQL(Connection conn) throws Exception {
    }
    
    public Document convertDocument(Document doc) {
    	Field protoField = doc.getField(LuceneManager.PROTOTYPE);
    	if (protoField != null && 
    			("CMSTask".equalsIgnoreCase(protoField.stringValue())
    			|| "CMSTaskContainer".equalsIgnoreCase(protoField.stringValue()))) {
    		return null;
    	}
    	
        Document ndoc = new Document();
        Enumeration e = doc.fields();
        
        String id = null, layer = null;
        Field idField = doc.getField(LuceneManager.ID);
        Field layerField = doc.getField(LuceneManager.LAYER_OF_SAVE);
        if (idField != null && layerField != null) {
        	id = idField.stringValue();
        	layer = layerField.stringValue();
        }
        
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
            
            if (!("84".equals(id) && "1".equals(layer) && "_task".equals(name)) &&
            		!("71".equals(id) && "1".equals(layer) && "_task".equals(name))) {
            	
            	ndoc.add(new Field(name, value, currstore, curridx));                    
            }
        }
               
        return ndoc;
   }
    
}