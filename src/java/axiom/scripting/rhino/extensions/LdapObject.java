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

import java.lang.reflect.*;
import java.util.Iterator;

import org.mozilla.javascript.*;

import com.novell.ldap.*;

/**
 * The JavaScript LdapClient class.  Provides JavaScript level functionality for accessing
 * the Java Novell LDAP library.
 * 
 * @jsconstructor LdapClient
 */
public class LdapObject extends ScriptableObject {

    private String ldapHost;
    private String ldapBase;
    private String ldapUIDAttr;
            
    private int ldapVersion = LDAPConnection.LDAP_V3;
    private int ldapPort = LDAPConnection.DEFAULT_PORT;
    private int ldapSSLPort = LDAPConnection.DEFAULT_SSL_PORT;
        
    public String getClassName() {
        return "LdapClient";
    }
    
    public String toString() {
        return "[LdapClient]";
    }
    
    /**
     * Set the host name of the LDAP server to connect to.
     * 
     * @jsfunction
     * @param {String} host The host name
     */
    public void setHost(String host) {
    	this.ldapHost = host;
    }
    
    /**
     * Set the base value used in LDAP searches.
     * 
     * @jsfunction
     * @param {String} base The base value
     */
    public void setBase(String base) {
    	this.ldapBase = base;
    }
    
    /**
     * Set the UID attribute used in LDAP searches.
     * 
     * @jsfunction
     * @param {String} attr The UID attribute
     */
    public void setUIDAttr(String attr) {
    	this.ldapUIDAttr = attr;
    }
    
    /**
     * Set the port number of the LDAP server to connect to.
     * 
     * @jsfunction
     * @param {Number} port The port number
     */
    public void setPort(int port) {
    	this.ldapPort = port;
    }
    
    /**
     * Set the SSL port number of the LDAP server to connect to.
     * 
     * @jsfunction
     * @param {Number} port The SSL port number
     */
    public void setSSLPort(int port) {
    	this.ldapSSLPort = port;
    }
    
    public int getLdapVersion() {
        return ldapVersion;
    }
    
    public String ldapVersion() {
        String version;
        switch (ldapVersion) {
        case LDAPConnection.LDAP_V3: version = "[LDAP_V3]"; break;
        default: version = "[Unknown]"; break;
        }
        return version;
    }
    
    /**
     * Get a user's LDAP entry attributes.
     * 
     * @jsfunction
     * @param {String} user The user name to authenticate with 
     * @param {String} passwd The user password to authenticate with
     * @returns {Object} A JavaScript object of key/value pairs containing the user's
     *                   LDAP entry attributes
     */
    public Object getEntry(String user, String psswd) throws Exception {
    	Context cx = Context.getCurrentContext();
    	ImporterTopLevel scope = new ImporterTopLevel(cx);
    	Scriptable s = cx.newObject(scope);
        if (user == null || user.length() == 0 || psswd == null || psswd.length() == 0) 
            return s;
        
        LDAPConnection conn = new LDAPConnection();
        try {
            conn.connect( this.ldapHost, this.ldapPort );
            
            String[] arr = {};
            LDAPSearchResults results = conn.search(this.ldapBase, LDAPConnection.SCOPE_SUB, this.ldapUIDAttr + "=" + user, arr, false);
                
            if (results.hasMore()) {
            	LDAPEntry entry = results.next();
            	conn.bind(this.ldapVersion, entry.getDN(), psswd.getBytes());
            	if (conn.isBound()) {
            		LDAPAttributeSet attrSet = entry.getAttributeSet();
            		Iterator itr = attrSet.iterator();
            		while(itr.hasNext()) {
            			LDAPAttribute attr = (LDAPAttribute) itr.next();
            			String name = attr.getName();
            			String value = attr.getStringValue();
            			s.put(name, s, value);
            		}
            	}
            }
             
        } catch (Exception ex) {
        } finally {
            try {
                conn.disconnect();
            } catch (Exception ignore) {
                // ignore
            }
        }
        return s;
    }
    
    /**
     * Authenticate the user credentials against the LDAP server.
     * 
     * @jsfunction
     * @param {String} user The user name to authenticate with 
     * @param {String} passwd The user password to authenticate with
     * @returns {Boolean} Whether authentication was a success or not
     */
    public boolean authenticate(String user, String psswd) {
        boolean success = false;
        
        if (user == null || user.length() == 0 || psswd == null || psswd.length() == 0) 
            return false;

        LDAPConnection conn = new LDAPConnection();
        try {
        	String[] arr = {};
            conn.connect( this.ldapHost, this.ldapPort );
            LDAPSearchResults results = conn.search(this.ldapBase, LDAPConnection.SCOPE_SUB, this.ldapUIDAttr + "=" + user, arr, false);

            if (results.hasMore()) {
                LDAPEntry entry = results.next();
                conn.bind(this.ldapVersion, entry.getDN(), psswd.getBytes());          
                if (conn.isBound()) {                
                	success = true;
                }
            }
        } catch (Exception ex) {
            success = false;
        } finally {
            try {
                conn.disconnect();
            } catch (Exception ignore) {
                // ignore
            }
        }
        
        return success;
    }
    
    public static LdapObject ldapObjCtor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) {
        return new LdapObject();
    }
    
    public static void init(Scriptable scope) {
        Method[] methods = LdapObject.class.getDeclaredMethods();
        ScriptableObject proto = new LdapObject();
        proto.setPrototype(getObjectPrototype(scope));
        Member ctorMember = null;
        
        final int length = methods.length;
        for (int i = 0; i < length; i++) {
            if ("ldapObjCtor".equals(methods[i].getName())) {
                ctorMember = methods[i];
                break;
            }
        }
        
        FunctionObject ctor = new FunctionObject("LdapClient", ctorMember, scope);
        ctor.addAsConstructor(scope, proto);
        String[] ldapFuncs = { "authenticate","getEntry","setHost","setBase","setUIDAttr","setPort","setSSLPort" };
        
        try {
            proto.defineFunctionProperties(ldapFuncs, LdapObject.class, 0);
        } catch (Exception ignore) {
            System.err.println ("Error defining function properties: "+ignore);
        }
    }
    
}