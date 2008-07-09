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

axiom.Test = {};
axiom.Test.globalSuite = function(){
	var suite = { global: (global._test || {}) };
	for each (var proto in app.getPrototypes()){
		if (global[proto] && global[proto].prototype){
			suite[proto] = (global[proto].prototype._test || {});
		}
	}
	return suite;
}

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
