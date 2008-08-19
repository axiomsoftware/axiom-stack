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
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.TransFSDirectory;

import axiom.framework.core.Application;
import axiom.objectmodel.db.TransSource;
import axiom.scripting.rhino.extensions.DOMParser;
import axiom.util.FileUtils;

import org.w3c.dom.*;

import org.xml.sax.InputSource;

public class LuceneVersion5Convertor extends LuceneConvertor {
    
	private org.w3c.dom.Document cms_props;
	
	public LuceneVersion5Convertor(Application app) {
		super(app);
		com.sun.org.apache.xerces.internal.parsers.DOMParser parser =
            new com.sun.org.apache.xerces.internal.parsers.DOMParser();
        try {
            parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            parser.parse(new org.xml.sax.InputSource(new FileReader(app.getAppDir()+File.separator+"cms.xml")));
            cms_props = parser.getDocument();
        } catch(Exception e) {
        	e.printStackTrace();
        }
	}
	
	protected void updateSQL(Connection conn) {
		return;
	}
	
	protected Document convertDocument(Document doc) {
    	Document ndoc = new Document();
        Enumeration e = doc.fields();
        if (doc.get("cms_sortable_prototype") == null) {
        	ndoc.add(new Field("cms_sortable_prototype", getDisplayPrototype(cms_props, doc.get("_prototype")), Field.Store.YES, Field.Index.UN_TOKENIZED));
        }
        if (doc.get("cms_sortabletitle") == null && doc.get("title") != null) {
        	ndoc.add(new Field("cms_sortabletitle", doc.get("title").toLowerCase(), Field.Store.YES, Field.Index.UN_TOKENIZED));
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
           
            ndoc.add(new Field(name, value, currstore, curridx));                    
        }
        
        return ndoc;
    }
    
    private String getDisplayPrototype(org.w3c.dom.Document cms_props, String proto) {
		NodeList protos = cms_props.getElementsByTagName("prototype");
		String value = proto;
		for (int j=0; j<protos.getLength(); j++) {
				Node n = protos.item(j);
				if (proto.equals(n.getAttributes().getNamedItem("name").getNodeValue())) {
					Node displayname = n.getAttributes().getNamedItem("displayname");
					if (displayname != null) {
					    value = displayname.getNodeValue();
					}
					break;
				}
		}
		return value;
    }
    
}