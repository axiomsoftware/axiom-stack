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

axiom = {
	apps: [],
	parse_id: function(str){
		// extract application name and _id from node in object tree
		// node ids are of the form: appname#id
		var pieces = str.split('#');
		var len = pieces.length;
		return {
			app: pieces[0],
			id: pieces.slice(1, len).join('#') // piece id back together, in case it contains #s
		}
	},
	submit: function(){
		// form submit handler for saving/creating objects
		this.store.baseParams = (this.store.baseParams || {});
		this.store.baseParams.commands = this.getRawValue();
		this.store.baseParams.app_name = Ext.getCmp('app-menu').text;
		this.store.load({add: true});
	},
	init: function() {
		// application initialization function

		// create a treeloader to talk to the object-listing service
		axiom.treeloader = new Ext.tree.TreeLoader({ url:        'nodes',
													 baseAttrs:  {iconCls: 'object-icon'}
												   });
		axiom.treeloader.on("beforeload", function(loader, node) {
								var info = axiom.parse_id(node.id);
								loader.baseParams._prototype = node.attributes._prototype;
								loader.baseParams.id = info.id;
								loader.baseParams.appname = info.app;
							});

		// create array of configs for each application, with root node already attached
		for(var key in apps){
			axiom.apps.push({
				text:    key,
				id:       key,
				iconCls:  '',
				noinfo:   true,
				children: [{text: 'root (Root)', id: key+'#0', _prototype: 'Root'}]
				})
		}

		// instantiate actual object tree
		axiom.tree = new Ext.tree.TreePanel({
						 title:      'Objects',
						 id:         'object_tree',
						 el:         'object_tree',
						 region:     'west',
						 autoScroll: true,
						 animate:    true,
						 split:      true,
						 containerScroll: true,
						 margins:    '10 0 0 10',
						 width:      '200px',
						 root:       new Ext.tree.AsyncTreeNode({ text:   'applications',
																  id:     'apps',
																  noinfo: true,
																  children: axiom.apps
																}),
						 loader:  axiom.treeloader
						 });

		// instantiate main tab panel with introduction. tabs for editing / creating
		// objects will be placed here
		axiom.main = new Ext.TabPanel({
						 region: 'center',
						 id:     'main',
						 title:  'Edit',
						 margins: '10 10 0 0',
 						 enableTabScroll: true,
						 items:[{ id: 'tab1',
								  html: '<img src="static/logo.gif"/><p>Welcome to the Axiom Management Interface!</p><p>Use the object tree to the left to explore your application or enter Javascript with the shell below.</p>',
								  cls: 'home',
    							  title: 'Home',
    							  closable:false,
    							  autoScroll:true
								}],
						 activeTab: 0,
						 plugins: new Ext.ux.TabCloseMenu()
						 });

		// event handlers for object tree
		axiom.tree.on({	click: function(node, evt){
							// left-click handler
							  if(node.attributes.noinfo) return false;
							  var info = axiom.parse_id(node.id);
							  Ext.Ajax.request({
								  url:       'object_info',
								  disableCaching: true,
								  params:   {id: info.id, _prototype: node.attributes._prototype, appname: info.app},
								  success:  function(res, options){
									  var panel = axiom.main.find('id', node.id)[0];
									  if(!panel){
										  panel = new axiom.ObjectForm({
													  id:           node.id,
													  title:        info.app+ ": "+ node.text,
													  items:        Ext.util.JSON.decode(res.responseText),
													  baseParams:   {appname: info.app, _prototype: node.attributes._prototype},
													  buttons:      [{ text: 'Save',
													  handler: function(){
														  panel.getForm().submit({url: 'save_object', waitMsg: 'Saving...'})
															  }}]
														 }
													  );
										  axiom.main.add(panel);
									  }
									  axiom.main.activate(panel);
									  panel.doLayout();
								  }});
						  },
						  contextmenu: function(node, evt){
							  // right-click handler for object tree node
							  var appname = axiom.parse_id(node.id).app;
							  var protos =  apps[appname];
							  var menu = [];

							  // create menu of prototypes available for creation
							  for(var i=0; i<protos.length;i++){
								  menu.push({text: protos[i],
											 handler: function(menunode){
												 var type = menunode.text;
												 Ext.Ajax.request({
													 url: 'add_form',
													 disableCaching: true,
													 params: {_prototype: type, appname: appname},
													 success: function(res, options){
														 var panel = new axiom.ObjectForm({
																		 id:         appname+'#new'+type,
																		 title:      appname + ": new "+type,
																		 items:      Ext.util.JSON.decode(res.responseText),
																		 baseParams: {appname: appname, _prototype: type, _parent: axiom.parse_id(node.id).id},
																		 buttons:    [{text: "Create",
																		 handler: function(){
																			 panel.getForm().submit({url: 'create_object',
																			 success: function(form, action){
																				 var callback = function(){
																					 axiom.main.remove(panel);
																					 var tree = Ext.getCmp('object_tree');
																					 tree.getNodeById(node.id).expand(false, true, function(){
																														  Ext.get(tree.getNodeById(appname+'#'+action.result._id).getUI().getEl()).highlight('ffff33');
																													  });

																				 };
																				 node.reload ? node.reload(callback) : node.parentNode.reload(callback);
																			 },
																			 waitMsg: 'Creating...'});
																		 }}]
																						  })
														 axiom.main.add(panel);
														 axiom.main.activate(panel);
														 panel.doLayout();
													 }
													 })
											 }
											});
							  }

							  // instantiate actual menu upon right-click
							  var contextmenu = new Ext.menu.Menu({ items: [{text: "Add Child", menu: menu},
																			{text: "Delete",
																			 handler: function(){
																				 var info = axiom.parse_id(node.id);
																				 Ext.MessageBox.confirm("Really?",
																										"Delete "+node.text+"?",
																										function(){
																											Ext.Ajax.request({ url: 'delete_object',
																															   params: {_id: info.id,
																																		appname: info.app,
																																		_prototype: node.attributes._prototype},
																															   success: function(){
																																   node.parentNode.reload();
																															   }
																															 })
																										});
																			 }}]
																  });
							  contextmenu.showAt(evt.getXY());
						  }
					  });

		// create datastore for shell history of commands and results
		var shellHistoryStore = new Ext.data.JsonStore({ root: 'commands',
														 fields: ['command'],
														 url: 'inspector_shell',
														 data: {commands: [] },
														 reader: new Ext.data.JsonReader({root: 'commands'}, [ {name: 'command', mapping: 'command'}])
													   });

		// config for toggle menu for switching between applications in shell
		var toggleConf = {
			xtype:'button',
			text: 'toggle',
			handler: function(){
				var multi = Ext.getCmp('multi-shell');
				if(multi.hidden){
					multi.show();
					Ext.getCmp('shell').bbar.hide();
					Ext.getCmp('multi-shell-field').setValue(Ext.getCmp('interp-shellfield').getValue());
				} else {
					multi.hide();
					Ext.getCmp('shell').bbar.show();
					Ext.getCmp('interp-shellfield').setValue(Ext.getCmp('multi-shell-field').getValue().replace(/\n/g,""));
				}
				axiom.shell.doLayout();
			}
		};

		// create menu items for shell application toggle
		var menu_actions = [];
		var handler = function(button, evt){
			Ext.getCmp('app-menu').setText(button.text);
		};
		for(var menu_key in apps){
			menu_actions.push(new Ext.Action({handler: handler,
											  text:    menu_key}));
		}

		// create grid used to display shell command history
		var shellgrid = new Ext.grid.GridPanel({ id: 'shell',
												 region: 'center',
												 split:true,
												 store: shellHistoryStore,
												 columns: [{id: 'commands', dataIndex: 'command'}],
												 viewConfig: {forceFit: true},
												 tbar: ["Shell: ",
														{text: (axiom.apps[0].text || 'No applications found.'), id: 'app-menu', menu: {items: menu_actions}}
													   ],
												 bbar: [">>>",
														"&nbsp;&nbsp;",
														new axiom.ShellField({ id: 'interp-shellfield', store: shellHistoryStore, ctCls: 'field-wrapper'}),
														{xtype: 'tbspacer'},
														"&nbsp;&nbsp;",
														toggleConf]
											   });

		// override so gridview's internal handler doesn't scroll to
		// the top on the datastore's load event.
		var view = shellgrid.getView();
		view.scrollToTop = Ext.emptyFn;
		view.on('rowsinserted', function(view, first, last){
					view.getRow(last).scrollIntoView();
				});

		// create multiline shell panel
		var multiline_shell = new Ext.Panel({
								  id: 'multi-shell',
								  region: 'east',
								  split:true,
								  hidden: true,
								  width:500,
								  layout:'fit',
								  items: [new axiom.MultiShellField({id:'multi-shell-field', store: shellHistoryStore})],
								  bbar: [{ xtype:'tbfill'},
										 { xtype: 'button', text:'run', tooltip: "Execute the code. (Ctrl+Enter)", handler: function(){Ext.getCmp('multi-shell-field').submitShell()}},
										 { xtype: 'tbseparator'},
										 toggleConf],
								  handles: 'w'
								  });
		// create single-line interperter-mode shell
		axiom.shell = new Ext.Panel({
						  id: 'shellcntr',
						  region: 'south',
						  split: true,
						  height: 180,
						  layout: 'border',
						  margins: '0 10 10 10',
						  items: [shellgrid, multiline_shell]
						  });

		// overall layout container
		axiom.nav = new Ext.Viewport({
										 layout: 'border',
										 items:  [axiom.tree, axiom.main, axiom.shell]
									 });
		axiom.tree.root.expand();
	}
}

