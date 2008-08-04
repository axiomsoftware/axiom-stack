package axiom.scripting.rhino.extensions;

import java.lang.reflect.*;
import java.util.Iterator;

import org.mozilla.javascript.*;

import com.novell.ldap.*;

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
    
    public void setHost(String host) {
    	this.ldapHost = host;
    }
    
    public void setBase(String base) {
    	this.ldapBase = base;
    }
    
    public void setUIDAttr(String attr) {
    	this.ldapUIDAttr = attr;
    }
    
    public void setPort(int port) {
    	this.ldapPort = port;
    }
    
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