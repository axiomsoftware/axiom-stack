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
 * Return the string content of the file
 */
function readFromFile(file){
	if(this.readFile){ 
 		return this.readFile(file);
	} else{
		var f = new java.io.File(file);
		if(!f.exists()) { 
			return '';
		}
		var reader = new java.io.BufferedReader(new java.io.FileReader(f));
		var lines = [];
		var line;
		while((line = reader.readLine()) !== null){
			lines.push(line);
		}
		reader.close();
		return lines.join('\n');
	}
}

function getStyleSheet(){
	return ['body {background:url(data:image/gif;base64,R0lGODlhZABkAIAAANbc39zh5CH5BAAAAAAALAAAAABkAGQAAAL/jI8Gy+0Po5xUpotv3bx7l4XYR5YmIKbHybaWmrryDMfzfdYizpN62AtyfhmhcUIcHZegZIIJXTifUeYUUbVeV1njltsNfhVh8bhs/qJ748Ca137j4nLauS6j41v6fe7u96cWKLhFWHh1aNKn2MHYuPEISSE5+TJoGQmYSbnJeWn42YkpGlFZKuWJ2nCK2lr6Khr7OctZm3lrmTu5C9nb+KsYfDhMWBx47Je8t4zXXPcsF/02vVaNdl2WHbbd1Z31XRUeNQ5VrkW6yqqqjsKufr4UfzTv9b5aL5SfFtq+nu5vH5t7rgjCMigLIS2FthjicqgLIi+JvigCsygMIzGNFMY4IvOoDCQzkc5IQjMpDSU1FQUAADs%3D) 0 0 repeat; font-size:68%; font-family:tahoma; color:#333;} ',
			'table { border-collapse:collapse; width:100%; font-size:1.0em;}',
			'table tr td, ',
			'table thead th{ background: #fff; border: 1px solid #b8c0c3; padding: 10px;}',
			'tr.head td {font-weight: bold;}',
			'td ul.prop-list { border:0px; margin:0; padding:0; }',
			'td .def-file { color: #999; }',
			'a {text-decoration:none;color:#4d666d;}',
			'a:hover {text-decoration:underline;color:#4d666d;}',
			'a:visited{color:#4d666d;}',
			'ul{ background: #fff; border: 1px solid #b8c0c3; padding:10px;}',
			'ul li{ list-style:none;}',
			'ul li a { font-size: 1.2em; }',
			'h1, h2, h3, h4, h5, h6{ font-family: "Georgia"; padding: 10px;}',
			'h1 {font-size:2.0em; background: #fff; border: 1px solid #b8c0c3;}',
			'h2 {font-size:1.6em; background: #fff; border: 1px solid #b8c0c3;}',
			'h3 {font-size:1.4em;}',
			'h4 {font-size:1.0em;}',
			'h5 {font-size:0.8em;}',
			'h6 {font-size:0.8em;}',
			'p {background: #fff; border: 1px solid #b8c0c3; padding: 10px;}',
			'div.section { padding: 5px; background: #eee; border: 1px solid #b8c0c3; margin-top: 10px; }',
			'div.head { border: 1px solid #b8c0c3; background: #fff; padding: 10px;}',
			'div.head h1 { border: none; padding: 0px; margin: 0px;}  ',
			'pre { font-size: 1.2em; }'].join('\n');
}

/**
 * Return if this a command-line Rhino invocation
 */
function isShellInvocation(){
	return !!this.arguments;
}

/**
 * Output a message to console or log.
 */
function report(str){
	if(this.print){
		this.print(str);
	} else {
		app.log(str);
	}
}

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
 * Copy path or File src to path or File dst.
 */
function copyFile(src, dst) {
	src = new java.io.File(src);
	dst = new java.io.File(dst);
    var success = true;
    var srcChannel, dstChannel;
    try {
        srcChannel = new java.io.FileInputStream(src).getChannel();
        dstChannel = new java.io.FileOutputStream(dst).getChannel();
        dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
    } catch (e) {
		this.report(e);
        success = false;
    } finally {
        if (srcChannel) {
            try {
                srcChannel.close();
            } catch (e) { this.report(e) }
            srcChannel = null;
        }
        if (dstChannel) {
            try {
                dstChannel.close();
            } catch (e) { this.report(e); }
            dstChannel = null;
        }
    }
    return success;
}

/**
 * Recursively delete the given directory and it's children.
 */
function deleteDir(dir) {
	dir = new java.io.File(dir);
	if (dir.isDirectory()) {
        children = dir.listFiles();
        for each(var child in children){
            if (!this.deleteDir(child)) {
                return false;
            } 
        }
    }
	return dir['delete']();
}

/**
 * Return an array of File objects for the immediate children of the given directory name.
 */
function listChildFiles(dir_name){
	var dir = new java.io.File(dir_name);
	var files = [];
	if(dir.isDirectory()){
		files = dir.listFiles();
	}
	return files;
}

