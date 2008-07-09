function run_tests(){
	axiom.SystemFile.writeToFile(axiom.Test.XMLReport(axiom.Test.run(axiom.Test.globalSuite())).toXMLString(), app.getDir()+java.io.File.separator+'test-results.xml');
	java.lang.System.exit(0);
}