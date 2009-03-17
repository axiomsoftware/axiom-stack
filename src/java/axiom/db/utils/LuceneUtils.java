package axiom.db.utils;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Enumeration;

import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexObjectsFactory;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.SegmentInfos;
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
import axiom.objectmodel.dom.IndexWriterManager;
import axiom.objectmodel.dom.LuceneManager;

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
	    	Connection conn = DriverManager.getConnection(LuceneManipulator.getUrl(dir));
	    	PreparedStatement pathIndexQuery = conn.prepareStatement("SELECT path,layer FROM PathIndices WHERE id = ?");
	    	for(int i=0; i< searcher.maxDoc(); i++){
		    	Document doc = searcher.doc(i);
		    	Enumeration fields = doc.fields();
		    	Element doc_elem = xmldoc.createElement("document");
		    	String luceneId = null;
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
		    		
		    		if(field.name().equals("_id")){
		    			luceneId = field.stringValue();
		    		}
		    		
		    		doc_elem.appendChild(field_elem);
		    	}
		    	
		    	// grab path and layer from pathindex table
		    	pathIndexQuery.setInt(1, Integer.parseInt(luceneId));
		    	ResultSet rows = pathIndexQuery.executeQuery();
		    	rows.beforeFirst();
		    	String path = null;
		    	String layer = null;
		    	while(rows.next()){
		    		path = rows.getString("path");
		    		layer = rows.getInt("layer")+"";
		    	} 
		    	Element path_elem = xmldoc.createElement("path");
		    	path_elem.appendChild(xmldoc.createTextNode(path));
		    	doc_elem.appendChild(path_elem);
		    	Element layer_elem = xmldoc.createElement("layer");
		    	layer_elem.appendChild(xmldoc.createTextNode(layer));
		    	doc_elem.appendChild(layer_elem);
		    	
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
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void importDocuments(File xml_file, File target) {
		DocumentBuilder builder = null;
		IndexWriter writer = null;
		Connection conn = null;
		try {
			PerFieldAnalyzerWrapper analyzer = LuceneManager.buildAnalyzer();
			FSDirectory newIndexDir = FSDirectory.getDirectory(target, true);
            writer = IndexWriterManager.getWriter(newIndexDir, analyzer, true);
    	    if (newIndexDir instanceof TransFSDirectory) {
    	        FSDirectory.setDisableLocks(true);
    	        TransFSDirectory d = (TransFSDirectory) newIndexDir;
    	        d.setDriverClass(TransSource.DEFAULT_DRIVER);
    	        d.setUrl(LuceneManipulator.getUrl(target));
    	        d.setUser(null);
    	        d.setPassword(null);
    	    }
			
    	    // parse xml dump
			builder = (DocumentBuilderFactory.newInstance()).newDocumentBuilder();
			org.w3c.dom.Document doc = builder.parse(xml_file);
			
			// initialize h2 tables
	    	conn = DriverManager.getConnection(LuceneManipulator.getUrl(target));
		  	conn.setAutoCommit(false);
		  	PreparedStatement initStatement = conn.prepareStatement(TransSource.TRANS_SQL_LUCENE);
		  	initStatement.execute();
		  	initStatement.close();
		  	initStatement = conn.prepareStatement(TransSource.TRANS_SQL_IDGEN);
		  	initStatement.execute();
		  	initStatement.close();
		  	initStatement = conn.prepareStatement(TransSource.TRANS_SQL_PATHINDICES);
		  	initStatement.execute();
		  	initStatement.close();
		  	initStatement = conn.prepareStatement(TransSource.TRANS_SQL_INDEX);
		  	initStatement.execute();
		  	initStatement.close();
		  	initStatement = null;
		  	conn.commit();
			
		  	// prepared statement for updating pathindex
		  	PreparedStatement pathIndexUpdater = conn.prepareStatement("INSERT INTO PathIndices (id, layer, path) VALUES (?,?,?)");
		  	
		  	// start walking the xml for documents to import
		  	Element root = doc.getDocumentElement();
			NodeList documents = root.getElementsByTagName("document");
	    	int maxId = -1; //assuming numerical lucene ids. almost certainly evil.
			for(int i=0; i < documents.getLength(); i++){
				Element document = (Element) documents.item(i);
								
				// begin add fields to new lucene docs to be written
				String luceneId = null;
				NodeList fields = document.getElementsByTagName("field");
				Document luceneDocument = new Document();
				for(int j=0; j < fields.getLength(); j++){
					Element field = (Element) fields.item(j);

					Field.Store currstore = Field.Store.YES;
					Field.Index curridx = Field.Index.UN_TOKENIZED;
					String name = "";
					String value = "";
					
					NodeList nameList = field.getElementsByTagName("name");
					if (nameList.getLength() > 0) {
						name = nameList.item(0).getTextContent();
					}
					
					NodeList valueList = field.getElementsByTagName("value");
					if (valueList.getLength() > 0) {
						value = valueList.item(0).getTextContent();
					}
					
					NodeList stored = field.getElementsByTagName("stored");
					if (stored.getLength() > 0 && stored.item(0).getTextContent().equals("false")) {
						currstore = Field.Store.NO;
					}
					
					NodeList compressed = field.getElementsByTagName("compressed");
					if (compressed.getLength() > 0 && compressed.item(0).getTextContent().equals("true")) {
						currstore = Field.Store.COMPRESS;
					}
					
					NodeList indexed = field.getElementsByTagName("indexed");
					if (indexed.getLength() > 0 && indexed.item(0).getTextContent().equals("false")) {
						curridx = Field.Index.NO;
					}
					
					NodeList tokenized = field.getElementsByTagName("tokenized");
					if (tokenized.getLength() > 0 && tokenized.item(0).getTextContent().equals("true")) {
						curridx = Field.Index.TOKENIZED;
					}
					
					if(name.equals("_id")){
						luceneId = value;
						maxId = Math.max(Integer.parseInt(luceneId),maxId);
					}

					luceneDocument.add(new Field(name, value, currstore, curridx));                    
				}
				writer.addDocument(luceneDocument);
				
				// grab layer and path, insert into path index table
				int layer = Integer.parseInt(document.getElementsByTagName("layer").item(0).getTextContent());
				String path = document.getElementsByTagName("path").item(0).getTextContent();
				pathIndexUpdater.setString(1, luceneId);
				pathIndexUpdater.setInt(2, layer);
				pathIndexUpdater.setString(3, path);
				pathIndexUpdater.execute();
			}
			
			// update igen table with last id found
			conn.createStatement().execute("INSERT INTO IdGen (id, cluster_host) VALUES( "+maxId+ ", '')");
			
			conn.commit();
			writer.close();
		    writer.flushCache();
		    LuceneManager.commitSegments(null, conn, target.getAbsoluteFile(), writer.getDirectory());
		    writer.finalizeTrans();
		    
		    newIndexDir.close();
		    SegmentInfos newSegmentInfos = IndexObjectsFactory.getFSSegmentInfos(newIndexDir);
		    newSegmentInfos.clear();
		    IndexObjectsFactory.removeDeletedInfos(newIndexDir);

		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.setProperty("org.apache.lucene.FSDirectory.class","org.apache.lucene.store.TransFSDirectory");
		LuceneUtils utils = new LuceneUtils();
		//utils.exportDocuments(new File(args[0]));
		utils.importDocuments(new File("test.xml"), new File("manage"));
	}

}
