/**
 * Create a new Category with the specified title.  
 * Parameters:
 *    title (String): title of the new Category
 * Returns: newly created category, or if false if category already exists.
 */
function create_category(title){
	title = (title || req.data.title);
	if(app.getHits("Category", {id: this.hash_id(title)}).length != 0){
		return false;
	}
	var category = new Category();
	category.edit({id: this.hash_id(title),
				   title: title});
	this.get("category").add(category);
	return category;
}

/**
 * Parameters:
 *     titles (Array): Optional. Array of string titles of categories to search for.
 * Return: All matching Category objects. If no titles specified, returns all
 * existing Categories.
 */
function get_categories(titles){
	var filter = titles ? new OrFilter(titles.map(function(i){return new Filter({title: i})})) : {};
	return app.getObjects("Category", filter, {sort:{id:"asc"}});
}

/**
 *  Delete the given Category.
 *  Parameters: 
 *      title (String): title of category to be deleted.
 *  Returns: Boolean indicating success of deletion.
 */
function delete_category(title){
	title = (title || req.data.title);
	var category = this.get_categories([title])[0];
	if(!category){
		return false;
	}
	category._parent.remove(category);
	return true;
}


