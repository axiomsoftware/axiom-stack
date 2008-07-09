package axiom.objectmodel.dom.convert;

import java.sql.Connection;
import java.util.Enumeration;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import axiom.framework.core.Application;

public class LuceneCMSStatusConvertor extends LuceneConvertor {

	public LuceneCMSStatusConvertor(Application app) {
        super(app);
    }
	
	protected Document convertDocument(Document doc) throws Exception {
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
            
            if (name.equalsIgnoreCase("_status")) {
            	name = "cms_status";
            }
            
            ndoc.add(new Field(name, value, currstore, curridx));                    
        }
               
        return ndoc;
	}

	protected void updateSQL(Connection conn) throws Exception {
	}
	
	public static void main(String[] args) {
		
	}

}