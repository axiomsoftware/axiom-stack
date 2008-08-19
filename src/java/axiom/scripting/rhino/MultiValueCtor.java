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
package axiom.scripting.rhino;

import java.lang.reflect.Method;

import org.mozilla.javascript.*;

public class MultiValueCtor extends FunctionObject {

    RhinoCore core;
    
    static Method mvObjCtor;

    static {
        try {
            mvObjCtor = MultiValueCtor.class.getMethod("jsConstructor", new Class[] {
                Context.class, Object[].class, Function.class, Boolean.TYPE });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Error getting MultiValueCtor.jsConstructor()");
        }
    }
    
    /**
     * Create and install a MultiValue constructor.
     * Part of this is copied from o.m.j.FunctionObject.addAsConstructor().
     *
     * @param prototype
     */
    public MultiValueCtor(RhinoCore core, Scriptable prototype) {
        super("MultiValue", mvObjCtor, core.global);
        this.core = core;
        addAsConstructor(core.global, prototype);
    }

    /**
     *  This method is used as ReferenceList constructor from JavaScript.
     */
    public static Object jsConstructor(Context cx, Object[] args, 
                                        Function ctorObj, boolean inNewExpr)
                         throws JavaScriptException {
        MultiValueCtor ctor = (MultiValueCtor) ctorObj;
        return new MultiValue(args);
    }
    
}