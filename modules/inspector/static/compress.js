var dir = (arguments[0] || '.').replace(/\/$/, '');

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

function compress(file, env){
	runCommand("java", '-jar', dir+'/yuicompressor-2.3.4.jar', file, env);
}

var js_base = {output:''};
compress(dir+'/ext-2.0.2/adapter/ext/ext-base.js', js_base);
compress(dir+'/ext-2.0.2/ext-all.js', js_base);

var inspector_js = {output: js_base.output};
compress(dir+'/ext-2.0.2/TabCloseMenu.js', inspector_js);
compress(dir+'/ext-2.0.2/radiogroup/radiogroup.js', inspector_js);
compress(dir+'/inspector.js', inspector_js);
writeToFile(inspector_js.output, dir+'/inspector-all.js');

var login_js = {output: js_base.output};
compress(dir+'/login.js', login_js);
writeToFile(login_js.output, dir+'/login-all.js');
