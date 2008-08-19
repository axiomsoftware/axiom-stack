/*
 * Axiom Stack Web Application Framework
 * Copyright (C) 2008  Axiom Software Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */
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