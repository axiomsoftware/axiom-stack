package axiom.objectmodel.dom;

import java.io.IOException;
import java.io.Reader;
import java.util.regex.*;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;

public class UrlAnalyzer extends Analyzer {
	public TokenStream tokenStream(String fieldName, final Reader reader) {
		return new TokenStream() {
			private final Pattern stripTokens = Pattern.compile("^(http:/|www|com|org|net)");
            private final Pattern endTokens = Pattern.compile("(\\.|/|-|_|\\?)$");
            private boolean done = false;
            public Token next() throws IOException {
            	if(!done){
	                final char[] buffer = new char[1];
	                StringBuffer sb = new StringBuffer();
	                int length = 0;
	                    
	                Matcher matcher = endTokens.matcher(sb);
	                boolean found = matcher.find();
	                
	                while (!found && (length = reader.read(buffer)) != -1) {
	                	sb.append(buffer, 0, length);
	                	Matcher startMatcher = stripTokens.matcher(sb);
	                	if(startMatcher.matches()){
	                		// strip prefix
	                		String tmp = sb.toString();
	                		sb = new StringBuffer(tmp.replaceFirst("^(http:/|www|com|org|net)", ""));
	                	}
	                	matcher = endTokens.matcher(sb);
	                	found = matcher.find();	  
	                	
	                	if(found){
		                	final String text = sb.toString().substring(0, matcher.end()-1).toLowerCase();
		                	int len = text.length();
		                	if(len > 0){
		                		// matched a token
		                		System.out.println("-- token: ["+text+"]");
		                		return new Token(text, 0, len);
		                	} else {
		                		// only contains a stop token, continue reading
		                		sb = new StringBuffer();
		                		found = false;
		                	}
		                }
	                }
                	// at end of string
	                done = true;
	                final String value = sb.toString().toLowerCase();
	                System.out.println("-- token: ["+value+"]");
	                return new Token(value, 0, value.length());
            	}
            	return null;
            }
        };
    }
}
