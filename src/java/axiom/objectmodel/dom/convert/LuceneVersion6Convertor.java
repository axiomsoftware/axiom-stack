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

import java.util.Enumeration;
import java.util.Date;
import java.sql.Connection;
import java.text.DateFormat;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import axiom.framework.core.Application;


public class LuceneVersion6Convertor extends LuceneConvertor {
	
	private final String[] months = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
	
	public LuceneVersion6Convertor(Application app) {
		super(app);
	}
	
	protected void updateSQL(Connection conn) {
		return;
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
        	if(name.equals("cms_lasteditedby")){
        		Date last = new Date(Long.parseLong(doc.get("_lastmodified")));
        		String user = doc.get("lastmodifiedby");
        		if(user == null){
        			user = "";
        		}
        		value = last.getDate()+" "+months[last.getMonth()]+" "+
        				(last.getYear()+"").substring(1)+", "+last.getHours()+':'+
        				(last.getMinutes()<10?'0'+last.getMinutes():last.getMinutes())+
        				" by "+user;
        	} 
        	ndoc.add(new Field(name, value, currstore, curridx));                    
        }
        
        return ndoc;
	}

}