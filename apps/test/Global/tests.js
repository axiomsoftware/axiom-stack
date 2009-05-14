_test = {
	setup: function() {
		app.log("Global Outer Setup");
	},
	teardown: function() {
		app.log("Global Outer Teardown");
	},
    liveconnect_suite: {
	test_checkJarLoaded: function() {
	    var clazz = null;
	    try {
		clazz = Packages.HelloWorld.HelloWorld;
	    } catch (e) {}
	    Assert.assertNotNull("test_checkJarLoaded", clazz);
	},
	test_checkJarLoaded_getMessage: function() {
	    var str = "Not Hello World";
	    try {
		str = Packages.HelloWorld.HelloWorld.getMessage();
	    } catch (e) {}
	    Assert.assertEquals("test_checkJarLoaded", "Hello World", str);
	}
    },
    configuration_suite: {
	test_dynamicallyAddRewriteRule: function() {
	    app.addRewriteRule('/blah', '/home');
	    var rules = app.__app__.getRewriteRules()[0];
	    Assert.assertIterableEquals("test_checkJarLoaded", ['/blah','/home'], rules);
	}
    },
	app_suite: {
		setup: function() {
			app.log("Global app_suite Setup");
			var lph = new LucenePlaceHolder();
			lph.id = 'lph';
			root.add(lph);
			res.commit();
		},
		teardown: function() {
			app.log("Global app_suite Teardown");
			for each(var child in root.getChildren()){
				root.remove(child);
			}
		},
		getPlaceHolder: function(){
 			return app.getObjects('LucenePlaceHolder', {id:'lph'})[0];
		},
		_add_referring_kitchen_sinks: function(num, obj) {
			var lph = this.getPlaceHolder();
			for(var i = 0; i < num; i++){
				var ks = new LuceneKitchenSink();
				lph.add(ks);
				ks.id = "kitchen_sink_" + i;
				ks.title = "kitchen sink " + i;
				ks.ref1 = new Reference(obj);
			}
			res.commit();
		},
		test_getName: function() {
			Assert.assertEquals("test_getName failed", "test", app.getName());
		},
		test_getSources: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			lph.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			lph.add(ks2);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			res.commit();

			var sources = app.getSources(ks2);
			Assert.assertEquals("test_getSources failed", ks1, sources[0]);
		},
		test_getSources_prototype: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			lph.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			lph.add(ks2);
			res.commit();

			var ks3 = new LuceneKitchenSink();
			ks3.title = "kitchen sink 3";
			ks3.id = "kitchen_sink_3";
			lph.add(ks3);
			res.commit();

			ks1.ref1 = new Reference(ks3);
			ks2.ref1 = new Reference(ks3);
			res.commit();

			var sources = app.getSources(ks3, "LuceneKitchenSink");
			Assert.assertEquals("test_getSources_prototype failed", 2, sources.length);
		},
		test_getSources_filter: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			lph.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			lph.add(ks2);
			res.commit();

			var ks3 = new LuceneKitchenSink();
			ks3.title = "kitchen sink 3";
			ks3.id = "kitchen_sink_3";
			lph.add(ks3);
			res.commit();

			ks1.ref1 = new Reference(ks3);
			ks2.ref1 = new Reference(ks3);
			res.commit();

			var sources = app.getSources(ks3, "LuceneKitchenSink", new Filter({title: "kitchen sink 2"}));
			Assert.assertEquals("test_getSources_filter failed", ks2, sources[0]);
		},
		test_getSourceCount: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "target kitchen sink";
			ks.id = "target kitchen sink";
			lph.add(ks);
			res.commit();

			var num = 5;
			this._add_referring_kitchen_sinks(num,ks);

			var count = app.getSourceCount(ks);
			Assert.assertEquals("test_getSourceCount failed", num, count);
		},
		test_getSourceCount_prototype: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "target kitchen sink";
			ks.id = "target kitchen sink";
			lph.add(ks);
			res.commit();

			var num = 5;
			this._add_referring_kitchen_sinks(num,ks);

			var count = app.getSourceCount(ks, "LuceneKitchenSink");
			Assert.assertEquals("test_getSourceCount_prototype failed", num, count);
		},
		test_getSourceCount_filter: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "target kitchen sink";
			ks.id = "target kitchen sink";
			lph.add(ks);
			res.commit();

			var num = 5;
			this._add_referring_kitchen_sinks(num,ks);

			var count = app.getSourceCount(ks, "LuceneKitchenSink", new Filter({title:"kitchen sink 2"}));
			Assert.assertEquals("test_getSourceCount_filter failed", 1, count);
		},
		test_getReferences: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			lph.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			lph.add(ks2);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			res.commit();

			var refs = app.getReferences(ks1,ks2);
			Assert.assertEquals("test_getReferences failed", 2, refs.length); //Because compute3 computes a reference for ref1, there should be two references to ks2
		},
		test_getTargets: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			lph.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			lph.add(ks2);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			res.commit();

			var targets = app.getTargets(ks1);
			Assert.assertEquals("test_getTargets failed", ks2, targets[0]);
		},
		test_getTargets_prototype: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			lph.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			lph.add(ks2);
			res.commit();

			var ks3 = new LuceneKitchenSink();
			ks3.title = "kitchen sink 3";
			ks3.id = "kitchen_sink_3";
			lph.add(ks3);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			ks1.ref2 = new Reference(ks3);
			res.commit();

			var targets = app.getTargets(ks1, "LuceneKitchenSink");
			Assert.assertEquals("test_getTargets_prototype failed", 2, targets.length);
		},
		test_getTargetCount: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			lph.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			lph.add(ks2);
			res.commit();

			var ks3 = new LuceneKitchenSink();
			ks3.title = "kitchen sink 3";
			ks3.id = "kitchen_sink_3";
			lph.add(ks3);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			ks1.ref2 = new Reference(ks3);
			res.commit();

			var count = app.getTargetCount(ks1);
			Assert.assertEquals("test_getTargetCount failed", 3, count); //Should have 3 here because of  a computed reference
		},
		test_getTargetCount_prototype: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			lph.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			lph.add(ks2);
			res.commit();

			var ks3 = new LuceneKitchenSink();
			ks3.title = "kitchen sink 3";
			ks3.id = "kitchen_sink_3";
			lph.add(ks3);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			ks1.ref2 = new Reference(ks3);
			res.commit();

			var count = app.getTargetCount(ks1, "LuceneKitchenSink");
			Assert.assertEquals("test_getTargetCount_prototype failed", 3, count); //Should have 3 here because of a computed reference
		}
	},
	filter_suite: {
		setup: function() {
			app.log("Global filter_suite Setup");
			var lph = new LucenePlaceHolder();
			lph.id = 'lph';
			root.add(lph);
			res.commit();
			lph = this.getPlaceHolder();

			var obj1 = new LuceneKitchenSink();
			obj1.title = "obj1";
			obj1.id = "obj1";
			obj1.tokenized = "dogs cats";
			obj1.untokenized = "dogs cats";
			lph.add(obj1);
			res.commit();

			var obj2 = new LuceneKitchenSink();
			obj2.title = "obj2";
			obj2.id = "obj2";
			obj2.tokenized = "dogs cats mice";
			obj2.untokenized = "dogs cats mice";
			lph.add(obj2);
			res.commit();

			var sourcesobj = new LuceneKitchenSink();
			sourcesobj.title = "sourcesobj";
			sourcesobj.id = "sourcesobj";
			lph.add(sourcesobj);
			res.commit();

			obj1.ref1 = new Reference(sourcesobj);
			obj2.ref1 = new Reference(sourcesobj);
			res.commit();

			var targetsobj = new LuceneKitchenSink();
			targetsobj.title = "targetsobj";
			targetsobj.id = "targetsobj";
			lph.add(targetsobj);
			res.commit();

			targetsobj.ref1 = new Reference(obj1);
			targetsobj.ref2 = new Reference(obj2);
			res.commit();
		},
		teardown: function() {
			app.log("Global filter_suite Teardown");
			for each(var child in root.getChildren()){
				root.remove(child);
			}
		},
		getPlaceHolder: function(){
 			return app.getObjects('LucenePlaceHolder', {id:'lph'})[0];
		},
		test_getObjects_filter_tokenized1: function() {
			var filter = new Filter({tokenized:"dogs cats"});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_filter_tokenized1 failed", 2, results.length);
		},
		test_getObjects_native_filter_tokenized1: function() {
			var filter = new NativeFilter("tokenized:dogs cats");
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_native_filter_tokenized1 failed", 2, results.length);
		},
	    test_getObjects_or_filter_object_tokenized1: function() {
			var filter = new OrFilter({tokenized:"dogs cats"});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_or_filter_object_tokenized1 failed", 2, results.length);
	    },
	    test_getObjects_or_filter_array_tokenized1: function() {
		var filter = new OrFilter([{tokenized:"dogs"}, {tokenized:'cats'}]);
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_or_filter_array_tokenized1 failed", 2, results.length);
	    },
	    test_getObjects_and_filter_object_tokenized1: function() {
			var filter = new AndFilter({tokenized:"dogs cats mice"});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_and_filter_object_tokenized1 failed", 1, results.length);
	    },
	    test_getObjects_and_filter_array_tokenized1: function() {
		var filter = new AndFilter([{tokenized:"dogs"}, {tokenized:'cats'}, {tokenized: 'mice'}]);
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_and_filter_array_tokenized1 failed", 1, results.length);
	    },
	    test_getObjects_or_filter_no_params: function() {
			var filter = new OrFilter({});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_or_filter_no_params failed", 4, results.length);
	    },
	    test_getObjects_and_filter_no_params: function() {
		var filter = new AndFilter({});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_and_filter_no_params failed", 4, results.length);
	    },
		test_getObjects_filter_untokenized1: function() {
			var filter = new Filter({untokenized:"dogs cats"});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_filter_untokenized1 failed", 1, results.length);
		},
		test_getObjects_filter_tokenized2: function() {
			var filter = new Filter({tokenized:"dogs cats mice"});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_filter_tokenized2 failed", 1, results.length);
		},
		test_getObjects_native_filter_tokenized2: function() {
			var filter = new NativeFilter("tokenized:dogs cats mice");
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_native_filter_tokenized2 failed", 2, results.length);
		},
		test_getObjects_filter_untokenized2: function() {
			var filter = new Filter({untokenized:"dogs cats mice"});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("test_getObjects_filter_untokenized2 failed", 1, results.length);
		},
		test_getSources_filter_tokenized1: function() {
			var filter = new Filter({tokenized:"dogs cats"});
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSources_filter_tokenized1 failed", 2, results.length);
		},
		test_getSources_native_filter_tokenized1: function() {
			var filter = new NativeFilter("tokenized:dogs cats");
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSources_native_filter_tokenized1 failed", 2, results.length);
		},
		test_getSources_filter_untokenized1: function() {
			var filter = new Filter({untokenized:"dogs cats"});
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSources_filter_untokenized1 failed", 1, results.length);
		},
		test_getSources_filter_tokenized2: function() {
			var filter = new Filter({tokenized:"dogs cats mice"});
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSources_filter_tokenized2 failed", 1, results.length);
		},
		test_getSources_native_filter_tokenized2: function() {
			var filter = new NativeFilter("tokenized:dogs cats mice");
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSources_native_filter_tokenized2 failed", 2, results.length);
		},
		test_getSources_filter_untokenized2: function() {
			var filter = new Filter({untokenized:"dogs cats mice"});
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSources_filter_untokenized2 failed", 1, results.length);
		},
		test_getSourceCount_filter_tokenized1: function() {
			var filter = new Filter({tokenized:"dogs cats"});
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSourceCount_filter_tokenized1 failed", 2, results);
		},
		test_getSourceCount_native_filter_tokenized1: function() {
			var filter = new NativeFilter("tokenized:dogs cats");
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSourceCount_native_filter_tokenized1 failed", 2, results);
		},
		test_getSourceCount_filter_untokenized1: function() {
			var filter = new Filter({untokenized:"dogs cats"});
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSourceCount_filter_untokenized1 failed", 1, results);
		},
		test_getSourceCount_filter_tokenized2: function() {
			var filter = new Filter({tokenized:"dogs cats mice"});
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSourceCount_filter_tokenized2 failed", 1, results);
		},
		test_getSourceCount_native_filter_tokenized2: function() {
			var filter = new NativeFilter("tokenized:dogs cats mice");
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSourceCount_native_filter_tokenized2 failed", 2, results);
		},
		test_getSourceCount_filter_untokenized2: function() {
			var filter = new Filter({untokenized:"dogs cats mice"});
			var lph = this.getPlaceHolder();
			var sources = lph.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getSourceCount_filter_untokenized2 failed", 1, results);
		},
		test_getTargets_filter_tokenized1: function() {
			var filter = new Filter({tokenized:"dogs cats"});
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargets_filter_tokenized1 failed", 2, results.length);
		},
		test_getTargets_native_filter_tokenized1: function() {
			var filter = new NativeFilter("tokenized:dogs cats");
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargets_native_filter_tokenized1 failed", 2, results.length);
		},
		test_getTargets_filter_untokenized1: function() {
			var filter = new Filter({untokenized:"dogs cats"});
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargets_filter_untokenized1 failed", 1, results.length);
		},
		test_getTargets_filter_tokenized2: function() {
			var filter = new Filter({tokenized:"dogs cats mice"});
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargets_filter_tokenized2 failed", 1, results.length);
		},
		test_getTargets_native_filter_tokenized2: function() {
			var filter = new NativeFilter("tokenized:dogs cats mice");
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargets_native_filter_tokenized2 failed", 2, results.length);
		},
		test_getTargets_filter_untokenized2: function() {
			var filter = new Filter({untokenized:"dogs cats mice"});
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargets_filter_untokenized2 failed", 1, results.length);
		},
		test_getTargetCount_filter_tokenized1: function() {
			var filter = new Filter({tokenized:"dogs cats"});
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargetCount_filter_tokenized1 failed", 3, results); //Results should be 3 because of computed references.
		},
		test_getTargetCount_native_filter_tokenized1: function() {
			var filter = new NativeFilter("tokenized:dogs cats");
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargetCount_native_filter_tokenized1 failed", 3, results);
		},
		test_getTargetCount_filter_untokenized1: function() {
			var filter = new Filter({untokenized:"dogs cats"});
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargetCount_filter_untokenized1 failed", 2, results); //Results should be 2 because of computed references.
		},
		test_getTargetCount_filter_tokenized2: function() {
			// XXX
			var filter = new Filter({tokenized:"dogs cats mice"});
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargetCount_filter_tokenized2 failed", 1, results);
		},
		test_getTargetCount_native_filter_tokenized2: function() {
			var filter = new NativeFilter("tokenized:dogs cats mice");
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargetCount_native_filter_tokenized2 failed", 3, results);
		},
		test_getTargetCount_filter_untokenized2: function() {
			var filter = new Filter({untokenized:"dogs cats mice"});
			var lph = this.getPlaceHolder();
			var target = lph.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("test_getTargetCount_filter_untokenized2 failed", 1, results);
		}
	},
    global_objects_suite: {
	setup: function() {
	    app.log("Global Objects Suite Setup");
	},
	teardown: function() {
	    app.log("Global Objects Suite Teardown");
	},
	test_Array_unique: function() {
	    var a = ['a','b','a','c','b','a','d'];

	    var control = ['a','b','c','d'];
	    Assert.assertIterableEquals("test_Array_unique failed", control, a.unique());
	},
	test_Array_remove_index: function() {
	    var a = ['a','b','c'];
	    a.remove(0);
	    Assert.assertEquals("test_Array_remove_index failed", -1, a.indexOf('a'));
	},
	test_Array_remove_index_return: function() {
	    var a = ['a','b','c'];
	    Assert.assertEquals("test_Array_remove_index_return failed", 'a', a.remove(0));
	},
	test_Array_remove_object: function() {
	    var a = ['a','b','c'];
	    a.remove('a');
	    Assert.assertEquals("test_Array_remove_object failed", -1, a.indexOf('a'));
	},
	test_Array_remove_object_return: function() {
	    var a = ['a','b','c'];
	    Assert.assertEquals("test_Array_remove_object_return failed", 'a', a.remove('a'));
	},
	test_Array_contains: function() {
	    var a = ['a','b','c'];
	    Assert.assertTrue("test_Array_contains failed", a.contains('a'));
	},
	test_Array_lastIndexOf: function() {
	    var a = ['a','b','c','a'];
	    Assert.assertIterableEquals("test_Array_lastIndexOf failed", 3, a.lastIndexOf('a'));
	},
	test_Array_indexOf: function() {
	    var a = ['a','b','c','a'];
	    Assert.assertEquals("test_Array_indexOf failed", 1, a.indexOf('b'));
	},
	test_Array_intersection: function() {
	    var a = ['a','b','c','a'];
	    var b = ['q','a','b','e','r'];

	    var control = ['a','b'];
	    Assert.assertIterableEquals("test_Array_intersection failed", control, Array.intersection(a,b));
	},
	test_Array_union: function() {
	    var a = ['a','b','c','a'];
	    var b = ['q','a','b','e','r'];

	    var control = ['a','b','c','q','e','r'];
	    Assert.assertIterableEquals("test_Array_union failed", control, Array.union(a,b));
	},
	test_Array_insert_params: function() {
	    var a = ['a','b','c','d'];
	    a.insert('e', 1);

	    var control = ['a','e','b','c','d'];
	    Assert.assertIterableEquals("test_Array_insert_params failed", control, a);
	},
	test_Array_insert_noindex: function() {
	    var a = ['a','b','c','d'];
	    a.insert('e');

	    var control = ['a','b','c','d','e'];
	    Assert.assertIterableEquals("test_Array_insert_noindex failed", control, a);
	},
	test_Array_insert_noparams: function() {
	    var a = ['a','b','c','a'];
	    var ex = null;
	    try {
		a.insert();
	    } catch (e) {
		ex = e;
	    }
	    Assert.assertNotNull("test_Array_insert_noparams failed", ex);
	}
    }
}
