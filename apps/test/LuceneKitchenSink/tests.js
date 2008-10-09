	this._test = {
	setup: function(){
		app.log('lucene outer setup');
	},
	teardown: function(){
		app.log('lucene outer teardown');
	},
	app_getObjects_suite: {
		setup: function(){
			app.log('lucene setup');
		},
		teardown: function(){
			app.log('lucene teardown');
			for each(var child in root.getChildren()){
				root.remove(child)
			}
		},

		test_getObjects_no_params: function(){
			var index = 10;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			Assert.assertTrue('app.getObjects() failed', app.getObjects().length, index+1); // +1 for Root object
		},
		test_getObjects_empty_array: function(){
			var index = 10;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			Assert.assertEquals('app.getObjects([]) failed', app.getObjects([]).length, index +1); // +1 for Root object
		},
		test_getObjects_one_proto: function(){
			var index = 10;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			Assert.assertEquals('app.getObjects("LuceneKitchenSink") failed', app.getObjects('LuceneKitchenSink').length, index); 
		},
		test_getObjects_two_protos: function(){
			var index = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			_add_landfills('LuceneLandFill', index);
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink", "LuceneLandFill"]) failed', app.getObjects(['LuceneKitchenSink', 'LuceneLandFill']).length == (index * 2));
		},
		test_getObjects_bad_protos: function(){
			var index = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			Assert.assertTrue('app.getObjects("SomeBadProto") failed', app.getObjects('SomeBadProto').length == 0);
		},
		test_getObjects_empty_filter: function(){
			var index = 10;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}) failed', app.getObjects(['LuceneKitchenSink'], {}).length == index);
		},
		test_getObjects_one_filter: function(){
			var index = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			Assert.assertTrue('app.getObjects("LuceneKitchenSink") failed', app.getObjects(['LuceneKitchenSink'], {id:'ks1'}).length == 1);
		},
		test_getObjects_two_filters: function(){
			var index = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {id:"ks1", title:"title 1"}) failed', app.getObjects(['LuceneKitchenSink'], {id:'ks1', title:'title 1'}).length == 1);
		},
		test_getObjects_no_result_filter: function(){
			var index = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {id:"nothere"}) failed', app.getObjects(['LuceneKitchenSink'], {id:'nothere'}).length == 0);
		},
		test_getObjects_empty_filter_params: function(){
			var index = 10;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, {}) failed', app.getObjects(['LuceneKitchenSink'], {}, {}).length == index);
		},
		test_getObjects_max_length: function(){
			var index = 10;
			var length = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, {maxlength:5}) failed', app.getObjects(['LuceneKitchenSink'], {}, {maxlength:length}).length == length);
		},
		test_getObjects_max_length_greater_than_total_objs: function(){
			var index = 3;
			var length = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, {maxlength:3}) failed', app.getObjects(['LuceneKitchenSink'], {}, {maxlength:length}).length == index);
		},
		test_getObjects_sort_object_asc: function(){
			var index = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			var sort = new Sort({'id':'asc'});
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {sort:sort});
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, {sort:sort}) failed', 
				objects[0].id == 'ks0' && objects[1].id == 'ks1' && objects[2].id == 'ks2' && objects[3].id == 'ks3' && objects[4].id == 'ks4');
		},
		test_getObjects_sort_object_desc: function(){
			var index = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			var sort = new Sort({'id':'desc'});
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {sort:sort});
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, {sort:sort}) failed', 
				objects[0].id == 'ks4' && objects[1].id == 'ks3' && objects[2].id == 'ks2' && objects[3].id == 'ks1' && objects[4].id == 'ks0');
		},
		test_getObjects_max_length_sort_object_asc: function(){
			var index = 10;
			var maxlength = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			var sort = new Sort({'id':'asc'});
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {sort:sort, maxlength:maxlength});
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, {sort:sort, maxlength:maxlength}) failed', 
				objects[0].id == 'ks0' && objects[1].id == 'ks1' && objects[2].id == 'ks2' && objects[3].id == 'ks3' && objects[4].id == 'ks4' &&
				objects.length == maxlength);
		},
		test_getObjects_max_length_sort_object_desc: function(){
			var index = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			var sort = new Sort({'id':'desc'});
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {sort:sort});
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, {sort:sort}) failed', 
				objects[0].id == 'ks4' && objects[1].id == 'ks3' && objects[2].id == 'ks2' && objects[3].id == 'ks1' && objects[4].id == 'ks0');
		},
		test_getObjects_by_path: function(){
			var index = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {path:'/'});
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, {sort:sort}) failed', objects.length == index);
		},
		test_getObjects_by_bad_path: function(){
			var index = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {path:'/badpath'});
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, {sort:sort}) failed', objects.length == 0);
		},
		test_getObjects_maxlength_path_sort_asc: function(){
			var index = 10;
			var sort = new Sort({'id':'asc'});
			var maxlength = 5;
			var path = '/';
			_add_kitchen_sinks('LuceneKitchenSink', index);
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {path:path, maxlength:maxlength, sort:sort});
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, {path:path, maxlength:maxlength, sort:sort}) asc failed', 
				objects[0].id == 'ks0' && objects[1].id == 'ks1' && objects[2].id == 'ks2' && objects[3].id == 'ks3' && objects[4].id == 'ks4' &&
				objects.length == maxlength);

		},
		test_getObjects_maxlength_path_sort_desc: function(){
			var index = 10;
			var sort = new Sort({'id':'desc'});
			var maxlength = 5;
			var path = '/';
			_add_kitchen_sinks('LuceneKitchenSink', index);
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {path:path, maxlength:maxlength, sort:sort});
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, {path:path, maxlength:maxlength, sort:sort}) desc failed', 
				objects[0].id == 'ks9' && objects[1].id == 'ks8' && objects[2].id == 'ks7' && objects[3].id == 'ks6' && objects[4].id == 'ks5' &&
				objects.length == maxlength);

		},
		test_getObjects_filter_path_sort_asc: function(){
			var index = 10;
			var sort = new Sort({'id':'asc'});
			var path = '/';
			var filter = {id:'ks5'};
			_add_kitchen_sinks('LuceneKitchenSink', index);
			var objects = app.getObjects(['LuceneKitchenSink'], filter, {path:path, sort:sort});
			Assert.assertTrue('app.getObjects(["LuceneKitchenSink"], {}, filter, {path:path, sort:sort}) failed', 
				objects.length == 1);

		},
		test_getObjects_by_integer_id: function() {
			var index = 10;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			var id = app.getObjects(['LuceneKitchenSink'])[0]._id;
			var objects = app.getObjects(['LuceneKitchenSink'], {_id:parseInt(id)});
			Assert.assertEquals('app.getObjects(["LuceneKitchenSink"],{_id:7}) [getObjects_by_integer_id] failed', 1, objects.length);
	    }
	
	},

	AxiomObject_suite: {
		setup: function(){
			app.log('AxiomObject suite setup');
		},

		teardown: function(){
			app.log('AxiomObject suite teardown');
			for each(var child in root.getChildren()){
				root.remove(child)
			}
		},
		_add_kitchen_sinks: function(index){
			for(var i = 0; i < index; i++){
				var ks = new LuceneKitchenSink();
				ks.id = 'ks' + i;
				ks.title = 'title ' + i;
				root.add(ks);
			}
			res.commit();
		},
		test_AxiomObject_add: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject add Test";
			ks.id = "axiomobject_add_test";
			root.add(ks);
			res.commit();
			var obj = root.get("axiomobject_add_test");
			Assert.assertEquals("AxiomObject.add() failed", ks, obj);
		},

		test_AxiomObject_copy: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject copy Test";
			ks.id = "axiomobject_copy_test";
			root.add(ks);
			res.commit();
			var copy = ks.copy();
			Assert.assertEquals("AxiomObject.copy() failed", ks.title, copy.title);
		},

		test_AxiomObject_copy_with_params: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject copy Test";
			ks.id = "axiomobject_copy_test";
			root.add(ks);
			res.commit();
			var copy = ks.copy("title","new copy");
			Assert.assertNotSame("AxiomObject.copy() (with params) failed", ks.title, copy.title);
		},

		// TODO: More edit tests, including errors
		test_AxiomObject_edit: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject edit Test";
			ks.id = "axiomobject_edit_test";
			root.add(ks);
			res.commit();
			ks.edit({title:"New Title"});
			Assert.assertEquals("AxiomObject.edit() failed", "New Title", ks.title);
		},

		test_AxiomObject_getAncestor: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject getAncestor Test1";
			ks1.id = "axiomobject_getancestor_test1";
			root.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject getAncestor Test2";
			ks2.id = "axiomobject_getancestor_test2";
			ks1.add(ks2);
			res.commit();
			var ancestor = ks2.getAncestor("LuceneKitchenSink");
			Assert.assertEquals("AxiomObject.getAncestor() failed", ks1, ancestor);
		},

		test_AxiomObject_getAncestor_inclusive: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject getAncestor Test1";
			ks1.id = "axiomobject_getancestor_test1";
			root.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject getAncestor Test2";
			ks2.id = "axiomobject_getancestor_test2";
			ks1.add(ks2);
			res.commit();
			var ancestor = ks2.getAncestor("LuceneKitchenSink",true);
			Assert.assertEquals("AxiomObject.getAncestor() (inclusive) failed", ks2, ancestor);
		},

		test_AxiomObject_getById: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject edit Test";
			ks.id = "axiomobject_edit_test";
			root.add(ks);
			res.commit();
			var _id = ks._id;
			var obj = root.getById(_id);
			Assert.assertEquals("AxiomObject.getById() failed", ks, obj);
		},

		//TODO: getChildren() tests

		test_AxiomObject_getChildCount: function() {
			this._add_kitchen_sinks(10);
			var count = root.getChildCount();
			Assert.assertEquals("AxiomObject.getChildCount() failed", 10, count);
		},

