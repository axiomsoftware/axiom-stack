/**
 *   Copyright 2007-2008 Axiom Software Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
if (!global.axiom) {
    global.axiom = {};
}

/**
 * Axiom comes with a built in framework for writing functional and unit tests for your application. These tests 
 * can be written globally or organized by specific prototype. A test is a function that contains a number of 
 * assertions- if all the assertions pass and no other errors occur, the test passes. 
 * @constructor
 */
axiom.Test = {};

/**
 * Checks the global scope to determine if a global test suite is defined and iterates over all prototypes within
 * the application determining if there is test suite defined.
 * @returns {Object} An object containing the global test suite along with all other prototypes within the application
 * that have a test suite defined
 */
axiom.Test.globalSuite = function(){
	var suite = { global: (global._test || {}) };
	for each (var proto in app.getPrototypes()){
		if (global[proto] && global[proto].prototype){
			suite[proto] = (global[proto].prototype._test || {});
		}
	}
	return suite;
}

/**
 * Executes all defined test suites for within the application.
 * @param {Object} suite Test suite to be run
 * @returns {Object} An object containing the number of errors, failures and results for individual tests
 */
axiom.Test.run = function(suite){
	var results = { 
		errors: 0,
		failures: 0,
		test_results: []
	};

	var start = new Date();
	for(var proto in suite){
		var tests = suite[proto];
		axiom.Test.runSuite(tests, results, proto);
	}
	results.total_time = ((new Date()).getTime() - start.getTime() ) / 1000.0;
	return results;
}

/**
 * Executes an individual test suite.
 * @param {Object} tests The tests to be run
 * @param {Object} results The results object to be modified
 * @param {String} proto Name of the prototype suite to be executed
 */
axiom.Test.runSuite = function(tests, results, proto){
	var setup = (tests.setup || function(){});
	var teardown = (tests.teardown || function(){});
	for(var test in tests){
		if(test.match(/^test/) && typeof tests[test] == 'function'){
			setup.call(tests);
			res.commit();
			var test_result = { name: proto+'.'+test };
			var test_start = new Date()
			try {
				tests[test].call(tests);
			} catch(e) {
				if(e instanceof AssertionFailedError){
					results.failures += 1;
					test_result.failure = e;
				} else {
					results.errors += 1;
					test_result.error = e;
				}
			} finally {
				test_result.time = ( (new Date()).getTime() - test_start.getTime() ) / 1000.0;
			}
			results.test_results.push(test_result);
			res.commit();
			teardown.call(tests);
			res.commit();
		} else if(typeof tests[test] == 'object'){
			setup.call(tests);
			res.commit();			
			axiom.Test.runSuite(tests[test], results, proto+'.'+test)
			teardown.call(tests);
			res.commit();
		}
	}
}

/**
 * Builds an XML report based on the results of the test suite execution.
 * @param {Object} results An object containing the results of all the test suites executed
 * @returns {Object} An XML object representing the results of the test suite execution
 */
axiom.Test.XMLReport = function(results) {
	var markup = <testsuite name="AxiomTestSuite"/>;
	markup.@errors = results.errors;
	markup.@failures = results.failures;
	markup.@tests = results.test_results.length;
	markup.@time = results.total_time;
	for each(var case_result in results.test_results){
		var case_element = <testcase/>;
		case_element.@name = case_result.name;
		case_element.@time = case_result.time;

		var defect = (case_result.error || case_result.failure);
		if(defect){
			var error = <error type=""/>;
			if(case_result.failure){
				error.setName('failure');
			}
			error.@message = defect.message;
			if(defect.mCallStack){
				error += defect.mCallStack.toString(); 
			} 
			case_element.testcase += error;
		}
		markup.testsuite += case_element;
	}
	return markup;
}