/**
 * Returns boolean indicating if the given directory is a prototype.
 */
function isPrototype(dir){
	var props = new java.io.File(dir.getPath()+java.io.File.separator+'prototype.properties');
	return props.exists();
}

/**
 * Returns an object associating prototype name with it's parent prototype name.
 */
function getPrototypeParents(src_dir){
	var data = {AxiomObject: null};
	var manage = this;
	this.listChildFiles(src_dir).filter(function(dir){
		return (dir.isDirectory && manage.isPrototype(dir) && dir.getName() != 'AxiomObject');
	}).map(function(dir){
		var parent = manage.readFromFile(dir.getPath()+java.io.File.separator+'prototype.properties').match(/_extends\s*=\s*(\w+)/);
		data[dir.getName()] = parent ? parent[1] : 'AxiomObject';
	});
	return data;
} 

/**
 * Return an array of objects information about each js file defined in this prototype.
 */
function getJSSource(dir_name){
	var files = [];
	for each(var child in this.listChildFiles(dir_name)){
		if(child.getName().match(/\.js$/)){
			files.push({name: child.getName(),
						content: this.readFromFile(child.getPath()) });
		}
	}
	return files;
}

/**
 * Takes an object associating name=>function and the name of the file in which it's defined. 
 * Returns an array of objects with information about functions, including their doc string.
 */
function extractFunctions(funcs, filename){
	var func_list = [];
	for(var i in funcs){
		func_list.push([i, {func: funcs[i],
			                filename: filename,
							jsdoc: funcs[i].__jsdoc__ || 'No doc string found.'}]);
	}
	return func_list;
}

/**
 * Return an object associating action name -> security level for the given prototype directory.
 */
