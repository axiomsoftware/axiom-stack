/**
  *  Copyright 2007-2008 Axiom Software Inc.
  *  This file is part of the Axiom Manage Application. 
  *
  *  The Axiom Manage Application is free software: you can redistribute 
  *  it and/or modify it under the terms of the GNU General Public License 
  *  as published by the Free Software Foundation, either version 3 of the 
  *  License, or (at your option) any later version.
  *
  *  The Axiom Manage Application is distributed in the hope that it will 
  *  be useful, but WITHOUT ANY WARRANTY; without even the implied warranty 
  *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  *  GNU General Public License for more details.
  *
  *  You should have received a copy of the GNU General Public License
  *  along with the Axiom Manage Application.  If not, see 
  *  <http://www.gnu.org/licenses/>.
  */

/**
 * Wrapper for manage interface.
 */
function main(){
	return this.manage_interface();
}

/**
 * Return array all applications running.
 */
function getApps(){
	return [a for each(a in Packages.axiom.main.Server.getServer().getApplications()) if(a.getName() != app.getName())];
}

/**
 * Return the application object associated with appName
 */
function getAppByName(appName){
	return Packages.axiom.main.Server.getServer().getApplication(appName);
}

function appAction(){
	var application = this.getAppByName(req.data.app);
	var commands = {
		stop: function(){
			application.stop();
			return application.getName()+" stopped.";
		},
		start: function(){
			application.start();
			return application.getName()+" started.";
		},
		restart: function(){
			application.stop();
			application.start();
			return application.getNamer()+" restarted.";
		},
		apigen: function(){
			var src = application.getAppDir();
			var target = application.getProperty('static.0', application.getServerDir()+java.io.File.separator+'static');
			this.generate(src, target);
			return <div>API Generated: <a href={application.getStaticMountpoint('/doc/index.html')}>View</a></div>;
		}
	};
	return commands[req.data.command].call(this);
}

/**
 * Create a new superuser account.  Returns an error string if an account has
 * already been created.
 */
function new_user(){
	if(app.getHits("AXSuperUser", {}).length == 0){
		var user = new AXSuperUser();
		this.add(user);
		user.username = req.data.username;

		// TODO: drop hash in app.properties file instead
		user.password = req.data.password.md5();
		res.redirect(this.getURI('login'));
	} else {
		return "Administrator account already exists.";
	}
}

function new_application(){
	try{
		var success = this.bootstrap(app.getServerDir(),req.data.app_name);
		if(!success) return success;
		// wait for application to be registered
		var start = (new Date()).getTime();
		while(!Packages.axiom.main.Server.getServer().getApplication(req.data.app_name)){
			java.lang.Thread.sleep(1000);
			if((new Date()).getTime() - start > 30000){
				// timeout after 30 seconds
				return {success: false, 
						message: "There was an error creating application "+req.data.app_name+": timeout while waiting for new app to be registered."};
			}
		}
		return success;
	} catch(e){
		return {success: false, 
				message: "There was an error creating application "+req.data.app_name+": "+e.toString()}
	}
}