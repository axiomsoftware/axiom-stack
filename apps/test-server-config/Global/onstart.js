function run_tests(){
	axiom.SystemFile.writeToFile(axiom.Test.XMLReport(axiom.Test.run(axiom.Test.globalSuite())).toXMLString(), app.serverDir +java.io.File.separator+ '..' +java.io.File.separator+'test-server-cfg-results.xml');
	app.log('Begin System Shutdown');
	java.lang.System.exit(0);
	app.log('End System Shutdown');
}