/**
 * Display/Form Handler method for Login Screen.
 */
function login() {
	var data = {};
	data.error = null;
	if (req.data.username && req.data.password) {
		data.username = req.data.username;
		data.password = req.data.password;
		var result = this.authenticate(data);
		if (result.success) {
			res.redirect(this.getURI("manage"));
			return;
		} else {
			data.error = result.error;
		}
	}
	return this.manage_login_view(data);
}

/**
 * Display/Form Handler method for the Forgot Password Screen.
 */
function forgotpw() {
	var data = {};
	data.error = null;
	if (req.data.email != null) {
		var user = app.getObjects("User",{email:req.data.email})[0];
		if (user) {
			var random = new Date().valueOf() + user.username;
			user.id = random.md5();
			user.reset_sent = true;
			user.send_reset();
			res.redirect(this.getURI("login?notice=true"));
			return;
		} else {
			data.error = "No user exists with the specified Email Address.";
		}
	}
	return this.manage_forgotpw_view(data);
}

/**
 * Display method for Main Manage Area.
 */
function manage() {
	var data = {};
	data.posts = app.getObjects("Post",{},{sort:{date:'desc'},maxlength:5}).map(function(obj) {
		return {
			_id: obj._id,
			title:obj.title,
			uri:obj.getURI(),
			post_status:obj.published ? "Published" : "Draft",
			comments_count: obj.all_comments_count(),
			comments: obj.all_comments(),
			author:obj.get_author_username()
		}
	});
	data.authors = app.getObjects("User",{},{maxlength:5});
	data.comments = app.getObjects("Comment",{},{sort:{date:'desc'},maxlength:5});
	return this.manage_wrap( { content:this.manage_overview_view(data) } );
}

/**
 * Display method for Post Editing.
 */
function edit_posting() {
	var postid = req.data.postid
	var data = {};
	data.post = null;
	if (postid != null) {
		data.post = app.getObjects("Post",{_id:postid})[0];
	}
	return this.manage_wrap( { content:this.manage_edit_posting_view(data) } );
}

/**
 * Form Handler method for Post Editing/Creation.  Form returned from edit_posting.
 */
function save_posting() {
	var data = {};
	if (req.data.postid) {
		data.entry_id = req.data.postid;
	}

	data.title = req.data.title;
	data.body = req.data.body;
	data.allow_comments =  req.data.allow_comments == 'true' ? true : false;

	if (req.data.cat) {
		if (typeof req.data.cat == "string") {
			var cat = app.getObjects("Category",{_id:req.data.cat})[0];
			data.categories = cat.getPath();
		} else {
			data.categories = req.data.cat.map(function(id){ return app.getObjects("Category",{_id:id})[0].getPath() });
		}
	}
	
	if (req.data.post_type == "Save Draft") {
		data.published = false;
	} else if (req.data.post_type == "Publish") {
		data.published = true;
	}
	var post = this.save_entry(data);

	if (req.data.image) {
		try {
			post.image = new Image(req.data.image);
			post.image.addThumbnail(post.image.render({maxWidth:75,maxHeight:75}),'thumb');
			post.image.addThumbnail(post.image.render({maxWidth:100,maxHeight:75}),'small');
			post.image.addThumbnail(post.image.render({maxWidth:240,maxHeight:180}),'medium');
			post.image.addThumbnail(post.image.render({maxWidth:500,maxHeight:375}),'large');
		} catch(e) {
			app.log("Error Uploading Image: " + e.message);
			post.image = null;
			res.redirect(this.getURI("edit_posting?postid=" + post._id + "&saved_image_error=true"));
			return;
		}
	}

	if (req.data.image_caption && post.image) {
		post.image.caption = req.data.image_caption;
	}

	if (post.image) {	
		post.image.alignment = req.data.image_align;
		post.image.size = req.data.image_size;
	}

	if (req.data.remove_image == "on") {
		post.image = null;
	}

	res.redirect(this.getURI("edit_posting?postid=" + post._id + "&saved=true"));
}


