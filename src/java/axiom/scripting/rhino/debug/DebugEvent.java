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
package axiom.scripting.rhino.debug;

import org.mozilla.javascript.*;

class DebugEvent {
	Scriptable scope;
	Scriptable parentScope;
	Scriptable thisObj;
	String resourceName;
	int lineNumber = -1;
	String functionName;
	int requestId = -1;
	Throwable exception;
	String eventType = BREAKPOINT;
	
	static String BREAKPOINT = "breakpoint";
	static String EXCEPTION = "exception";
	static String FUNCTION_ENTRY = "functionEntry";
	static String FUNCTION_EXIT = "functionExit";
	
	Object toScriptable(Context cx, Scriptable scope) {
		Scriptable s = cx.newObject(scope);
		if (this.resourceName != null) { s.put("resourceName", s, this.resourceName); }
		if (this.lineNumber != -1) { s.put("lineNumber", s, new Integer(this.lineNumber)); }
		if (this.functionName != null) { s.put("functionName", s, this.functionName); }
		if (this.scope != null) { s.put("scope", s, this.scope); }
		if (this.parentScope != null) { s.put("parentScope", s, this.parentScope); }
		if (this.thisObj != null) { s.put("thisObj", s, this.thisObj); }
		if (this.requestId != -1) { s.put("requestId", s, new Integer(this.requestId)); }
		if (this.exception != null) { s.put("exception", s, this.exception); }
		if (this.eventType != null) { s.put("eventType", s, this.eventType); }
		return s;
	}
}