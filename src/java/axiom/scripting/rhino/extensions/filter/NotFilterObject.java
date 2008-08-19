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

import java.lang.reflect.Method;

import org.mozilla.javascript.*;

/**
 * The JavaScript NotFilter class, for passing in as a filter parameter to the Query API.
 * The NotFilter is a filter with a NOT operation performed on the filter that the 
 * NotFilter is constructed with.  For example, <br><br>
 * 
 * <code>
 * var filter = new Filter({id: "company-picnic"}); <br>
 * app.getObjects("Post", new NotFilter(filter)); <br>
 * </code>
 * <br>
 * This will return all objects of the Post prototype, with an id that is not 
 * "company-picnic".
 *
 * @jsconstructor NotFilter
 * @param {Filter} filter The filter to execute the NOT operation on
 */
public class NotFilterObject extends OpFilterObject {
    
    protected NotFilterObject() {
        super();
    }

    protected NotFilterObject(final Object[] args) throws Exception {
        super();
        filters = new IFilter[1];
        if (args.length >= 1) {
            if (args[0] instanceof IFilter)  {
                filters[0] = (IFilter) args[0];
            } else if (args[0] instanceof String) {
                filters[0] = new NativeFilterObject(new Object[] {args[0]});
            } else if (args[0] instanceof Scriptable) {
                Scriptable s = (Scriptable) args[0];
                if (s.getClassName().equals("String")) {
                    filters[0] = new NativeFilterObject(new Object[] {s});
                } else {
                    filters[0] = new FilterObject(s, null, null);
                }
            } else {
                throw new Exception("Parameter to the NotFilter constructor is not a valid filter.");
            }
            
            if (args.length > 1 && args[1] instanceof Boolean) {
                this.cached = ((Boolean) args[1]).booleanValue();
            }
        } else {
            throw new Exception("No parameters specified to the NotFilter constructor.");
        }
    }
    
    public String getClassName() {
        return "NotFilter";
    }
    
    public String toString() {
        return "[NotFilter]";
    }
    
    public static NotFilterObject filterObjCtor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) 
    throws Exception {
        if (args.length == 0) {
            throw new Exception("No arguments specified to the NotFilter constructor.");
        }
        
        return new NotFilterObject(args);
    }
    
    public static void init(Scriptable scope) {
        Method[] methods = NotFilterObject.class.getMethods();
        ScriptableObject proto = new NotFilterObject();
        proto.setPrototype(getObjectPrototype(scope));
        final int attributes = READONLY | DONTENUM | PERMANENT;
        
        final int length = methods.length;
        for (int i = 0; i < length; i++) {
            String methodName = methods[i].getName();
            
            if ("filterObjCtor".equals(methodName)) {
                FunctionObject ctor = new FunctionObject("NotFilter", methods[i], scope);
                ctor.addAsConstructor(scope, proto);
            } else if (methodName.startsWith("jsFunction_")) {
                methodName = methodName.substring(11);
                FunctionObject func = new FunctionObject(methodName, methods[i], proto);
                proto.defineProperty(methodName, func, attributes);
            }
        }
    }
    
}