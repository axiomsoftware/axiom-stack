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

import java.io.File;
import java.sql.*;
import java.util.*;

import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.TransFSDirectory;

import axiom.framework.core.Application;
import axiom.objectmodel.db.TransSource;
import axiom.util.FileUtils;

public class LuceneVersion2Convertor extends LuceneConvertor {
    
	public LuceneVersion2Convertor(Application app) {
		super(app);
	}
	
	protected void updateSQL(Connection conn) {
		return;
	}
	
    public Document convertDocument(Document doc) {
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
             if (name.startsWith("__")) {
            	 name = name.substring(1);
            	 if ("_state".equals(name)) {
            		 name = "_status";
            	 } else if ("_prototype".equals(name) || "_parentproto".equals(name)) {
            		 if ("HopObject".equalsIgnoreCase(value)) {
            			 value = "AxiomObject";
            		 }
            	 } 
             } 
             ndoc.add(new Field(name, value, currstore, curridx));                    
         }
                
         return ndoc;
    }
    
}