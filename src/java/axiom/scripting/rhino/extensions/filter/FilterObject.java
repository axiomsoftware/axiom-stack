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

import java.lang.reflect.*;
import java.util.*;
    
import org.mozilla.javascript.*;

/**
 * The JavaScript Filter class, for passing in as a filter parameter to the Query API.
 * The Filter class encapsulates the basic type of filtering passed to the Query API.
 * Query parameters are specified as a series of key/value pairs indicating the property
 * names and their respective values to query against.  Objects that meet the specified
 * criterion are returned by the Query API.  For example, <br /><br />
 * <code>
 * app.getObjects("Post", new Filter({id: "company-picnic"}));
 * </code>
 * <br /><br />
 * 
 * This will return all Axiom Objects of the Post prototype, with an id exactly 
 * matching "company-picnic".<br /><br />
 * 
 * The second parameter is the Analyzer. Should you decide that the analyzer we use is not suitable
 * for your needs, you can specify the one you prefer. By default all filters are processed using the StandardAnalyzer.<br /><br />
 * <code>
 * app.getObjects("Post", new Filter({id: "company-picnic"}, 'WhitespaceAnalyzer'));
 * </code><br /><br />
 * 
 * The third parameter is to specify whether or not to cache the filter bitset. This is useful
 * when you have to rerun the same filter multiple times. Why reprocess all the documents in your
 * system if you can cache the results. That is what this does. There is a timeout that can be set as well.<br /><br />
 * <code>
 * app.getObjects("Post", new Filter({id: "company-picnic"}, 'WhitespaceAnalyzer', true));
 * </code><br /><br />
 * 
 * One thing to note is that the Analyzer, while the second parameter, does not need to be specified. If you don't want
 * to specify it, you can use the cacheFilter parameter in the second parameter slot. Axiom checks the types.<br /><br />
 * <code>
 * app.getObjects("Post", new Filter({id: "company-picnic"}, true));
 * </code><br /><br />
 * 
 * @jsconstructor Filter
 * @param {Object} filter A JavaScript object of key/value pairs indicating the property 
 *                        names and values to query against in the search
 * @param {Analyzer} analyzer A specified analyzer to use in processing the query
 * @param {Boolean} cacheFilter Specify whether or not to cache the filter bitset
 */
public class FilterObject extends ScriptableObject implements IFilter {
    
    private HashMap<String, Object> filters = new HashMap<String, Object>();
    private String analyzer = null;
    boolean cached = false;
        
    public FilterObject() {
        super();
    }
    
    public FilterObject(final Object arg, final Object optional1, final Object optional2) 
    throws Exception {
        super();
        if (arg instanceof ScriptableObject) {
            final ScriptableObject sobj = (ScriptableObject) arg;
            final Object[] ids = ScriptableObject.getPropertyIds(sobj);
            final int idslength = ids.length;
            
            for (int i = 0; i < idslength; i++) {
                final String id = ids[i].toString();
                final Object currval = ScriptableObject.getProperty(sobj, id);
                
                Object[] arrayelems = null;
                if (currval instanceof NativeArray) {
                    final NativeArray na = (NativeArray) currval;
                    final int nalength = (int) na.getLength();
                    arrayelems = new Object[nalength];
                    for (int j = 0; j < nalength; j++) {
                        Object currelem = NativeArray.getProperty(na, j);
                        if (currelem != null) {
                            arrayelems[j] = currelem;
                        } else {
                            arrayelems[j] = null;
                        }
                    }
                } else if(currval instanceof Number){
            		Number num = (Number)currval;
            		String number = num.toString();
                    arrayelems = new Object[1];
            		arrayelems[0] = number.endsWith(".0") ? number.substring(0, number.length() -2) : number;
                } else {
                    arrayelems = new Object[1];
                    arrayelems[0] = currval;
                }
                
                filters.put(id, arrayelems);
            }            
        } else {
            throw new Exception("Could not create the Filter object, improper constructor arguments specified.");
        }
        
        if (optional1 != null && optional1 instanceof String) {
            this.analyzer = (String) optional1;
        } else if (optional1 != null && optional1 instanceof Boolean) {
            this.cached = ((Boolean) optional1).booleanValue();
        }
        
        if (optional2 != null && optional2 instanceof Boolean) {
            this.cached = ((Boolean) optional2).booleanValue();
        }
    }
    
    public String getClassName() {
        return "Filter";
    }
    
    public String toString() {
        return "[Filter]";
    }
    
    public static FilterObject filterObjCtor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) 
    throws Exception {
        if (args.length == 0) {
            throw new Exception("No arguments specified to the Filter constructor.");
        }
        
        if (args[0] == null) {
            throw new Exception("Null argument passed to the Filter constructor.");
        }
        
        return new FilterObject(args[0], args.length > 1 ? args[1] : null, 
                args.length > 2 ? args[2] : null);
    }
    
    public static void init(Scriptable scope) {
        Method[] methods = FilterObject.class.getDeclaredMethods();
        ScriptableObject proto = new FilterObject();
        proto.setPrototype(getObjectPrototype(scope));
        final int attributes = READONLY | DONTENUM | PERMANENT;
        
        final int length = methods.length;
        for (int i = 0; i < length; i++) {
            String methodName = methods[i].getName();
            
            if ("filterObjCtor".equals(methodName)) {
                FunctionObject ctor = new FunctionObject("Filter", methods[i], scope);
                ctor.addAsConstructor(scope, proto);
            } else if (methodName.startsWith("jsFunction_")) {
                methodName = methodName.substring(11);
                FunctionObject func = new FunctionObject(methodName, methods[i], proto);
                proto.defineProperty(methodName, func, attributes);
            }
        }
    }
    
    public Set<String> getKeys() {
        return filters.keySet();
    }
    
    public Object getValueForKey(Object key) {
        return filters.get(key);
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