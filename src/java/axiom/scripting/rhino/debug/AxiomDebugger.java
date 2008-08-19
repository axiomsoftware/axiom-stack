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

import java.util.Arrays;
import java.util.HashMap;

import org.mozilla.javascript.*;
import org.mozilla.javascript.debug.*;

import axiom.framework.ErrorReporter;
import axiom.framework.core.Application;
import axiom.framework.core.RequestEvaluator;
import axiom.framework.core.Session;
import axiom.scripting.rhino.AxiomObject;
import axiom.scripting.rhino.RhinoEngine;

public class AxiomDebugger implements Debugger {

	private Application app;
	private int callDepth = 0;
	private boolean isStepping = false;
	private boolean isJumping = false;
	
	public AxiomDebugger(Application app) {
		this.app = app;
	}
	
	public void handleCompilationDone(Context cx, DebuggableScript fnOrScript, String source) { }

	public DebugFrame getFrame(Context cx, DebuggableScript fnOrScript) {
		return new AxiomDebugFrame(this.app, fnOrScript);
	}
	
	private static HashMap<String, AxiomDebugger> debuggers = new HashMap<String, AxiomDebugger>();
	
	public static synchronized AxiomDebugger getDebugger(Application app) {
		AxiomDebugger debugger = debuggers.get(app.getName());
		if (debugger == null) {
			debugger = new AxiomDebugger(app);
			debuggers.put(app.getName(), debugger);
		}
		return debugger;
	}
	
	public static synchronized void removeDebugger(Application app) {
		debuggers.remove(app.getName());
	}
	
	class AxiomDebugFrame implements DebugFrame	{
		
	    Application app;
		DebuggableScript script;
		Scriptable activation;
		Scriptable thisObj;
		int[] sortedLineNums = null;
	    
	    AxiomDebugFrame(Application app, DebuggableScript script) {
	    	this.app = app;
	        this.script = script;
	    }
	    
	    public void onDebuggerStatement(Context cx) { }
	    
	    public void onEnter(Context cx, Scriptable activation, Scriptable thisObj, Object[] args) { 
	    	this.activation = activation;
	    	this.thisObj = thisObj;
	    	
	    	AxiomDebugger debugger = (AxiomDebugger) cx.getDebugger();
	    	if (debugger != null) {
	    		if (skipFunction() || debugger.isJumping) {
	    			synchronized (debugger) {
	    				debugger.callDepth++;
	    			}
	    			return;
	    		}
	    		
	    		boolean ignoreDebugging = false;
	    		synchronized (debugger) {
	    			if (debugger.callDepth > 0) {
	    				ignoreDebugging = true;
	    			}
	    		}
	    		if (ignoreDebugging) {
	    			return;
	    		}
	    	}
	    	
	    	RequestEvaluator re = this.app.getCurrentRequestEvaluator();
	    	if (re != null && isOnFunctionEnterSet()) {
	    		debugger.isStepping = false;
	    		Debug debug = re.getSession().getDebugObject();
	    		DebugMonitor monitor = debug.addWaitingEvaluator(re);
	    		Scriptable scope = ((RhinoEngine) re.getScriptingEngine()).getGlobal();
	    		DebugEvent event = new DebugEvent();
	    		event.resourceName = this.script.getSourceName();
	    		event.functionName = this.script.getFunctionName();
	    		event.scope = activationToScope(cx, scope, this.activation);
	    		event.parentScope = this.activation.getParentScope();
	    		event.thisObj = this.thisObj;
	    		event.lineNumber = this.getStartLineNum();
	    		event.requestId = re.hashCode();
	    		event.eventType = DebugEvent.FUNCTION_ENTRY;
	    		debug.pushEvent(event);

	    		try {
	    			synchronized (monitor) {
	    				monitor.wait();
	    			}
	    		} catch (Exception ex) {
	    			this.app.logError(ErrorReporter.errorMsg(this.getClass(), "onEnter"), ex);
	    		}
	    		
	    		debugger.isStepping = monitor.isStepping;
	    		monitor.isStepping = false;
	    		monitor.isJumping = false;
	    	}
	    }
	    
