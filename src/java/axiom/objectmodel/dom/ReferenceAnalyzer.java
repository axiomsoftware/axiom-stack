package axiom.objectmodel.dom;

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.Token;

public class ReferenceAnalyzer extends Analyzer {
    public TokenStream tokenStream(String fieldName, final Reader reader) {
        return new TokenStream() {
            private boolean done = false;
            private static final String DELIM = LuceneManager.NULL_DELIM;
            public Token next() throws IOException {
                if (!done) {
                    done = true;
                    final char[] buffer = new char[512];
                    StringBuffer sb = new StringBuffer();
                    int length = 0;
                    while ((sb.indexOf(DELIM) < 0) && (length = reader.read(buffer)) != -1) {
                        sb.append(buffer, 0, length);
                    }
                    final String value = sb.toString();
                    final int index = value.indexOf(DELIM);
                    if (index < 0) {
                        return null;
                    } else {
                        final String text = value.substring(0, index);
                        return new Token(text, 0, text.length());
                    }
                } 
                
                return null;
            }
        };
    }
}