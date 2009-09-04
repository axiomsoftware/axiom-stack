package axiom.objectmodel.dom;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;

import axiom.objectmodel.dom.UrlAnalyzer;

import java.io.StringReader;
import java.io.IOException;
import java.util.ArrayList;

import junit.framework.TestCase;

public class UrlAnalyzerTest extends TestCase {
    public void testTokenStreamHomepageWithSlash() {
	UrlAnalyzer analyzer = new UrlAnalyzer();
	TokenStream ts = analyzer.tokenStream("searchable_url", new StringReader("http://www.axiomstack.com/"));
	int token_count = 0;

	try {
	    Token t = null;
	    while ((t=ts.next())!=null) {
		System.out.println(t.toString());
		token_count++;
	    } ;
	} catch(IOException ioe) {
	    ioe.printStackTrace();
	}
	assertEquals("There were an unexpected number of tokens returned.", 2, token_count);
    }

    public void testTokenStreamHomepageWithSlashContent() {
	UrlAnalyzer analyzer = new UrlAnalyzer();
	TokenStream ts = analyzer.tokenStream("searchable_url", new StringReader("http://www.axiomstack.com/"));
	ArrayList<String> terms = new ArrayList<String>();

	try {
	    Token t = null;
	    while ((t=ts.next())!=null) {
		System.out.println(t.toString());
		terms.add(t.termText());
	    }
	} catch(IOException ioe) {
	    ioe.printStackTrace();
	    fail("There was an exception.");
	}

	assertEquals("There were an unexpected number of tokens returned.", 2, terms.size());
	assertEquals("Unexpected token found.", "http", (String)terms.get(0));
	assertEquals("Unexpected token found.", "axiomstack", (String)terms.get(1));
    }

    public void testTokenStreamPath() {
	UrlAnalyzer analyzer = new UrlAnalyzer();
	TokenStream ts = analyzer.tokenStream("searchable_url", new StringReader("http://www.axiomstack.com/test_path"));
	ArrayList<String> terms = new ArrayList<String>();

	try {
	    Token t = null;
	    while ((t=ts.next())!=null) {
		System.out.println(t.toString());
		terms.add(t.termText());
	    }
	} catch(IOException ioe) {
	    ioe.printStackTrace();
	    fail("There was an exception.");
	}

	assertEquals("There were an unexpected number of tokens returned.", 4, terms.size());
	assertEquals("Unexpected token found.", "http", (String)terms.get(0));
	assertEquals("Unexpected token found.", "axiomstack", (String)terms.get(1));
	assertEquals("Unexpected token found.", "test", (String)terms.get(2));
	assertEquals("Unexpected token found.", "path", (String)terms.get(3));
    }
}
