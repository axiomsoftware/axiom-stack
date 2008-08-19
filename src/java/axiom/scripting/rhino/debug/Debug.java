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

import java.lang.reflect.Method;
import java.util.*;

import org.mozilla.javascript.*;

import axiom.framework.core.RequestEvaluator;
import axiom.framework.core.Session;
import axiom.framework.repository.Resource;
import axiom.scripting.rhino.RhinoEngine;

/**
 * @jsinstance session.debug
 */
public class Debug extends ScriptableObject {

	HashMap<String, HashSet<Integer>> breakpoints = new HashMap<String, HashSet<Integer>>();
	
	Hashtable<Integer, DebugMonitor> waitingEvaluators = new Hashtable<Integer, DebugMonitor>();
	
	HashMap<String, HashSet<String>> whitelist = new HashMap<String, HashSet<String>>();
	
	Stack<DebugEvent> events = new Stack<DebugEvent>();
	
	Session session;
	
	boolean isOnException = false;
	boolean isOnFunctionEntry = false;
	boolean isOnFunctionExit = false;
	
	public Debug(Object[] args) {
		super();
	}
	
	public static void init(Scriptable scope) throws Exception {
        Method[] methods = Debug.class.getDeclaredMethods();
        ScriptableObject proto = new Debug(null);
        proto.setPrototype(getObjectPrototype(scope));
        
        final int ATTRS = READONLY | DONTENUM | PERMANENT;
        final int length = methods.length;
        for (int i = 0; i < length; i++) {
            String methodName = methods[i].getName();
            if (methodName.startsWith("jsFunction_")) {
                methodName = methodName.substring(11);
                FunctionObject func = new FunctionObject(methodName, methods[i], proto);
                proto.defineProperty(methodName, func, ATTRS);                
            } else if (methodName.startsWith("jsGet_")) {
                methodName = methodName.substring(6);
                proto.defineProperty(methodName, null, methods[i], null, ATTRS);
            } else if (methodName.equals("debugObjCtor")) {
                FunctionObject ctor = new FunctionObject("Debug", methods[i], scope);
                ctor.addAsConstructor(scope, proto);
            }
        }
    }
	
