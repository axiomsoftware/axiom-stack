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
package axiom.scripting.rhino.extensions.filter;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.*;

import org.mozilla.javascript.*;

abstract public class OpFilterObject extends ScriptableObject implements IFilter {
    
    IFilter[] filters = null;
    private String analyzer = null;
    boolean cached = false; 
    
    public IFilter[] getFilters() {
        return filters;
    }
    
    /**
     * Set the Lucene analyzer used on the query represented by this filter object.
     * 
     * @param {String} analyzer The name of the analyzer (e.g. "WhitespaceAnalyzer")
     */
    public void jsFunction_setAnalyzer(Object analyzer) {
        if (analyzer instanceof String) {
            this.analyzer = (String) analyzer;
        }
    }
    
    /**
     * Get the Lucene analyzer used on the query represented by this filter object.
     * 
     * @returns {String} The name of the analyzer
     */
    public String jsFunction_getAnalyzer() {
        return this.analyzer;
    }
    
    public boolean isCached() {
        return this.cached;
    }
    
}