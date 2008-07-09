package org.mozilla.javascript;

import java.io.IOException;
import java.io.Reader;

public class TokenStreamInjector {

    private TokenStream ts;
    public TokenStreamInjector(Parser parser, Reader sourceReader, String sourceString,
            int lineno) {
        ts = new TokenStream(parser, sourceReader, sourceString, lineno);
    }
    
    public int getOffset() {
        return ts.getOffset();
    }
    public int getLineno() {
        return ts.getLineno();
    }
    private int peek;
    private boolean peeked;
    public int getToken() throws IOException {
        if (peeked) {
            peeked = false;
            return peek;
        }   
        return ts.getToken();
    }
    public int peekToken() throws IOException {
        if (!peeked) {
            peek = getToken();
            peeked = true;
        }
        return peek;
    }
    public boolean eof() {
        return ts.eof();
    }
    public String getString() {
        return ts.getString();
    }

}