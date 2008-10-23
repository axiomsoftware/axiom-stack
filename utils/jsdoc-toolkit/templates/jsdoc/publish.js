function publish(symbolSet) {
	// filters
	function isaClass($) {return ($.is("CONSTRUCTOR") || $.isNamespace)}
	function isHook($){
		return $.comment.getTag('hook').length;
	}
	function isGlobal($) {
		for each(tag in $.comment.tags){
			if(tag.title.equalsIgnoreCase("jsinstance")){
				return !isHook($);
			}
		}
		if($.alias == "_global_"){
			return !isHook($);
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
		symbolsDir: ""
	};

	if (JSDOC.opt.s && defined(Link) && Link.prototype._makeSrcLink) {
		Link.prototype._makeSrcLink = function(srcFilePath) {
			return "&lt;"+srcFilePath+"&gt;";
		};
	}

	IO.mkPath(publish.conf.outDir);

	// used to check the details of things being linked to
	Link.symbolSet = symbolSet;

	var classTemplate = null;
	var extensionTemplate = null;
	var globalTemplate = null;
	var builtinTemplate = null;
	var hooksTemplate = null;
	try {
		classTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+"class.tmpl");
		extensionTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+"extension.tmpl");
		globalTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+"global.tmpl");
		builtinTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+"builtin.tmpl");
		hooksTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+"hooks.tmpl");
	}
	catch(e) {
		print(e.message);
		quit();
	}

	var symbols = symbolSet.toArray();

	var objects = symbols.filter(isaClass).sort(makeSortby("alias"));

	var hooksObjects = symbols.filter(isHook).sort(makeSortby("alias"));
	var extensionObjects = symbols.filter(isExtension).sort(makeSortby("alias"));
	var builtinObjects = symbols.filter(isBuiltin).sort(makeSortby("alias"));
	for each(var obj in builtinObjects){
		obj.methods = obj.methods.filter(function($){return !isHook($);});
	}

	var globalObjects = symbols.filter(isGlobal).sort(makeSortby("alias"));
	globalObjects.methods = [];

	for(var i = 0; i < globalObjects.length; i++){
		if(globalObjects[i].alias == '_global_'){
			//var index = i;
			globalObjects[i].methods = globalObjects[i].methods.filter(function($){ return !isHook($); });
			for each(var method in globalObjects[i].methods){
				globalObjects.methods.push(method.alias);
			}
			globalObjects.splice(i, 1);
		}
	}

	Link.base = "";

	publish.currentClass = "";
	buildIndexTemplate(globalTemplate.process(globalObjects), publish, globalObjects, "index", "Global Objects and Functions");
	buildIndexTemplate(extensionTemplate.process(extensionObjects), publish, extensionObjects, "extensions", "Prototype Extensions");
	buildIndexTemplate(builtinTemplate.process(builtinObjects), publish, builtinObjects, "builtin", "Built-in Prototypes");

	var methods = {};
	for each(func in hooksObjects){
		var bucket = func.memberOf;
		if(!methods[bucket]){
			methods[bucket] = [func];
		} else{
			methods[bucket].push(func);
		}
	}
	hooksObjects.methods = methods;
    buildIndexTemplate(hooksTemplate.process(hooksObjects), publish, hooksObjects, "hooks", "Hooks", 'hooks_index');

	for (var i = 0, l = objects.length; i < l; i++) {
		var objType = getObjectType(objects[i]);
		var sidebarTemplate = "";
		if(objType == 'global'){
			publish.currentClass = objects[i].alias;
			sidebarTemplate = globalTemplate.process(globalObjects);
		} else if(objType == 'extension'){
			publish.currentClass = objects[i].alias;
			sidebarTemplate = extensionTemplate.process(extensionObjects);
		} else if(objType == 'builtin'){
			publish.currentClass = objects[i].alias;
			sidebarTemplate = builtinTemplate.process(builtinObjects);
		}

		publish.classesIndex = sidebarTemplate;
		var output = classTemplate.process(objects[i]);
		IO.saveFile(publish.conf.outDir, objects[i].alias+publish.conf.ext, output);
	}


}

function getObjectType(objects){
	if(objects.comment.getTag('hook').length > 0){
		return 'hook';
	}
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

function buildIndexTemplate(template, publish, data, filename, title, index_template){
	var indexTemplate = null;
	try {
		indexTemplate = new JSDOC.JsPlate(publish.conf.templatesDir+(index_template || "index")+".tmpl");
	}
	catch(e) {
		print(e.message);
		quit();
	}
	publish.classesIndex = template;
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
	if(str){
		str = str.replace(/\{@link ([^} ]+) ?\}/gi,
				  function(match, symbolName) {
					  return new Link().toSymbol(symbolName);
				  }
		);
	}
	return str;
}
