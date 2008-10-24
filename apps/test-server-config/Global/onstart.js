function run_tests(){
	var results = axiom.Test.run(axiom.Test.globalSuite());
	var testfilename = app.serverDir + java.io.File.separator + '..' + java.io.File.separator + 'test-results.xml';
	axiom.Test.buildXMLReport(results, testfilename);

	app.log('Begin System Shutdown');
	java.lang.System.exit(0);
	app.log('End System Shutdown');
}
