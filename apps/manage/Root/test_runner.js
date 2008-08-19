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

function run_all(){
	return this.wrap({content: 'test_results',
					  results: this.dispatchShell('axiom.Test.run(axiom.Test.globalSuite())', req.data.app_name)
					 });
}