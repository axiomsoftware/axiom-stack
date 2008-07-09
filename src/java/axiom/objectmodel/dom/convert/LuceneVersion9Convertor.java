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