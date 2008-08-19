this._test = {
	setup: function(){
		onStart();
	},
	teardown: function(){
		app.getObjects(["HomePage", "Post", "Category"],{}).map(function(i){i._parent.remove(i)});
	},
	_get_home: function(){
		return app.getHits('HomePage', {}).objects(0,1)[0];
	},
	test_save_entry: function(){
		// save_entry a new entry and make it sure it can be found via the query api
		var homepage = this._get_home();
		homepage.save_entry({title: 'hello', body: "Hiya, World!"});
		res.commit();
		var hits = app.getHits("Post", {title: 'hello'});
		Assert.assertEquals("Couldn't find new entry.", 1, hits.length);
	},
	test_create_category: function(){
		// test Category creation
		var homepage = this._get_home();
		var title = "Political Rants";
		homepage.create_category(title);
		res.commit();
		Assert.assertEquals("Couldn't find new Category.", 1, app.getHits("Category", {title: title}).length);
		var creation_result = homepage.create_category(title);
		res.commit();
		Assert.assertFalse("Duplicate creation should return false.", creation_result);
		Assert.assertEquals("Should still only be one Category with title ["+title+"].", 
									  1, app.getHits("Category", {title: title}).length);
	},
	test_get_categories: function(){
		var homepage = this._get_home();
		homepage.create_category("politics");
		homepage.create_category("sports");
		homepage.create_category("personal");
		res.commit();
		Assert.assertEquals("Should be a total of three Categories.", 3, homepage.get_categories().length);
		Assert.assertEquals("Couldn't find a named Category.", 1, homepage.get_categories(["sports"]).length);
	},
	test_delete_category: function(){
		var homepage = this._get_home();
		Assert.assertFalse("Should return false if Category not found.", homepage.delete_category("doesn't exist"));
		homepage.create_category("politics");
		res.commit();
		Assert.assertTrue("Should return true on success.", homepage.delete_category("politics"));
		res.commit();
		Assert.assertEquals("Should be no Categories left.", 0, app.getHits("Category", {}).length)
	},
	test_get_date_string: function(){
		// test date formatting function used in day holder ids
		var d = new Date(2007, 2, 13);
		Assert.assertEquals(this._get_home().get_date_string(d), "03-13-2007");
	},
	test_date_obj_creation: function(){
		// be sure only one date container is created for two save_entries on the same date
		var homepage = this._get_home();
		var current_date = new Date();
		var d = homepage.save_entry({title: 'test1', body: "Test-o-rama!"})._parent.id;
		res.commit();

		Assert.assertEquals("Couldn't find newly created date container: "+d, 1, app.getHits("Day", {id: homepage.get_date_string(current_date)}).length);
		homepage.save_entry({title: 'test2', body: "Son of test-o-rama!"});
		res.commit();

		Assert.assertEquals("Only one date container should have been created.", 1, app.getHits("Day",{}).length);
	},
	test_get_posts_excluding_drafts: function(){
		// test only published posts showing up in get posts
 		var homepage = this._get_home();
		var published = homepage.save_entry({title: "Published", body: "Published", published: true});
		var draft = homepage.save_entry({title: "Draft", body: "Draft"});
		res.commit();
		Assert.assertEquals("Only one post should be on homepage", 1, homepage.get_posts().length);
		Assert.assertEquals("Published post should show up in get_posts", "Published", homepage.get_posts().objects(0,1)[0].title);
	},
	test_search: function(){
	 	var homepage = this._get_home();
		var published = homepage.save_entry({title: "A New Technology On The Horizon?", body: "I fear the monkeys will take over soon.", published: true});
		var draft = homepage.save_entry({title: "A Work In Progress", body: "I better not be published yet.  Otherwise, monkeys will devour me."});
		res.commit();

		var horizon_hits = homepage.search("horizon");
		Assert.assertEquals(1, horizon_hits.length);
		Assert.assertEquals("A New Technology On The Horizon?", horizon_hits.objects(0,1)[0].title);
		
		var monkey_hits = homepage.search("monkeys");
		Assert.assertEquals("Searching for 'monkeys' - shouldn't see non-published items in search.", 1, monkey_hits.length);
		Assert.assertEquals("Searching for 'monkeys' - should see only published post.", "A New Technology On The Horizon?", monkey_hits.objects(0,1)[0].title);

		var progress_hits = homepage.search("progress");
		Assert.assertEquals("Shouldn't see non-published items in search.", 0, progress_hits.length);
	}, 
	test_settings: function(){
		var homepage = this._get_home();
		homepage.settings({posts_per_page: 99, bozo_property: 'I only exist in dreams.'});
		res.commit();
		Assert.assertEquals(99, homepage.posts_per_page);
		Assert.assertUndefined(homepage.bozo_property);
	}

}
