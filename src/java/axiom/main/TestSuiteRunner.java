package axiom.main;

import java.sql.Connection;
import java.util.Properties;

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

        // use separate test db so we don't wreck an app's existing db!
        Properties serverProps = server.getProperties();
        serverProps.setProperty("dbHome", "test-"+serverProps.getProperty("dbHome", "db"));

        server.checkAppManager(0);
        server.startApplication(appName);
        Application app = server.getApplication(appName);
                
        RequestEvaluator reqeval = app.getEvaluator();
        try{
        	// TODO change this when we've made a healthier way to access an app's global scope
        	Scriptable result = (Scriptable) reqeval.invokeInternal(null, "eval", new Object[]{"_test"});
        	runSuite(result, server, appName);
        } catch(Exception e){
        	e.printStackTrace();
        }
	}
	
	public void runSuite(Scriptable suite, Server server, String name){
		Object setup = suite.get("setup", suite);
		Object teardown = suite.get("teardown", suite);
		Application app = server.getApplication(name);
		RequestEvaluator evaluator = new RequestEvaluator(app);
		for(Object i: suite.getIds()){
			Scriptable obj = (Scriptable) suite.get((String)i, suite);
			if(obj instanceof Function && !"setup".equals(i) && !"teardown".equals(i) && i.toString().startsWith("test")){
				try{
					if(setup != null){
						evaluator.invokeInternal(suite, "setup", new Object[]{});
						evaluator.getThread().commit(0, evaluator);
					}
					evaluator.invokeInternal(suite, i.toString(), new Object[]{});
					if(setup != null){
						evaluator.invokeInternal(suite, "teardown", new Object[]{});
						evaluator.getThread().commit(0, evaluator);
					}
					Connection conn = evaluator.app.getTransSource().getConnection();
					server.stopApplication(name);
					conn.createStatement().execute("DELETE FROM PathIndices");
					conn.createStatement().execute("DELETE FROM Lucene");
					conn.commit();
					conn.close();
					server.startApplication(name);
					app = server.getApplication(name);
					evaluator = new RequestEvaluator(app);
					System.out.print(".");
				} catch(Exception e){
					System.out.println(" error running"+ i);
					e.printStackTrace();
				}
			} else {
				runSuite(obj, server, name);	
			}
		}
	}
}
