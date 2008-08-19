/**
 * Display method for post.
 */
function main() {
	if (!this.published) {
		return this.notfound();
	}

	return this.wrap({ content:this.post_view() });
}

/**
 * Form Handler method for saving user Comments.  Form submitted from main.
 */
function submit_comment() {
	if (!this.allow_comments) {
		return this.notfound();
	}

	if (req.data.comment_type == "OpenID") {
		var identity = req.data.openid_identifier;
		var identity_server;
		var identity_delegation;
		try {
			var openid_info = this.get_openid_info(identity);
			identity_server = openid_info.server;
			identity_delegate = openid_info.delegate;
		} catch(e) {
			res.redirect(this.getURI("?submit_failed=true"));
		}

		var unique_string = session._id + "-" + new Date().valueOf();
		var comment = req.data.comment;
		if (typeof session.data.pending_comments == "undefined") {
			session.data.pending_comments = {};
		}
		session.data.pending_comments[unique_string] = {};
		session.data.pending_comments[unique_string].identity = identity;
		session.data.pending_comments[unique_string].identity_server = identity_server;
		session.data.pending_comments[unique_string].body = comment;

		if (identity_delegate) {
			identity = identity_delegate;
		}
		app.log("ID/Delegate: " + identity);
		
		res.redirect(identity_server + "?openid.mode=checkid_setup&openid.identity=" + escape(identity) + "&openid.return_to=" + escape("http://" + req.data.http_host + this.getURI("submit_comment?comment_id=" + unique_string)));
	}

	if (req.data["openid.mode"] == "id_res") {
		app.log(req.data.toSource());
		var server = session.data.pending_comments[req.data.comment_id].identity_server;
		var identity = session.data.pending_comments[req.data.comment_id].identity;
		var body = session.data.pending_comments[req.data.comment_id].body;

		var signed_params = req.data["openid.signed"].split(",");
		var params = {};
		for (var i = 0; i < signed_params.length; i++) {
			var current_param = signed_params[i];
			params["openid." + current_param] = req.data["openid." + current_param];
		}
		params["openid.mode"] = "check_authentication";
		params["openid.signed"] = req.data["openid.signed"];
		params["openid.assoc_handle"] = req.data["openid.assoc_handle"];
		params["openid.sig"] = req.data["openid.sig"];


		// Curl Command for verification
		var postbody = [];
		for (var p in params) {
			postbody.push(p + "=" + escape(params[p]));
		}
		app.log("curl -d \"" + postbody.join("&") + "\" "+server);
		// End


		try {
			var client = new org.apache.commons.httpclient.HttpClient();
			var method = new org.apache.commons.httpclient.methods.PostMethod(server);
			for (var j in params) {
				method.addParameter(j, params[j]);
			}
			var statusCode = client.executeMethod(method);
			var result = "";
			if (statusCode != -1) {
				result = method.getResponseBodyAsString();
			}
			method.releaseConnection();
		} catch (e) {
			app.log("Error Posting Verification: " + e.message);
			res.redirect(this.getURI("?submit_failed=true"));
			return;
		}

		if (result.match(/is_valid:true/g)) { 
			var whitelisted = get_home_page().openid_whitelist.contains(identity);
			var data = {};
			data.name = null;
			data.email = null;
			data.openid_identifier = identity;
			data.comment_type = "OpenID";
			data.body = new XMLList(body.replace(/\&(?!amp;)/g, "&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/\n/g,"<br/>"));
			data.approved = whitelisted;
			this.add_comment(data);
			delete session.data.pending_comments[req.data.comment_id];

			if (check_setting('comment_notifications',true)) {
				var mailer = new Mail();
				var admin = app.getObjects("User",{username:"admin"})[0];
				var hp = get_home_page();
				mailer.setFrom(admin.email, admin.fullname);
				var author = this.author.getTarget();
				mailer.setSubject("Axiom Blog - Comment Notification");
				mailer.setTo(author.email);
				mailer.addText(author.fullname+",\n\nYour Post,  \"" + this.title + "\" has received a new comment.\n\nPlease visit http://"+req.data.http_host+hp.getURI('manage_postings')+" to Manage Posts.");
				mailer.send();
			}

			if (whitelisted) {
				res.redirect(this.getURI("?submitted_whitelist=true"));
			} else {
				res.redirect(this.getURI("?submitted=true"));
			}
			return;
		} else {
			res.redirect(this.getURI("?submit_failed=true"));
			return;
		}
	}

	if (req.data.comment_type == "Anonymous") {
		var data = {};
		data.name = req.data.name;
		data.email = req.data.email;
		data.openid_identifier = null;
		data.comment_type = req.data.comment_type;
		data.body = new XMLList(req.data.comment.replace(/\&(?!amp;)/g, "&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/\n/g,"<br/>"));
		data.approved = false;
		this.add_comment(data);

		if (check_setting('comment_notifications',true)) {
			var mailer = new Mail();
			var admin = app.getObjects("User",{username:"admin"})[0];
			var hp = get_home_page();
			mailer.setFrom(admin.email, admin.fullname);
			var author = this.author.getTarget();
			mailer.setSubject("Axiom Blog - Comment Notification");
			mailer.setTo(author.email);
			mailer.addText(author.fullname+",\n\nYour Post,  \"" + this.title + "\" has received a new comment.\n\nPlease visit http://"+req.data.http_host+hp.getURI('manage_postings')+" to Manage Posts.");
			mailer.send();
		}
		res.redirect(this.getURI("?submitted=true"));
	}

}

/**
 * Grabs an OpenID Profile and returns the openid.server Address.
 */
function get_openid_info(id) {
	var client = new org.apache.commons.httpclient.HttpClient();
	var method = new org.apache.commons.httpclient.methods.GetMethod(id);
	var statusCode = client.executeMethod(method);
	var result = "";
	if (statusCode != -1) {
		result = method.getResponseBodyAsString();
	}
	method.releaseConnection();

	var tidy = new Packages.org.w3c.tidy.Tidy();
	tidy.setQuiet(true);
	tidy.setShowWarnings(false);
	tidy.setHideComments(true);
	tidy.setFixUri(true);
	tidy.setNumEntities(true);
	tidy.setXHTML(true);
	tidy.setDocType("omit");
	var baos = new Packages.java.io.ByteArrayOutputStream();
	var str = new Packages.java.lang.String(result.toString());
	var ios = new Packages.java.io.ByteArrayInputStream(str.getBytes("UTF-8"));
	tidy.parse(ios,baos);
	var xml = new XMLList(baos.toString().replace(/xmlns="[^"]+"/g,""));
	var obj = {};
	obj.server = xml..link.(@rel=="openid.server").@href.toString();
	obj.delegate = xml..link.(@rel=="openid.delegate").@href.toString();
	return obj;
}

/**
 * Add a new comment to the post. 
 * Parameters:
 *     data: Object with properties:
 *          name (String):  Name of commenter.
 *          email (String): Email address of commenter.
 *          openid_identifier (String): OpenID Identifier of commenter.
 *          comment_type (String): Type of Commenter Authentication
 *          body (XML):  Body of comment.
 *          approved (Boolean): True only if it is an OpenID Comment and the author is whitelisted.
 *  Return: new Comment object
 */
function add_comment(data){
	data = (data || req.data);
	var comment = new Comment();
	comment.edit({
		name:				data.name,
		email:				data.email,
		openid_identifier:	data.openid_identifier,
		comment_type:		data.comment_type,
		body:				data.body,
		date:				new Date(),
		approved:			data.approved
	})
	this.add(comment);
	return comment;
}

/**
 * Get all the approved Comments on this post.
 * Return: Array of Comment objects.
 */
function approved_comments(){
	return this.getChildren('Comment',{'approved':'true'},{sort:{date:'asc'}});
}

/**
 * Get all comments on this post, approved and unapproved.
 * Return: Array of Comment objects.
 */
function all_comments(){
	return this.getChildren('Comment',{},{sort:{date:'asc'}});
}

/**
 * Get the total number of approved comments on this post.
 * Return: Integer total.
 */
function approved_comments_count() {
	return this.getChildCount('Comment',{'approved':'true'});
}


/**
 * Get the total number of approved comments on this post.
 * Return: Integer total.
 */
function all_comments_count() {
	return this.getChildCount('Comment');
}

/**
 * Get all categories associated with this post.  
 * Return: Array of Category objects
 */
function get_categories(){
	var cats = [];
	if (this.categories != null) {
		for (var i = 0; i < this.categories.length; i++) {
			cats.push(this.categories[i].getTarget());
		}
	}
	return cats;
}

/**
 * Get all categories associated with this post in a nice format for posts
 * Return: XMLList Object with comma seperated links to each Category
 */
function get_categories_view() {
	var cats = this.get_categories().map(function(obj) {
		var str = '<a href="' + obj.getURI() + '">' + obj.title + '</a>';
		return str.replace(/&(?!\w+;)/g, '&amp;')
	}).join(', ');

	return this.categories != null ? new XMLList(cats) : '';
}


/**
 * Get the author's name.  Handles error if the author has been deleted.
 */
function get_author_name() {
	if (this.author != null) {
		return this.author.getTarget().fullname;
	}
	return "Unknown";
}

/**
 * Get the author's username.  Handles error if the author has been deleted.
 */
function get_author_username() {
	if (this.author != null) {
		return this.author.getTarget().username;
	}
	return "Unknown (Author removed)";
}

/**
 * Get the author's email.  Handles error if the author has been deleted.
 */
function get_author_email() {
	if (this.author != null) {
		return this.author.getTarget().email;
	}
	return app.getObjects("User",{username: "admin"})[0].email;
}

/**
 * Template logic function that determines if the date should be shown when iterating over posts.
 * Return: Boolean, true if date should be shown.
 */
function show_date(index, posts) {
	if (index == 0) {
		return true;
	}

	if (posts[index].date.toLocaleDateString() == posts[index - 1].date.toLocaleDateString()) {
		return false;
	}

	return true;
}
