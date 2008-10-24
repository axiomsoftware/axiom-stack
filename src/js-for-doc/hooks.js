/**
 * Called before the changes from a request are committed.
 * @param {Array} insertedNodes List of objects newly created and persisted in this request.
 * @param {Array} updatedNodes List of existing objects modified in this request.
 * @param {Array} deletedNodes List of objects to be deleted in this request.
 * @hook
 */
onCommit = function(/**Array*/ insertedNodes, /**Array*/ updatedNodes, /**Array*/ deletedNodes){

};

/**
 * Called after a commit has successfully taken place.
 * @hook
 */
postCommit = function(){

};

/**
 * Called when this application is stopped.
 * @hook
 */
onStop = function(){

}

/**
 * Called when a user is logged out of a session.
 * @hook
 */
onLogout = function(){

}

/**
 * Called when this application is started.  The name of the function to be called on startup
 * can be configured in app.properties
 * @hook
 */
onStart = function(){

};

/**
 * Called when a session expires due to timeout.
 * @param {Session} session The expired session object.
 * @hook
 * @function
 */
onSessionTimeout = function(/**Session*/ session){

}

/**
 * Called when a request for terminates in a 404 not found error.  The return value of this
 * function is returned to the http client.
 * @hook
 */
AxiomObject.prototype.notfound = function() {

};

/**
 * Called when an uncaught exception occurs inside a request. The return value of this
 * function is returned to the http client.
 * @hook
 */
AxiomObject.prototype.error = function() {

};

/**
 * Called when a request is made for by session that lacks the security priviledges to access
 * the path that was requested. The return value of this function is returned to the http client.
 * @hook
 */
AxiomObject.prototype.unauthorized = function() {

};

/**
 * If defined, overrides the security settings defined in the prototype's security.properties.  To fall back on the
 * security.properties defined security settings, use the value from calling [prototype].isAllowedDefault
 * @param {String} action Name of the method the request would invoke.
 * @param {Array} roles Array of Strings representing the allowed roles for this method, as defined in the prototype's security.properties
 * @returns {Boolean} True if request should be allowed, false if it should be denied.
 * @hook
 */
AxiomObject.prototype.isAllowed = function(action, roles){

};

/**
 * Called when changes to an object are about to be persisted, just before commit time.
 * @hook
 */
AxiomObject.prototype.onPersist = function(){

};