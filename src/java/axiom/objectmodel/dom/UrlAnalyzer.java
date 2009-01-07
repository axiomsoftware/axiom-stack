package axiom.objectmodel.dom;

import java.io.IOException;
import java.io.Reader;
import java.util.regex.*;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;

public class UrlAnalyzer extends Analyzer {
	public TokenStream tokenStream(String fieldName, final Reader reader) {
       System.out.println("UrlAnalyzer::tokenStream");
		return new TokenStream() {
            private final Pattern delims = Pattern.compile("(http://)|/|-|_");
            public Token next() throws IOException {
            	System.out.println("entering TokenStream::next ");
                final char[] buffer = new char[512];
                StringBuffer sb = new StringBuffer();
                int length = 0;
                    
                Matcher matcher = delims.matcher(sb);
                boolean found = matcher.find();
                    
                while (!found && (length = reader.read(buffer)) != -1) {
                	sb.append(buffer, 0, length);
                	matcher = delims.matcher(sb);
                	found = matcher.find();
                }
                final String value = sb.toString();
                if(found){
                	final String text = value.substring(0, matcher.end());
                	System.out.println("token matched: ["+text+"]");
                	return new Token(text, 0, text.length());
                } else {
                	// at end of string
                	System.out.println("token at end: ["+value+"]");
                	return new Token(value, 0, value.length());
                }
            }
        };
    }
}
