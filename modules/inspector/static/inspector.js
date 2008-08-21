/**
 *  Copyright 2007-2008 Axiom Software Inc.
 *  This file is part of the Axiom Inspector Application.
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

var axiom = {
	submit: function(){
		// shell submission handler
		this.store.baseParams = (this.store.baseParams || {});
		this.store.baseParams.commands = this.getRawValue();
		this.store.load({ add: true,
						  shell: this,
						  callback: function(records, options, success){
							  if(success && typeof options.shell.history_index != "undefined"){
								  // reset current shell command index to last command
								  options.shell.history_index = options.shell.store.getCount();
							  }
						  }
						});
	},
	error: function(str){
		// display an error window with str for content
		Ext.MessageBox.show({ msg: str,
							  title: "Error",
							  icon: Ext.MessageBox.ERROR,
							  buttons: Ext.MessageBox.OK
							});
	},
	http_failure_handler: function(a,b,c){
		if(a instanceof Ext.form.BasicForm){
			// form submission failure
			axiom.error("Form submission failure.  Server returned: "+b.response.responseText);
		} else if(c){
			// shell submission failure
			axiom.error("Shell invocation failed. Server returned: "+c.responseText);
		} else {
			// ajax failure
			axiom.error("Action failed. Server returned: "+a.responseText);
		}
	},
	on_form_success: function(form, action, node, panel){
		var callback = function(){
			axiom.main.remove(panel);
			var tree = Ext.getCmp('object_tree');
			tree.getNodeById(node.id).expand(false, true, function(){ Ext.get(tree.getNodeById(action.result._id).getUI().getEl()).highlight('ffff33'); });
		};
		if(node.reload){
			node.reload(callback);
		} else {
			node.parentNode.reload(callback);
		}
	},
	load_resource: function(resource, callback){
		Ext.Ajax.request({ url: handler_url,
						   disableCaching: true,
						   params: {
							   method: 'load_resource',
							   resource: resource+''
						   },
						   success: function(res, options){
							   var panel = Ext.getCmp(resource);
							   if(!panel){
								   var data = Ext.decode(res.responseText);
								   panel = new axiom.DebugPanel({  id:           resource,
																   tabTip:  	 resource,
																   title:        resource,
																   line_data:    data.line_data,
																   breakpoints:  data.breakpoints,
																   break_on_function_entry: data.break_on_function_entry,
																   break_on_function_exit: data.break_on_function_exit,
																   break_on_exceptions: data.break_on_exceptions
																});
								   axiom.main.add(panel);
							   }
							   axiom.main.activate(panel);
							   axiom.main.doLayout();
							   if(typeof callback == 'function'){
								   callback(panel);
							   }
						   },
						   failure: axiom.http_failure_handler
						 });
	},
	clear_all_breakpoint_markers: function(){
		Ext.each(Ext.query('.breakpoint'),function(n){Ext.get(n).removeClass('breakpoint');});
	},
	init: function() {
		// application initialization function

		Ext.QuickTips.init();

		// create a treeloader to talk to the object-listing service
		axiom.treeloader = new Ext.tree.TreeLoader({ url:        handler_url,
													 baseAttrs:  {iconCls: 'object-icon'}
												   });
		axiom.treeloader.on("beforeload", function(loader, node) {
								loader.baseParams._prototype = node.attributes._prototype;
								loader.baseParams.method = 'nodes';
								loader.baseParams._id = node.id;
							});

		// instantiate object tree
		axiom.tree = new Ext.tree.TreePanel({ title:      'Objects',
											  id:         'object_tree',
											  autoScroll: true,
											  animate:    true,
											  collapsible: true,
											  containerScroll: true,
											  root:       new Ext.tree.AsyncTreeNode({text: 'root',
																					  id: '0',
																					  qtip: "Root",
																					  leaf: false,
																					  _prototype: 'Root',
																					  iconCls: 'object-icon'}),
											  loader:     axiom.treeloader
											});

		axiom.prototypes = new Ext.tree.TreePanel({ title: 'Prototypes',
													id:    'prototypes-tree',
													root:  new Ext.tree.AsyncTreeNode({ text: 'prototypes', id: '__get_prototypes__'}),
													rootVisible: false,
													loader:	new Ext.tree.TreeLoader({ url: handler_url,
																					  baseParams: { method: 'prototype_resources'},
																					  baseAttrs:  {listeners: {click: function(){
																												   if(this.attributes.leaf){
																													   axiom.load_resource(this.id);
																												   }
																											   }
																											  }}
																					})
												  });

		// tab panel to hold object and prototype trees
		var treecontainer = new Ext.TabPanel({ id:       'tree_panel',
											   margins:  '10 0 0 10',
											   region:     'west',
											   split:      true,
											   width:    '260px',
											   activeTab: 0,
											   items:     [axiom.tree, axiom.prototypes]
											 });

		// instantiate main tab panel with introduction. tabs for editing / creating
		// objects will be placed here
		axiom.main = new Ext.TabPanel({ region: 'center',
										id:     'main',
										title:  'Edit',
										margins: '10 10 0 0',
 										enableTabScroll: true,
										items:[{ id: 'home-tab',
												 el: 'home-tab',
												 cls: 'home',
    											 title: 'Home',
    											 closable:false,
    											 autoScroll:true
										}],
										listeners: {
											tabchange: function(self, tab){
												var node = Ext.getCmp('object_tree').getNodeById(self.getActiveTab().id);
												if(node){
													axiom.tree.selectPath(node.getPath());
												}

											}
										},
										activeTab: 0,
										plugins: new Ext.ux.TabCloseMenu()
									  });

		// event handlers for object tree
		axiom.tree.on({	click: function(node, evt){
							// left-click handler
							if(node.attributes.noinfo) return false;
							Ext.Ajax.request({
								  url:       handler_url,
								  disableCaching: true,
								  params:   {id: node.id, method: 'object_info', _prototype: node.attributes._prototype},
								  success:  function(res, options){
									  var panel = axiom.main.find('id', node.id)[0];
									  if(!panel){
										  panel = new axiom.ObjectForm({
													  id:           node.id,
													  tabTip:		node.attributes.path,
													  title:        node.text,
													  items:        Ext.decode(res.responseText),
													  baseParams:   {_prototype: node.attributes._prototype, method: 'save_object'},
													  buttons:      [{ text: 'Save',
														               handler: function(){
																		   panel.getForm().submit({url: handler_url,
																								   waitMsg: 'Saving...',
																								   success: function(form, action){
 																								     axiom.on_form_success(form, action, node, panel);
 																								   },
																								   failure: axiom.http_failure_handler
																								 });
																	   }
																	 }]
														 }
													  );
										  axiom.main.add(panel);
									  }
									  axiom.main.activate(panel);
									  panel.doLayout();
								  },
								  failure: axiom.http_failure_handler
							});
						  },
						  contextmenu: function(node, evt){
							  // right-click handler for object tree node

							  // create menu of prototypes available for creation
							  var menu = [];
							  for(var i=0; i<prototypes.length;i++){
								  menu.push({text: prototypes[i],
											 handler: function(menunode){
												 var type = menunode.text;
												 Ext.Ajax.request({
													 url: handler_url,
													 disableCaching: true,
													 params: {method: 'add_form', _prototype: type},
													 success: function(res, options){
														 var items = Ext.util.JSON.decode(res.responseText);
														 var panel = new axiom.ObjectForm({
																		 id:         'new'+type,
																		 title:      "new "+type,
																		 fileUpload: items.fileUpload,
																		 items:      items,
																		 baseParams: {_prototype: type, _parent: node.id, method: 'create_object'},
																		 buttons:    [{	text: "Create",
																						handler: function(){
																							panel.getForm().submit({
																								url: handler_url,
 																								success: function(form, action){
																									axiom.on_form_success(form, action, node, panel);
																								},
																								failure: axiom.http_failure_handler,
																								waitMsg: 'Creating...'});
																						}}]
																			 });
														 axiom.main.add(panel);
														 axiom.main.activate(panel);
														 panel.doLayout();
													 },
													 failure: axiom.http_failure_handler
													 });
											 }
											});
							  }

							  // instantiate actual menu upon right-click
							  var contextmenu = new Ext.menu.Menu({ items: [{text: "Add Child", menu: menu},
																			{text: "Refresh", handler: function(){node.reload();}},
																			{text: "Delete",
																			 handler: function(){
																				 Ext.MessageBox.confirm("Really?",
																										"Delete "+node.text+"?",
																										function(btn){
																											if(btn == 'yes'){
																												Ext.Ajax.request({ url: handler_url,
																																   params: { method: 'delete_object',
																																			 _id: node.id,
																																			 _prototype: node.attributes._prototype},
																																   success: function(response, options){
																																	   var res = Ext.util.JSON.decode(response.responseText);
																																	   if(!res.success){
																																		   axiom.error(res.message);
																																	   }
																																	   node.parentNode.reload();
																																   },
																																   failure: axiom.http_failure_handler
																																 });
																												}
																										});
																			 }}]
																  });
							  contextmenu.showAt(evt.getXY());
						  }
					  });

		// config for toggle menu for switching between multiline / single-line mode
		var toggleConf = {
			xtype: 'button',
			text: 'toggle',
			tooltip: "Switch between single and multiline shell.",
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

		// create datastore for shell history of commands and results
		var shellHistoryStore = new Ext.data.Store({ proxy: new Ext.data.HttpProxy({url: handler_url}),
													 baseParams: {method:'shell'},
													 reader: new Ext.data.JsonReader({root: 'items', fields: ['command', 'prints', 'results']}),
													 listeners: {
														 loadexception: axiom.http_failure_handler
													 }
												   });

		// create grid used to display shell command history
		var shellgrid = new Ext.grid.GridPanel({ id: 'shell',
												 region: 'center',
												 split:true,
												 store: shellHistoryStore,
												 columns: [{dataIndex: 'command', renderer: function(val, metadata, record){
																return String.format(">>> {0}<br/><i>{1}</i><br/><b>{2}</b>", record.data.command, record.data.prints.join("<br/>"), record.data.results);
															}}],
												 viewConfig: {forceFit: true},
												 tbar : ["Javascript Shell", "->", new Ext.Button({text:'logout',
																								   handler: function(){
																									   Ext.Ajax.request({ url: handler_url,
																														  params: { method: 'logout' },
																														  success: function(){window.location = handler_url;},
																														  failure: axiom.http_failure_handler
																														});
																								   }
																								  })],
												 bbar: [">>>",
														"&nbsp;&nbsp;",
														new axiom.ShellField({ id: 'interp-shellfield', store: shellHistoryStore, ctCls: 'field-wrapper'}),
														{xtype: 'tbspacer'},
														"&nbsp;&nbsp;",
														toggleConf]
											   });
		// override the gridview rowinserted handler to prevent
		// scrolling to the top on the datastore load event.
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
										 { xtype: 'button', text:'run', tooltip: "Execute the code. (Ctrl+Enter)", handler: function(){Ext.getCmp('multi-shell-field').submitShell();}},
										 { xtype: 'tbseparator'},
										 toggleConf],
								  handles: 'w'
								  });

		var debugger_scope = { id: 'debug-scope',
							   region: 'west',
							   xtype: 'grid',
							   store: new Ext.data.GroupingStore({ reader: new Ext.data.ArrayReader({},
																									[{name: 'name'},
																									 {name: 'type'},
																									 {name: 'value'},
																									 {name: 'scope'}]),
																   data: [],
																   groupField: 'scope',
																   sortInfo:{field: 'scope', direction: "DESC"}
																 }),
							   view: new Ext.grid.GroupingView({ forceFit:true,
																 groupTextTpl: '{text}'
															   }),
							   columns: [
								   {header: "Name", dataIndex: 'name'},
								   {header: "Type", dataIndex: 'type'},
								   {header: "Value", dataIndex: 'value'},
								   {header: "Scope", dataIndex: 'scope', hidden: true}
							   ],
							   title: "Debugger",
							   split: true,
							   width: 300,
							   collapsible: true,
							   autoScroll:true
							 };

		// create single-line interperter-mode shell
		axiom.shell = new Ext.TabPanel({  margins: '0 10 10 10',
										  region: 'south',
										  split: true,
										  height: 180,
										  activeTab: 0,
										  items:[{ id: 'shellcntr',
												   title: "Shell",
												   activeTab: 0,
												   layout: 'border',
												   items: 	[shellgrid, multiline_shell]
												 }, debugger_scope]
									   });

		// overall layout container
		axiom.nav = new Ext.Viewport({ layout: 'border',
									   items:  [treecontainer, axiom.main, axiom.shell]
									 });
		axiom.tree.root.expand();

		axiom.dispatcher = new axiom.DebugDispatcher();
		axiom.dispatcher.start();
	}
};

/**
 * JS interperter field class, with some basic key event handlers.
 */
