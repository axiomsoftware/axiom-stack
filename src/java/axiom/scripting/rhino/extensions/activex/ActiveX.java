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
package axiom.scripting.rhino.extensions.activex;
import java.io.*;
import org.mozilla.javascript.*;

import com.jacob.com.*;//Variant;
//import org.eclipse.swt.ole.win32.Variant;

import com.siteworx.Wrapper;

import java.lang.reflect.Method;


public class ActiveX implements Scriptable {//, Callable {

	private static final long serialVersionUID = -2217817036516774463L;
	
	private com.siteworx.Wrapper theActivex;
	private int pActiveX;
	public FunctionObject fnObj;
	private Scriptable prototype, parent;
	
	private StringBuffer strRUMethod;// = new StringBuffer();

	// The zero-argument constructor used by Rhino runtime to create instances
	public ActiveX() {
	}


    /**
     * Get parent.
     */
    public Scriptable getParentScope() {
        return parent;
    }

    /**
     * Set parent.
     */
    public void setParentScope(Scriptable parent) {
        this.parent = parent;
    }


	/*
	 *  (non-Javadoc)
	 * @see org.mozilla.javascript.Callable#call(org.mozilla.javascript.Context, org.mozilla.javascript.Scriptable, org.mozilla.javascript.Scriptable, java.lang.Object[])
	 * @deprecate
	 */
/*	public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
		String s = "ActiveX :: call method!...";
		System.err.println(s);
		return (Object)s;
	}
*/
/*
	public boolean isProperty(String name) {
		boolean isProp = false;
		int propIndex = theActivex.jnifindproperty(pActiveX, name);
		if(propIndex!=-1) {
			isProp = true;
		}
		return isProp;
	}
*/
/*
	public boolean isMethod(String name) {
		boolean isMethod = false;
		int propIndex = theActivex.jnifindmethod(pActiveX, name);
		if(propIndex!=-1) {
			isMethod = true;
		}
		return isMethod;
	}
*/	
	// Method jsConstructor defines the JavaScript constructor
	public void jsConstructor(String strProgID) {
		theActivex = new com.siteworx.Wrapper();
		pActiveX = theActivex.jniinit();
		theActivex.jnicreateobject(pActiveX, strProgID, theActivex.bigNameList);
		theActivex.setNameList();
		theActivex.buildDispInfo(pActiveX);
		//System.out.println("MajorVersion: " + theActivex.jnigetproperty(pActiveX, "MajorVersion"));
		//System.out.println("MinorVersion: " + theActivex.jnigetproperty(pActiveX, "MinorVersion"));
	}

	// The class name is defined by the getClassName method
	public String getClassName() {
		return "ActiveX";
	}
    
    public String toString() {
        return "[ActiveX]";
    }

//  jsFunction_findMethod
//	public int jsFunction_findMethod(String strMethod) {
//		return theActivex.jnifindmethod(pActiveX, strMethod);
//	}
//	public int jsFunction_findMethod(String strMethod) {
//		for (int i = 0; i < theActivex.m_strMethodList.length; i++) {
//			if( strMethod.compareTo(theActivex.m_strMethodList[i])==0 ) {
//				//fnObj = new FunctionObject(strMethod, , this.getTopLevelScope()); // sample
//				return i;
//			}
//		}
//		return 0;
//	}


	public String f0() {
		//System.out.println("\t... (0) !!!\n");
		//theActivex.jniinvokemethod0(pActiveX, name);
		return theActivex.jniinvokemethod0(pActiveX, this.strRUMethod.toString());
		//return new String("f0() - Reflected");
	}
	public String f1(String arg1) {
		//System.out.println("\t... (1) de belea!!!  ->'" + arg1 + "'\n");
		return theActivex.jniinvokemethod1(pActiveX, this.strRUMethod.toString(), arg1);
		//return new String("f1() - Reflected");
	}
	public String f2(String arg1, String arg2) {
		//System.out.println("\t... (2) !!!  -> '" + arg1 + "' : '" + arg2 + "'\n");
		return theActivex.jniinvokemethod2(pActiveX, this.strRUMethod.toString(), arg1, arg2);
		//return new String("f2() - Reflected");
	}

