_test = {
	setup: function() {
		app.log("Application Config Setup");
	},
	teardown: function() {
		app.log("Application Config Teardown");
	},
	app_settings_suite: {
		setup: function() {
			app.log("app_settings_suite setup");
		},
		teardown: function() {
			app.log("app_settings_suite teardown");
		},
		test_dbdir_property: function() {
			var dbDir = new axiom.SystemFile(app.dbDir);
			var appDbdir = new axiom.SystemFile(app.serverDir + axiom.SystemFile.separator + app.getProperty("dbDir"));
			Assert.assertEquals("test_dbdir_property() failed ", dbDir.getAbsolutePath(), appDbdir.getAbsolutePath());
		},
		test_event_log_property: function(){
			var eventLog = new axiom.SystemFile(app.logDir + axiom.SystemFile.separator + app.getProperty("eventLog") + ".log");
			Assert.assertEquals("test_event_log_property() failed ", eventLog.isFile(), true);
		},
		test_db_blob_dir_property: function() {
			var db_blob_dir = new axiom.SystemFile(app.serverDir + axiom.SystemFile.separator + app.getProperty("db.blob.dir"));
			Assert.assertEquals("test_db_blob_dir_property() failed ", db_blob_dir.isDirectory(), true);
		},
	}
}
