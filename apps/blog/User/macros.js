/**
 * Store hash of password.
 * Parameters:
 *      password (String):  user's new password
 */
function setPassword(password) {
    this.password = password.md5();
}

/**
 * Set the user's role.
 * Parameters: 
 *      role (String): user's new role. 
 */
function setRole(role) {
	this.role = role;
}

/**
 * Get this user's roles. Needed for Axiom user object interface.
 * Returns: Array of roles string.
 */
function getRoles() {
	return [this.role];
}

/**
 * Determine if user is an Administrator or not.  Used for some TAL logic.
 * Returns: Boolean
 */
function is_admin() {
	return this.role == "Administrator";
}
