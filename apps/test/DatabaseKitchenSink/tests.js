this._test = {
	setup: function(){
		app.log('db outer setup');
		var conn = null;
		var ps = null;
		try{
			var path = 'db' + Packages.java.io.File.separator + 'testing_frameworkDB' + Packages.java.io.File.separator + 'testing_framework';
			var url = 'jdbc:h2:file:' + path;
			Packages.java.lang.Class.forName('org.h2.Driver');
			conn = Packages.java.sql.DriverManager.getConnection(url, "sa", "");
			conn.setAutoCommit(true);
			try{
				ps = conn.prepareStatement("CREATE TABLE KITCHENSINK (axid VARCHAR(255) not null, id VARCHAR(255) not null, title VARCHAR(255) not null)");
				ps.execute();
			}
			catch(e){
				app.log(e.toString());
			}
			try{
				ps = conn.prepareStatement("CREATE TABLE LANDFILL (axid VARCHAR(255) not null, id VARCHAR(255) not null)");
				ps.execute();
			}
			catch(e){
				app.log(e.toString());
			}
		} catch(e){
			app.log(e.toString());
		} finally{
			try{
				if(conn != null){
					conn.close();
				}
			} catch(e){	}
			try{
				if(ps != null){
					ps.close();
				}
			} catch(e){ }
		}
	},
	teardown: function(){
		app.log('db outer teardown');
		var c = root.getChildren();
		app.log(c.length);
		if(true){
		var conn = null;
		var ps = null;
		try{
			var path = 'db' + Packages.java.io.File.separator + 'testing_frameworkDB' + Packages.java.io.File.separator + 'testing_framework';
			var url = 'jdbc:h2:file:' + path;
			Packages.java.lang.Class.forName('org.h2.Driver');
			conn = Packages.java.sql.DriverManager.getConnection(url, "sa", "");
			try{
				ps = conn.prepareStatement("DROP TABLE KITCHENSINK");
				ps.execute();
			}
			catch(e){
				app.log(e.toString());
			}
			try{
				ps = conn.prepareStatement("DROP TABLE LANDFILL");
				ps.execute();
			}
			catch(e){
				app.log(e.toString());
			}
		} catch(e){
			app.log(e.toString());
		} finally{
			try{
				if(conn != null){
					conn.close();
				}
			} catch(e){	}
			try{
				if(ps != null){
					ps.close();
				}
			} catch(e){ }
		}
		}
	},
	app_getObjects_suite: {
		setup: function(){
			app.log('database setup');
			var dph = new DatabasePlaceHolder();
			dph.id = 'dph';
			root.add(dph);
			res.commit();
		},
		teardown: function(){
			app.log('database teardown');
			var dbks = app.getObjects('DatabaseKitchenSink');
			for(var i = 0; i < dbks.length; i++){
				dbks[i].del();
			}
			var dbks = app.getObjects('DatabaseLandFill');
			for(var i = 0; i < dbks.length; i++){
				dbks[i].del();
			}
		},
		getPlaceHolder: function(){
 			return app.getObjects('DatabasePlaceHolder', {id:'dph'})[0]; 
		},
		test_getObjects_one_proto: function(){
			var index = 10;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			Assert.assertEquals('test_getObjects_one_proto failed', app.getObjects('DatabaseKitchenSink').length, index); 
		},
		test_getObjects_two_protos: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			_add_landfills(this.getPlaceHolder(), 'DatabaseLandFill', index);
			Assert.assertEquals('test_getObjects_two_protos failed', app.getObjects(['DatabaseKitchenSink', 'DatabaseLandFill']).length, (index * 2));
		},
		test_getObjects_bad_protos: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			Assert.assertEquals('test_getObjects_bad_protos failed', app.getObjects('SomeBadProto').length, 0);
		},
		test_getObjects_empty_filter: function(){
			var index = 10;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			Assert.assertEquals('test_getObjects_empty_filter failed', app.getObjects(['DatabaseKitchenSink'], {}).length, index);
		},
		test_getObjects_one_filter: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			Assert.assertEquals('test_getObjects_one_filter failed', app.getObjects(['DatabaseKitchenSink'], {id:'ks1'}).length, 1);
		},
		test_getObjects_two_filters: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			Assert.assertEquals('test_getObjects_two_filters failed', app.getObjects(['DatabaseKitchenSink'], {id:'ks1', title:'title 1'}).length, 1);
		},
		test_getObjects_no_result_filter: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			Assert.assertEquals('test_getObjects_no_result_filter failed', app.getObjects(['DatabaseKitchenSink'], {id:'nothere'}).length, 0);
		},
		test_getObjects_empty_filter_params: function(){
			var index = 10;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			Assert.assertEquals('test_getObjects_empty_filter_params failed', app.getObjects(['DatabaseKitchenSink'], {}, {}).length, index);
		},
		test_getObjects_max_length: function(){
			var index = 10;
			var length = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			Assert.assertEquals('test_getObjects_max_length failed', app.getObjects(['DatabaseKitchenSink'], {}, {maxlength:length}).length, length);
		},
		test_getObjects_max_length_greater_than_total_objs: function(){
			var index = 3;
			var length = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			Assert.assertEquals('test_getObjects_max_length_greater_than_total_objs failed', app.getObjects(['DatabaseKitchenSink'], {}, {maxlength:length}).length, index);
		},
		test_getObjects_sort_object_asc: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			var sort = new Sort({'id':'asc'});
			var objects = app.getObjects(['DatabaseKitchenSink'], {}, {sort:sort});
			Assert.assertTrue('test_getObjects_sort_object_asc failed', 
				objects[0].id == 'ks0' && objects[1].id == 'ks1' && objects[2].id == 'ks2' && objects[3].id == 'ks3' && objects[4].id == 'ks4');
		},
		test_getObjects_sort_object_desc: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			var sort = new Sort({'id':'desc'});
			var objects = app.getObjects(['DatabaseKitchenSink'], {}, {sort:sort});
			Assert.assertTrue('test_getObjects_sort_object_desc failed', 
				objects[0].id == 'ks4' && objects[1].id == 'ks3' && objects[2].id == 'ks2' && objects[3].id == 'ks1' && objects[4].id == 'ks0');
		},
		test_getObjects_max_length_sort_object_asc: function(){
			var index = 10;
			var maxlength = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			var sort = new Sort({'id':'asc'});
			var objects = app.getObjects(['DatabaseKitchenSink'], {}, {sort:sort, maxlength:maxlength});
			Assert.assertTrue('test_getObjects_max_length_sort_object_asc failed', 
				objects[0].id == 'ks0' && objects[1].id == 'ks1' && objects[2].id == 'ks2' && objects[3].id == 'ks3' && objects[4].id == 'ks4' &&
				objects.length == maxlength);
		},
		test_getObjects_max_length_sort_object_desc: function(){
			var index = 5;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			var sort = new Sort({'id':'desc'});
			var objects = app.getObjects(['DatabaseKitchenSink'], {}, {sort:sort});
			Assert.assertTrue('test_getObjects_max_length_sort_object_desc failed', 
				objects[0].id == 'ks4' && objects[1].id == 'ks3' && objects[2].id == 'ks2' && objects[3].id == 'ks1' && objects[4].id == 'ks0');
		},
		test_getObjects_by_integer_id: function() {
			var index = 10;
			_add_kitchen_sinks(this.getPlaceHolder(), 'DatabaseKitchenSink', index);
			var id = app.getObjects(['DatabaseKitchenSink'])[0]._id;
			var objects = app.getObjects(['DatabaseKitchenSink'], {_id:parseInt(id)});
			Assert.assertEquals('test_getObjects_by_integer_id failed', 1, objects.length);
	    }
	}
}
/*
	}
}
*/