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
let(props = app.getProperties(), mountpoint=0){
	if(props['inspector.user'] && props['inspector.password']){
		mountpoint = props['inspector.mountpoint'] || 'inspector';
		if(this[mountpoint]){
			app.log("Could not attach inspector to mountpoint "+
					mountpoint+" - property or function of the same name alreay exists.");
		} else {
			this[mountpoint] = function(){
				app.log('in Root::inspect');
				if(req.data.method == 'authenticate' || session.data.inspector == true){
					return AXInspector.prototype[req.data.method || 'main'].call(AXInspector.prototype, req.data);
				} else{
					return AXInspector.prototype.main.call(AXInspector.prototype, {login: true});
				}
			};
		}
	}
};