axiom.ShellField = Ext.extend(Ext.form.Field,{
	initComponent: function(){
		axiom.ShellField.superclass.initComponent.call(this);
		this.history_index = 0;
		this.on('specialkey', function(f, e){
			switch(e.getKey()){
            case e.ENTER:
                this.submitShell();
				this.setValue('');
				break;
			case e.UP:
				if(this.history_index > 0){
					this.history_index--;
				}
				var hist = this.store.getAt(this.history_index);
				if(hist){
					this.setValue(hist.get('command'));
				}
				e.stopEvent();
				break;
			case e.DOWN:
				var count = this.store.getCount();
				if(this.history_index+1 == count){
					this.history_index++;
					this.setValue('');
				} else if(this.history_index < count){
					this.history_index++;
					this.setValue(this.store.getAt(this.history_index).get("command"));
				}
				e.stopEvent();
				break;
            }
		}, this);
	},
	submitShell: axiom.submit
});

/**
 *  Multiline JS shell panel class.
 */
axiom.MultiShellField = Ext.extend(Ext.form.TextArea,{
	initComponent: function(config){
		Ext.applyIf(config, {shim: false});
		axiom.MultiShellField.superclass.initComponent.call(this, config);
		this.on('specialkey', function(f, e){
            if(e.getKey() == e.ENTER && e.ctrlKey){
                this.submitShell();
            }
		}, this);
	},
	submitShell: axiom.submit
});

