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

this.server_properties = [
	'# server.properties',
	'# -----------------',
	'# Global Axiom settings specified here.'
].join('\n');

this.app_properties = [
	'# app.properties',
	'# --------------',
	'# Application specific settings specified here.'
].join('\n');

this.axiomobject_prototype = [
	'id',
	'id.type = String',
	'id.index = UNTOKENIZED',
	'',
	'_children',
	'_children.type = Collection(AxiomObject)',
	'_children.accessname = id'
].join('\n');

this.root_main = <html xmlns:tal="http://axiomstack.com/tale">
					 <head><title>Welcome to Axiom!</title></head>
						 <body>
							 <h1>Welcome to Axiom!</h1>
						 </body>
					 </html>;

/**
 * Write the given string to the target path or File.
 */
function writeToFile(str, target){
	var writer;
	try{
		writer = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(target)));
		writer.print(str);
	} catch(e){
		this.report(e);
	}finally{
		if(writer)
			writer.close();
		writer = null;
	}
}

/**
 * Write to console or log message, depending on execution context.
 */
function report(str){
	if(this.print){
		this.print(str);
	} else {
		app.log(str);
	}
}


/**
 * Create sample Axiom app.
 */
function bootstrap(axiom_root_dir, appname){
	if(!axiom_root_dir.match(new RegExp(java.io.File.separator+'$')))
		axiom_root_dir += java.io.File.separator;

	var dir = axiom_root_dir+'apps'+java.io.File.separator+appname;
	var app_dir = new java.io.File(dir);
	if(!app_dir.exists()){
		app_dir.mkdirs();

		this.writeToFile([
			"automaticResourceUpdate = true",
			"mountpoint = /"+appname
		].join('\n'), dir+java.io.File.separator+'app.properties')

		var ax_dir = dir+java.io.File.separator+'AxiomObject';
		(new java.io.File(ax_dir)).mkdir();
		this.writeToFile(this.axiomobject_prototype, ax_dir+java.io.File.separator+'prototype.properties');

		var root_dir = dir+java.io.File.separator+'Root';
		(new java.io.File(root_dir)).mkdir();
		this.writeToFile(this.root_main.toXMLString(), root_dir+java.io.File.separator+'main.tal');
		return {success: true};
	} else {
		return {success: false, message: "Application already exists.  Please try a different name."}
	}
}

/**
 *  main when run through Rhino shell
 */
if(this.arguments){
	if(this.arguments.length == 2){
		this.bootstrap(this.arguments[0], this.arguments[1]);
	} else {
		this.report("Usage:\njava -jar js.jar axiom-generate.js AXIOM_INSTALL_DIRECTORY APPNAME");
	}
}

