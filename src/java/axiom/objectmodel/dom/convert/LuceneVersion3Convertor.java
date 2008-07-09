package axiom.objectmodel.dom.convert;

import java.io.File;
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
import axiom.util.FileUtils;

public class LuceneVersion3Convertor extends LuceneConvertor {
    
	public LuceneVersion3Convertor(Application app){
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