/**
 *  Widget combining date and time fields.
 *  Submits result in number of milliseconds since unix epoch.
 */
axiom.DateAndTimeField = function(config){
	axiom.DateAndTimeField.superclass.constructor.call(this, config);
	this.date = new Ext.form.DateField({ value: config.value ? (new Date(config.value)) : '' });
	this.time = new Ext.form.TimeField({ value: (new Date(config.value)).format('g:i A') });

	this.date.on('blur', function(){this.calcDate();}, this);
	this.time.on('blur', function(){this.calcDate();}, this);

	this.on('render', function(container, position){
				var wrapper = this.el.insertSibling({html: '<table cellspacing="0" cellpadding="0" border="0"><tr><td> </td><td> </td></tr></table>'}, null, true);
				var cells = Ext.query('td', wrapper);
				this.date.render(cells[0]);
				this.time.render(cells[1]);
				this.el.setStyle({display: 'none'});
	},this);
};
Ext.extend(axiom.DateAndTimeField, Ext.form.Field,{
	calcDate: function(){
		var date_val = this.date.getValue();
		var date = 0;
		if(date_val){
			date = date_val.getTime();
		}
		var time_obj = Date.parseDate(this.time.getRawValue(), "g:i A");
		var time = 0;
		if(time_obj){
			time = (time_obj.getHours()*3600000) + (time_obj.getMinutes()*60000);
		}
		this.setValue(date+time);
	}
});
Ext.ComponentMgr.registerType('dateandtime', axiom.DateAndTimeField);

