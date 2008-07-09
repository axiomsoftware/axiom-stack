_test = {
	setup: function() {
		app.log("Global Outer Setup");
	},
	teardown: function() {
		app.log("Global Outer Teardown");
	},
	app_suite: {
		setup: function() {
			app.log("Global app_suite Setup");
		},
		teardown: function() {
			app.log("Global app_suite Teardown");
			for each(var child in root.getChildren()){
				root.remove(child)
			}
		},

		_add_referring_kitchen_sinks: function(num, obj) {
			for(var i = 0; i < num; i++){
				var ks = new LuceneKitchenSink();
				root.add(ks);
				ks.id = "kitchen_sink_" + i;
				ks.title = "kitchen sink " + i;
				ks.ref1 = new Reference(obj);
			}
			res.commit();
		},

		test_getName: function() {
			Assert.assertEquals("app.getName() failed", "test", app.getName());
		},

		test_getSources: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			root.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			root.add(ks2);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			res.commit();

			var sources = app.getSources(ks2);
			Assert.assertEquals("app.getSources() failed", ks1, sources[0]);
		},

		test_getSources_prototype: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			root.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			root.add(ks2);
			res.commit();

			var ks3 = new LuceneKitchenSink();
			ks3.title = "kitchen sink 3";
			ks3.id = "kitchen_sink_3";
			root.add(ks3);
			res.commit();

			ks1.ref1 = new Reference(ks3);
			ks2.ref1 = new Reference(ks3);
			res.commit();

			var sources = app.getSources(ks3, "LuceneKitchenSink");
			Assert.assertEquals("app.getSources() (prototype) failed", 2, sources.length);
		},

		test_getSources_filter: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			root.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			root.add(ks2);
			res.commit();

			var ks3 = new LuceneKitchenSink();
			ks3.title = "kitchen sink 3";
			ks3.id = "kitchen_sink_3";
			root.add(ks3);
			res.commit();

			ks1.ref1 = new Reference(ks3);
			ks2.ref1 = new Reference(ks3);
			res.commit();

			var sources = app.getSources(ks3, "LuceneKitchenSink", new Filter({title: "kitchen sink 2"}));
			Assert.assertEquals("app.getSources() (filter) failed", ks2, sources[0]);
		},
		
		test_getSourceCount: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "target kitchen sink";
			ks.id = "target kitchen sink";
			root.add(ks);
			res.commit();
			
			var num = 5;
			this._add_referring_kitchen_sinks(num,ks);

			var count = app.getSourceCount(ks);
			Assert.assertEquals("app.getSourceCount() failed", num, count);
		},

		test_getSourceCount_prototype: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "target kitchen sink";
			ks.id = "target kitchen sink";
			root.add(ks);
			res.commit();
			
			var num = 5;
			this._add_referring_kitchen_sinks(num,ks);

			var count = app.getSourceCount(ks, "LuceneKitchenSink");
			Assert.assertEquals("app.getSourceCount() (prototype) failed", num, count);
		},

		test_getSourceCount_filter: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "target kitchen sink";
			ks.id = "target kitchen sink";
			root.add(ks);
			res.commit();
			
			var num = 5;
			this._add_referring_kitchen_sinks(num,ks);

			var count = app.getSourceCount(ks, "LuceneKitchenSink", new Filter({title:"kitchen sink 2"}));
			Assert.assertEquals("app.getSourceCount() (filter) failed", 1, count);
		},


		test_getReferences: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			root.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			root.add(ks2);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			res.commit();

			var refs = app.getReferences(ks1,ks2);
			Assert.assertEquals("app.getReferences() failed", 1, refs.length);
		},

		test_getTargets: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			root.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			root.add(ks2);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			res.commit();

			var targets = app.getTargets(ks1);
			Assert.assertEquals("app.getTargets() failed", ks2, targets[0]);
		},

		test_getTargets_prototype: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			root.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			root.add(ks2);
			res.commit();

			var ks3 = new LuceneKitchenSink();
			ks3.title = "kitchen sink 3";
			ks3.id = "kitchen_sink_3";
			root.add(ks3);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			ks1.ref2 = new Reference(ks3);
			res.commit();

			var targets = app.getTargets(ks1, "LuceneKitchenSink");
			Assert.assertEquals("app.getTargets() (prototype) failed", 2, targets.length);
		},

		test_getTargetCount: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			root.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			root.add(ks2);
			res.commit();

			var ks3 = new LuceneKitchenSink();
			ks3.title = "kitchen sink 3";
			ks3.id = "kitchen_sink_3";
			root.add(ks3);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			ks1.ref2 = new Reference(ks3);
			res.commit();

			var count = app.getTargetCount(ks1);
			Assert.assertEquals("app.getTargetCount() failed", 2, count);
		},

		test_getTargetCount_prototype: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "kitchen sink 1";
			ks1.id = "kitchen_sink_1";
			root.add(ks1);
			res.commit();

			var ks2 = new LuceneKitchenSink();
			ks2.title = "kitchen sink 2";
			ks2.id = "kitchen_sink_2";
			root.add(ks2);
			res.commit();

			var ks3 = new LuceneKitchenSink();
			ks3.title = "kitchen sink 3";
			ks3.id = "kitchen_sink_3";
			root.add(ks3);
			res.commit();

			ks1.ref1 = new Reference(ks2);
			ks1.ref2 = new Reference(ks3);
			res.commit();

			var count = app.getTargetCount(ks1, "LuceneKitchenSink");
			Assert.assertEquals("app.getTargetCount() (prototype) failed", 2, count);
		}

	},
	filter_suite: {
		setup: function() {
			app.log("Global filter_suite Setup");
			var obj1 = new LuceneKitchenSink();
			obj1.title = "obj1";
			obj1.id = "obj1";
			obj1.tokenized = "dogs cats";
			obj1.untokenized = "dogs cats";
			root.add(obj1);
			res.commit();

			var obj2 = new LuceneKitchenSink();
			obj2.title = "obj2";
			obj2.id = "obj2";
			obj2.tokenized = "dogs cats mice";
			obj2.untokenized = "dogs cats mice";
			root.add(obj2);
			res.commit();

			var sourcesobj = new LuceneKitchenSink();
			sourcesobj.title = "sourcesobj";
			sourcesobj.id = "sourcesobj";
			root.add(sourcesobj);
			res.commit();

			obj1.ref1 = new Reference(sourcesobj);
			obj2.ref1 = new Reference(sourcesobj);
			res.commit();

			var targetsobj = new LuceneKitchenSink();
			targetsobj.title = "targetsobj";
			targetsobj.id = "targetsobj";
			root.add(targetsobj);
			res.commit();

			targetsobj.ref1 = new Reference(obj1);
			targetsobj.ref2 = new Reference(obj2);
			res.commit();
		},
		teardown: function() {
			app.log("Global filter_suite Teardown");
			for each(var child in root.getChildren()){
				root.remove(child)
			}
		},

		test_getObjects_filter_tokenized1: function() {
			var filter = new Filter({tokenized:"dogs cats"});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("Filter Test getObjects (filter, tokenized) 1 failed", 2, results.length);
		},

		test_getObjects_native_filter_tokenized1: function() {
			var filter = new NativeFilter("tokenized:dogs cats");
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("Filter Test getObjects (native filter, tokenized) 1 failed", 2, results.length);
		},

		test_getObjects_filter_untokenized1: function() {
			var filter = new Filter({untokenized:"dogs cats"});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("Filter Test getObjects (filter, untokenized) 1 failed", 1, results.length);
		},

		test_getObjects_filter_tokenized2: function() {
			var filter = new Filter({tokenized:"dogs cats mice"});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("Filter Test getObjects (filter, tokenized) 2 failed", 1, results.length);
		},

		test_getObjects_native_filter_tokenized2: function() {
			var filter = new NativeFilter("tokenized:dogs cats mice");
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("Filter Test getObjects (native filter, tokenized) 2 failed", 2, results.length);
		},

		test_getObjects_filter_untokenized2: function() {
			var filter = new Filter({untokenized:"dogs cats mice"});
			var results = app.getObjects("LuceneKitchenSink", filter);
			Assert.assertEquals("Filter Test getObjects (filter, untokenized) 2 failed", 1, results.length);
		},

		test_getSources_filter_tokenized1: function() {
			var filter = new Filter({tokenized:"dogs cats"});
			var sources = root.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSources (filter, tokenized) 1 failed", 2, results.length);
		},

		test_getSources_native_filter_tokenized1: function() {
			var filter = new NativeFilter("tokenized:dogs cats");
			var sources = root.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSources (native filter, tokenized) 1 failed", 2, results.length);
		},


		test_getSources_filter_untokenized1: function() {
			var filter = new Filter({untokenized:"dogs cats"});
			var sources = root.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSources (filter, untokenized) 1 failed", 1, results.length);
		},

		test_getSources_filter_tokenized2: function() {
			var filter = new Filter({tokenized:"dogs cats mice"});
			var sources = root.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSources (filter, tokenized) 2 failed", 1, results.length);
		},

		test_getSources_native_filter_tokenized2: function() {
			var filter = new NativeFilter("tokenized:dogs cats mice");
			var sources = root.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSources (native filter, tokenized) 2 failed", 2, results.length);
		},

		test_getSources_filter_untokenized2: function() {
			var filter = new Filter({untokenized:"dogs cats mice"});
			var sources = root.get("sourcesobj");
			var results = app.getSources(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSources (filter, untokenized) 2 failed", 1, results.length);
		},

		test_getSourceCount_filter_tokenized1: function() {
			var filter = new Filter({tokenized:"dogs cats"});
			var sources = root.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSourceCount (filter, tokenized) 1 failed", 2, results);
		},


		test_getSourceCount_native_filter_tokenized1: function() {
			var filter = new NativeFilter("tokenized:dogs cats");
			var sources = root.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSourceCount (native filter, tokenized) 1 failed", 2, results);
		},

		test_getSourceCount_filter_untokenized1: function() {
			var filter = new Filter({untokenized:"dogs cats"});
			var sources = root.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSourceCount (filter, untokenized) 1 failed", 1, results);
		},

		test_getSourceCount_filter_tokenized2: function() {
			var filter = new Filter({tokenized:"dogs cats mice"});
			var sources = root.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSourceCount (filter, tokenized) 2 failed", 1, results);
		},

		test_getSourceCount_native_filter_tokenized2: function() {
			var filter = new NativeFilter("tokenized:dogs cats mice");
			var sources = root.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSourceCount (native filter, tokenized) 2 failed", 2, results);
		},

		test_getSourceCount_filter_untokenized2: function() {
			var filter = new Filter({untokenized:"dogs cats mice"});
			var sources = root.get("sourcesobj");
			var results = app.getSourceCount(sources,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getSourceCount (filter, untokenized) 2 failed", 1, results);
		},

		test_getTargets_filter_tokenized1: function() {
			var filter = new Filter({tokenized:"dogs cats"});
			var target = root.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargets (filter, tokenized) 1 failed", 2, results.length);
		},

		test_getTargets_native_filter_tokenized1: function() {
			var filter = new NativeFilter("tokenized:dogs cats");
			var target = root.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargets (native filter, tokenized) 1 failed", 2, results.length);
		},

		test_getTargets_filter_untokenized1: function() {
			var filter = new Filter({untokenized:"dogs cats"});
			var target = root.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargets (filter, untokenized) 1 failed", 1, results.length);
		},

		test_getTargets_filter_tokenized2: function() {
			var filter = new Filter({tokenized:"dogs cats mice"});
			var target = root.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargets (filter, tokenized) 2 failed", 1, results.length);
		},

		test_getTargets_native_filter_tokenized2: function() {
			var filter = new NativeFilter("tokenized:dogs cats mice");
			var target = root.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargets (native filter, tokenized) 2 failed", 2, results.length);
		},

		test_getTargets_filter_untokenized2: function() {
			var filter = new Filter({untokenized:"dogs cats mice"});
			var target = root.get("targetsobj");
			var results = app.getTargets(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargets (filter, untokenized) 2 failed", 1, results.length);
		},

		test_getTargetCount_filter_tokenized1: function() {
			var filter = new Filter({tokenized:"dogs cats"});
			var target = root.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargetCount (filter, tokenized) 1 failed", 2, results);
		},

		test_getTargetCount_native_filter_tokenized1: function() {
			var filter = new NativeFilter("tokenized:dogs cats");
			var target = root.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargetCount (native filter, tokenized) 1 failed", 2, results);
		},

		test_getTargetCount_filter_untokenized1: function() {
			var filter = new Filter({untokenized:"dogs cats"});
			var target = root.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargetCount (filter, untokenized) 1 failed", 1, results);
		},

		test_getTargetCount_filter_tokenized2: function() {
			var filter = new Filter({tokenized:"dogs cats mice"});
			var target = root.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargetCount (filter, tokenized) 2 failed", 1, results);
		},

		test_getTargetCount_native_filter_tokenized2: function() {
			var filter = new NativeFilter("tokenized:dogs cats mice");
			var target = root.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargetCount (native filter, tokenized) 2 failed", 2, results);
		},

		test_getTargetCount_filter_untokenized2: function() {
			var filter = new Filter({untokenized:"dogs cats mice"});
			var target = root.get("targetsobj");
			var results = app.getTargetCount(target,"LuceneKitchenSink",filter);
			Assert.assertEquals("Filter Test getTargetCount (filter, untokenized) 2 failed", 1, results);
		},
	}
}
