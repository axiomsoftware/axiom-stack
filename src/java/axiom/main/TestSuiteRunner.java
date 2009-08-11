package axiom.main;

import org.mozilla.javascript.Scriptable;

import axiom.framework.core.Application;
import axiom.scripting.rhino.RhinoEngine;

public class TestSuiteRunner extends CommandlineRunner {

	public TestSuiteRunner(String[] args) {
		super(args);
	}

	public void run(){
        Server server = new Server(config);
        server.init();
        server.checkAppManager(0);
        server.startApplication(appName);
        Application app = server.getApplication(appName);
        
        Scriptable global = ((RhinoEngine)app.getEvaluator().getScriptingEngine()).getGlobal();
        System.out.println(global.get("_test", global));
	}
	
}