	public Method genMethod( String methodName ) {
		Method _func_to_call = null;
		if(methodName.compareTo("f2")==0) {
			try {
				Class _class = getClass();
				Class[] argType = { String.class, String.class };
				_func_to_call = _class.getMethod(methodName, argType);			
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		else 
		if(methodName.compareTo("f1")==0) {
			try {
				Class _class = getClass();
				Class[] argType = { String.class };
				_func_to_call = _class.getMethod(methodName, argType);			
			} catch (Exception e) {
				e.printStackTrace();
			}
		}	
		else 
			if(methodName.compareTo("f0")==0) {
				try {
					Class _class = getClass();
					Class[] argType = null;
					_func_to_call = _class.getMethod(methodName, argType);			
				} catch (Exception e) {
					e.printStackTrace();
				}
			}	
		return _func_to_call;
	}
	
    /**
     * Get the named property.
     * <p>
     * Handles the available properties and returns NOT_FOUND for all
     * other names.
     * @param name the property name
     * @param start the object where the lookup began
     */
	public Object get(String name, Scriptable start) {
		//System.err.println("ActiveX::get("+name +", "+ start+")");
		for (int i = 0; i < theActivex.m_strMethodList.length; i++) {
			if( name.compareTo(theActivex.m_strMethodList[i])==0 ) {
				if(theActivex.m_dispInfo[i].m_flag == Wrapper.Dispatch_PropertyGet ) {
					return new String( new StringBuffer( theActivex.jnigetproperty(pActiveX, name) ) );
					/*
					if(theActivex.m_dispInfo[i].get_outputType() == Variant.VariantInt ||
						theActivex.m_dispInfo[i].get_outputType() == Variant.VariantByte ||
						theActivex.m_dispInfo[i].get_outputType() == Variant.VariantShort) 
						//return new Integer( theActivex.jnigetproperty(pActiveX, name) );
						return new String( new StringBuffer( theActivex.jnigetproperty(pActiveX, name) ) );
					//jnigetpropertybstr
					if(theActivex.m_dispInfo[i].get_outputType() == Variant.VariantString )
						//return new String( theActivex.jnigetpropertybstr(pActiveX, name) );
						return new String( new StringBuffer(theActivex.jnigetproperty(pActiveX, name)) );
					*/
					/*if(theActivex.m_dispInfo[i].m_flag == Variant.VariantBoolean )
						return new Boolean( theActivex.jnigetproperty(pActiveX, name)==1 );
					if(theActivex.m_dispInfo[i].m_flag == Variant.VariantDouble ||
							theActivex.m_dispInfo[i].m_flag == Variant.VariantFloat)
						return new Double( theActivex.jnigetproperty(pActiveX, name) );*/
				}
				else {
					if(theActivex.m_dispInfo[i].m_flag == Wrapper.Dispatch_Method ) {
						StringBuffer _name = new StringBuffer("f");
						int parCount = 0;
						parCount = theActivex.m_dispInfo[i].get_paramCount();
						strRUMethod = new StringBuffer().append(name);
						_name.append(parCount);
						final Method genericMethod = genMethod(_name.toString());
						return new FunctionObject(_name.toString(), genericMethod, prototype);
					}
				}
			}
		}
        return NOT_FOUND;
	}


    /**
     * Get the indexed property.
     * <p>
     * Look up the element in the associated vector and return
     * it if it exists. If it doesn't exist, create it.<p>
     * @param index the index of the integral property
     * @param start the object where the lookup began
     */
	public Object get(int index, Scriptable start) {
		Object result;
        if( index >= theActivex.m_strMethodList.length )
            result=null;
        result = theActivex.m_strMethodList[index];
        if (result != null)
            return result;
        return result;
	}


    /**
     *
     * @param name the name of the property
     * @param start the object where lookup began
     */
	public boolean has(String name, Scriptable start) {
		for (int i = 0; i < theActivex.m_strMethodList.length; i++) {
			if( name.compareTo(theActivex.m_strMethodList[i])==0 ) {
				return true;
			}
		}
		return false;
	}


    /**
     * Defines all numeric properties by returning true.
     *
     * @param index the index of the property
     * @param start the object where lookup began
     */
	public boolean has(int index, Scriptable start) {
        return true;
	}


    /**
     * Set a named property.
     *
     * We do nothing here, so all properties are effectively read-only.
     */
	public void put(String name, Scriptable start, Object value) {
	}


    /**
     * Set an indexed property.
     *
     * We do nothing here, so all properties are effectively read-only.
     */
	public void put(int index, Scriptable start, Object value) {
	}


    /**
     * Remove a named property.
     *
     * This method shouldn't even be called since we define all properties
     * as PERMANENT.
     */
	public void delete(String name) {
	}


    /**
     * Remove an indexed property.
     *
     * This method shouldn't even be called since we define all properties
     * as PERMANENT.
     */
	public void delete(int index) {
	}


    /**
     * Get prototype.
     */
    public Scriptable getPrototype() {
        return prototype;
    }

    /**
     * Set prototype.
     */
    public void setPrototype(Scriptable prototype) {
        this.prototype = prototype;
    }


	public Object[] getIds() {
		return new Object[0];
	}


	public Object getDefaultValue(Class hint) {
		return "[object ActiveX]";
	}


    /**
     * instanceof operator.
     *
     * We mimick the normal JavaScript instanceof semantics, returning
     * true if <code>this</code> appears in <code>instance</code>'s prototype
     * chain.
     */
	public boolean hasInstance(Scriptable instance) {
        Scriptable proto = instance.getPrototype();
        while (proto != null) {
            if (proto.equals(this))
                return true;
            proto = proto.getPrototype();
        }
        return false;
	}
}