/**
 *  JS interperter field class, with some basic key event handlers.
 */
axiom.ShellField = Ext.extend(Ext.form.Field,{
	initComponent: function(){
		axiom.ShellField.superclass.initComponent.call(this);
		this.on('specialkey', function(f, e){
            if(e.getKey() == e.ENTER){
                this.submitShell();
				this.setValue('');
            }
		}, this);
	},
	submitShell: axiom.submit
});

/**
 *  Multiline JS shell panel class.
 */
axiom.MultiShellField = Ext.extend(Ext.form.TextArea,{
	initComponent: function(){
		axiom.MultiShellField.superclass.initComponent.call(this);
		this.on('specialkey', function(f, e){
            if(e.getKey() == e.ENTER && e.ctrlKey){
                this.submitShell();
            }
		}, this)
	},
	submitShell: axiom.submit
});

/**
 *  Widget combining date and time fields.
 *  Submits result in number of milliseconds since unix epoch.
 */
axiom.DateAndTimeField = function(config){
	axiom.DateAndTimeField.superclass.constructor.call(this, config);
	this.date = new Ext.form.DateField({ value: (new Date(config.value)) });
	this.time = new Ext.form.TimeField({ value: (new Date(config.value)).format('g:i A') });

	this.date.on('blur', function(){this.calcDate()}, this);
	this.time.on('blur', function(){this.calcDate()}, this);

	this.on('render', function(container, position){
		var wrapper = this.el.insertSibling({html: '<table cellspacing="0" cellpadding="0" border="0"><tr><td> </td><td> </td></tr></table>'}, null, true)
		var cells = Ext.query('td', wrapper);
		this.date.render(cells[0]);
		this.time.render(cells[1]);
		this.el.setStyle({display: 'none'});
	},this);
}
Ext.extend(axiom.DateAndTimeField, Ext.form.Field,{
	calcDate: function(){
		var date = this.date.getValue().getTime();
		var time_obj = Date.parseDate(this.time.getRawValue(), "g:i A");
		var time = time_obj.getHours()*3600000;
		time += time_obj.getMinutes()*60000;
		this.setValue(date+time);
	}
});
Ext.ComponentMgr.registerType('dateandtime', axiom.DateAndTimeField);

/**
 *  Basic FormPanel extension for object creation / editing
 */
axiom.ObjectForm = function(config){
	Ext.applyIf(config, { frame:        true,
						  autoScroll:   true,
						  bodyStyle:    'padding:5px 5px 0',
						  labelWidth:   100,
						  width:        500,
						  labelAlign:   'right',
						  defaultType:  'textfield',
						  closable:     true
						});
	axiom.ObjectForm.superclass.constructor.call(this, config);
}
Ext.extend(axiom.ObjectForm, Ext.form.FormPanel)

Ext.onReady(axiom.init);