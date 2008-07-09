package axiom.objectmodel.dom;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.store.*;
import org.apache.lucene.search.*;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.*;

public class LuceneSearcher {

    public void displayAll(String sdir, String keytosearch, String sval) throws Exception {
        IndexSearcher searcher = null;
        //IndexWriter writer = null;
        try {
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver").newInstance();
            Directory dir = FSDirectory.getDirectory(sdir, false);
            if (dir instanceof TransFSDirectory) {
                TransFSDirectory d = (TransFSDirectory) dir;
                System.setProperty("derby.system.home", "c:\\axiom\\db");
                d.setDriverClass("org.apache.derby.jdbc.EmbeddedDriver");
                d.setUrl("jdbc:derby:Transactions;create=false");
                
                d.setUser("");
                d.setPassword("");
            }
            IndexObjectsFactory.initDeletedInfos(dir);
            
            //writer = new IndexWriter(dir, new StandardAnalyzer(), false);
            //writer.delete("0");
            //writer.close();
            //writer = null;
            
            searcher = new IndexSearcher(dir);
            final int totalDocsSum = searcher.getIndexReader().numDocs();
            System.out.println("TOTAL DOCS = " + totalDocsSum);
            /*for (int i = 0; i < totalDocsSum; i++) {
                System.out.println("------ Document " + i + " ------");
                Document doc = null;
                try {
                    doc = searcher.doc(i);
                } catch (Exception ex) {
                    continue;
                }
                Enumeration e = doc.fields();
                while (e.hasMoreElements()) {
                    Field f = (Field) e.nextElement();
                    String name = f.name();
                    String value = f.stringValue();
                    System.out.println("field = " + name + "\tvalue = " + value);
                }
            }*/
//            Query t = new TermQuery(new Term("id","211"));
//            Query t = new TermQuery(new Term("id","1023"));
            Query t = new TermQuery(new Term(keytosearch, sval));
            Hits h = searcher.search(t);
            int totalDocs = h.length();
            for (int i = 0; i < totalDocs; i++) {
                System.out.println("------ Document " + i + " ------");
                Document doc = null;
                try {
                    doc = h.doc(i);
                } catch (Exception ex) {
                    continue;
                }
                Enumeration e = doc.fields();
                while (e.hasMoreElements()) {
                    Field f = (Field) e.nextElement();
                    String name = f.name();
                    String value = f.stringValue();
                    System.out.println("field = " + name + "\tvalue = " + value);
                }
            }
        } finally {
            if (searcher != null) {
                searcher.close();
                searcher = null;
            }
        }
    }
    
    public void tryNull(String sdir) {
        try {
            Directory dir = FSDirectory.getDirectory(sdir, true);

            IndexObjectsFactory.initDeletedInfos(dir);
            
            IndexWriter writer = new IndexWriter(dir, new StandardAnalyzer(), true);
            
            Document d = new Document();
            d.add(new Field("f1", "cat\u0000dog", Field.Store.YES, Field.Index.TOKENIZED));
            
            writer.addDocument(d);
            writer.close();
        } catch (Exception ex) {
            
        } finally {
            
        }
    }
    public static void writeFirst() throws Exception {
        Directory dir = FSDirectory.getDirectory("c:\\development\\blah", true);
        IndexObjectsFactory.initDeletedInfos(dir);
        IndexWriter writer = new IndexWriter(dir, new ReferenceAnalyzer(), true);
        Document d = new Document();
        d.add(new Field("age", "10", Field.Store.YES ,Field.Index.UN_TOKENIZED));
        writer.addDocument(d);
        d = new Document();
        d.add(new Field("age","15", Field.Store.YES, Field.Index.UN_TOKENIZED));
        writer.addDocument(d);
        writer.close();
        writer = null;
    }
    public static void thenRead() throws Exception {
        Directory dir = FSDirectory.getDirectory("c:\\development\\blah", false);
        IndexObjectsFactory.initDeletedInfos(dir);
        IndexSearcher searcher = new IndexSearcher(dir);
        QueryParser qp = new QueryParser("age",new ReferenceAnalyzer());
        Query q = qp.parse("age:[00 TO null]");
        Hits hits = searcher.search(q);
        System.out.println("hits length = " + hits.length());
        for (int i = 0; i < hits.length(); i++) {
            System.out.println("age = " + hits.doc(i).getField("age").stringValue());
        }
    }
    
    public static void doLuceneHttpRequest() throws Exception {
        URLConnection conn = null;
        OutputStreamWriter wr = null;
        BufferedReader rd = null;
        try {
            // Construct data
            String data = URLEncoder.encode("key1", "UTF-8") + "=" + URLEncoder.encode("value1", "UTF-8");
            data += "&" + URLEncoder.encode("key2", "UTF-8") + "=" + URLEncoder.encode("value2", "UTF-8");
            
            // Send data
            URL url = new URL("http://lime.siteworx.com:9000/");
            conn = url.openConnection();
            conn.setDoOutput(true);
            wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();
            
            // Get the response
            rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                // Process line...
            }
        } finally {
            if (wr != null) {
                wr.close();
                wr = null;
            }
            if (rd != null) {
                rd.close();
                rd = null;
            }
        }
    }
    
    public static void displayDuplicates(String file) {
        BufferedReader br = null;
        FileReader fr = null;
        ArrayList duplicates = new ArrayList();
        try {
            fr = new FileReader(new File(file));
            br = new BufferedReader(fr);
            String line = null;
            boolean read = false;
            HashSet ids = new HashSet();
            while ((line = br.readLine()) != null) {
                if (line.startsWith("--------------")) {
                    read = true;
                    continue;
                } else if (line.length() == 0 || line.startsWith("[") || line.startsWith("\n")) {
                    read = false;
                }
                if (read) {
                    String id = line.split("\t")[0];
                    if (ids.contains(id)) {
                        duplicates.add(id);
                    } else {
                        ids.add(id);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (fr != null) {
                try { fr.close(); } catch (Exception ignore) { }
                fr = null;
            }
            if (br != null) {
                try { br.close(); } catch (Exception ignore) { }
                br = null;
            }
        }
        
        int size = duplicates.size();
        for (int i = 0; i < size; i++) {
            System.out.println(duplicates.get(i));
        }
    }
    
    
    
    public static void main(String[] args) throws Exception {
        displayDuplicates(args[0]);
    }
}