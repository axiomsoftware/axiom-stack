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

import org.mozilla.javascript.*;

import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.db.DbKey;
import axiom.scripting.rhino.QueryDispatcher;
import axiom.scripting.rhino.RhinoCore;


public abstract class HitsObject extends ScriptableObject {

    protected Object hits = null;
    protected RhinoCore core = null;
    protected QueryDispatcher qbean = null;
    protected int mode = DbKey.LIVE_LAYER;
    
    public HitsObject() {
        super();
    }
    
    public HitsObject(final Object[] args) {
        super();
        this.core = (RhinoCore) args[0];
        this.qbean = (QueryDispatcher) args[1];
        RequestEvaluator reqeval = this.core.app.getCurrentRequestEvaluator();
        if (reqeval != null) {
            this.mode = reqeval.getLayer();
        }
    }
    
    public void finalize() throws Throwable {
        super.finalize();
    }
    
    public abstract void jsFunction_discard();
    
    public abstract String getClassName();
    
    public abstract String toString();
        
    public abstract int jsGet_length();
    
    public abstract int jsGet_total();
    
    public abstract Scriptable jsFunction_objects(Object startInd, Object len);
    
    public Object jsFunction_get(Object index) {
    	Scriptable arr = this.jsFunction_objects(index, 1);
    	return arr.get(0, arr);
    }
    
    public abstract Object jsFunction_getHits(Object prototype, Object filter);

    public abstract int jsFunction_getHitCount(Object prototype, Object filter);
    
    public abstract Scriptable jsFunction_getFacets();
    
}