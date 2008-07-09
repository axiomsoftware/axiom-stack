/**
  *  Copyright 2007-2008 Axiom Software Inc.
  *  This file is part of the Axiom Manage Application. 
  *
  *  The Axiom Manage Application is free software: you can redistribute 
  *  it and/or modify it under the terms of the GNU General Public License 
  *  as published by the Free Software Foundation, either version 3 of the 
  *  License, or (at your option) any later version.
  *
  *  The Axiom Manage Application is distributed in the hope that it will 
  *  be useful, but WITHOUT ANY WARRANTY; without even the implied warranty 
  *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  *  GNU General Public License for more details.
  *
  *  You should have received a copy of the GNU General Public License
  *  along with the Axiom Manage Application.  If not, see 
  *  <http://www.gnu.org/licenses/>.
  */

/**
 * Evaluate the given commands and return the result.
 */
function eval_shell() {
	try {
 		return this.dispatchShell(req.data.commands, req.data.app_name); 
    } catch(e) {
		return "Exception: " + e.toSource();
    }
}


function invoke_printable_shell(commands, app_name){
	var application = this.getAppByName(app_name);
	var evaluator = application.getEvaluator();
	var results = evaluator.invokeInternal(null, '__shell_eval__', [commands]);
	application.releaseEvaluator(evaluator);
	return results;
}


function inspector_shell(){
	var result = {commands: [{command: ">>> "+req.data.commands+"<br/>"}]};
	try {
 		var result_obj = this.invoke_printable_shell(req.data.commands, req.data.app_name); 
		if(result_obj.print_results.length){
			result.commands[0].command += "<i>"+result_obj.print_results.join('<br/>')+"</i><br/><br/>"
		}
		result.commands[0].command += result_obj.result;
    } catch(e) {
		result.commands[0].command += "Exception: " + e.toSource();
    }
	return result;
}

function dispatchShell(commands, app_name){
	var application = this.getAppByName(app_name);
	var evaluator = application.getEvaluator();
	var results = evaluator.invokeInternal(null, 'eval', [commands]);
	application.releaseEvaluator(evaluator);
	return results;
}
