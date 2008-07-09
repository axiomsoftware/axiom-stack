/**
 * Displays Verification screen for Inactive Accounts/Password Resets
 */
function main() {
	if (!this.activated) {
		var data = {};
		var filter = new NotFilter({_id:this._id});
		data.invalid_emails = app.getFields("email","User",filter).toSource();
		return this.verify_view(data);
	}

	if (this.reset_sent) {
		return this.reset_view();
	}
	return this.notfound();
}

/**
 * Form Handler method for Activation.  Form returned from main.
 */
function verify() {
	if (!this.activated) {
		this.fullname = req.data.fullname;
		this.email = req.data.email;
		this.setPassword(req.data.password1);
		this.activated = true;
		this.reset_sent = false;
		this.id = this._id;
		session.login(this);
		res.redirect(get_home_page().getURI("manage"));
		return ;
	}

	if (this.reset_sent) {
		this.setPassword(req.data.password1);
		this.reset_sent = false;
		this.id = this._id;
		res.redirect(get_home_page().getURI("login?reset=true"));
		return ;
	}
	return this.notfound();
}

/**
 * Send invite to new user.
 */
function send_invite() {
	var mailer = new Mail();
	var admin = app.getObjects("User",{username:"admin"})[0];
	var hp = get_home_page();
	mailer.setFrom(admin.email, admin.fullname);

	mailer.setSubject("Axiom Blog - Invitation to join " + hp.title + " as an "+this.role+".");
	mailer.setTo(this.email);
	mailer.addText(this.fullname+",\n\nYou have been invited to join the  \"" + hp.title + "\" Blog!\n\nPlease visit http://"+req.data.http_host+this.getURI()+" to register as an " + this.role + ".");
	mailer.send();
}

/**
 * Send a password reset notification to the email address of this user.
 */
function send_reset() {
	var mailer = new Mail();
	var admin = app.getObjects("User",{username:"admin"})[0];
	mailer.setFrom(admin.email, admin.fullname);

	mailer.setSubject("Axiom Blog - Password Reset Notice");
	mailer.setTo(this.email);
	mailer.addText(this.fullname+",\n\nPlease visit http://"+req.data.http_host+this.getURI()+" to reset your password.");
	mailer.send();
}
