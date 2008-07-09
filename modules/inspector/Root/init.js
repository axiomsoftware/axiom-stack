let(props = app.getProperties(), mountpoint=0){
	if(props['inspector.user'] && props['inspector.password']){
		mountpoint = props['inspector.mountpoint'] || 'inspector';
		if(this[mountpoint]){
			app.log("Could not attach inspector to mountpoint "+
					mountpoint+" - property or function of the same name alreay exists.");
		} else {
			this[mountpoint] = function(){
				if(req.data.method == 'authenticate' || session.data.inspector == true){
					return AXInspector.prototype[req.data.method || 'main'].call(AXInspector.prototype, req.data);
				} else{
					return AXInspector.prototype.main.call(AXInspector.prototype, {login: true});
				}
			};
		}
	}
};