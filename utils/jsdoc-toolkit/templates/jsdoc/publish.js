function publish(symbolSet) {
	// filters
	function isaClass($) {return ($.is("CONSTRUCTOR") || $.isNamespace)}
	function isGlobal($) {
		for each(tag in $.comment.tags){
			if(tag.title.equalsIgnoreCase("jsinstance")){
				return true;
			}
		}
		if($.alias == "_global_"){
			return true;
		}
		return false;
	};
	function isExtension($) {
		return $.isNamespace && $.alias != "_global_";
	};
	function isBuiltin($) {
		for each(tag in $.comment.tags){
			if(tag.title.equalsIgnoreCase('jsconstructor') || tag.title.equalsIgnoreCase('jsnoconstructor')){
				return true;
			}
		}
		if(!$.srcFile.equalsIgnoreCase('alldoc.js') && $.is("CONSTRUCTOR") && !$.isNamespace){
			return true;
		}
		return false;
	};

	publish.conf = {  // trailing slash expected for dirs
		ext: ".html",
		outDir: JSDOC.opt.d || SYS.pwd+"../out/jsdoc/",
		templatesDir: SYS.pwd+"../templates/jsdoc/",
		symbolsDir: "symbols/",
		srcDir: "symbols/src/"
	};

	if (JSDOC.opt.s && defined(Link) && Link.prototype._makeSrcLink) {
		Link.prototype._makeSrcLink = function(srcFilePath) {
			return "&lt;"+srcFilePath+"&gt;";
		}
	}
	
	IO.mkPath((publish.conf.outDir+"symbols").split("/"));
		
	// used to check the details of things being linked to
	Link.symbolSet = symbolSet;
	
	var classTemplate = null;
	var extensionTemplate = null;
	var globalTemplate = null;
	var builtinTemplate = null;
	try {
		classTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+"class.tmpl");
		extensionTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+"extension.tmpl");
		globalTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+"global.tmpl");
		builtinTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+"builtin.tmpl");
	}
	catch(e) {
		print(e.message);
		quit();
	}
	
	var symbols = symbolSet.toArray();

	var objects = symbols.filter(isaClass).sort(makeSortby("alias"));

	var extensionObjects = symbols.filter(isExtension).sort(makeSortby("alias"));
	var builtinObjects = symbols.filter(isBuiltin).sort(makeSortby("alias"));
	var globalObjects = symbols.filter(isGlobal).sort(makeSortby("alias"));
	globalObjects.methods = [];

	for(var i = 0; i < globalObjects.length; i++){
		if(globalObjects[i].alias == '_global_'){
			index = i;
			for each(method in globalObjects[i].methods){
				globalObjects.methods.push(method.alias);
			}
			globalObjects.splice(i, 1);
		}
	}
	
	

	

	Link.base = "../";
	var objectsTemplateCache = {
		builtin:builtinTemplate.process(builtinObjects),
		global:globalTemplate.process(globalObjects),
		extension:extensionTemplate.process(extensionObjects)
	};

	
	for (var i = 0, l = objects.length; i < l; i++) {
		publish.classesIndex = objectsTemplateCache[getObjectType(objects[i])];
		var output = classTemplate.process(objects[i]);
		IO.saveFile(publish.conf.outDir+"symbols/", objects[i].alias+publish.conf.ext, output);
	}
	
	// regenrate the index with different relative links
	Link.base = "";
	buildIndexTemplate(globalTemplate, publish, globalObjects, "index", "Global Objects and Global Functions");
	buildIndexTemplate(extensionTemplate, publish, extensionObjects, "extensions", "Prototype Extensions");
	buildIndexTemplate(builtinTemplate, publish, builtinObjects, "builtin", "Built-in Prototypes");


}

