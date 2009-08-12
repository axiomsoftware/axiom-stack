function tal_ng_test(){
    axiom.Test.run({tal_ng: this.tal_ng_tests});
}

function print(str){
    java.lang.System.out.println(str);
}

this.tal_ng_tests = {
    test_attr: function(){
	this._assert_same_result('<x xmlns:tal="http://axiomstack.com/tale" tal:attr="foo: \'bar\'"/>',
				 {},
				 '<x foo="bar"></x>');
    },
    _assert_same_result: function(doc, scope, expected){
	Assert.assertEquals(expected, TAL(new XHTML(doc), scope).toXMLString());
	var tal_ng = TAL_compose(new XHTML(doc));
	print(tal_ng.toSource());
	var rendered = tal_ng(scope).toXMLString();
	Assert.assertEquals(expected, rendered);

    }
};