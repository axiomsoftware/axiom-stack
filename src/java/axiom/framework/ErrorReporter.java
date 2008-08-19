/*
 * Axiom Stack Web Application Framework
 * Copyright (C) 2008  Axiom Software Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */
package axiom.framework;

public class ErrorReporter {

	public static String errorMsg(Class theClass, String method) {
		return "Error in " + theClass.getName() + "." + method + "(): ";
	}
	
	public static String warningMsg(Class theClass, String method) {
		return "Warning in " + theClass.getName() + "." + method + "(): ";
	}
	
	public static String fatalErrorMsg(Class theClass, String method) {
		return "Fatal Error in " + theClass.getName() + "." + method + "(): ";
	}
	
	public static String ctorErrorMsg(Class theClass) {
		return "Error in " + theClass.getName() + ".ctor: ";
	}
	
}