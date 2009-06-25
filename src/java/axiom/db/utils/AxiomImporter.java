package axiom.db.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexObjectsFactory;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.TransFSDirectory;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import axiom.objectmodel.db.TransSource;
import axiom.objectmodel.dom.IndexWriterManager;
import axiom.objectmodel.dom.LuceneManager;
import axiom.util.UnicodeUtils;

public class AxiomImporter implements ContentHandler {
	File xml_file = null;
	File target = null;
	IndexWriter writer = null;
	Connection conn = null;
	FSDirectory newIndexDir = null;
	PerFieldAnalyzerWrapper analyzer = LuceneManager.buildAnalyzer();
	org.w3c.dom.Document doc = null;
	Element element = null;
	Element root = null;
	Element field = null;
	StringBuffer value = new StringBuffer();
	DOMImplementation impl = null;
	HashMap<String, String> controlcode_map;
	int maxId = -1; //assuming numerical lucene ids. almost certainly evil.

	public AxiomImporter(File xml_file, File target) {
		this.xml_file = xml_file;
		this.target = target;
		
		controlcode_map = UnicodeUtils.getControlCodeMap(false);
		
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			impl = builder.getDOMImplementation();
		} catch (ParserConfigurationException pce) {
			pce.printStackTrace();
		}
		
		this.setup();
	}

	void setup() {
		try {
			newIndexDir = FSDirectory.getDirectory(target, true);
            writer = IndexWriterManager.getWriter(newIndexDir, analyzer, true);
    	    if (newIndexDir instanceof TransFSDirectory) {
    	        FSDirectory.setDisableLocks(true);
    	        TransFSDirectory d = (TransFSDirectory) newIndexDir;
    	        d.setDriverClass(TransSource.DEFAULT_DRIVER);
    	        d.setUrl(LuceneManipulator.getUrl(target));
    	        d.setUser(null);
    	        d.setPassword(null);
    	    }
			
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
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	protected void finalize() {
		try {
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
		} catch(SQLException sqle) {
			sqle.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				conn.close();
			} catch (SQLException sqle) {
			}
			conn = null;
		}
	}
	
	public void run() {
		try {
		  	XMLReader parser = org.xml.sax.helpers.XMLReaderFactory.createXMLReader();
		  	parser.setContentHandler(this);
		  	parser.setFeature("http://xml.org/sax/features/use-entity-resolver2", false);
		  	parser.setFeature("http://xml.org/sax/features/validation", false);
		  	parser.parse(new InputSource(new FileInputStream(xml_file)));
		} catch (SAXException saxe) {
			saxe.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void serializeDoc(OutputStream out) {
		try {
			DOMSource domSource = new DOMSource(doc);
			StreamResult streamResult = new StreamResult(out);
	    	TransformerFactory tf = TransformerFactory.newInstance();
	    	Transformer serializer;
			serializer = tf.newTransformer();
			serializer.setOutputProperty(OutputKeys.INDENT,"yes");
			serializer.transform(domSource, streamResult);
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}
	}
	
	public void importDocument(org.w3c.dom.Document doc) {
		System.out.println("start importDocument");
		System.out.println("writer doc count: " + writer.docCount());
		serializeDoc(System.out);
		try {
		  	// prepared statement for updating pathindex
		  	PreparedStatement pathIndexUpdater = conn.prepareStatement("INSERT INTO PathIndices (id, layer, path) VALUES (?,?,?)");
	    	
			Element document = root;
			
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
					value = UnicodeUtils.unescapeControlCodes(valueList.item(0).getTextContent(), controlcode_map);
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
			//System.out.println(document.getElementsByTagName("layer"));
			//System.out.println(document.getElementsByTagName("layer").item(0));
			NodeList layers = document.getElementsByTagName("layer");
			int layer = 0;
			if (layers != null && layers.item(0) != null) {
				Element layer_item = (Element)layers.item(0);
				String content = layer_item.getTextContent();
				if (content != null && !(content.equals("null"))) {
					//System.out.println("content: " + content);
					layer = Integer.parseInt(content);
				}
			}
			String path = document.getElementsByTagName("path").item(0).getTextContent();
			pathIndexUpdater.setString(1, luceneId);
			pathIndexUpdater.setInt(2, layer);
			pathIndexUpdater.setString(3, path);
			pathIndexUpdater.execute();
			
			//writer.flushCache();
		} catch(SQLException sqle) {
			//sqle.printStackTrace();
		} catch (IOException e) {
			//e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.gc();
		System.out.println("end importDocument");
	}
	
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		if (element != null && value != null) {
			value.append(new String(ch));
		}
		/*
			System.out.println("ch: " + new String(ch));
			System.out.println("start: " + start);
			System.out.println("length: " + length);
		*/
	}

	@Override
	public void endDocument() throws SAXException {
		
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (element != null && value != null) {
			element.appendChild(doc.createTextNode(value.toString()));
			//System.out.println("Old Buffer cap: " + value.capacity());
			value = null;
			//System.out.println("New Buffer cap: " + value.capacity());
		}
		
		if (localName.equals("document")) {
			importDocument(doc);
			doc = null;
			element = null;
			field = null;
			System.exit(-1);
		} else if (localName.equals("field")){
			root.appendChild(element);
			element = null;
			field = null;
		} else if (localName.equals("path") || localName.equals("layer")) {
			root.appendChild(element);
		} else {
			field.appendChild(element);
		}
	}

	@Override
	public void endPrefixMapping(String prefix) throws SAXException {
		
	}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length)throws SAXException {
		
	}

	@Override
	public void processingInstruction(String target, String data) throws SAXException {
		
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		
	}

	@Override
	public void skippedEntity(String name) throws SAXException {
		
	}

	@Override
	public void startDocument() throws SAXException {
		
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
		System.out.println("localName --> " + localName);
		if (localName.equals("document")) {
			try {
				org.w3c.dom.Document document = (DocumentBuilderFactory.newInstance()).newDocumentBuilder().getDOMImplementation().createDocument(null, "document", null);
				doc = document;
				root = document.getDocumentElement();
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
			}
		} else if (localName.equals("field")) {
			field = doc.createElement(localName);
		} else if(doc != null) {
			element = doc.createElement(localName);
			value = new StringBuffer();
		}
	}

	@Override
	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		
	}
}
