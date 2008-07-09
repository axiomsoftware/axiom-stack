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