/**
 * Form component for boolean values
 */
axiom.AXBoolean = function(config){
	Ext.applyIf(config,	{ horizontal: true,
						  radios: [{ value:'true',  boxLabel:'true', checked: config.value},
								   { value:'false', boxLabel:'false', checked: !config.value}]
						});

	axiom.AXBoolean.superclass.constructor.call(this, config);
};
Ext.extend(axiom.AXBoolean, Ext.ux.RadioGroup);
Ext.ComponentMgr.registerType('axboolean', axiom.AXBoolean);

/**
 * Textfield extension to handle undefined values more sensibly for AxiomObjects
 */
axiom.AXTextField = function(config){
	axiom.AXTextField.superclass.constructor.call(this, config);
	if(typeof config.value == 'undefined'){
		this.setValue("undefined");
		this.value_undefined = true;
	}
	this.on({ render: function(){
				  if(this.value_undefined){
					  this.getEl().addClass('axtextfield-undefined');
				  }
			  },
			  change: function(field, new_val, old_val){
				  if(this.value_undefined && new_val != '' && old_val == 'undefined'){
					  this.value_undefined = false;
				  }
			  },
			  focus: function(){
				  if(this.value_undefined){
					  this.getEl().removeClass('axtextfield-undefined');
					  this.setValue('');
				  }
			  },
			  blur: function(){
				  if(this.value_undefined){
					  this.getEl().addClass('axtextfield-undefined');
					  this.setValue('undefined');
				  }
			  }
			});
};
Ext.extend(axiom.AXTextField, Ext.form.TextField, {
			   isDirty: function(){
				   return !this.value_undefined && axiom.AXTextfield.superclass.isDirty.call(this);
			   }
});
Ext.ComponentMgr.registerType('axtextfield', axiom.AXTextField);