/* Commenting this test -- AXSTK-399
		test_AxiomObject_getChildCount_with_params: function() {
			this._add_kitchen_sinks(10);
			app.log(root.getChildren().map(function(obj) { return obj.id }).toSource());
			var count = root.getChildCount(["LuceneKitchenSink"],{id:"ks5"});
			Assert.assertEquals("AxiomObject.getChildCount() (with filter) failed", 1, count);
		},
*/
		test_AxiomObject_getParentPath: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject getParentPath Test1";
			ks1.id = "axiomobject_getparentpath_test1";
			root.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject getParentPath Test2";
			ks2.id = "axiomobject_getparentpath_test2";
			ks1.add(ks2);
			res.commit();
			var parent_path = ks2.getParentPath();
			Assert.assertEquals("AxiomObject.getParentPath() failed", "/axiomobject_getparentpath_test1", parent_path);
		},

		test_AxiomObject_getPath: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject getPath Test";
			ks.id = "axiomobject_getpath_test";
			root.add(ks);
			res.commit();
			var path = ks.getPath();
			Assert.assertEquals("AxiomObject.getParentPath() failed", "/axiomobject_getpath_test", path);
		},

		test_AxiomObject_getPropertyNames: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject getPropertyNames Test";
			ks.id = "axiomobject_getpropertynames_test";
			root.add(ks);
			res.commit();

			// If you change the schema of LuceneKitchenSink, make sure you update this line:
			var expected = ["creator", "lastmodifiedby", "title", "id"].sort();

			var props = ks.getPropertyNames().sort();
			Assert.assertEquals("AxiomObject.getPropertyNames() failed", expected.toSource(), props.toSource());
		},

		test_AxiomObject_getSchema: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject getSchema Test";
			ks.id = "axiomobject_getschema_test";
			root.add(ks);
			res.commit();

			// If you change the schema of LuceneKitchenSink (specifically, the title property), make sure you update this line:
			var title_type = "String";

			var schema = ks.getSchema();
			Assert.assertEquals("AxiomObject.getSchema() failed", title_type, ks.getSchema().title.type.value);
		},

		test_AxiomObject_getTypePropertyValue: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject getTypePropertyValue Test";
			ks.id = "axiomobject_gettypepropertyvalue_test";
			root.add(ks);
			res.commit();

			// If you change the schema of LuceneKitchenSink (specifically, the title property), make sure you update this line:
			var title_type = "String";

			Assert.assertEquals("AxiomObject.getSchema() failed", title_type, ks.getTypePropertyValue("title.type"));
		},

		test_AxiomObject_getURI: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject getURI Test";
			ks.id = "axiomobject_geturi_test";
			root.add(ks);
			res.commit();

			Assert.assertEquals("AxiomObject.getURI() failed", "/test/axiomobject_geturi_test", ks.getURI());
		}, 

		test_AxiomObject_hasChildren: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject hasChildren Test1";
			ks1.id = "axiomobject_haschildren_test1";
			root.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject hasChildren Test2";
			ks2.id = "axiomobject_haschildren_test2";
			ks1.add(ks2);
			res.commit();

			Assert.assertTrue("AxiomObject.hasChildren() failed", ks1.hasChildren());			
		},


		test_AxiomObject_href: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject href Test";
			ks.id = "axiomobject_href_test";
			root.add(ks);
			res.commit();

			Assert.assertEquals("AxiomObject.href() failed", "/test/axiomobject_href_test/", ks.href());
		}, 

		test_AxiomObject_isChild: function() {
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject isChild Test1";
			ks1.id = "axiomobject_ischild_test1";
			root.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject isChild Test2";
			ks2.id = "axiomobject_ischild_test2";
			ks1.add(ks2);
			res.commit();

			Assert.assertTrue("AxiomObject.isChild() failed", ks1.isChild(ks2));			
		},

		test_AxiomObject_remove: function() {
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject remove Test";
			ks.id = "axiomobject_remove_test";
			root.add(ks);
			res.commit();

			root.remove(ks);

			var removed = root.get("axiomobject_remove_test");

			Assert.assertNull("AxiomObject.remove() failed", removed);
		},
		test_AxiomObject_root_getChildrenWithPrototype: function() {
			var index = 5;
			_add_kitchen_sinks('LuceneKitchenSink', index);
			var children = root.getChildren('LuceneKitchenSink');
			Assert.assertEquals("AxiomObject.test_AxiomObject_root_getChildrenWithPrototype() failed", 5, children.length);
		}
	},
	Performance_suite: {
		setup: function(){
			app.log('Performance suite setup');
		},
		teardown: function(){
			app.log('Performance suite teardown');
			for each(var child in root.getChildren()){
				root.remove(child)
			}
		},
		test_Performance_1000_objects_insert: function() {
			app.log('this test');
			var num = 1000;
			var start = new Date();
			var slowspeed = 75;
			for(var i = 0; i < num; i++){
				var ks = new LuceneKitchenSink();
				ks.id = 'testks' + i;
				ks.title = 'testtitle ' + i;
				root.add(ks);
			}
			res.commit();
			var now = new Date();
			var persec = num / ((now.getTime()-start.getTime()) / 1000);
			app.log("Inserted " + num + " objects at a rate of " + persec.toFixed() + " per second");
			Assert.assertTrue("test_Performance_1000_objects_insert failed", persec > slowspeed);			
		}		
	}
}
	