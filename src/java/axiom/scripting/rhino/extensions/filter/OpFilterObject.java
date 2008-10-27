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
    
    protected OpFilterObject() {
    	super();
    }
    
    protected OpFilterObject(final Object[] args) throws Exception {
    	super();
    	if (args != null) {
	        final int length = args.length;
	        if ((length == 1 && args[0] instanceof NativeArray && args[0] instanceof Scriptable 
	                && !((Scriptable) args[0]).getClassName().equals("String"))
	             || (length == 2 && args[0] instanceof Scriptable 
	                && !((Scriptable) args[0]).getClassName().equals("String")
	                && args[1] instanceof Boolean)) {
	            Scriptable s = (Scriptable) args[0];
	            final int arrlen = s.getIds().length;
	            filters = new IFilter[arrlen];
	            for (int i = 0; i < arrlen; i++) {
	            	filters[i] = this.getFilterFromArg(s.get(i, s), i);
	            }
	            if (length == 2) {
	                this.cached = ((Boolean) args[1]).booleanValue();
	            }
	        } else {
	            filters = new IFilter[length];
	            for (int i = 0; i < length; i++) {
	                if (i == length - 1 && args[i] instanceof Boolean) {
	                    this.cached = ((Boolean) args[i]).booleanValue();
	                    continue;
	                }
	                filters[i] = this.getFilterFromArg(args[i], i);
	            }
	        }
    	}
    }
    
    private IFilter getFilterFromArg(Object arg, int i) throws Exception{
    	IFilter filter = null;
    	if (arg instanceof IFilter)  {
             filter = (IFilter) arg;
        } else if (arg instanceof String) {
            filter = new NativeFilterObject(new Object[] {arg});
        } else if (arg instanceof Scriptable) {
            Scriptable s = (Scriptable) arg;
            if (s.getClassName().equals("String")) {
                filter = new NativeFilterObject(new Object[] {s});
            } else {
            	filter = new FilterObject(s, null, null);
            }
        } else {
            throw new Exception("Parameter " + (i+1) + " to the " + this.getClassName() + " constructor is not a valid filter.");
        }
    	
    	return filter;
    }
    
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