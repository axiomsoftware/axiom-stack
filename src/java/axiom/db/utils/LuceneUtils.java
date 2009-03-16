package axiom.db.utils;
import java.io.*;
import java.util.Enumeration;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.TransFSDirectory;

import axiom.objectmodel.db.TransSource;

public class LuceneUtils {

	
	public void export(File dir){
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
			IndexSearcher searcher = new IndexSearcher(directory);
		    for(int i=0; i< searcher.maxDoc(); i++){
		    	Document doc = searcher.doc(i);
		    	Enumeration fields = doc.fields();
		    	while(fields.hasMoreElements()){
		    		Field field = (Field) fields.nextElement();
		    		System.out.println(field.name() +" : "+field.stringValue());
		    	}
		    System.out.println("\n\n----------\n\n");
		    }
    	} catch(IOException e){
    		e.printStackTrace();
    	}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		LuceneUtils utils = new LuceneUtils();
		utils.export(new File(args[0]));
	}

}
