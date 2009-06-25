package axiom.db.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.TransFSDirectory;

import axiom.objectmodel.db.TransSource;
import axiom.util.UnicodeUtils;

public class AxiomExporter {
	File dir = null;
	
	public AxiomExporter(File dir){
		this.dir = dir;
	}
	
	public void export() {
		long start = System.currentTimeMillis();
		HashMap<String, String> controlcode_map = UnicodeUtils.getControlCodeMap(true);
		
		try{
			Directory directory = FSDirectory.getDirectory(dir,false);
			if(directory instanceof TransFSDirectory){
		        FSDirectory.setDisableLocks(true);
		        TransFSDirectory d = (TransFSDirectory) directory;		        
		        d.setDriverClass(TransSource.DEFAULT_DRIVER);
		        d.setUrl(LuceneManipulator.getUrl(dir));
		        d.setUser(null);
		        d.setPassword(null);
			}

			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(dir.getName()+".xml")));
			
			bos.write("<documents>".getBytes());
			
			IndexSearcher searcher = new IndexSearcher(directory);
	    	Connection conn = DriverManager.getConnection(LuceneManipulator.getUrl(dir));
	    	PreparedStatement pathIndexQuery = conn.prepareStatement("SELECT path,layer FROM PathIndices WHERE id = ?");
	    	int doc_num = searcher.maxDoc();
	    	System.out.println("Exporting " + doc_num + " documents.");
	    	for(int i=0; i < doc_num; i++){
	    		if ((i > 0) && ((i/doc_num)%.1 > 0.099) && ((i/doc_num)%.1 < 1.01)) {
	    			System.out.println("i: " + i);
	    			System.out.println((doc_num/i) + "%");
	    		}
		    	Document doc = searcher.doc(i);
		    	Enumeration fields = doc.fields();
		    	bos.write("<document>\n".getBytes());
		    	String luceneId = null;
		    	while(fields.hasMoreElements()){
		    		Field field = (Field) fields.nextElement();
		    				    		
		    		String field_elem = "<field>\n";
		    		
		    		String name = "<name>\n";
		    		name += field.name()+"\n";
		    		name += "</name>\n";
		    		
		    		String tokenized = "<tokenized>\n";
		    		tokenized += field.isTokenized()+"\n";
		    		tokenized += "</tokenized>\n";

		    		String compressed = "<compressed>\n";
		    		compressed += field.isCompressed()+"\n";
		    		compressed += "</compressed>\n";

		    		String indexed = "<indexed>\n";
		    		indexed += field.isIndexed()+"\n";
		    		indexed += "</indexed>\n";

		    		String stored = "<stored>\n";
		    		stored += field.isStored()+"\n";
		    		stored += "</stored>\n";

		    		String value = "<value>\n";
		    		String field_value = UnicodeUtils.escapeControlCodes(field.stringValue(), controlcode_map);
		    		
		    		value += "<![CDATA["+field_value+"]]>\n";		    		
		    		value += "</value>\n";
		    		
		    		if(field.name().equals("_id")){
		    			luceneId = field.stringValue();
		    		}

		    		field_elem += name + compressed + indexed + stored + value;
		    		field_elem += "</field>\n";
		    		
			    	bos.write(field_elem.getBytes());
		    	}
		    	
		    	// grab path and layer from path index table
		    	pathIndexQuery.setInt(1, Integer.parseInt(luceneId));
		    	ResultSet rows = pathIndexQuery.executeQuery();
		    	rows.beforeFirst();
		    	String path = null;
		    	String layer = null;
		    	while(rows.next()){
		    		path = rows.getString("path");
		    		layer = rows.getInt("layer")+"";
		    	}

		    	String path_elem = "<path>\n";
		    	path_elem += path;
		    	path_elem += "</path>\n";
		    	bos.write(path_elem.getBytes());
		    	
		    	String layer_elem = "<layer>\n";
		    	layer_elem += layer;
		    	layer_elem += "</layer>\n";
		    	bos.write(layer_elem.getBytes());
		    	
		    	bos.write("</document>\n".getBytes());
		    	bos.flush();
		    }
	    	
			bos.write("</documents>".getBytes());	   
			bos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		long end = System.currentTimeMillis();
		System.out.println("Export took: " + ((end-start)/60000) + " minutes.");
	}
}