/**
 *  Basic FormPanel extension for object creation / editing
 */
axiom.ObjectForm = function(config){
	Ext.applyIf(config, { frame:        true,
						  autoScroll:   true,
						  bodyStyle:    'padding:5px 5px 0',
						  labelWidth:   200,
						  width:        500,
						  labelAlign:   'right',
						  defaultType:  'textfield',
						  closable:     true
						});
	axiom.ObjectForm.superclass.constructor.call(this, config);
};
Ext.extend(axiom.ObjectForm, Ext.form.FormPanel);

/**
 * Panel extension for displaying debug information
 */
axiom.DebugPanel = function(config){
	var store = new Ext.data.SimpleStore({ fields: [{name: 'line_text'}] });
	store.loadData(config.line_data);
	Ext.applyIf(config, { store: store,
						  columns: [
							  new Ext.grid.RowNumberer(),
							  {header: "File", dataIndex: 'line_text', id: 'line_text', sortable: false}
						  ],
						  tbar: [{xtype: 'button',
								  text: 'Step',
								  handler: function(){this.debug_call('step');},
								  scope: this
								 },
								 {xtype: 'button',
								  text: 'Step Over',
								  handler: function(){this.debug_call('step_over');},
								  scope: this
								 },
								 {xtype: 'button',
								  text: 'Go',
								  handler: function(){this.debug_call('go');},
								  scope: this},
								 {xtype: 'button',
								  text: 'Clear All Breakpoints',
								  handler: function(){
									  this.debug_call('clear_all_breakpoints');
									  axiom.clear_all_breakpoint_markers();
								  },
								  scope: this},
								 {text:"Break On", menu: {items: [{text: 'Exceptions',
																   checked: (config.break_on_exceptions || false),
																   checkHandler: function(item, checked){this.toggle_break_on('exceptions', checked);},
																   scope: this
																  },
																  {text: 'Function Entry',
																   checked: (config.break_on_function_entry || false),
																   checkHandler: function(item, checked){this.toggle_break_on('entry', checked);},
																   scope: this
																  },
																  {text: 'Function Exit',
																   checked: (config.break_on_function_exit || false),
																   checkHandler: function(item, checked){this.toggle_break_on('exit', checked);},
																   scope: this
																  }]
														 }}
								],
						  autoExpandColumn: 'line_text',
						  height:350,
						  width:600,
						  closable: true,
						  hideHeaders: true,
						  listeners: {
							  cellclick: function(grid, row, col, evt){
								  if(col == 0) grid.toggle_breakpoint(row);
							  },
							  render: 	function(grid){
								  Ext.each(grid.initialConfig.breakpoints, grid.display_breakpoint, grid);
							  }
						  }
						});
	axiom.DebugPanel.superclass.constructor.call(this, config);
	this.getSelectionModel().lock();
};
Ext.extend(axiom.DebugPanel, Ext.grid.GridPanel, {
	toggle_breakpoint: function(line_num){
		var line_num_el = Ext.get(this.getView().getCell(line_num, 0));
		var method;
		if(line_num_el.hasClass('breakpoint')){
			line_num_el.removeClass('breakpoint');
			method = 'remove_breakpoint';
		} else {
			line_num_el.addClass('breakpoint');
			method = 'add_breakpoint';
		}
		Ext.Ajax.request({url: handler_url,
						  params: {
							  method: method,
							  line: line_num+1,
							  resource: this.title+''
						  }
						 });
	},
	display_breakpoint: function(line_num){
		Ext.get(this.getView().getCell(line_num-1,0)).addClass('breakpoint');
	},
	toggle_break_on: function(name, val){
		this.debug_call('break_on_'+name, {value: val});
	},
	debug_call: function(method, extra_params){
		var params = Ext.applyIf({ method: method,
								   resource: this.title,
								   debug_id: axiom.dispatcher.debug_id
								 }, extra_params);
		Ext.Ajax.request({url: handler_url, params: params});
	},
	go_to_line: function(num){
		var select_model = this.getSelectionModel();
		select_model.unlock();
		select_model.selectRow(num-1);
		select_model.lock();
		this.getView().getRow(num-1).scrollIntoView();
	}
});

