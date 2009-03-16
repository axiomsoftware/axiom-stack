package axiom.db.utils;
import java.io.*;
import java.util.Enumeration;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.TransFSDirectory;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.dom.*; 

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

			org.w3c.dom.Document xmldoc = null;
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			DOMImplementation impl = builder.getDOMImplementation();
			xmldoc = impl.createDocument(null, "documents", null);
			Element root = xmldoc.getDocumentElement();
			
			IndexSearcher searcher = new IndexSearcher(directory);
		    for(int i=0; i< searcher.maxDoc(); i++){
		    	Document doc = searcher.doc(i);
		    	Enumeration fields = doc.fields();
		    	while(fields.hasMoreElements()){
		    		Field field = (Field) fields.nextElement();
		    		
		    		Element doc_elem = xmldoc.createElement("document");
		    		
		    		Element name = xmldoc.createElement("name");
		    		name.appendChild(xmldoc.createTextNode(field.name()));
		    		doc_elem.appendChild(name);
		    		
		    		Element tokenized = xmldoc.createElement("tokenized");
		    		tokenized.appendChild(xmldoc.createTextNode(field.isTokenized()+""));
		    		doc_elem.appendChild(tokenized);
		    		
		    		Element compressed = xmldoc.createElement("compressed");
		    		compressed.appendChild(xmldoc.createTextNode(field.isCompressed()+""));
		    		doc_elem.appendChild(compressed);
		    		
		    		Element indexed = xmldoc.createElement("indexed");
		    		indexed.appendChild(xmldoc.createTextNode(field.isIndexed()+""));
		    		doc_elem.appendChild(indexed);

		    		Element stored = xmldoc.createElement("stored");
		    		stored.appendChild(xmldoc.createTextNode(field.isStored()+""));
		    		doc_elem.appendChild(stored);
		    		
		    		Element value = xmldoc.createElement("value");
		    		value.appendChild(xmldoc.createCDATASection(field.stringValue()));
		    		doc_elem.appendChild(value);
		    		
		    		root.appendChild(doc_elem);
		    	}
		    }
		    DOMSource domSource = new DOMSource(xmldoc);
		    StreamResult streamResult = new StreamResult(System.out);
		    TransformerFactory tf = TransformerFactory.newInstance();
		    Transformer serializer = tf.newTransformer();
		    serializer.setOutputProperty(OutputKeys.INDENT,"yes");
		    serializer.transform(domSource, streamResult); 
    	} catch(Exception e){
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