	public static Debug debugObjCtor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) 
    throws Exception {
        return new Debug(args);
    }
	
	public String getClassName() {
        return "Debug";
    }
	
	public void setSession(Session session) {
		this.session = session;
	}
	
	public synchronized void jsFunction_addBreakpoint(Object resource, Object line) {
		String resourceName = getResourceName(resource);
		int lineNum = getNumberFromScriptable(line);
		if (resourceName == null || lineNum < 0) {
			return;
		}
		
		HashSet<Integer> lines = this.breakpoints.get(resourceName);
		if (lines == null) {
			lines = new HashSet<Integer>();
			this.breakpoints.put(resourceName, lines);
		}
		lines.add(new Integer(lineNum));
	}
	
	public synchronized void jsFunction_clearBreakpoint(Object resource, Object line) {
		String resourceName = getResourceName(resource);
		if (resourceName == null) {
			return;
		}
		
		int lineNum = getNumberFromScriptable(line);
		if (lineNum < 0) {
			this.breakpoints.remove(resourceName);
		} else {
			HashSet<Integer> lines = this.breakpoints.get(resourceName);
			if (lines != null) {
				lines.remove(new Integer(lineNum));
			}
		}
	}
	
	public synchronized void jsFunction_clearAllBreakpoints(Object resource) {
		String resourceName = getResourceName(resource);
		if (resourceName != null) {
			this.breakpoints.remove(resourceName);
		} else {
			this.breakpoints.clear();
		}
	}
	
	public synchronized Object jsFunction_popEvent() {
		if (this.events.size() > 0) {
			DebugEvent event = this.events.pop();
			if (this.session != null) {
				RequestEvaluator re = this.session.getApp().getCurrentRequestEvaluator();
				if (re != null) {
					Scriptable scope = ((RhinoEngine) re.getScriptingEngine()).getGlobal();
					return event.toScriptable(Context.getCurrentContext(), scope);
				}
			}
		}
		return Scriptable.NOT_FOUND;
	}
	
	public synchronized int jsFunction_getEventSize() {
		return this.events.size();
	}
	
	public synchronized void pushEvent(DebugEvent event) {
		this.events.push(event);
	}
	
	public void jsFunction_go(Object reqEvalId) {
		int reqId = getNumberFromScriptable(reqEvalId);
		DebugMonitor monitor = this.waitingEvaluators.remove(new Integer(reqId));
		if (monitor != null) {
			synchronized (monitor) {
				monitor.isStepping = false;
				monitor.notifyAll();
			}
		}
	}
	
	public void jsFunction_step(Object reqEvalId) {
		int reqId = getNumberFromScriptable(reqEvalId);
		DebugMonitor monitor = this.waitingEvaluators.remove(new Integer(reqId));
		if (monitor != null) {
			synchronized (monitor) {
				monitor.isStepping = true;
				monitor.isJumping = false;
				monitor.notifyAll();
			}
		}
	}
	
	public void jsFunction_stepOver(Object reqEvalId) {
		int reqId = getNumberFromScriptable(reqEvalId);
		DebugMonitor monitor = this.waitingEvaluators.remove(new Integer(reqId));
		if (monitor != null) {
			synchronized (monitor) {
				monitor.isStepping = true;
				monitor.isJumping = true;
				monitor.notifyAll();
			}
		}
	}
	
	public void jsFunction_continueAll() {
		synchronized (this.waitingEvaluators) {
			Iterator<Integer> iter = this.waitingEvaluators.keySet().iterator();
			while (iter.hasNext()) {
				Integer key = iter.next();
				DebugMonitor monitor = this.waitingEvaluators.remove(key);
				if (monitor != null) {
					synchronized (monitor) {
						monitor.isStepping = false;
						monitor.notifyAll();
					}
				}
			}
		}
	}
	
	public synchronized DebugMonitor addWaitingEvaluator(RequestEvaluator re) {
		DebugMonitor monitor = new DebugMonitor();
		this.waitingEvaluators.put(new Integer(re.hashCode()), monitor);
		return monitor;
	}
	
	public synchronized boolean isBreakpointSet(Object resource, int lineNum) {
		String resourceName = getResourceName(resource);
		if (resourceName != null) {
			HashSet<Integer> lineNums = this.breakpoints.get(resourceName);
			if (lineNums != null) {
				return lineNums.contains(new Integer(lineNum));
			}
		}
		return false;
	}
	
	public synchronized void jsFunction_setWhitelist(Object list) {
		if (list instanceof Scriptable) {
			this.whitelist.clear();
			Scriptable s = (Scriptable) list;
			Object[] protoIds = s.getIds();
			for (int i = 0; i < protoIds.length; i++) {
				String protoName = (String) protoIds[i];
				Object plist = s.get(protoName, s);
				HashSet<String> funcs = new HashSet<String>();
				
				if (plist instanceof String) {
					funcs.add((String) plist);
				} else if (plist instanceof Scriptable && "String".equalsIgnoreCase(((Scriptable) plist).getClassName())) {
					funcs.add(ScriptRuntime.toString(plist));
				} else if (plist instanceof NativeArray) {
					NativeArray na = (NativeArray) plist;
					int length = (int) na.getLength();
					for (int j = 0; j < length; j++) {
						funcs.add(ScriptRuntime.toString(na.get(j, na)));
					}
				}
				
				this.whitelist.put(protoName, funcs);
			}
		}
	}
	
	public synchronized boolean isWhitelisted(String protoName, String funcName) {
		if (protoName == null) {
			return false;
		}
		if (funcName != null && "".equals(funcName)) {
			return true;
		}
		HashSet<String> funcs = this.whitelist.get(protoName);
		if (funcs != null) {
			if (funcs.size() == 0) {
				return true;
			}
			if (funcName == null) {
				return false;
			}
			return funcs.contains(funcName);
		}
		return false;
	}
	
	public synchronized Scriptable jsFunction_getBreakpoints(Object resource) {
		String resourceName = getResourceName(resource);
		Object[] breakpoints;
		if (resourceName != null) {
			HashSet<Integer> lines = this.breakpoints.get(resourceName);
			if (lines != null) {
				breakpoints = lines.toArray();
			} else {
				breakpoints = new Object[0];
			}
		} else {
			breakpoints = new Object[0];
		}
		
		RequestEvaluator re = this.session.getApp().getCurrentRequestEvaluator();
		Scriptable scope;
		if (re != null) {
			scope = ((RhinoEngine) re.getScriptingEngine()).getGlobal();
		} else {
			scope = RhinoEngine.getRhinoCore(this.session.getApp()).getScope();
		}
		
		return Context.getCurrentContext().newArray(scope, breakpoints);
	}
	
	public synchronized void jsFunction_setBreakOnException(Object value) {
		this.isOnException = getBooleanFromScriptable(value);
	}
	
	public synchronized void jsFunction_setBreakOnFunctionEntry(Object value) {
		this.isOnFunctionEntry = getBooleanFromScriptable(value);
	}
	
	public synchronized void jsFunction_setBreakOnFunctionExit(Object value) {
		this.isOnFunctionExit = getBooleanFromScriptable(value);
	}
	
	public synchronized boolean jsFunction_getBreakOnException() {
		return this.isOnException;
	}
	
	public synchronized boolean jsFunction_getBreakOnFunctionEntry() {
		return this.isOnFunctionEntry;
	}
	
	public synchronized boolean jsFunction_getBreakOnFunctionExit() {
		return this.isOnFunctionExit;
	}
	
	private static String getResourceName(Object resource) {
		if (resource == null || resource == Undefined.instance) {
			return null;
		}
		
		if (resource instanceof NativeJavaObject) {
			NativeJavaObject njo = (NativeJavaObject) resource;
			resource = njo.unwrap();
		}
		
		if (resource instanceof Resource) {
			return ((Resource) resource).getName();
		} else if (resource instanceof String) {
			return (String) resource;
		} else if (resource instanceof Scriptable && "String".equals(((Scriptable) resource).getClassName())) {
			return ScriptRuntime.toString(resource);
		} 
		
		return null;
	}
	
	private static int getNumberFromScriptable(Object number) {
		if (number == null || number == Undefined.instance) {
			return -1;
		}
		if (number instanceof Number) {
			return ((Number) number).intValue();
		} else if (number instanceof Scriptable && "Number".equals(((Scriptable) number).getClassName())) {
			return (int) ScriptRuntime.toNumber(number);
		}
		return -1;
	}
	
	private static boolean getBooleanFromScriptable(Object value) {
		if (value == null || value == Undefined.instance) {
			return false;
		}
		if (value instanceof Boolean) {
			return ((Boolean) value).booleanValue();
		} else if (value instanceof Scriptable && "Boolean".equals(((Scriptable) value).getClassName())) {
			return ScriptRuntime.toBoolean(value);
		}
		return false;
	}
}