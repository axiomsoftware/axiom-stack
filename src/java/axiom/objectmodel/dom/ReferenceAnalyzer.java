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