function _add_kitchen_sinks(parent, prototype, index){
	for(var i = 0; i < index; i++){
		var ks = new global[prototype]();
		ks.id = 'ks' + i;
		ks.title = 'title ' + i;
		parent.add(ks);
	}
	res.commit();
}

function _add_landfills(parent, prototype, index){
	for(var i = 0; i < index; i++){
		var lf = new global[prototype]();
		lf.id = 'lf' + i;
		parent.add(lf);
	}
	res.commit();
}

