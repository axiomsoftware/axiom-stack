/**
 *  Save data to the entry.
 *  Parameters: data (Javascript Object) with properties: 
 *      title (String):              title of the entry
 *      body (String):               XML body of the entry
 *      categories (String):         optional. comment-seperated list of category paths
 *      allow_comments (Boolean):    optional. bool indicating if comments allowed on the post. defaults to true.
 *      published (Boolean):         optional. publish the entry to the live blog. defaults to false.
 *      date (Date):                 optional. date of post publish. defaults to current time.
 *      entry_id (Integer):          optional.  if specified, indicates the draft entry to be
 *                                   saved and published.  if omitted, a new entry will be created.
 * Returns: Post object.
 */
function save_entry(data){
	data = (data || req.data);

	var entry;
	if(data.entry_id){
		entry = app.getHits("Post", {_id: data.entry_id}).objects(0,1)[0];
		delete data.entry_id;
	} else {
		entry = this.create_new_post(data.title);
		delete data.title;
		entry.date = new Date();
	}

	entry.edit(data);

	if(!entry.author){
		if (session.user) {
			entry.author = new Reference(session.user);
		}
	}

	return entry;
}


/**
 *  Creates a new, empty post using the current date and attaches it to a date container.  
 *  Will create the date container if none yet exists.
 *  Parameters:
 *    title (String): title of the new Post
 *  Returns: the new Post object.
 */
function create_new_post(title){
	var post = new Post();

	var current_date = new Date();
	var id_str = this.get_date_string(current_date);

	var day_container = this.get(id_str);
	if(!day_container){
		day_container = new Day();
		this.add(day_container);
		day_container.id = id_str;
	}
	var id = this.hash_id(title);
	var count = 0;
	while (day_container.get(id)) {
		count++;
		id = this.hash_id(title) + "_" + count;
	}
	day_container.add(post);
	post.title = title;
	post.id = id;
	return post;
}

/**
 * Format the given date for use in uri/id string.
 * Parameters:
 *    date (Date): date object to be formatted
 * Returns: String of the form 'mm-dd-yyyy'.
 */
function get_date_string(date){
	var pad = function(x){ 
		x = new String(x);
		return x.length == 1 ? '0'+x : x;
	}
	return [pad(date.getMonth()+1), pad(date.getDate()), date.getFullYear()].join('-');
}