	    public void onExit(Context cx, boolean byThrow, Object resultOrException) { 
	    	AxiomDebugger debugger = (AxiomDebugger) cx.getDebugger();
	    	if (debugger != null) {
	    		boolean ignoreDebugging = false;
	    		synchronized (debugger) {
	    			if (debugger.callDepth > 0) {
	    				ignoreDebugging = true;
	    			}
	    		}
	    		
	    		if (skipFunction() || debugger.isJumping) {
	    			synchronized (debugger) {
	    				if (ignoreDebugging) {
	    					debugger.callDepth--;
	    				}
	    			}
	    			return;
	    		}
	    		
	    		if (ignoreDebugging) {
	    			return;
	    		}
	    	}
	    	
	    	RequestEvaluator re = this.app.getCurrentRequestEvaluator();
	    	if (re != null && isOnFunctionExitSet()) {
	    		debugger.isStepping = false;
	    		Debug debug = re.getSession().getDebugObject();
	    		DebugMonitor monitor = debug.addWaitingEvaluator(re);
	    		Scriptable scope = ((RhinoEngine) re.getScriptingEngine()).getGlobal();
	    		DebugEvent event = new DebugEvent();
	    		event.resourceName = this.script.getSourceName();
	    		event.functionName = this.script.getFunctionName();
	    		event.scope = activationToScope(cx, scope, this.activation);
	    		event.parentScope = this.activation.getParentScope();
	    		event.thisObj = this.thisObj;
	    		event.lineNumber = this.getEndLineNum();
	    		event.requestId = re.hashCode();
	    		event.eventType = DebugEvent.FUNCTION_EXIT;
	    		debug.pushEvent(event);

	    		try {
	    			synchronized (monitor) {
	    				monitor.wait();
	    			}
	    		} catch (Exception ex) {
	    			this.app.logError(ErrorReporter.errorMsg(this.getClass(), "onExit"), ex);
	    		}
	    		
	    		debugger.isStepping = monitor.isStepping;
	    		monitor.isStepping = false;
	    		monitor.isJumping = false;
	    	}
	    } 
	    
	    public void onLineChange(Context cx, int lineNumber) {
	    	RequestEvaluator re = this.app.getCurrentRequestEvaluator();
	    	AxiomDebugger debugger = (AxiomDebugger) cx.getDebugger();
	    	if (debugger != null) {
	    		boolean ignoreDebugging = false;
	    		synchronized (debugger) {
	    			if (debugger.callDepth > 0) {
	    				ignoreDebugging = true;
	    			}
	    		}
	    		if (ignoreDebugging) {
	    			return;
	    		}
	    	}
	    	
	    	if (re != null 
	    			&& (isBreakpoint(this.script.getSourceName(), lineNumber)
	    					|| debugger.isStepping)) {
	    		
	    		debugger.isStepping = false;
	    		debugger.isJumping = false;
	    		Debug debug = re.getSession().getDebugObject();
	    		DebugMonitor monitor = debug.addWaitingEvaluator(re);
	    		Scriptable scope = ((RhinoEngine) re.getScriptingEngine()).getGlobal();
	    		DebugEvent event = new DebugEvent();
	    		event.resourceName = this.script.getSourceName();
	    		event.lineNumber = lineNumber;
	    		event.functionName = this.script.getFunctionName();
	    		event.scope = activationToScope(cx, scope, this.activation);
	    		event.parentScope = this.activation.getParentScope();
	    		event.thisObj = this.thisObj;
	    		event.requestId = re.hashCode();
	    		debug.pushEvent(event);

	    		try {
	    			synchronized (monitor) {
	    				monitor.wait();
	    			}
	    		} catch (Exception ex) {
	    			this.app.logError(ErrorReporter.errorMsg(this.getClass(), "onLineChange"), ex);
	    		}
	    		
	    		debugger.isStepping = monitor.isStepping;
	    		debugger.isJumping = monitor.isJumping;
	    		monitor.isStepping = false;
	    		monitor.isJumping = false;
	    	}
	    }
	    
