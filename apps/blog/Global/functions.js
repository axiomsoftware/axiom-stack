/**
 *  Returns the HomePage object
 *  Return: HomePage Object
 */
function get_home_page() {
	return root.get("home");
}


/**
 *  Checks if a blog setting is set to a particular value.
 *  Parameters: 
 *      setting (String):	Name of the setting.
 *      value (String):		Value to check.
 *  Return: Boolean
 */
function check_setting(setting,value) {
	return (get_home_page()[setting] == value);
}

function get_manage_pagination(base_url,current_page,num_pages) {
	if (num_pages == 1) {
		return '';
	}

	var pagination = [];
	if (current_page > 1) {
		pagination.push('<li><a href="' + base_url + '&amp;page=' + (current_page - 1) + '">&lt;</a></li>');
	}
	for (var i = 0; i < num_pages; i++) {
		var page = i + 1;
		if (page == current_page) {
			pagination.push('<li><strong>' + page + '</strong></li>');
		} else {
			pagination.push('<li><a href="' + base_url + '&amp;page=' + page + '">' + page + '</a></li>');
		}
	}
	if (current_page < num_pages) {
		pagination.push('<li><a href="' + base_url + '&amp;page=' + (current_page + 1) + '">&gt;</a></li>');
	}

	return new XML('<div class="bpagination"><ul>' + pagination.join('<li> | </li>') + '</ul></div>');
}



function get_pagination(base_url,current_page,num_pages) {
	if (num_pages == 1) {
		return '';
	} else {
		var pagination = [];
		if (current_page > 1) {
			pagination.push('<a href="' + base_url + '&amp;page=' + (current_page - 1) + '">&lt;</a>');

		}
		for (var i = 0; i < num_pages; i++) {
			var page = i + 1;
			if (page == current_page) {
				pagination.push('<strong>' + page + '</strong>');
			} else {
				pagination.push('<a href="' + base_url + '&amp;page=' + page + '">' + page + '</a>');
			}
		}
		if (current_page < num_pages) {
			pagination.push('<a href="' + base_url + '&amp;page=' + (current_page + 1) + '">&gt;</a>');

		}
		return new XML('<div class="searchPages">' + pagination.join(' | ') + '</div>');
	}
}

Date.prototype.formatted = function() {
	return this.format('h:mm a, yyyy-MM-dd');
}
