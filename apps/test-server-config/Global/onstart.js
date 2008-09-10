function run_tests(){
	axiom.SystemFile.writeToFile(axiom.Test.XMLReport(axiom.Test.run(axiom.Test.globalSuite())).toXMLString(), app.getDir()+java.io.File.separator+'test-results.xml');
	app.log('Begin System Shutdown');
	java.lang.System.exit(0);
	app.log('End System Shutdown');
}