package axiom.objectmodel.dom.convert;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Enumeration;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import axiom.framework.core.Application;

public class LuceneVersion8Convertor extends LuceneConvertor {
    
    public LuceneVersion8Convertor(Application app) {
        super(app);
    }
    
    protected void updateSQL(Connection conn) throws Exception {
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            DatabaseMetaData dmd = conn.getMetaData();
            rs = dmd.getColumns(null, null, "IDGEN", "CLUSTER_HOST");
            if (rs.next()) {
                rs.close();
                rs = null;
                return;
            }
            
            final String query = "ALTER TABLE IdGen ADD cluster_host VARCHAR(10) NOT NULL DEFAULT ''";
            pstmt = conn.prepareStatement(query);
            pstmt.execute();
            pstmt.close();
            pstmt = null;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
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
    
}