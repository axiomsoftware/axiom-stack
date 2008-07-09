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
