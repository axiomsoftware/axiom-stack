/**
 * Create a new, non-activated user.
 * Parameters:
 *     data (Object) with properties: 
 *         username (String):  username of user.
 *         email (String):     email of user.
 *         role (String):      role of user. should be "Administrator" or "Author"
 *         fullname (String):  full name of user.
 *         password (String):  password of user.
 * Returns: Newly created user object.
 */
function create_user(data) {
	data = (data || req.data);
	var user = new User();
	if(data.password){
		user.setPassword(data.password);
		delete data.password;
	}
	user.edit(data)
	this.add(user);
	return user;
}

/**
 * Edit the data of a particular user.
 * Parameters:
 *     data (Object) with properties: 
 *         user_id (String):   indicates the user to be edited.
 *         username (String):  optional. username of user.
 *         email (String):     optional. new email of user.
 *         role (String):      optional. new role of user. should be "Administrator" or "Author"
 *         fullname (String):  optional. new full name of user.
 *         password (String):  optional. new password of user.
 * Returns: False if User not found, or result of edit operation.
 */
function edit_user(data) {
	data = (data || req.data);
	if(!data.username)
		return false;

	var user = app.getHits("User", {_id: data.user_id}).objects(0,1)[0];
	if(!user)
		return false;

	if(data.password){
		user.setPassword(data.password);
		delete data.password;
	}
	return user.edit(data);
}

/**
 * Remove a user from the system.
 * Parameters:
 *     username (String):  username of user to be deleted
 * Returns: boolean indicating success of delete.
 */
function delete_user(username) {
	username = (username || req.data.username);
	var hits = app.getHits("User", {username: username});
	if(hits.length == 0){
		return false;
	}
	var user = hits.objects(0,1)[0];
	return user._parent.remove(user);
}
