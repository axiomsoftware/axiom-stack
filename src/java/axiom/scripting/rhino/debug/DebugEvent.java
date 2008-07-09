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