package org.mozilla.javascript;

public class ScriptRuntimeWrapper {
    
    public static String defaultObjectToSource(Context cx, Scriptable scope,
                                                Scriptable thisObj, Object[] args) {
        return ScriptRuntime.defaultObjectToSource(cx, scope, thisObj, args);
    }
}