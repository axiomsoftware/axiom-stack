function TAL_compose(doc){
    // namespace checking goes here later
    var lines = [];
    TAL_compose._helper(doc, lines, 0);
    var body = "var doc = new XHTML("+doc.toXMLString().toSource()+");";
    lines.push(TAL_compose.close);
    print(body+lines.join(''));
    return new Function('data', body+lines.join(''));
}

TAL_compose._helper = function(doc, lines, id){
    var tal = new Namespace('tal', 'http://axiomstack.com/tale');
    var tal_directive;
    var commands = [];
    if((tal_directive=doc.@tal::attr).length()){
	var terms = eval('({'+tal_directive+'})'); // hm. can't eval here with foo: bar, where bar is a variable ref.  may need AST to crawl...
	for (name in terms) {
	    lines.push("doc..(@id="+id+").@"+name+" = "+TAL_compose.serialize(terms[name])+";");
	}
	delete doc.@tal::attr;
    }
};

TAL_compose.serialize = function(name, scope){
	//.toSource()
}


TAL_compose.close = "doc.removeNamespace(new Namespace('tal', 'http://xml.zope.org/namespaces/tal'));"+
			"return doc;";
