this._test = {
	setup: function(){
		app.log('LuceneKitchSink tests outer setup');
	},
	teardown: function(){
		app.log('LuceneKitchSink test outer teardown');
	},
	app_getObjects_suite: {
		setup: function(){
			app.log('app_getObjects_suite setup');
			var lph = new LucenePlaceHolder();
			lph.id = 'lph';
			root.add(lph);
			res.commit();
		},
		teardown: function(){
			app.log('app_getObjects_suite teardown');
			for each(var child in root.getChildren()){
				root.remove(child)
			}
		},
		getPlaceHolder: function(){
 			return app.getObjects('LucenePlaceHolder', {id:'lph'})[0];
		},
		test_getObjects_no_params: function(){
			var index = 10;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			Assert.assertEquals('test_getObjects_no_params failed', app.getObjects().length, index + 2); // +1 for Root & LucenePlaceHolder objects
		},
		test_getObjects_empty_array: function(){
			var index = 10;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			Assert.assertEquals('test_getObjects_empty_array failed', app.getObjects([]).length, index + 2); // +1 for Root object
		},
		test_getObjects_one_proto: function(){
			var index = 10;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			Assert.assertEquals('test_getObjects_one_proto failed', app.getObjects('LuceneKitchenSink').length, index);
		},
		test_getObjects_two_protos: function(){
			var index = 5;
			var parent = this.getPlaceHolder();
			_add_kitchen_sinks(parent, 'LuceneKitchenSink', index);
			_add_landfills(parent, 'LuceneLandFill', index);
			Assert.assertEquals('test_getObjects_two_protos failed', app.getObjects(['LuceneKitchenSink', 'LuceneLandFill']).length , (index * 2));
		},
		test_getObjects_bad_protos: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			Assert.assertEquals('test_getObjects_bad_protos failed', app.getObjects('SomeBadProto').length, 0);
		},
		test_getObjects_empty_filter: function(){
			var index = 10;
			_add_kitchen_sinks(this.getPlaceHolder(),'LuceneKitchenSink', index);
			Assert.assertEquals('test_getObjects_empty_filter failed', app.getObjects(['LuceneKitchenSink'], {}).length, index);
		},
		test_getObjects_one_filter: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			Assert.assertEquals('test_getObjects_one_filter failed', app.getObjects(['LuceneKitchenSink'], {id:'ks1'}).length, 1);
		},
		test_getObjects_two_filters: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			Assert.assertEquals('test_getObjects_two_filters failed', app.getObjects(['LuceneKitchenSink'], {id:'ks1', title:'title 1'}).length, 1);
		},
		test_getObjects_no_result_filter: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			Assert.assertEquals('test_getObjects_no_result_filter failed', app.getObjects(['LuceneKitchenSink'], {id:'nothere'}).length, 0);
		},
		test_getObjects_empty_filter_params: function(){
			var index = 10;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			Assert.assertEquals('test_getObjects_empty_filter_params failed', app.getObjects(['LuceneKitchenSink'], {}, {}).length, index);
		},
		test_getObjects_max_length: function(){
			var index = 10;
			var length = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			Assert.assertEquals('test_getObjects_max_length failed', app.getObjects(['LuceneKitchenSink'], {}, {maxlength:length}).length, length);
		},
		test_getObjects_max_length_greater_than_total_objs: function(){
			var index = 3;
			var length = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			Assert.assertEquals('test_getObjects_max_length_greater_than_total_objs failed', app.getObjects(['LuceneKitchenSink'], {}, {maxlength:length}).length, index);
		},
		test_getObjects_sort_object_asc: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			var sort = new Sort({'id':'asc'});
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {sort:sort});
			Assert.assertTrue('test_getObjects_sort_object_asc failed',
				objects[0].id == 'ks0' && objects[1].id == 'ks1' && objects[2].id == 'ks2' && objects[3].id == 'ks3' && objects[4].id == 'ks4');
		},
		test_getObjects_sort_object_desc: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			var sort = new Sort({'id':'desc'});
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {sort:sort});
			Assert.assertTrue('test_getObjects_sort_object_desc failed',
				objects[0].id == 'ks4' && objects[1].id == 'ks3' && objects[2].id == 'ks2' && objects[3].id == 'ks1' && objects[4].id == 'ks0');
		},
		test_getObjects_max_length_sort_object_asc: function(){
			var index = 10;
			var maxlength = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			var sort = new Sort({'id':'asc'});
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {sort:sort, maxlength:maxlength});
			Assert.assertTrue('test_getObjects_max_length_sort_object_asc failed',
				objects[0].id == 'ks0' && objects[1].id == 'ks1' && objects[2].id == 'ks2' && objects[3].id == 'ks3' && objects[4].id == 'ks4' &&
				objects.length == maxlength);
		},
		test_getObjects_max_length_sort_object_desc: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			var sort = new Sort({'id':'desc'});
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {sort:sort});
			Assert.assertTrue('test_getObjects_max_length_sort_object_desc failed',
				objects[0].id == 'ks4' && objects[1].id == 'ks3' && objects[2].id == 'ks2' && objects[3].id == 'ks1' && objects[4].id == 'ks0');
		},
		test_getObjects_by_path: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {path:'/'});
			Assert.assertEquals('app.getObjects(["LuceneKitchenSink"], {}, {sort:sort}) failed', objects.length, index);
		},
		test_getObjects_by_bad_path: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {path:'/badpath'});
			Assert.assertEquals('app.getObjects(["LuceneKitchenSink"], {}, {sort:sort}) failed', objects.length, 0);
		},
		test_getObjects_maxlength_path_sort_asc: function(){
			var index = 10;
			var sort = new Sort({'id':'asc'});
			var maxlength = 5;
			var path = '/';
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {path:path, maxlength:maxlength, sort:sort});
			Assert.assertTrue('test_getObjects_maxlength_path_sort_asc failed',
				objects[0].id == 'ks0' && objects[1].id == 'ks1' && objects[2].id == 'ks2' && objects[3].id == 'ks3' && objects[4].id == 'ks4' &&
				objects.length == maxlength);

		},
		test_getObjects_maxlength_path_sort_desc: function(){
			var index = 10;
			var sort = new Sort({'id':'desc'});
			var maxlength = 5;
			var path = '/';
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			var objects = app.getObjects(['LuceneKitchenSink'], {}, {path:path, maxlength:maxlength, sort:sort});
			Assert.assertTrue('test_getObjects_maxlength_path_sort_desc failed',
				objects[0].id == 'ks9' && objects[1].id == 'ks8' && objects[2].id == 'ks7' && objects[3].id == 'ks6' && objects[4].id == 'ks5' &&
				objects.length == maxlength);

		},
		test_getObjects_filter_path_sort_asc: function(){
			var index = 10;
			var sort = new Sort({'id':'asc'});
			var path = '/';
			var filter = {id:'ks5'};
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			var objects = app.getObjects(['LuceneKitchenSink'], filter, {path:path, sort:sort});
			Assert.assertEquals('test_getObjects_filter_path_sort_asc failed', objects.length, 1);
		},
		test_getObjects_by_integer_id: function() {
			var index = 10;
			_add_kitchen_sinks(this.getPlaceHolder(), 'LuceneKitchenSink', index);
			var id = app.getObjects(['LuceneKitchenSink'])[0]._id;
			var objects = app.getObjects(['LuceneKitchenSink'], {_id:parseInt(id)});
			Assert.assertEquals('test_getObjects_by_integer_id failed', 1, objects.length);
	    }
	},
	AxiomObject_suite: {
		setup: function(){
			app.log('AxiomObject_suite setup');
			var lph = new LucenePlaceHolder();
			lph.id = 'lph';
			root.add(lph);
			res.commit();
		},
		teardown: function(){
			app.log('AxiomObject_suite teardown');
			for each(var child in root.getChildren()){
				root.remove(child)
			}
		},
		getPlaceHolder: function(){
 			return app.getObjects('LucenePlaceHolder', {id:'lph'})[0];
		},
		test_AxiomObject_add: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject add Test";
			ks.id = "axiomobject_add_test";
			lph.add(ks);
			res.commit();
			var obj = lph.get("axiomobject_add_test");
			Assert.assertEquals("test_AxiomObject_add failed", ks, obj);
		},
		test_AxiomObject_copy: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject copy Test";
			ks.id = "axiomobject_copy_test";
			lph.add(ks);
			res.commit();
			var copy = ks.copy();
			Assert.assertEquals("test_AxiomObject_copy failed", ks.title, copy.title);
		},
		test_AxiomObject_copy_with_params: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject copy Test";
			ks.id = "axiomobject_copy_test";
			lph.add(ks);
			res.commit();
			var copy = ks.copy("title","new copy");
			Assert.assertNotSame("test_AxiomObject_copy_with_params failed", ks.title, copy.title);
		},
		test_AxiomObject_edit: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject edit Test";
			ks.id = "axiomobject_edit_test";
			lph.add(ks);
			res.commit();
			ks.edit({title:"New Title"});
			Assert.assertEquals("AxiomObject.edit() failed", "New Title", ks.title);
		},
		test_AxiomObject_getAncestor_String: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject getAncestor Test1";
			ks1.id = "axiomobject_getancestor_test1";
			lph.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject getAncestor Test2";
			ks2.id = "axiomobject_getancestor_test2";
			ks1.add(ks2);
			res.commit();
			var ancestor = ks2.getAncestor("LuceneKitchenSink");
			Assert.assertEquals("test_AxiomObject_getAncestor_String failed", ks1, ancestor);
		},
		test_AxiomObject_getAncestor_String_inclusive: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject getAncestor Test1";
			ks1.id = "axiomobject_getancestor_test1";
			lph.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject getAncestor Test2";
			ks2.id = "axiomobject_getancestor_test2";
			ks1.add(ks2);
			res.commit();
			var ancestor = ks2.getAncestor("LuceneKitchenSink",true);
			Assert.assertEquals("test_AxiomObject_getAncestor_String_inclusive failed", ks2, ancestor);
		},
		test_AxiomObject_getAncestor_Array: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject getAncestor Test1";
			ks1.id = "axiomobject_getancestor_test1";
			lph.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject getAncestor Test2";
			ks2.id = "axiomobject_getancestor_test2";
			ks1.add(ks2);
			res.commit();
			var ancestor = ks2.getAncestor(["LuceneKitchenSink"]);
			Assert.assertEquals("test_AxiomObject_getAncestor_Array failed", ks1, ancestor);
		},
		test_AxiomObject_getAncestor_Array_inclusive: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject getAncestor Test1";
			ks1.id = "axiomobject_getancestor_test1";
			lph.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject getAncestor Test2";
			ks2.id = "axiomobject_getancestor_test2";
			ks1.add(ks2);
			res.commit();
			var ancestor = ks2.getAncestor(["LuceneKitchenSink"],true);
			Assert.assertEquals("test_AxiomObject_getAncestor_Array_inclusive failed", ks2, ancestor);
		},
		test_AxiomObject_getById: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject edit Test";
			ks.id = "axiomobject_edit_test";
			lph.add(ks);
			res.commit();
			var _id = ks._id;
			var obj = lph.getById(_id);
			Assert.assertEquals("test_AxiomObject_getById failed", ks, obj);
		},
		test_AxiomObject_computeProperty: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject Compute Property Test";
			ks.id = "axiomobject_compute_property_test";
			lph.add(ks);
			res.commit();
			Assert.assertNotNull("AxiomObject.compute failed", ks.compute1);
		},
		test_AxiomObject_computeMultipleProperties: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject Compute Multiple Property Test";
			ks.id = "axiomobject_compute_multiple_property_test";
			lph.add(ks);
			res.commit();

			Assert.assertEquals("AxiomObject.compute on multiple properties failed", ks.compute1, ks.compute2);
		},
		test_AxiomObject_computeReference: function() {
			var lph = this.getPlaceHolder();
			var ks0 = new LuceneKitchenSink();
			ks0.title = "AxiomObject Compute Reference Test 0";
			ks0.id = "axiomobject_compute_reference_test_0";
			lph.add(ks0);
			res.commit();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject Compute Reference Test 1";
			ks1.id = "axiomobject_compute_reference_test_1";
			lph.add(ks1);
			ks0.ref1 = new Reference(ks1);
			res.commit();
			Assert.assertTrue("AxiomObject.compute on Reference failed", (ks0.compute3 instanceof Reference));
		},
		//TODO: getChildren() tests
		test_AxiomObject_getChildCount: function() {
			var lph = this.getPlaceHolder();
			var index = 10;
			_add_kitchen_sinks(lph, 'LuceneKitchenSink', index);
			var count = lph.getChildCount();
			Assert.assertEquals("test_AxiomObject_getChildCount failed", 10, count);
		},
		test_AxiomObject_getChildCount_with_params: function() {
			var lph = this.getPlaceHolder();
			var index = 10;
			_add_kitchen_sinks(lph, 'LuceneKitchenSink', index);
			var count = lph.getChildCount(["LuceneKitchenSink"],{id:"ks5"});
			Assert.assertEquals("test_AxiomObject_getChildCount_with_params failed", 1, count);
		},
		test_AxiomObject_getParentPath: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject getParentPath Test1";
			ks1.id = "axiomobject_getparentpath_test1";
			lph.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject getParentPath Test2";
			ks2.id = "axiomobject_getparentpath_test2";
			ks1.add(ks2);
			res.commit();
			var parent_path = ks2.getParentPath();
			Assert.assertEquals("test_AxiomObject_getParentPath", "/lph/axiomobject_getparentpath_test1", parent_path);
		},
		test_AxiomObject_getPath: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject getPath Test";
			ks.id = "axiomobject_getpath_test";
			lph.add(ks);
			res.commit();
			var path = ks.getPath();
			Assert.assertEquals("test_AxiomObject_getPath failed", "/lph/axiomobject_getpath_test", path);
		},
		test_AxiomObject_getPropertyNames: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject getPropertyNames Test";
			ks.id = "axiomobject_getpropertynames_test";
			lph.add(ks);
			res.commit();

			// If you change the schema of LuceneKitchenSink, make sure you update this line (computed properties are ok to leave out if they are not dependent on properties being set in this test):
			var expected = ["creator", "lastmodifiedby", "title", "id"].sort();

			var props = ks.getPropertyNames().sort();
			Assert.assertEquals("test_AxiomObject_getPropertyNames failed", expected.toSource(), props.toSource());
		},
		test_AxiomObject_getSchema: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject getSchema Test";
			ks.id = "axiomobject_getschema_test";
			lph.add(ks);
			res.commit();

			// If you change the schema of LuceneKitchenSink (specifically, the title property), make sure you update this line:
			var title_type = "String";

			var schema = ks.getSchema();
			Assert.assertEquals("test_AxiomObject_getSchema failed", title_type, ks.getSchema().title.type.value);
		},
		test_AxiomObject_getTypePropertyValue: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject getTypePropertyValue Test";
			ks.id = "axiomobject_gettypepropertyvalue_test";
			lph.add(ks);
			res.commit();

			// If you change the schema of LuceneKitchenSink (specifically, the title property), make sure you update this line:
			var title_type = "String";

			Assert.assertEquals("test_AxiomObject_getTypePropertyValue", title_type, ks.getTypePropertyValue("title.type"));
		},
		test_AxiomObject_getURI: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject getURI Test";
			ks.id = "axiomobject_geturi_test";
			lph.add(ks);
			res.commit();

			Assert.assertEquals("test_AxiomObject_getURI", "/test/lph/axiomobject_geturi_test", ks.getURI());
		},
		test_AxiomObject_hasChildren: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject hasChildren Test1";
			ks1.id = "axiomobject_haschildren_test1";
			lph.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject hasChildren Test2";
			ks2.id = "axiomobject_haschildren_test2";
			ks1.add(ks2);
			res.commit();

			Assert.assertTrue("test_AxiomObject_hasChildren failed", ks1.hasChildren());
		},
		test_AxiomObject_href: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject href Test";
			ks.id = "axiomobject_href_test";
			lph.add(ks);
			res.commit();

			Assert.assertEquals("test_AxiomObject_href failed", "/test/lph/axiomobject_href_test/", ks.href());
		},
		test_AxiomObject_isChild: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject isChild Test1";
			ks1.id = "axiomobject_ischild_test1";
			lph.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject isChild Test2";
			ks2.id = "axiomobject_ischild_test2";
			ks1.add(ks2);
			res.commit();

			Assert.assertTrue("test_AxiomObject_isChild failed", ks1.isChild(ks2));
		},
		test_AxiomObject_hasChildren: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject hasChildren Test1";
			ks1.id = "axiomobject_haschildren_test1";
			lph.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject hasChildren Test2";
			ks2.id = "axiomobject_haschildren_test2";
			ks1.add(ks2);
			res.commit();

			Assert.assertTrue("test_AxiomObject_hasChildren failed", ks1.hasChildren());
		},
		test_AxiomObject_href: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject href Test";
			ks.id = "axiomobject_href_test";
			lph.add(ks);
			res.commit();

			Assert.assertEquals("test_AxiomObject_href failed", "/test/lph/axiomobject_href_test/", ks.href());
		},
		test_AxiomObject_isChild: function() {
			var lph = this.getPlaceHolder();
			var ks1 = new LuceneKitchenSink();
			ks1.title = "AxiomObject isChild Test1";
			ks1.id = "axiomobject_ischild_test1";
			lph.add(ks1);
			res.commit();
			var ks2 = new LuceneKitchenSink();
			ks2.title = "AxiomObject isChild Test2";
			ks2.id = "axiomobject_ischild_test2";
			ks1.add(ks2);
			res.commit();

			Assert.assertTrue("test_AxiomObject_isChild failed", ks1.isChild(ks2));
		},
		test_AxiomObject_remove: function() {
			var lph = this.getPlaceHolder();
			var ks = new LuceneKitchenSink();
			ks.title = "AxiomObject remove Test";
			ks.id = "axiomobject_remove_test";
			lph.add(ks);
			res.commit();

			lph.remove(ks);

			var removed = lph.get("axiomobject_remove_test");

			Assert.assertNull("AxiomObject.remove() failed", removed);
		},
		test_AxiomObject_getChildrenWithPrototype: function() {
			var index = 5;
			var lph = this.getPlaceHolder();
			_add_kitchen_sinks(lph, 'LuceneKitchenSink', index);
			var children = lph.getChildren('LuceneKitchenSink');
			Assert.assertEquals("test_AxiomObject_getChildrenWithPrototype failed", 5, children.length);
		}
	},
	Performance_suite: {
		setup: function(){
			app.log('Performance suite setup');
			app.log('app_getObjects_suite setup');
			var lph = new LucenePlaceHolder();
			lph.id = 'lph';
			root.add(lph);
			res.commit();
		},
		teardown: function(){
			app.log('Performance suite teardown');
			for each(var child in root.getChildren()){
				root.remove(child)
			}
		},
		getPlaceHolder: function(){
 			return app.getObjects('LucenePlaceHolder', {id:'lph'})[0];
		},
		test_Performance_1000_objects_insert: function() {
			var lph = this.getPlaceHolder();
			var num = 1000;
			var start = new Date();
			var slowspeed = 150;
			for(var i = 0; i < num; i++){
				var ks = new LuceneKitchenSink();
				ks.id = 'testks' + i;
				ks.title = 'testtitle ' + i;
				lph.add(ks);
			}
			res.commit();
			var now = new Date();
			var persec = num / ((now.getTime()-start.getTime()) / 1000);
//			app.log("Inserted " + num + " objects at a rate of " + persec.toFixed() + " per second");
			Assert.assertTrue("test_Performance_1000_objects_insert failed " + persec.toFixed() + " per second: was "+persec, persec > slowspeed);
		}
	}
}