	    public void onExceptionThrown(Context cx, Throwable ex) {
	    	AxiomDebugger debugger = (AxiomDebugger) cx.getDebugger();
	    	if (debugger != null) {
	    		boolean ignoreDebugging = false;
	    		synchronized (debugger) {
	    			if (debugger.callDepth > 0) {
	    				ignoreDebugging = true;
	    			}
	    		}
	    		if (ignoreDebugging) {
	    			return;
	    		}
	    	}
	    	
	    	RequestEvaluator re = this.app.getCurrentRequestEvaluator();
	    	if (re != null && isOnExceptionSet()) {
	    		Debug debug = re.getSession().getDebugObject();
	    		DebugMonitor monitor = debug.addWaitingEvaluator(re);
	    		Scriptable scope = ((RhinoEngine) re.getScriptingEngine()).getGlobal();
	    		DebugEvent event = new DebugEvent();
	    		event.resourceName = this.script.getSourceName();
	    		event.functionName = this.script.getFunctionName();
	    		event.scope = activationToScope(cx, scope, this.activation);
	    		event.parentScope = this.activation.getParentScope();
	    		event.thisObj = this.thisObj;
	    		if (ex instanceof RhinoException) {
	    			event.lineNumber = ((RhinoException) ex).lineNumber();
	    		}
	    		event.requestId = re.hashCode();
	    		event.exception = ex;
	    		event.eventType = DebugEvent.EXCEPTION;
	    		debug.pushEvent(event);

	    		try {
	    			synchronized (monitor) {
	    				monitor.wait();
	    			}
	    		} catch (Exception iex) {
	    			this.app.logError(ErrorReporter.errorMsg(this.getClass(), "onExceptionThrown"), ex);
	    		}
	    		
	    		debugger.isStepping = monitor.isStepping;
	    		monitor.isStepping = false;
	    		monitor.isJumping = false;
	    	}
	    }
	    
	    private boolean isBreakpoint(String sourceName, int lineNumber) {
	    	boolean isBreakpoint = false;
	    	try {
	    		Debug debug = this.app.getCurrentRequestEvaluator().getSession().getDebugObject();
	    		isBreakpoint = debug.isBreakpointSet(sourceName, lineNumber);
	    	} catch (Exception ex) {
	    		isBreakpoint = false;
	    	}
	    	return isBreakpoint;
	    }
	    
	    private boolean isOnExceptionSet() {
	    	boolean isExceptionSet = false;
	    	try {
	    		Debug debug = this.app.getCurrentRequestEvaluator().getSession().getDebugObject();
	    		isExceptionSet = debug.jsFunction_getBreakOnException();
	    	} catch (Exception ex) {
	    		isExceptionSet = false;
	    	}
	    	return isExceptionSet;
	    }
	    
	    private boolean skipFunction() {
	    	boolean skip = false;
	    	try {
	    		Debug debug = this.app.getCurrentRequestEvaluator().getSession().getDebugObject();
	    		String funcName = null, protoName = null;
	    		if (this.script != null) {
	    			funcName = this.script.getFunctionName();
	    		}
	    		if (this.thisObj != null) {
	    			protoName = this.thisObj.getClassName();
	    		}
	    		
	    		skip = debug.isWhitelisted(protoName, funcName);
	    		
	    	} catch (Exception ex) {
	    		skip = false;
	    	}
	    	
	    	return skip;
	    }
	    
	    private boolean isOnFunctionEnterSet() {
	    	boolean isEnterSet = false;
	    	try {
	    		Debug debug = this.app.getCurrentRequestEvaluator().getSession().getDebugObject();
	    		isEnterSet = debug.jsFunction_getBreakOnFunctionEntry();
	    	} catch (Exception ex) {
	    		isEnterSet = false;
	    	}
	    	return isEnterSet;
	    }
	    
	    private boolean isOnFunctionExitSet() {
	    	boolean isExitSet = false;
	    	try {
	    		Debug debug = this.app.getCurrentRequestEvaluator().getSession().getDebugObject();
	    		isExitSet = debug.jsFunction_getBreakOnFunctionExit();
	    	} catch (Exception ex) {
	    		isExitSet = false;
	    	}
	    	return isExitSet;
	    }
	    
	    private Scriptable activationToScope(Context cx, Scriptable scope, 
	    									Scriptable activation) {
	    	
	    	Scriptable obj = cx.newObject(scope);
	    	Object[] ids = activation.getIds();
	    	for (int i = 0; i < ids.length; i++) {
	    		String id = (String) ids[i];
	    		obj.put(id, obj, activation.get(id, activation));
	    	}
	    	return obj;
	    }
	    
	    private int getStartLineNum() {
	    	int line = -1;
	    	int[] linenums = this.script.getLineNumbers();
	    	if (linenums != null && linenums.length > 0) {
	    		Arrays.sort(linenums);
	    		this.sortedLineNums = linenums;
	    		line = linenums[0];
	    	}
	    	return line;
	    }
	    
	    private int getEndLineNum() {
	    	if (this.sortedLineNums != null) {
	    		return this.sortedLineNums[this.sortedLineNums.length - 1];
	    	}
	    	
	    	int line = -1;
	    	int[] linenums = this.script.getLineNumbers();
	    	if (linenums != null && linenums.length > 0) {
	    		Arrays.sort(linenums);
	    		line = linenums[linenums.length - 1];
	    	}
	    	return line;
	    }
	}
}