function getObjectType(objects){
	for each(tag in objects.comment.tags){
		var title = new String(tag.title);
		if(objects.alias == '_global_' || title.equalsIgnoreCase('jsinstance')){
			return 'global';
		} else if(objects.isNamespace){
			return 'extension';
		} else if(title.equalsIgnoreCase('jsconstructor') || title.equalsIgnoreCase('jsnoconstructor')) {
			return 'builtin';
		} else if(!objects.srcFile.equalsIgnoreCase('alldoc.js') && objects.is("CONSTRUCTOR")){
			return 'builtin';
		}
	}

}

function buildIndexTemplate(template, publish, data, filename, title){
	var indexTemplate = null;
	try {
		indexTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+"index.tmpl");
	}
	catch(e) {
		print(e.message);
		quit();
	}
	publish.classesIndex = template.process(data);
	publish.classTitle = title;
	var index = indexTemplate.process(data);
	IO.saveFile(publish.conf.outDir, filename + publish.conf.ext, index);
}


/** Just the first sentence. */
function summarize(desc) {
	if (typeof desc != "undefined")
		return desc.match(/([\w\W]+?\.)[^a-z0-9]/i)? RegExp.$1 : desc;
}

/** make a symbol sorter by some attribute */
function makeSortby(attribute) {
	return function(a, b) {
		if (a[attribute] != undefined && b[attribute] != undefined) {
			a = a[attribute].toLowerCase();
			b = b[attribute].toLowerCase();
			if (a < b) return -1;
			if (a > b) return 1;
			return 0;
		}
	}
}

function include(path) {
	var path = publish.conf.templatesDir+path;
	return IO.readFile(path);
}

function makeSrcFile(path, srcDir, name) {
	if (JSDOC.opt.s) return;
	
	if (!name) {
		name = path.replace(/\.\.?[\\\/]/g, "").replace(/[\\\/]/g, "_");
		name = name.replace(/\:/g, "_");
	}
	
	var src = {path: path, name:name, charset: IO.encoding, hilited: ""};
	
	if (defined(JSDOC.PluginManager)) {
		JSDOC.PluginManager.run("onPublishSrc", src);
	}

	if (src.hilited) {
		IO.saveFile(srcDir, name+publish.conf.ext, src.hilited);
	}
}

function makeSignature(params) {
	if (!params) return "()";
	var signature = "("
	+
	params.filter(
		function($) {
			return $.name.indexOf(".") == -1; // don't show config params in signature
		}
	).map(
		function($) {
			return $.name;
		}
	).join(", ")
	+
	")";
	return signature;
}

/** Find symbol {@link ...} strings in text and turn into html links */
function resolveLinks(str, from) {
	str = str.replace(/\{@link ([^} ]+) ?\}/gi,
		function(match, symbolName) {
			return new Link().toSymbol(symbolName);
		}
	);
	
	return str;
}

/*
	// Generate Files	
	var files = JSDOC.opt.srcFiles;
 	for (var i = 0, l = files.length; i < l; i++) {
 		var file = files[i];
 		var srcDir = publish.conf.outDir + "symbols/src/";
		makeSrcFile(file, srcDir);
 	}

	try {
		var fileindexTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+"allfiles.tmpl");
	}
	catch(e) { print(e.message); quit(); }
	
	var documentedFiles = symbols.filter(isaFile);
	var allFiles = [];
*/	
//	for (var i = 0; i < files.length; i++) {
//		allFiles.push(new JSDOC.Symbol(files[i], [], "FILE", new JSDOC.DocComment("/** */")));
//	}
/*	
	for (var i = 0; i < documentedFiles.length; i++) {
		var offset = files.indexOf(documentedFiles[i].alias);
		allFiles[offset] = documentedFiles[i];
	}
		
	allFiles = allFiles.sort(makeSortby("name"));

	var filesIndex = fileindexTemplate.process(allFiles);
	IO.saveFile(publish.conf.outDir, "files"+publish.conf.ext, filesIndex);
	fileindexTemplate = filesIndex = files = null;
*/
