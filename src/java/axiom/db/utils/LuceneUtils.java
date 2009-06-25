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
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.dom.*; 

import axiom.objectmodel.db.TransSource;
import axiom.objectmodel.dom.IndexWriterManager;
import axiom.objectmodel.dom.LuceneManager;

public class LuceneUtils {
	public static String usage() {
		String usage = "";
		
		usage = "java -cp h2.jar:axiom.jar:axiom-lucene.jar axiom.db.utils.LuceneUtils [options]\n";
		usage += "Options:\n";
		usage += "\t-i - Import File (ie. -i <xml file> <application db directory>)\n";
		usage += "\t-e - Export File (ie. -e <application db directory>)\n";
		
		return usage;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.setProperty("org.apache.lucene.FSDirectory.class","org.apache.lucene.store.TransFSDirectory");
		String task = "";
		if (args.length > 0) {
			if (args[0].equals("-e") && args.length > 1 ) {
				AxiomExporter exporter = new AxiomExporter(new File(args[1]));
				exporter.export();
				task = "Export ";
			} else if (args[0].equals("-i") && args.length > 2) {
				AxiomImporter importer = new AxiomImporter(new File(args[1]), new File(args[2]));
				importer.run();
				task = "Import ";
			} else {
				System.out.println(usage());
			}
		} else {
			System.out.println(usage());
		}
		
		System.out.println(task+"Complete.");
	}

}
