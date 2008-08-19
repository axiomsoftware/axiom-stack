function main() {
	var hp = get_home_page();
	var current_page = parseInt((req.data.page || 1), 10);
	var all_posts = this.get_posts().sort(function(a,b) { return b.date - a.date} );
	var pages = Math.ceil(all_posts.length / hp.posts_per_page);
	var posts = all_posts.splice((current_page - 1) * hp.posts_per_page, hp.posts_per_page);
	return this.wrap({ content:this.category_view({
						posts:posts,
						pagination:get_pagination(this.getURI('?'),current_page,pages)})
					});
}

/**
 * Get all Posts associated with this Category.
 * Return: Array of Post objects.
 */
function get_posts(){
	return app.getSources(this, ["Post"], new NativeFilter("published:true"));
}

/**
 * Get the number of Posts associated with this Category.
 * Return: Number of Post objects.
 */
function get_post_count(){
	return app.getSourceCount(this, ["Post"], new NativeFilter("published:true"));
}
