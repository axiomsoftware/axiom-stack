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

Ext.onReady(function(){
	var submitter = function(obj, evt){
		if(evt.getKey() == evt.ENTER || (evt.type == 'click' && (/button/i).test(evt.target.tagName))){
			Ext.getCmp("login-panel").submit();
		}
	};
	var panel = new Ext.FormPanel({ id:           "login-panel",
									cls:          "login-panel",
									url:          handler_url,
									standardSubmit: true,
									onSubmit:     Ext.emptyFn,
									submit:       function() {
										this.getForm().getEl().dom.submit();
									},
									items:
										login_errors.concat([{ layout: "column",
															  items:  [{columnWidth: .5,
															   layout:     'form',
															   defaults: {width: 200},
                											   items:    [ {name: 'username', xtype: 'textfield', fieldLabel: 'Username',
																			anchor:'95%', listeners: {specialkey: submitter}},
                        												   {name: 'password', xtype: 'textfield', inputType:'password',
																			fieldLabel: 'Password', anchor:'95%', listeners: {specialkey: submitter}},
																		   {name: 'method', xtype: 'textfield', inputType: 'hidden', value: 'authenticate'}]
															  },
															  {columnWidth: .5,
															   layout:     'form',
															   html:       '<div class="logo-wrap"><img src="'+static_mountpoint+'/logo.gif"/></div>'
			 												  }]
												   }]),
									buttons:      [{ text: 'Login', handler: submitter, type:'submit'}],
									frame:        true,
									bodyStyle:    'padding:5px 5px 0',
									width:        700,
									shadow:       true
								  });
	panel.render(document.body);

});