/**
 * Class to poll for and distribute debug events
 */
axiom.DebugDispatcher = function(){
	this.poll_debug_events = {
		run: function(){ this.poll();},
		scope: this,
		interval: 4000
	};
};
Ext.extend(axiom.DebugDispatcher, Ext.util.Observable,{
	start: function(){
		Ext.TaskMgr.start(this.poll_debug_events);
	},
	stop: function(){
		Ext.TaskMgr.stop(this.poll_debug_events);
	},
	poll: function(){
		Ext.Ajax.request({
				url: handler_url,
				params: { method: 'get_debug_events' },
				success: function(res, config){
					if(res.responseText != '0'){
						var results = Ext.decode(res.responseText);
						this[results.event].call(this, results);
					}
				},
				scope:this
		});
	},
	breakpoint_hit: function(data){
		this.debug_id = data.debug_id;
		axiom.load_resource(data.resource, function(panel){
								panel.go_to_line(data.line);
								var scope = Ext.getCmp('debug-scope');
								scope.getStore().loadData(data.scope);
								axiom.shell.activate(scope);
								var exception = data.exception;
								if(exception){
									axiom.shell.add(new Ext.Panel({ title: 'Exception',
																	html: String.format('<b>{0}</b><br/><pre>{1}</pre>',
																						exception.message,
																						exception.trace),
																	closable: true
																  }));
								}
							});
	}
});

/**
 * Override gridview's cell rendering so the text is selectable
 */
Ext.override(Ext.grid.GridView, {
    templates: {
        cell: new Ext.Template(
                    '<td class="x-grid3-col x-grid3-cell x-grid3-td-{id} {css}" style="{style}" tabIndex="0" {cellAttr}>',
                    '<div class="x-grid3-cell-inner x-grid3-col-{id}" {attr}>{value}</div>',
                    "</td>"
            )
    }
});

Ext.onReady(axiom.init);