function getSecurityMap(dir){
	var propstring = this.readFromFile(dir+java.io.File.separator+'security.properties');
	var security_map = {};
	if(propstring){
		for each(var line in propstring.split('\n')){
			if(!line.match(/^#/)){
				var match = line.match(/\s*(\w+)\s*=\s*(.*)\s*$/);
				security_map[match[1]] = match[2];
			}
		}
	}
	return security_map;
}


/**
 * Return an array of objects with information about each property defined 
 * on this prototype.
 */
function getProperties(dir){
	var propstring = this.readFromFile(dir+java.io.File.separator+'prototype.properties');
	var props = [];
	for each(var chunk in propstring.split(/\r?\n\r?\n/)){
		if(chunk.match(/^_extends/)){ continue; }
		var lines = chunk.split(/\r?\n/);
		var doc_lines = [];
		var raw_lines = [];
		for each(var line in lines){
			if(line.match(/^#/)){
				doc_lines.push(line.replace(/^#/,''));
			} else{
				raw_lines.push(line);
			}
		}
		
		var data = {doc: doc_lines.join('\n'),
			        raw:  raw_lines.join('\n')};
		for each(var line in raw_lines){
			var result = line.match(/(\w+)\.(\w+)\s*=\s*(.+)\s*/);
			if(result){
				data[result[2]] = result[3];
			} else{
				data.name = line;
			}
		}
		props.push(data);
	}
	return props;
}

/**
 * Return an array of objects with information about the TAL files for 
 * this prototype.
 */
function getTAL(dir){
	var manage = this;
	return this.listChildFiles(dir).filter(function(file){
		return file.getName().match(/\.tal$/);
	}).map(function(tal_file){
		var content = manage.readFromFile(tal_file.getPath());
		var doc_match = content.match(/\<!--\s*([^-]+)\s*-->/);
		var doc = doc_match ? doc_match[1] : '';
	 	return {file:     tal_file.getName(),
				contents: content,
				doc:      doc};
		
	});
}

/**
 * Generate an HTML wrapper for the xml object body and return the wrapped 
 * result.  If rel is true, stylesheet is linked to as if one level higher.
 */
function getHTMLWrapper(body, rel){
    var style_href = (rel ? '../' : '')+'static/styles.css';
	return <html><head>
                   		<style>{this.getStyleSheet()}</style>
		                <title>AxiomDoc</title>
		              </head>{body}
	           </html>
}

/**
 * Takes an object associating prototype names with parent names.
 * Return an XML fragment representing the inheritence tree for this prototype.
 */
function inheritencePath(parents, name, top){
	var self_link = top ? name : '<a href="../'+name+'/index.html">'+name+'</a>';
	if(!parents[name]) return new XMLList(self_link);
	return this.inheritencePath(parents, parents[name])+new XMLList(' &gt; '+self_link); 
}

/**
 * Return XML fragment for the body of a prototype's doc page.
 */
function formatPrototype(dir, parents){
	var path = dir.getPath()
	var result = <div class="head">
		            <h1>{dir.getName()}</h1>{this.inheritencePath(parents, dir.getName(), true)}
	             </div>;
 
	// methods
	var table = <table><tr class="head"><td>Name</td><td>Security</td><td>Doc</td></tr></table>;
	var funcs = [];
	var files = this.getJSSource(path);
	if(files.length){
		for each(var f in files){
			var data = {};
			with(data){ 
				try {
					eval(f.content);
				} catch(e){
					this.report(e);
				}
			}
 			funcs = funcs.concat(this.extractFunctions(data, f.name));
		}
		var security_map = this.getSecurityMap(path);
		funcs.sort(function(a,b){ return (security_map[a[0]]+'').localeCompare(security_map[b[0]]) });
		for each(var block in funcs){
			var info = block[1];
			table.table += <tr><td>{block[0]} <span class="def-file">({info.filename})</span></td><td>{security_map[block[0]] || 'Private'}</td>
                <td><pre>{info.jsdoc}</pre></td>
		        </tr>;
		}
	} else {
		table.table += <tr><td> No methods defined on this prototype.</td></tr>;
	}
	result += <div class="section"><h2>Methods</h2>{table}</div>;

	// properties
	var prop_table = <table><thead><th>Name</th><th>Type</th><th>Details</th><th>Doc</th></thead></table>;
	var props = this.getProperties(dir);
	if(props.length){
		for each(var prop in props){
			var other_props = <ul class="prop-list"/>;
			for(var subprop in prop){
				if(!subprop.match(/name|type|doc|raw/))
					other_props.ul += <li>{subprop}: {prop[subprop]}</li>
			}
			prop_table.table += <tr>
                <td>{prop.name}</td>
				<td>{prop.type || "String"}</td>
				<td>{other_props}</td>
			    <td><pre>{prop.doc || "No doc string found." }</pre></td>
                </tr>;
		}
	} else{
		prop_table.table += <tr><td>No properties defined on this prototype.</td></tr>;
	}	
	result += <div class="section"><h2>Properties</h2>{prop_table}</div>;

	// templates
	var rows = this.getTAL(dir).map(function(tal){return <tr><td>{tal.file}</td><td><pre>{tal.doc || "No doc string found."}</pre></td></tr>});
	var tal_table;
	if(rows.length){
		tal_table= <table>{new XMLList(rows.join(''))}</table>;
	} else {
		tal_table= <table><tr><td>No TAL files defined on this prototype.</td></tr></table>;
	}
	result += <div class="section"><h2>Templates</h2>{tal_table}</div>;
	
	return result;
}

/**
 * Create documentation for the application in src. Write results to target 
 * (default current directory).
 */
function generate(src, target){
	target = (target || './');
	var parents = this.getPrototypeParents(src);
	
	// create documentation directory 
	var target_dir = new java.io.File(target);
	if(!target_dir.exists())
		target_dir.mkdir();
	if(target.charAt(target.length-1) != java.io.File.separator){
		target += java.io.File.separator;
	}
	var doc_dir = new java.io.File(target+'doc');
	if(doc_dir.exists()){
		this.deleteDir(target+'doc');
	}
	doc_dir.mkdir();

	// loop over each prototype in the src dir and generate it's doc page
	var prototypes = [];
	for each(var f in this.listChildFiles(src)){
		var body = <body/>;
		if(this.isPrototype(f)){
			prototypes.push(f.getName());
			body.body += this.formatPrototype(f, parents);
		}
		var prototype_dir = new java.io.File(target+'doc'+java.io.File.separator+f.getName());
		prototype_dir.mkdir();
		var html = this.getHTMLWrapper(body, true);
		this.writeToFile(html.toXMLString(), prototype_dir.getPath()+java.io.File.separator+'index.html');
	}

	// create left nav
	var nav_body = <body><ul/></body>;
	for each(var proto in prototypes){
		var link = <li><a target="content">{proto}</a></li>;
		link..a.@href = proto+java.io.File.separator+'index.html';
		nav_body.body.ul += link;
	}
	this.writeToFile(this.getHTMLWrapper(nav_body).toXMLString(), doc_dir.getPath()+java.io.File.separator+'nav.html')

	// create default content page
	var content_body = <body> <h1> AxiomDoc</h1> <p>Something useful goes here.</p> </body>;
	this.writeToFile(this.getHTMLWrapper(content_body).toXMLString(), doc_dir.getPath()+java.io.File.separator+'content.html');

	// create frame wrapper
	var frame = <frameset cols="20%, 80%">
		<frame name="nav" src="nav.html"/>
		<frame name="content" src="content.html"/>
		</frameset>;
	this.writeToFile(frame.toXMLString(), doc_dir.getPath()+java.io.File.separator+'index.html');
}

/**
 *  main when run through Rhino shell 
 */
if(this.isShellInvocation()){
	if(this.arguments.length == 2){
		this.generate(this.arguments[0], this.arguments[1]);
	} else {
		this.report("Usage:\njava -jar js.jar -f axiom-doc.js src target");
	}
}