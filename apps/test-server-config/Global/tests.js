_test = {
	setup: function() {
		app.log("Server Config Setup");
	},
	teardown: function() {
		app.log("Server Config Teardown");
	},
	server_settings_suite: {
		setup: function() {
			app.log("server_settings_suite setup");
		},
		teardown: function() {
			app.log("server_settings_suite teardown");
		},
		test_dbHome_property: function() {
			var dbHome = new axiom.SystemFile(app.serverDir + axiom.SystemFile.separator + app.getProperty("dbHome"));
			Assert.assertEquals("test_dbHome_property() failed ", dbHome.isDirectory(), true);
		},
		test_appHome_property: function() {
			var appHome = app.serverDir + axiom.SystemFile.separator + app.getProperty("appHome") + axiom.SystemFile.separator + app.name;
			Assert.assertEquals("test_appHome_property() failed ", app.dir, appHome);
		},
		test_logdir_property: function() {
			var logdir = app.serverDir + axiom.SystemFile.separator + app.getProperty("logdir");
			Assert.assertEquals("test_logdir_property() ", app.logDir, logdir);
		},
		test_db_blob_dir_property: function() {
			var db_blob_dir = new axiom.SystemFile(app.serverDir + axiom.SystemFile.separator + app.getProperty("db.blob.dir"));
			Assert.assertEquals("test_db_blob_dir_property() failed ", db_blob_dir.isDirectory(), true);
		},
		test_request_log_property: function(){
			var requestLog = new axiom.SystemFile(app.logDir + axiom.SystemFile.separator + app.getProperty("requestLog") + ".log");
			Assert.assertEquals("test_request_log_property() failed ", requestLog.isFile(), true);
		},
		test_event_log_property: function(){
			var eventLog = new axiom.SystemFile(app.logDir + axiom.SystemFile.separator + app.getProperty("eventLog") + ".log");
			Assert.assertEquals("test_event_log_property() failed ", eventLog.isFile(), true);
		}
	}
}
