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
package axiom.ext.sessionmanager;

import axiom.framework.core.Application;
import axiom.framework.core.RequestEvaluator;
import axiom.framework.core.Session;
import axiom.framework.core.SessionBean;
import axiom.scripting.ScriptingEngine;
import axiom.scripting.rhino.RhinoEngine;
import org.mozilla.javascript.Context;

public class SessionManager extends axiom.framework.core.SessionManager{

	private boolean hasOnSessionTimeout = false;
	
	private ScriptingEngine scriptingEngine = null;
	
    public SessionManager() {
    	super();    	
    }
    
    /**
     * Remove the session from the sessions-table and logout the user.
     */
    public void discardSession(Session session) {
    	Context.enter();
    	RequestEvaluator thisEvaluator = null;
    	try {
	        if(hasOnSessionTimeout) {
	        	Object[] args = new Object[1];
	        	args[0] = new SessionBean(session);
	        	
				thisEvaluator = this.app.getEvaluator();
				thisEvaluator.invokeInternal(((RhinoEngine)thisEvaluator.getScriptingEngine()).getCore().getScope(), "onSessionTimeout", args);
	        }
    	} catch (Exception e) {
    		app.logError("Error in discardSession " + e);
    	} finally {
    		this.app.releaseEvaluator(thisEvaluator);
    	}
        logoutSession(session);
        sessions.remove(session.getSessionId());
		Context.exit();
    }

    public void init(Application app) {
        this.app = app;
    	try{
        	String engineClassName = app.getProperty("scriptingEngine", "axiom.scripting.rhino.RhinoEngine");
        	Class clazz = app.getClassLoader().loadClass(engineClassName);
	        scriptingEngine = (ScriptingEngine) clazz.newInstance();
	        scriptingEngine.init(app, null);
	        scriptingEngine.updatePrototypes();
	        hasOnSessionTimeout = scriptingEngine.hasFunction(null, "onSessionTimeout");
    	}
    	catch(Exception e){
    		app.logError("Error in SessionManager " + e);
    	}
    }
    
    public void expire(Session session){
    	Context.enter();
    	RequestEvaluator thisEvaluator = null;
    	try {
	        if(hasOnSessionTimeout && session.isLoggedIn()) {
	        	Object[] args = new Object[1];
	        	args[0] = new SessionBean(session);
	        	thisEvaluator = this.app.getCurrentRequestEvaluator();
				((RhinoEngine)thisEvaluator.getScriptingEngine()).invoke(((RhinoEngine)thisEvaluator.getScriptingEngine()).getCore().getScope(), "onSessionTimeout", args, ScriptingEngine.ARGS_WRAP_DEFAULT,
                        true);
	        }
    	} catch (Exception e) {
    		app.logError("Error in discardSession " + e);
    	} finally {
    		Context.exit();
    	}
    }
}
