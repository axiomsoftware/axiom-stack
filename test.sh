#!/bin/bash
java -Dorg.apache.lucene.FSDirectory.class=org.apache.lucene.store.TransFSDirectory -DmainClass=axiom.main.TestSuiteRunner -cp launcher.jar axiom.main.launcher.Main -d . -f server.properties -a test run_tests
#java -Dorg.apache.lucene.FSDirectory.class=org.apache.lucene.store.TransFSDirectory -DmainClass=axiom.main.CommandlineRunner -cp launcher.jar axiom.main.launcher.Main -d . -f server.properties -a test run_tests
