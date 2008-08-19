this._test =  {
	setup: function(){
		onStart();
	},
	teardown: function(){
		app.getObjects(["HomePage", "Post", "Category", "Day"],{}).map(function(i){i._parent.remove(i)});
	},
	_get_home: function(){
		return app.getHits('HomePage', {}).objects(0,1)[0];
	},
	test_add_comment: function(){
		var post = this._get_home().save_entry({title:"Test Entry", body: "Test Body", publish: true});
		res.commit();

		post.add_comment({name: 'Mr. Rogers', body: "Howdy, neighbors!", email: "oedipus@colonus.rex"});
		Assert.assertEquals("Should be no approved comments.", 0, post.approved_comments_count());
		Assert.assertEquals("Should be no approved comments.", 0, post.approved_comments().length);
		
	},
}
