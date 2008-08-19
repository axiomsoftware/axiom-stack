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
            AxiomObject axobj = (AxiomObject) object;
            while (axobj != null) {
                s.push(axobj);
                axobj = (AxiomObject) axobj.get("__parent__", axobj);
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