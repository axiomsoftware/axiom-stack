/**
 * Authenicate a username and password.  If successful, log the user in.
 * Parameters:
 *     data (post or object) with properties:
 *              username (String)   user's username
 *              password (String):  user's password        
 * Returns: Object with properties:
 *     success (Boolean): Indicates if login succesful.
 *     error (String):    Description of error if login unsuccesful.
 */
function authenticate(data) {
    data = (data || req.data);
	if(data.username && data.password) {
		var user = app.getHits('User', new AndFilter({activated: true}, {username: data.username}, {password: data.password.md5()})).objects(0,1)[0];
		if(user){
			session.login(user);
			return {success: true};
		}
		var inactive = app.getHits('User', new AndFilter({activated: false}, {username: data.username}, {password: data.password.md5()})).objects(0,1)[0];
		if(inactive) {
			res.redirect(inactive.getURI());
			return;
		}
	} 
	return {success: false, error: 'No user found with that username/password combination.'};
}

function logout() {
	session.logout();
	res.redirect(this.getURI('login'));
}
