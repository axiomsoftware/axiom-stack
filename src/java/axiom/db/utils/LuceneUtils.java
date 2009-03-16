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
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.dom.*; 

import axiom.objectmodel.db.TransSource;

public class LuceneUtils {
	
	public void exportDocuments(File dir){
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
		    	Element doc_elem = xmldoc.createElement("document");
		    	while(fields.hasMoreElements()){
		    		Field field = (Field) fields.nextElement();
		    		
		    		Element field_elem = xmldoc.createElement("field"); 
		    		
		    		Element name = xmldoc.createElement("name");
		    		name.appendChild(xmldoc.createTextNode(field.name()));
		    		field_elem.appendChild(name);
		    		
		    		Element tokenized = xmldoc.createElement("tokenized");
		    		tokenized.appendChild(xmldoc.createTextNode(field.isTokenized()+""));
		    		field_elem.appendChild(tokenized);
		    		
		    		Element compressed = xmldoc.createElement("compressed");
		    		compressed.appendChild(xmldoc.createTextNode(field.isCompressed()+""));
		    		field_elem.appendChild(compressed);
		    		
		    		Element indexed = xmldoc.createElement("indexed");
		    		indexed.appendChild(xmldoc.createTextNode(field.isIndexed()+""));
		    		field_elem.appendChild(indexed);

		    		Element stored = xmldoc.createElement("stored");
		    		stored.appendChild(xmldoc.createTextNode(field.isStored()+""));
		    		field_elem.appendChild(stored);
		    		
		    		Element value = xmldoc.createElement("value");
		    		value.appendChild(xmldoc.createCDATASection(field.stringValue()));
		    		field_elem.appendChild(value);
		    		
		    		doc_elem.appendChild(field_elem);
		    	}
	    		root.appendChild(doc_elem);
		    }
		    DOMSource domSource = new DOMSource(xmldoc);
		    StreamResult streamResult = new StreamResult(System.out);
		    TransformerFactory tf = TransformerFactory.newInstance();
		    Transformer serializer = tf.newTransformer();
		    serializer.setOutputProperty(OutputKeys.INDENT,"yes");
		    serializer.transform(domSource, streamResult); 
    	} catch(ParserConfigurationException e){
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    	} catch (TransformerConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DOMException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void importDocuments(File xml_file) {
		DocumentBuilder builder;
		try {
			builder = (DocumentBuilderFactory.newInstance()).newDocumentBuilder();
			org.w3c.dom.Document doc = builder.parse(xml_file);
			
			Element root = doc.getDocumentElement();
			NodeList documents = root.getChildNodes();
			for(int i=0; i < documents.getLength(); i++){
				Element document = (Element) documents.item(i);
				NodeList fields = document.getChildNodes();
				for(int j=0; j < fields.getLength(); j++){
					Element field = (Element) fields.item(j);
					String tag = field.getTagName();

					// TODO: begin add fields to new docs to be written

				}
			}
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		LuceneUtils utils = new LuceneUtils();
		utils.exportDocuments(new File(args[0]));
	}

}
