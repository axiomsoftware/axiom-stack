/**
 * Unauthorized handler.  Redirects request to login function.
 */
function unauthorized() {
    res.redirect(get_home_page().getURI('login'));
}

/**
 * Not Found (404) handler. Displays a nice Page not Found Message.
 */
function notfound() {
	var not_found_msg = <div class="maincol">
	<h1>Page Not Found</h1>
	<p>We are unable to locate the page you requested.</p>
	<p>The page may have moved or may no longer be available.</p>
</div>;

	return this.wrap({ title:"Page Not Found", content: not_found_msg });
}
