function init() {
	var nc = root.get('notcached');
	if (!nc) {
		nc = new NotCached();
		nc.id="notcached";
		root.add(nc);
	}

	var nc = root.get('cached');
	if (!nc) {
		nc = new Cached();
		nc.id="cached";
		root.add(nc);
	}
}
