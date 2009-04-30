function xhtml_test(){
    axiom.Test.run({xhtml: this.xhtml_tests});
}

this.xhtml_tests = {
    // sanity check
    test_constructor: function(){
	this._assert_same_as_source("<omg>ponies</omg>");
    },
    // test XHTML accepted entities aren't managled
    test_accepted_entities: function(){
	this._assert_same_as_source("<copyright>&copy; 2006 Monkey Business Inc</copyright>");
    },
    test_bare_ampersands: function(){
	this._assert_same_as_source("<omg>ponies & rainbows</omg>");
    },
    test_bare_ampersands_in_text: function(){
	var xhtml = new XHTML("<omg>ponies & rainbows</omg>");
	Assert.assertEquals("text() did not preserve escaping", "ponies & rainbows", xhtml.text());
    },
    test_bare_amperands_in_child_queries: function(){
	var xhtml = new XHTML("<outer><junk/><inner>this & that</inner></outer>");
	Assert.assertEquals("subquery did not preserve entity escaping", "<inner>this & that</inner>", xhtml.inner.toXMLString());
    },
    test_bare_amperands_in_descendent_queries: function(){
	var xhtml = new XHTML("<outer><junk/><wrap><inner>this & that</inner></wrap></outer>");
	Assert.assertEquals("subquery did not preserve entity escaping", "<inner>this & that</inner>", xhtml..inner.toXMLString());
    },
    // leading stuff that asplodes XMLList
    test_leading_doctype: function(){
	var xhtml = new XHTML('<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" '
			      + '"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">'
			      + '<html>blah blah blah</html>');
	Assert.assertEquals("leading doctype was not stripped", "<html>blah blah blah</html>", xhtml.toXMLString());
    },
    test_leading_xml_declaration: function(){
        var xhtml = new XHTML('<?xml version="1.0" encoding="|ISO-8859-1|"?>'
			      + '<that>blah</that>');
	Assert.assertEquals("leading xml declaration was not stripped", "<that>blah</that>", xhtml.toXMLString());
    },
    test_leading_whitespace: function(){
        var xhtml = new XHTML(' <that>blah</that>');
	Assert.assertEquals("leading whitespace was not stripped", "<that>blah</that>", xhtml.toXMLString());
    },
    // helper methods
    _assert_same_as_source: function (str){
	var xhtml = new XHTML(str);
	Assert.assertEquals("input and serialized xhtml not the same", str, xhtml.toXMLString());
    }
};