/**
 * Display method for Post Management.
 */
function manage_postings() {
	var url = this.getURI("manage_postings?");
	var filter = {};
	var data = {};
	data.view = "All";
	data.query = null;

	if (req.data.view == "Drafts") {
		data.view = "Drafts";
		url += "view=Drafts";
		filter = new Filter({published:false});
	}
	if (req.data.view == "Published") {
		data.view = "Published";
		url += "view=Published";
		filter = new Filter({published:true});
	}
	if (req.data.query) {
		data.query = req.data.query;
		url += "query=" + req.data.query;
		var query = req.data.query.replace(/"/g,"").replace(/([+\-&!(){}\[\]\'|^~\?:])/g, "\\$1");
		filter = new NativeFilter(query, "post_search");
	}

	var hits = app.getHits("Post",filter,{sort:{date:"desc"}});
	var page = req.data.page != null ? parseInt(req.data.page) : 1;
	var size = 10;
	var start = (page - 1) * size;
	var pages = Math.ceil(hits.length / size);

	data.posts = hits.objects(start,size).map(function(obj) {
		return {
			editable: (session.user.username == obj.get_author_username() || session.user.is_admin()) ? true : false,
			_id: obj._id,
			title: obj.title,
			uri: obj.getURI(),
			published: obj.published,
			cats_count: obj.categories.length,
			cats: obj.get_categories_view(),
			comments_count: obj.all_comments_count(),
			comments: obj.all_comments(),
			post_status: obj.published ? new XMLList("Published<br/>" + obj.date.formatted()) : "Draft",
			author: obj.get_author_username()
		}
	});

	data.start = hits.length == 0 ? 0 : start + 1;
	data.len = start + data.posts.length;
	data.total = hits.length;
	data.pagination = get_manage_pagination(url,page,pages);


	return this.manage_wrap( { content:this.manage_postings_view(data) });
}

/**
 * Form Handler method for Post Deletion.  Form returned from manage_postings.
 */
function delete_posts() {
	if (req.data.post == null) {
		res.redirect(this.getURI("manage_postings?error=true"));
		return;
	}

	if (typeof req.data.post == "string") {
		var post = app.getObjects("Post",{_id:req.data.post})[0];
		post._parent.remove(post);
	} else {
		for (var i in req.data.post) {
			var post = app.getObjects("Post",{_id:req.data.post[i]})[0];
			post._parent.remove(post);
		}
	}
	res.redirect(this.getURI("manage_postings?deleted=true"));
}

/**
 * AJAX Handler for Saving Comments.  Call comes from manage_postings.
 */
function save_comments() {
	var deleteid = req.data.deleteid;
	var approveid = req.data.approveid;

	if (req.data.deleteid) {
		var comment = app.getObjects("Comment",{_id:req.data.deleteid})[0];
		comment._parent.remove(comment);
		var response = {};
		response.success = true;
		return response;
	}

	if (req.data.approveid) {
		var comment = app.getObjects("Comment",{_id:req.data.approveid})[0];
		comment.approved = true;
		var response = {};
		response.success = true;
		return response;
	}
}


/**
 * Display method for Whitelist Management.
 */
function manage_whitelist() {
	var url = this.getURI("manage_whitelist?");
	var filter = {};
	var data = {};

	if (req.data.view == null || req.data.view == "All") {
		data.view = "All";
		var whitelisted = this.openid_whitelist.map(function(obj){return obj});
		var fields = app.getFields("openid_identifier","Comment",{comment_type:"OpenID"},{unique:true});
		var blacklisted = fields.filter(function(obj) {return !get_home_page().openid_whitelist.contains(obj)});
		data.accounts = Array.union(whitelisted, blacklisted);
	}

	if (req.data.view == "Whitelist") {
		data.view = "Whitelist";
		url += "view=Whitelist";
		data.accounts = this.openid_whitelist.map(function(obj){return obj});
	}

	if (req.data.view == "Pending") {
		data.view = "Pending";
		url += "view=Pending";
		var fields = app.getFields("openid_identifier","Comment",{comment_type:"OpenID"},{unique:true});
		data.accounts = fields.filter(function(obj) {return !get_home_page().openid_whitelist.contains(obj)});
	}

	return this.manage_wrap({ content:this.manage_whitelist_view(data) });
}

/**
 * Form Handler method for Comment management.  Form returned from manage_comments.
 */
function save_whitelist() {
	var type = req.data.type;
	var account = req.data.account;

	if (type == "Add") {
		var account = req.data.openid_identity
		if (!this.openid_whitelist.contains(account)) {
			this.openid_whitelist = this.openid_whitelist.concat(new MultiValue(account));
		}

		var comments = app.getObjects("Comment",{comment_type:"OpenID", openid_identifier:account});
		for (var i = 0; i < comments.length; i++) {
			comments[i].approved = true;
		}

		res.redirect(this.getURI("manage_whitelist?added=true"));
		return;
	}

	if (type == "Whitelist Selected Accounts") {
		if (account == null) {
			res.redirect(this.getURI("manage_whitelist?error=true"));
			return;
		}

		if (typeof account == "string") {
			this.whitelist(account);
			res.redirect(this.getURI("manage_whitelist?whitelisted=true"));
			return;
		} else {
			for (var i in account) {
				this.whitelist(account[i]);
			}
			res.redirect(this.getURI("manage_whitelist?whitelisted=true"));
			return;			
		}
	}


	if (type == "Remove Selected Accounts from Whitelist") {
		if (account == null) {
			res.redirect(this.getURI("manage_whitelist?error=true"));
			return;
		}

		if (typeof account == "string") {
			this.remove_from_whitelist(account);
			res.redirect(this.getURI("manage_whitelist?whitelisted=true"));
			return;
		} else {
			for (var i in account) {
				this.remove_from_whitelist(account[i]);
			}
			res.redirect(this.getURI("manage_whitelist?whitelisted=true"));
			return;	
		}
	}
}

/**
 * Convenience method for Whitelist management.
 */
function whitelist(account) {
	if (!this.openid_whitelist.contains(account)) {
		this.openid_whitelist = this.openid_whitelist.concat(new MultiValue(account));
	}

	var comments = app.getObjects("Comment",{comment_type:"OpenID", openid_identifier:account});
	for (var i = 0; i < comments.length; i++) {
			comments[i].approved = true;
	}
	return true;
}


/**
 * Convenience method for Whitelist management.
 */
function remove_from_whitelist(account) {
	if (this.openid_whitelist.contains(account)) {
		var new_mv;
		for (var i = 0; i < this.openid_whitelist.length; i++) {
			if (this.openid_whitelist[i] == account) {
				var new_mv = this.openid_whitelist.splice(i, 1);
			}
		}
		this.openid_whitelist = new_mv;
	}

	var comments = app.getObjects("Comment",{comment_type:"OpenID", openid_identifier:account});
	for (var j = 0; j < comments.length; j++) {
			comments[j].approved = false;
	}

	return true;
}

/**
 * Display method for Category Management.
 */
function manage_categories() {
	var data = {};
	data.categories = app.getObjects("Category",{},{sort:{title:"asc"}});
	return this.manage_wrap({content: this.manage_categories_view(data) });
}

/**
 * Form Handler method for Adding Categories.  Form returned from manage_categories.
 */
function add_category() {
	if (this.create_category(req.data.name)) {
		res.redirect(this.getURI("manage_categories?added=true"));
	} else {
		res.redirect(this.getURI("manage_categories?duplicate=true"));
	}
}

/**
 * Form Handler method for Deleting Categories.  Form returned from manage_categories.
 */
function delete_categories() {
	if (req.data.cat == null) {
		res.redirect(this.getURI("manage_categories?delete_error=true"));
		return;
	}

	if (typeof req.data.cat == "string") {
		var cat = app.getObjects("Category",{_id:req.data.cat})[0];
		cat._parent.remove(cat);
	} else {
		for (var i in req.data.cat) {
			var cat = app.getObjects("Category",{_id:req.data.cat[i]})[0];
			cat._parent.remove(cat);
		}
	}
	res.redirect(this.getURI("manage_categories?deleted=true"));
}


/**
 * Display method for User Management.
 */
function manage_authors() {
	var editid = req.data.editid
	var data = {};
	data.user = null;
	if (editid != null) {
		data.user = app.getObjects("User",{_id:editid})[0];
		var filter = new NotFilter({_id:editid});
		data.invalid_usernames = app.getFields("username","User",filter).toSource();
		data.invalid_emails = app.getFields("email","User",filter).toSource();
	} else {
		data.invalid_usernames = app.getFields("username","User").toSource();
		data.invalid_emails = app.getFields("email","User").toSource();
	}

	data.authors = app.getObjects("User",{},{sort:{username:"asc"}});
	return this.manage_wrap( { content: this.manage_authors_view(data) } );
}

/**
 * Form Handler method for Saving Users.  Form returned from manage_authors.
 */
function save_author() {
	if (req.data.deleteid != null) {
		var user = app.getObjects("User",{_id:req.data.deleteid})[0];
		this.delete_user(user.username);
		res.redirect(this.getURI("manage_authors?deleted=true"));
		return;
	}
	var data = {};
	if (req.data.editid != null) {
		data.user_id = req.data.editid;
		if (req.data.username != null) {
			data.username = req.data.username;
		}
		data.email = req.data.email;
		data.role = req.data.role;
		data.fullname = req.data.fullname;
		if (req.data.password1 != "") {
			data.password = req.data.password1;
		}
		this.edit_user(data);
		res.redirect(this.getURI("manage_authors?saved=true"));
	} else {
		data.username = req.data.username;
		data.email = req.data.email;
		data.role = req.data.role;
		data.fullname = req.data.fullname;
		data.password = req.data.password1;
		data.activated = false;
		var random = new Date().valueOf() + data.username;
		data.id = random.md5();
		var user = this.create_user(data);
		user.send_invite();
		res.redirect(this.getURI("manage_authors?saved=true"));
	}
}


/**
 * Display method for Blog Settings Management.
 */
function manage_settings() {
	return this.manage_wrap({content:this.manage_settings_view()});
}

/**
 * Form Handler method for Saving Settings.  Form returned from manage_settings.
 */
function save_settings() {
	var data = {};
	data.title = req.data.title;
	data.description = req.data.description;
	data.posts_per_page = parseInt(req.data.posts_per_page);
	data.author_email_links = req.data.author_email_links == 'true' ? true : false;
	data.allow_comments = req.data.allow_comments == 'true' ? true : false;
	data.comment_email_links = req.data.comment_email_links == 'true' ? true : false;
	data.comment_notifications = req.data.comment_notifications == 'true' ? true : false;
	this.settings(data);
	res.redirect(this.getURI("manage_settings?saved=true"));
}

/**
 * Convenience Method used to clean XML.  This method is used on WordPress XML files, 
 * stripping potentially bad markup so that an XML Object can be instantiated from the text.
 * The XML Object is needed so that E4X operations can be performed for easy XML parsing.
 * Parameters:
 *   str (String):  String of the WordPress XML File's contents.
 * Returns: String which XML Object can be instantiated from.
 */
function fix_wordpress_xml(str) {
	str = str.replace(/<\?xml[^>]*>/g,"");
	//str = str.replace(/&(?!\w+;)/g, '&amp;');
	var regex = /<(wp:meta_\w+)>.*<\/\1>/g
	var matches = str.match(regex);
	if (matches != null) {
		for (var i = 0; i < matches.length; i++) {
			str = str.replace(matches[i],"");
		}
	}
	return str;
}

/**
 * Form Handler method for Importing WordPress posts.  Form returned from manage_settings.
 */
function import_wordpress() {
	var xml;
	try {
		xml = new XMLList(this.fix_wordpress_xml(req.data.wordpress.getText()));
	} catch(e) {
		app.log("Error parsing XML from wordpress file: " + e.message);
		res.redirect(this.getURI("manage_settings?import_error=true"));
		return;
	}
	var contentns = new Namespace("content","http://purl.org/rss/1.0/modules/content/");
	var wpns = new Namespace("wp","http://wordpress.org/export/1.0/");

	var categories = {};
	// Creates Categories
	for each (category in xml.channel.wpns::category) {
		var title = category.wpns::cat_name.toString();
		var id = category.wpns::category_nicename.toString();
		var cat = this.get(id);
		if (!cat) {
			cat = new Category();
			this.get("category").add(cat);
			cat.title = title;
			cat.id = id;
		}
		categories[title] = cat;
		res.commit();
	}

	// Creates Posts
	for each (item in xml.channel.item) {
		if (item.wpns::post_type == "post") {
			var post_date = new Date(item.pubDate.toString());
			var day_id_str = this.get_date_string(post_date);
			var day_container = this.get(day_id_str);
			if(!day_container) {
				day_container = new Day();
				this.add(day_container);
				day_container.id = this.get_date_string(post_date);
				res.commit();
			}
			var post_data = {};
			post_data.title = item.title.toString();
			var id = post_data.title.toLowerCase().replace(/\s+/g, '-').replace(/[^\w-]/g, '');
			var post = day_container.get(id);
			if(!post) {
				post = new Post();
				day_container.add(post);
				post.id = id;
				res.commit();
			}

			// All properties except for Body are set here.
			post_data.allow_comments = item.wpns::comment_status == "open" ? true : false;
			post_data.published = item.wpns::status == "publish" ? true : false;
			post_data.author = session.user.getPath(); 
			post_data.date = post_date;
			var cats = [];
			for each (category in item.category) {
				if (category.@domain.toString() != "") {
					if (category.@domain.toString() == "category") {
						cats.push(categories[category.toString()].getPath());
					}
				} else {
					cats.push(categories[category.toString()].getPath());
				}
			}
			post_data.categories = cats.join(',');
			post.edit(post_data);
			res.commit();

			// Here, we are using LiveConnect to create an instance of the Tidy HTML Parser.
			// This is used to fix up any bad markup that may exist in the body of a Post.
			var tidy = new Packages.org.w3c.tidy.Tidy();
			tidy.setQuiet(true);
			tidy.setShowWarnings(false);
			tidy.setPrintBodyOnly(true);
			tidy.setNumEntities(true);
			tidy.setXHTML(true);
			var baos = new Packages.java.io.ByteArrayOutputStream();
			var str = new Packages.java.lang.String(item.contentns::encoded.toString());
			var ios = new Packages.java.io.ByteArrayInputStream(str.getBytes("UTF-8"));
			tidy.parse(ios,baos);
			try {
				post.body = new XMLList(baos.toString());
				res.commit();
			} catch(e) {
				app.log("Error setting body for Post - " + post.title + ": " + e.message);
			}
		}

	}
	res.redirect(this.getURI("manage_settings?imported=true"));
}


/**
 * Display method for Blog Template Management.
 */
function manage_templates() {
	return this.manage_wrap({content:this.manage_templates_view()});
}

/**
 * Form Handler method for Saving Template Settings.  Form returned from manage_templates.
 */
function save_templates() {
	this.settings(req.data);
	res.redirect(this.getURI("manage_templates?saved=true"));
}
