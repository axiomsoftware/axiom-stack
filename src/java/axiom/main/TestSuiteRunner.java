package axiom.main;

import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

import axiom.framework.core.Application;
import axiom.framework.core.RequestEvaluator;
import axiom.scripting.rhino.RhinoEngine;

public class TestSuiteRunner extends CommandlineRunner {

	public TestSuiteRunner(String[] args) {
		super(args);
	}

    public static void main(String[] args) throws Exception {
    	TestSuiteRunner runner = new TestSuiteRunner(args);
    	runner.run();
        System.exit(0);
    }
	
	public void run(){
        Server server = new Server(config);
        server.init();
        server.checkAppManager(0);
        server.startApplication(appName);
        Application app = server.getApplication(appName);
        
        RequestEvaluator reqeval = app.getEvaluator();
        try{
        	// TODO change this when we've made a healthier way to access an app's global scope
        	Scriptable result = (Scriptable) reqeval.invokeInternal(null, "eval", new Object[]{"_test"});
        	runSuite(result, reqeval);
        } catch(Exception e){
        	e.printStackTrace();
        }
	}
	
	public void runSuite(Scriptable suite, RequestEvaluator evaluator){
		Object setup = suite.get("setup", suite);
		Object teardown = suite.get("teardown", suite);
		for(Object i: suite.getIds()){
			Scriptable obj = (Scriptable) suite.get((String)i, suite);
			if(obj instanceof Function){
				System.out.println(i);
				try{
					if(setup != null){
						evaluator.invokeInternal(suite, "setup", new Object[]{});
						//evaluator.
					}
					evaluator.invokeInternal(suite, i.toString(), new Object[]{});
					if(setup != null){
						evaluator.invokeInternal(suite, "teardown", new Object[]{});
					}
				} catch(Exception e){
					e.printStackTrace();
				}
			} else {
				runSuite(obj, evaluator);	
			}
		}
	}
}
