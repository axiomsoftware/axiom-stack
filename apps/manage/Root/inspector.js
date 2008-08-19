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

function inject(func, args, appname){
	args = args.map(function(arg){
						switch(typeof arg){
						case 'undefined': return 'undefined';
						case 'number': return arg;
						case 'string': return '"'+arg.replace(/\"/g, '\\"')+'"';
						default: return arg.toSource()}
					});
	return this.dispatchShell('('+func.toSource()+')('+args.join(',')+')', appname);
}

function nodes(){
	return this.inject(this.nodes_injector, [req.data._prototype, parseInt(req.data.id)], req.data.appname);
}

function nodes_injector(_prototype,_id){
	var this_node = app.getObjects(_prototype, {_id: _id}, {maxlength: 1})[0];
	return this_node.getChildren().map(function(obj){
										   return { text:            obj.id + ' ('+ obj._prototype+')',
													id:              app.getName()+'#'+obj._id,
													_prototype:      obj._prototype,
													leaf:            !obj.hasChildren() };
									   });
}

function add_form(){
	return this.inject(this.add_form_injector, [req.data._prototype], req.data.appname);
}

function add_form_injector(_prototype){
	var typemap = { xhtml: 'textarea',
					time: 'dateandtime' }
	var schema = (new global[_prototype]()).getSchema();
	var obj_results = {xtype: 'fieldset', defaults: {width: '400px', msgTarget: 'under'}, fileUpload: true, autoHeight: true, title: "Object Properties", items: []};
	for(var prop in schema){
		obj_results.items.push({ fieldLabel: prop,
								 xtype:      (typemap[schema[prop].type.value.toLowerCase()] || 'textfield'),
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

function create_object(){
	return this.inject(this.create_object_injector, [req.data], req.data.appname)
}

function create_object_injector(data){
	var parent = app.getHits([], {_id: data._parent}).objects(0,1)[0];
	var obj = new global[data._prototype]();
	for(var i in data){
		if(i.match(/^http_/)){
			delete data[i]
		}
	}
	delete data._prototype;
	delete data._parent;
	obj.edit(data);
	parent.add(obj);
	return {success: true, _id: obj._id};
}

function object_info(){
	return this.inject(this.object_info_injector, [req.data._prototype, req.data.id], req.data.appname);
}

function object_info_injector(_prototype, _id){
	var obj = app.getObjects(_prototype, {_id: _id}, {maxlength: 1})[0];

	var typemap = {
		xhtml: 'textarea',
		time: 'dateandtime'
	}
	var defaults = {width: '400px', msgTarget: 'under'};
	var obj_results = {xtype: 'fieldset', defaults: defaults, autoHeight: true, title: "Object Properties", items: []};
	var system_results = {
		xtype: 'fieldset',
		collapsed: true,
		collapsible: true,
		autoHeight: true,
		title: "System Properties",
		items: ['_id', '_status', '_layer'].map(function(prop){
													return {
														fieldLabel: prop,
														name:       prop,
														value:      obj[prop],
														xtype:      'textfield',
														readOnly:   true,
														cls:        'form-item-disabled'
													}
												})
	}

	var schema = obj.getSchema();
	for(var prop in schema){
		var val = obj[prop];
		if(val instanceof MultiValue){
			val = [ (val.type() == "Reference" ? f.getTarget().getPath() : f+"") for each(f in val) ].join(',');
		} else if(val instanceof Date){
			val = val.getTime();
		} else{
			val = val+'';
		}
		obj_results.items.push({ fieldLabel: prop,
								 xtype:      (typemap[schema[prop].type.value.toLowerCase()] || 'textfield'),
								 name:       prop,
								 value:      val
							   });
	}
	return [obj_results, system_results];
}

function save_object(){
	var result = this.inject(this.save_object_injector, [req.data._prototype, req.data._id, req.data], req.data.appname);
	if(result && result.errors && result.errors._accessname){
		result.errors.id = result.errors._accessname;
		delete result.errors._accessname;
	}
	return result;
}

function save_object_injector(_prototype, _id, data){
	return app.getHits(_prototype, {_id: _id}).objects(0,1)[0].edit(data);
}

function delete_object(){
	return this.inject(this.delete_object_injector, [req.data._prototype, req.data._id], req.data.appname);
}

function delete_object_injector(_prototype, _id){
	var obj = app.getHits(_prototype, {_id: _id}, {maxlength: 1}).objects(0,1)[0];
	if(!obj){
		return {success: false}
	} else {
		obj._parent.remove(obj);
		return { success: true}
	}
}

function apps_info(){
	var hash = {};
	for each(var app in this.getApps()){
		hash[app.getName()] = app.getPrototypes().toArray().invoke('getName').sort();
	}
	return hash;
}
