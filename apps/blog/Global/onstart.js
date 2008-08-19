function onStart(){
	if(!root.get('home')){
		app.log("No Home Page found...Running Initial Setup");

		// Default settings
		var hp = new HomePage();
		root.add(hp);
		hp.id = "home";
		hp.title= "My Axiom Blog";
		hp.description = "My Axiom Blog's Description";

		// Categories will be added here
		var category_dir = new AxiomObject();
		category_dir.id = "category";
		hp.add(category_dir);

		var defuser = new User();
		hp.add(defuser);
		defuser.username = "admin";
		defuser.fullname = "Default Administrator";
		defuser.email = "";
		defuser.setPassword("password");
		defuser.setRole("Administrator");
		defuser.activated = false;
		var random = new Date().valueOf() + defuser.username;
		defuser.id =  random.md5();
	}
}
