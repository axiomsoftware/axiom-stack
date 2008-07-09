function _add_kitchen_sinks(prototype, index){
	for(var i = 0; i < index; i++){
		var ks = new global[prototype]();
		ks.id = 'ks' + i;
		ks.title = 'title ' + i;
		root.add(ks);
	}
	res.commit();
}

function _add_landfills(prototype, index){
	for(var i = 0; i < index; i++){
		var lf = new global[prototype]();
		lf.id = 'lf' + i;
		root.add(lf);
	}
	res.commit();
}

