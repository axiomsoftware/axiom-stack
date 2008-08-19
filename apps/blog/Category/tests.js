this._test = {
	setup: function(){
		onStart();
	},
	teardown: function(){
		app.getObjects(["HomePage", "Post", "Category", "Day"],{}).map(function(i){i._parent.remove(i)});
	},
	_get_home: function(){
		return app.getHits('HomePage', {}).objects(0,1)[0];
	},
	test_get_posts: function(){
		var homepage = this._get_home();
		var spam = homepage.create_category("spam");
		var opera = homepage.create_category("opera");
		res.commit();

		var spam_path = spam.getPath();
		var opera_path = opera.getPath();
		homepage.save_entry({title: "Operatic spam is pretty cool.", body: "<h1>SPAM!</h1>", categories: spam_path + "," + opera_path});
		homepage.save_entry({title: "Only lonely spam.", body: "Nothing but spam here, man.", categories: spam_path});
		res.commit();

		Assert.assertEquals("Should be two entries associated with the spam category.", 2, spam.get_posts().length);
		Assert.assertEquals("Should be one entry associated with the opera category.", 1, opera.get_posts().length);
		Assert.assertEquals("Operatic spam is pretty cool.", opera.get_posts()[0].title);
	}
}
