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
package axiom.scripting.rhino.extensions;

import java.lang.reflect.Method;
import java.util.ArrayList;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.PropertyException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import axiom.framework.ErrorReporter;
import axiom.objectmodel.db.Key;
import axiom.objectmodel.db.Node;
import axiom.objectmodel.db.NodeManager;
import axiom.objectmodel.dom.LuceneManager;
import axiom.scripting.rhino.LuceneQueryDispatcher;
import axiom.scripting.rhino.LuceneQueryDispatcher.LuceneQueryParams;

/**
 * This class encapsulates the behavior of the JavaScript level LuceneHits object.  
 * Hits are returned from the Query API call when <code> app.getHits() </code> is 
 * invoked.  The result, if the returned objects are from Lucene, is an instance of 
 * this class which is used to retrieve objects in the result set.
 * 
 * @jsnoconstructor LuceneHits
 */
public class LuceneHitsObject extends HitsObject{

	private LuceneQueryParams params = null;
	private LuceneManager lmgr = null;
	private ArrayList hits = null;

	public LuceneHitsObject(){
		super();
	}

    public LuceneHitsObject(final Object[] args) {
        super(args);
        this.hits = (ArrayList) args[2];
        this.params = (LuceneQueryParams) args[3];
        this.lmgr = (LuceneManager) args[4];
    }
	
	public LuceneQueryParams getParams(){
    	return params;
    }

    public void finalize() throws Throwable {
        super.finalize();
        this.lmgr.releaseIndexSearcher(this.params.searcher);
    }
    
    /**
     * Discard this object, when it is finished being used.  After this function is 
     * called, this object can no longer be used.  This is called to do cleanup/garbage
     * collection on the Lucene resources that this object holds.
     */
    public void jsFunction_discard() {
        this.lmgr.releaseIndexSearcher(this.params.searcher);
    }

    public String getClassName() {
        return "LuceneHits";
    }
    
    public String toString() {
        return "[LuceneHits]";
    }
    
    public static void init(Scriptable scope) throws PropertyException {
        Method[] methods = LuceneHitsObject.class.getDeclaredMethods();
        ScriptableObject proto = new LuceneHitsObject();
        proto.setPrototype(getObjectPrototype(scope));
        
        final int ATTRS = READONLY | DONTENUM | PERMANENT;
        final int length = methods.length;
        for (int i = 0; i < length; i++) {
            String methodName = methods[i].getName();
            if (methodName.startsWith("jsFunction_")) {
                methodName = methodName.substring(11);
                FunctionObject func = new FunctionObject(methodName, methods[i], proto);
                proto.defineProperty(methodName, func, ATTRS);                
            } else if (methodName.startsWith("jsGet_")) {
                methodName = methodName.substring(6);
                proto.defineProperty(methodName, null, methods[i], null, ATTRS);
            } else if (methodName.equals("hitsObjCtor")) {
                FunctionObject ctor = new FunctionObject("LuceneHits", methods[i], scope);
                ctor.addAsConstructor(scope, proto);
            }
        }
    }

    /**
     * Get a new LuceneHits object that takes the result set contained in this LuceneHits
     * object and narrows the results further with the specified input criterion.
     * 
     * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {FilterObject} [filter] The filter to apply to the search
     * @returns {LuceneHits} A new LuceneHits object with the narrowed search results
     */
    public Object jsFunction_getHits(Object prototype, Object filter) {
        Object ret = null;
        try {
            ret = ((LuceneQueryDispatcher)qbean).filterHits(prototype, filter, this.params);
        } catch (Exception ex) {
            this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "jsFunction_getHits"), ex);
        }
        return ret;
    }
    
    /**
     * Get the number of results in the result set of the specified input criterion
     * applied to the result set of this LuceneHits object.  This is the equivalent of
     * <code> this.getHits(prototypes, filter).length </code> but more efficient and 
     * thus, preferable.
     * 
     * @param {String|Array} [prototype] The prototype(s) to search against, 
     *                                   if not specified, search against all prototypes
     * @param {FilterObject} [filter] The filter to apply to the search
     * @returns {Number} The number of narrowed search results
     */
    public int jsFunction_getHitCount(Object prototype, Object filter) {
        int ret = 0;
        try {
            ret = ((LuceneQueryDispatcher)qbean).filterHitsLength(prototype, filter, this.params);
        } catch (Exception ex) {
            this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "jsFunction_getHitCount"), ex);
        }
        return ret;
    }

    public static HitsObject hitsObjCtor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) 
    throws Exception {
        return new LuceneHitsObject(args);
    }

    /**
     * Get the objects in the result set starting from <code> start </code>, retrieving
     * <code> length </code> number of objects. 
     * 
     * @param {Number} start The start index
     * @param {Number} length The number of objects to retrieve
     * @returns {Array} An array of the requested objects from the result set
     */
    public Scriptable jsFunction_objects(Object startInd, Object len) {
        ArrayList objects = new ArrayList();
        final Context cx = Context.getCurrentContext();
        final Scriptable global = this.core.getScope();
        
        if (startInd instanceof Number && len instanceof Number) {
            int start, length;
            try {
                start = ((Number) startInd).intValue();
                length = ((Number) len).intValue();
            } catch (Exception ex) {
                start = -1;
                length = -1;
            }

            int hitslength = hits != null ? hits.size() : 0;
            if (hits == null || start == -1 || length == -1 || start >= hitslength) {
                return cx.newArray(global, objects.toArray());
            }
            
            final int limit = start + length < hitslength ? start + length : hitslength;
            NodeManager nmgr = this.core.app.getNodeManager();
            
            for (int i = start; i < limit; i++) {
                try {
                    Key key = (Key) hits.get(i);
                    Node node = nmgr.getNode(key);
                    if (node != null) {
                        objects.add(Context.toObject(node, global));
                    }
                } catch (Exception ex) {
                    core.app.logError(ErrorReporter.errorMsg(this.getClass(), "jsFunction_objects") 
                    		+ "Could not get document " 
                            + "at index " + i + ".", ex);
                }
            } 
        }
        
        return cx.newArray(global, objects.toArray());
    }
    
    /**
     * The total number of results in the result set of this LuceneHits object.
     * @type Number
     */
    public int jsGet_length() {
        if (this.hits == null) { 
            return 0;
        }
        return this.hits.size();
    }
    
    /**
     * @jsomit
     * @deprecated replaced by <code> hits.length </code>
     */
    public int jsGet_total() {
        if (this.hits == null) {
            return 0;
        }
        return this.hits.size();
    }

    /**
     * @jsomit
     */
    public Scriptable jsFunction_getFacets(){
    	return Context.getCurrentContext().newObject(this.core.getScope());
    }
}