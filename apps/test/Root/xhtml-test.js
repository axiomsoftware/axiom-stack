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
    // test proper singleton closure
    test_singleton_elements: function(){
	this._assert_same_as_source('<input type="textfield"/>');
	this._assert_same_as_source('<img src="http://www.google.com/foo.gif"/>');
	this._assert_same_as_source('<br/>');
	this._assert_same_as_source('<hr/>');
	this._assert_same_as_source('<link href="../over/there.css" rel="css"/>');
	this._assert_same_as_source('<meta content="llamas" type="keywords"/>');
	this._assert_same_as_source('<param value="none"/>');
    },
    test_not_allowed_singletons_elements: function(){
	this._assert_same_as_source('<script type="text/javascript"></script>');
	this._assert_same_as_source('<div class="foo"></div>');
	this._assert_same_as_source('<span></span>');
    },
    // test attributes
    test_direct_attribute_access: function(){
	var xhtml = new XHTML('<div><a href="foo && bar">moo</a></div>');
	Assert.assertEquals('ampersands not preserved in direct attribute access',
			    xhtml.a.@href.toString(),
			    "foo && bar");
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
    test_leading_newline: function(){
        var xhtml = new XHTML('\n<that>blah</that>');
	Assert.assertEquals("leading newline was not stripped", "<that>blah</that>", xhtml.toXMLString());
    },
    // TALE integration tests
    test_tal_content_with_ampersands: function(){
	var xhtml = new XHTML('<bluefish>green eggs & ham</bluefish>');
	var tal = new XHTML('<onefish xmlns:tal="http://axiomstack.com/tale"><twofish tal:content="redfish"/></onefish>');
	tal = TAL(tal, {redfish: xhtml});
	Assert.assertEquals("ampersand not escaped when inserted into TALE",
			    '<bluefish>green eggs & ham</bluefish>',
			    tal..bluefish.toXMLString());
    },
    test_tal_from_file: function(){
	Assert.assertEquals("ampersand in query string from file not preserved",
			    '<div>\n'
			    + '  <a href="suessian?onefish=twofish&redfish=bluefish">The Good Doctor</a>\n'
			    + '</div>',
			    root.xhtml_query_string_test().toXMLString());
    },
    test_tal_from_file_with_entities: function(){
	Assert.assertEquals("html entities not preserved in TALE from file",
			   '<div>&nbsp;</div>',
			   root.xhtml_entities_test().toXMLString());
    },
    // test persistence
    test_persisted_value_via_edit: function(){
	var obj = new LuceneKitchenSink();
	var str = "<foo>bar & baz</foo>";
	root.add(obj);
	obj.id = obj._id;
	obj.edit({xhtml: str});
	res.commit();
	Assert.assertNotNull("xhtml property was not saved", obj.xhtml);
	Assert.assertEquals("xhtml property not the same as passed to edit",
			    str,
			    obj.xhtml.toXMLString());
    },
    // helper methods
    _assert_same_as_source: function (str){
	var xhtml = new XHTML(str);
	Assert.assertEquals("input and serialized xhtml not the same", str, xhtml.toXMLString());
    }
};