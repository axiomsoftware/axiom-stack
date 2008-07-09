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

function nodes(data){
	var this_node = app.getHits(data._prototype, {_id: data._id}).objects(0,1)[0];
	return this_node.getChildren().map(function(obj){
										   return { text:            obj.id,
													id:              obj._id,
													qtip:            obj._prototype,
													path:            obj.getPath(),
													_prototype:      obj._prototype,
													leaf:            !obj.hasChildren() };
									   });
}

function truncate_field(str,len){
	return (str.length > len ? str.slice(0, len)+'...' : str);
}

function add_form(data){
	var _prototype = (data|| req.data)._prototype;
	var schema = app.getSchema(_prototype, false);
	var obj_results = {xtype: 'fieldset', defaults: {width: '400px', msgTarget: 'under'}, fileUpload: true, autoHeight: true, title: "Object Properties", items: []};
	for(var prop in schema){
		obj_results.items.push({ fieldLabel: prop,
								 xtype:      (this.typemap(schema[prop].type.value) || 'textfield'),
								 name:       prop,
								 value:      ''
							   });
	}
	if(_prototype == "Image" || _prototype == "File"){
		obj_results.items.push({ fieldLabel: 'file',
								 inputType:  'file',
								 xtype:      'textfield',
								 name:       '__filedata__',
								 value:      ''
							   });
	}
	return obj_results;
}

function create_object(data){
	data = (data || req.data);
	var parent = app.getHits([], {_id: data._parent}).objects(0,1)[0];
	var arg;
	if(data._prototype == "Image" || data._prototype == "File"){
		arg = req.data.__filedata__;
	}
	var obj = new global[data._prototype](arg);
	data = this.clean_props(data,true);
	delete data._prototype;
	delete data._parent;
	parent.add(obj);
	app.log(data.toSource());
	var errors = obj.edit(data);

	// ExtJS quirk - since file uploads are sent through an iframe, one has set
	// the return type to text/html so it can parse the data out of it
	res.setContentType("text/html");
	res.write({success:!errors, _id: obj._id, errors: errors}.toSource());
}

function typemap(type){
	return { xhtml: 'textarea',
			 time: 'dateandtime',
			 'boolean': 'axboolean'
		   }[(type || '').toLowerCase()];
}

function object_info(data){
	data = (data || req.data);
	var obj = app.getHits(data._prototype, {_id: data.id}).objects(0,1)[0];

	var max_field_length = 30;
	var defaults = {width: '400px', msgTarget: 'under'};
	var obj_results = {xtype: 'fieldset', defaults: defaults, autoHeight: true, title: "Object Properties", items: []};
	var inspect = this;
	var system_results = {
		xtype: 'fieldset',
		collapsed: true,
		collapsible: true,
		autoHeight: true,
		title: "System Properties",
		items: ['_id', '_status', '_layer', '_prototype', 'getPath'].map(function(prop){
													return {
														fieldLabel: inspect.truncate_field(prop, 30),
														name:       prop,
														value:      (typeof obj[prop] == 'function') ? obj[prop]() : obj[prop],
														xtype:      'textfield',
														readOnly:   true,
														cls:        'form-item-disabled',
														width:      '400px'
													};
												})
	};

	var schema = obj.getSchema();
	for(var prop in schema){
		var val = obj[prop];
		var type = typeof val;
		if(val instanceof MultiValue){
			val = [ (val.type() == "Reference" ? f.getTarget().getPath() : f+"") for each(f in val) ].join(',');
		} else if(val instanceof Date){
			val = val.getTime();
		} else if (type != 'undefined' && type != 'number' && type != 'boolean') {
			val = val+'';
		}
		obj_results.items.push({ fieldLabel: this.truncate_field(prop,30),
								 xtype:      (this.typemap(schema[prop].type.value) || 'axtextfield'),
								 name:       prop,
								 value:      val
							   });
	}
	return [obj_results, system_results];
}

function clean_props(data, check_existing){
	for(var i in data){
		if(i.match(/^http/) || data[i] == 'undefined' || (check_existing && data[i] == '')){
			delete data[i];
		}
	}
	return data;
}

function save_object(data){
	data = (data || req.data);
	app.log(data.toSource());
	var obj = app.getHits(data._prototype, {_id: data._id}).objects(0,1)[0];
	var result = (obj.edit(this.clean_props(data,true)) || {errors: false});
	if(result.errors && result.errors._accessname){
		result.errors.id = result.errors._accessname;
		delete result.errors._accessname;
	}
	result.success = !result.errors;
	result._id = obj._id;
	return result;
}

function delete_object(data){
	data = (data || req.data);
	var _prototype = data._prototype;
	var _id = data._id;
	var obj = app.getHits(_prototype, {_id: _id}, {maxlength: 1}).objects(0,1)[0];
	if(!obj){
		return {success: false, message: "Could not find object of type "+_prototype+" and _id "+_id+" in application "+app.getName()+"."};
	} else {
		obj._parent.remove(obj);
		return { success: true, message: _prototype+" "+_id+" deleted successfully."};
	}
}

function shell(){
	var results;
	var __buffer__ = [];
	var println = function(str){__buffer__.push(str);};
	try{
		results = eval(req.data.commands);
		var type = typeof results;
		if(type == "string" || type == "number" || type == "undefined" || type == "boolean"){
			results = results+'';
		} else {
			results = results.toSource();
		}
	} catch(e){
		results = e;
	}
	return {items: [{command: req.data.commands, prints: __buffer__, results: results }]};
}

function authenticate(data){
	data = (data || req.data);
	var props = app.getProperties();
	if(data.username == props['inspector.user'] && data.password == props['inspector.password']){
		session.data.inspector = true;
		return this.main();
	} else {
		return this.main({login: true, error:"Incorrect username or password."});
	}
}

function login_errors(data){
	if(data.login && data.error){
		return [{html: '<div>'+data.error+"</div>", cls: 'error'}].toSource();
	}
	return '[]';
}

function logout(){
	delete session.data.inspector;
}

function addable_prototypes(){
	return [p for each(p in app.getPrototypes())].filter(function(p){
		return !(/^AXInspector/).test(p);
	}).sort();
}