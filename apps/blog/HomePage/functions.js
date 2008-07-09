/**
 * Main method for HomePage.
 */
function main() {
	var current_page = parseInt((req.data.page || 1), 10);
	var hits = this.get_posts();
	var pages = Math.ceil(hits.length / this.posts_per_page);
	var posts = hits.objects((current_page - 1) * this.posts_per_page, this.posts_per_page);
	return this.wrap({ content:this.home_view({
						posts: posts, 
						pagination: get_pagination(this.getURI('?'),current_page,pages)})
					 });
}

/**
 * Display method for Search Results.
 */
function blog_search() {
	if (req.data.query == null) {
		return this.wrap({ content:this.search_view({ posts:[], pagination: '' }) });
	}

	var query = req.data.query.replace(/"/g,"").replace(/([+\-&!(){}\[\]\'|^~\?:])/g, "\\$1");

	if (query == "") {
		return this.wrap({ content:this.search_view({ posts:[], pagination: '' }) });
	}

	var current_page = parseInt((req.data.page || 1), 10);
	var hits = this.search(query);
	var pages = Math.ceil(hits.length / this.posts_per_page);
	var posts = hits.objects((current_page - 1) * this.posts_per_page, this.posts_per_page);
	var start = (current_page - 1) * this.posts_per_page + 1
	var len = posts.length;
	var total = hits.length;
	return this.wrap({ content:this.search_view({
						posts: posts,
						start: start,
						len: len,
						total: total,
						pagination: get_pagination(this.getURI('search?query=' + req.data.query),current_page,pages)})
					 });
}

/**
 *  Get the 5 most recently published posts, sorted by date published.
 *  Returns: Array of Post objects.
 */
function recent_posts() {
	return app.getObjects('Post', {published:true}, {sort:{'date':'desc'}, maxlength:5});
}


/**
 *  Get published posts to display, sorted by date published.
 *  Returns: Hits object.
 */
function get_posts(){
	return app.getHits('Post', {published:true}, {sort:{'date':'desc'}});
}


/**
 * Search published Posts.  Return a slice of the results.
 * Parameters: 
 *     query (String):  text to search for.
 * Returns: Hits object.
 */
function search(query){
	return app.getHits("Post", new AndFilter({published: true}, new NativeFilter((query), "post_search")));
}

/**
 *  Writes User's Custom CSS Out to Response
 */
function user_style() {
	res.contentType = 'text/css';
	res.write(this.custom_style);
}

/**
 *  Creates a "nicename" for a Post or Category to be used for its accessname.
 *  Returns: String.
 */
function hash_id(title) {
	return title.toLowerCase().replace(/\s+/g, '-').replace(/[^\w-]/g, '');
}



/**
 * Edit blog-wide settings by setting properties on the homepage.
 * Parameters:
 *     data (post or object): map name -> value of properties to be set. properties not in
 *                            Homepage's schema are ignored.
 * Returns: Result of edit operation.
 */
function settings(data){
	data = (data || req.data);
	var schema = this.getSchema();
	for(var prop in data){
		if(!schema[prop]){
			delete data[prop];
		}
	}
	return this.edit(data);
}


/**
 * Generate RSS feed for site.
 */
function rss() {
	var xml = <rss version="2.0">
	<channel>
		<title>{this.title}</title>
		<link>{"http://"+req.data.http_host+this.href()}</link>
		<description>{this.description}</description>
	</channel>
</rss>;

	var posts = app.getObjects(['Post'],{'published':true},{sort:{'date':'desc'},maxlength:10});

	for(var i = 0; i < posts.length; i++) {
		xml.channel.* += <item>
		<title>{posts[i].title}</title>
		<link>{"http://"+req.data.http_host+posts[i].getURI()}</link>
		<description>{posts[i].body}</description>
		<pubDate>{posts[i].postdate}</pubDate>
	</item>;
	}

	res.setLastModified(posts[0].date);
	res.setETag(posts[0].date.toGMTString());
	res.contentType = 'text/xml';
	res.write(xml, '<?xml version="1.0"?>');
}
