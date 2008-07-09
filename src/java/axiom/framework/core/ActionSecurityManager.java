/**
 * Axiom class for Security Management
 * 
 * This class implements the security model for Axiom
 * Basically, the components in a URL are traversed and the permissions are checked on 
 * each object and each action to ensure they are permitted by the current user of the 
 * system.
 * 
 */

package axiom.framework.core;

import java.util.*;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import axiom.framework.ErrorReporter;
import axiom.objectmodel.INode;
import axiom.scripting.ScriptingEngine;
import axiom.scripting.ScriptingException;
import axiom.scripting.rhino.AxiomObject;
import axiom.scripting.rhino.RhinoEngine;


public class ActionSecurityManager {
    
    public static final String SECURITY_METHOD = "isAllowed";
    public static final String GET_ROLES_METHOD = "getRoles";
    public static final Object[] EMPTY_ARGS = new Object[0];
    
	public static Object getUserRoles(INode user, Application app, int rettype) {
		LinkedList userroles = new LinkedList();
		userroles.add("@Anyone");

        ScriptingEngine seng = app.getCurrentRequestEvaluator().scriptingEngine;
        Scriptable scope = ((RhinoEngine) seng).getCore().getScope();
        
		if (user != null) {
			userroles.add("@Authenticated");
            
            Object result = null; 
            try {    
                result = seng.invoke(Context.toObject(user, scope),
                    GET_ROLES_METHOD,
                    new Object[0],
                    ScriptingEngine.ARGS_WRAP_DEFAULT,
                    false);
            } catch (ScriptingException ex) {
                app.logError(ErrorReporter.errorMsg(ActionSecurityManager.class, "getUserRoles") 
                		+ "Could NOT call getRoles() on the user object.", ex);
            }
            
            if (result != null && result instanceof Scriptable) {
                Scriptable s = (Scriptable) result;
                Object[] ids = s.getIds();
                final int length = ids.length;
                for (int i = 0; i < length; i++) {
                    userroles.add(s.get(i, s));
                }
            }
		}
        
		return (rettype == 0) ? userroles : 
                         Context.getCurrentContext().newArray(scope, userroles.toArray());
	}
    
	public static boolean checkRoles(Application app, Object[] roles) 
    throws SecurityException {
		RequestEvaluator re = app.getCurrentRequestEvaluator();
		INode user = re.getSession().getUserNode();
		LinkedList userRoles;
		userRoles = (LinkedList) getUserRoles(user, app, 0);
		try {
			ArrayList cRoles = new ArrayList();
			Collections.addAll(cRoles, roles);
			for (int i = 0; i < userRoles.size(); i++) {
				if (cRoles.contains(userRoles.get(i))) {
					return true;
				}
			}
		} catch (Exception e) { 
			app.logError(ErrorReporter.errorMsg(ActionSecurityManager.class, "checkRoles"), e);
        }
		return false;
	}
    
	public static void checkAdmin(Application app) throws SecurityException {
		if (!checkRoles(app, new String[] {"@Admin"})) {
			app.getCurrentRequestEvaluator().getResponse().setStatus(401);
			throw new RuntimeException("Unauthorized");
		}
	}    
    
    public static boolean isAllowed(Application app, Object object, String action, 
                                        ScriptingEngine seng, RequestEvaluator reqeval) 
    throws SecurityException {
    	if ("unauthorized".equalsIgnoreCase(action)) {
    		return true;
    	}
    	
        Stack s = new Stack();
        if (object instanceof AxiomObject) {
            AxiomObject hobj = (AxiomObject) object;
            while (hobj != null) {
                s.push(hobj);
                hobj = (AxiomObject) hobj.get("__parent__", hobj);
            }
        } else {
            return false;
        }
        
        INode user = reqeval.getSession().getUserNode();
        final Object roles = ActionSecurityManager.getUserRoles(user, app, 1);
        
        final int size = s.size();
        for (int i = 0; i < size; i++) {
            boolean allowed = false;
            String currAction = (i == size - 1) ? action : "main";
            try {
                Object result = seng.invoke(s.pop(),
                        SECURITY_METHOD,
                        new Object[] { currAction, roles },
                        ScriptingEngine.ARGS_WRAP_DEFAULT,
                        false);
                
                if (result != null) {
                    allowed = ((Boolean) result).booleanValue();
                }
            } catch (Exception ex) {
                allowed = false;
                app.logError(ErrorReporter.errorMsg(ActionSecurityManager.class, "isAllowed") 
                		+ "Error running " + SECURITY_METHOD + " on " + object, ex);
            }
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
    
}