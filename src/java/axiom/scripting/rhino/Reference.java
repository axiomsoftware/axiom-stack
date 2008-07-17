package axiom.scripting.rhino;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.*;
import java.util.*;

import org.mozilla.javascript.*;

import axiom.framework.ErrorReporter;
import axiom.framework.IPathElement;
import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.INode;
import axiom.objectmodel.IProperty;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.db.Key;
import axiom.objectmodel.db.WrappedNodeManager;

/**
 * The class that encapsulates the behaviors of the Axiom Reference type
 * 
 * @jsconstructor
 */
public class Reference extends ScriptableObject implements IProperty, Serializable {
    
    private Key targetKey = null;
    private Key sourceKey = null;
    private String sourceProperty = null;
    private int sourceIndex = 0;
    private String sourceXPath = null;
    private RhinoCore core = null;
    private WrappedNodeManager wnmgr = null;
    private axiom.objectmodel.db.Node sourceNode = null;
    
    static int ATTRS = DONTENUM;
    
    public Reference(RhinoCore core) {
        super();
        setRhinoCore(core);
    }

    public Reference(RhinoCore core, Object[] args) throws Exception {
        super();
        setRhinoCore(core);

        if (args != null) {
            if (args.length > 0) {
                if (args[0] instanceof Key) {
                    targetKey = (Key) args[0];
                } else if (args[0] instanceof AxiomObject) {
                    AxiomObject hobj = (AxiomObject) args[0];
                    if (hobj.node instanceof axiom.objectmodel.db.Node) {
                        axiom.objectmodel.db.Node curr = (axiom.objectmodel.db.Node) hobj.node;
                        targetKey = curr.getKey();
                    }
                } else if (args[0] instanceof axiom.objectmodel.db.Node) {
                    axiom.objectmodel.db.Node curr = (axiom.objectmodel.db.Node) args[0];
                    targetKey = curr.getKey();
                } else if (args[0] instanceof String || (args[0] instanceof Scriptable 
                        && "String".equals(((Scriptable) args[0]).getClassName()))) { 
                    String path = args[0] instanceof String ? (String) args[0] :
                                    ScriptRuntime.toString(args[0]);
                    IPathElement pe = AxiomObject.traverse(path, core.app);
                    if (pe == null || !(pe instanceof axiom.objectmodel.db.Node)) {
                        throw new Exception("Path passed to Reference constructor does not " 
                                + "point to a valid object.");
                    }
                    axiom.objectmodel.db.Node curr = (axiom.objectmodel.db.Node) pe;
                    targetKey = curr.getKey();
                } else {
                    throw new RuntimeException("Illegal argument specified to Reference.constructor");
                }
            } 
        }
        
        if (targetKey == null) {
            throw new RuntimeException("Reference ctor: The target key is null.");
        }
    }
    
    public static Reference init(RhinoCore core) throws PropertyException {
        // create prototype object
        Reference proto = new Reference(core);
        proto.setPrototype(getObjectPrototype(core.global));
        proto.setParentScope(core.global);
        
        // install JavaScript methods and properties
        Method[] methods = Reference.class.getDeclaredMethods();
        for (int i=0; i<methods.length; i++) {
            String methodName = methods[i].getName();

            if (methodName.startsWith("jsFunction_")) {
                methodName = methodName.substring(11);
                FunctionObject func = new FunctionObject(methodName, methods[i], proto);
                proto.defineProperty(methodName, func, ATTRS);
            } else if (methodName.startsWith("jsGet_")) {
                methodName = methodName.substring(6);
                proto.defineProperty(methodName, null, methods[i], null, ATTRS);
            }
        }
        
        return proto;
    }
    
    public String getClassName() {
        return "Reference";
    }
    
    public String toString() {
        return "[Reference]";
    }
    
    /**
     * @deprecated replaced by ref.target
     */
    public Object jsFunction_getTarget() {
        return getTarget();
    }
    
    /**
     * The target object which this reference is referring to.
     * @type {AxiomObject} 
     */
    public Object jsGet_target() {
        return getTarget();
    }
    
    /**
     * @deprecated replaced by ref.source
     */
    public Object jsFunction_getSource() {
        return getSource();
    }
 
    /**
     * The source object of this reference
     * @type {AxiomObject}
     */
    public Object jsGet_source() {
    	return getSource();
    }
    
    /**
     * The _id of the target object of the reference.  Equivalent to <code> ref.target._id </code>.
     * @type {String}
     */
    public String jsGet_targetId() {
        return targetKey.getID();
    }
    
    /**
     * The path of the target object in this reference.  Equivalent to <code> ref.target.getPath() </code>.
     * @type {String}
     */
    public String jsGet_targetPath() {
    	try {
    		axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) getObject(targetKey, false);
    		return core.app.getNodeHref(n, null, true);
    	} catch (UnsupportedEncodingException x) {
    		return null;
    	}
    }
    
    /**
     * The _id of the source object of the reference.  Equivalent to <code> ref.source._id </code>.
     * @type {String}
     */
    public String jsGet_sourceId() {
        return getSourceKey().getID();
    }
    
    /**
     * The path of the source object in this reference.  Equivalent to <code> ref.source.getPath() </code>.
     * @type {String}
     */
    public String jsGet_sourcePath() {
        try {
        	axiom.objectmodel.db.Node n = (axiom.objectmodel.db.Node) getObject(getSourceKey(), false);
            return core.app.getNodeHref(n, null, true);
        } catch (UnsupportedEncodingException x) {
        	return null;
        }
    }
    
    /**
     * The name of the property on the source object that is this reference.  That is,
     * if <code> src.prop = ref </code>, then <code> ref.sourceProperty = prop </code>.
     * @type {String}
     */
    public String jsGet_sourceProperty() {
        return this.sourceProperty;
    }
    
    /**
     * The position that this Reference object holds inside a MultiValue, if it is part of
     * a MultiValue.  If it is not, its just 0.
     * @type {Number}
     */
    public int jsGet_sourceIndex() {
        return this.sourceIndex;
    }
    
    public int getSourceIndex() {
        return this.sourceIndex;
    }
    
    public String getSourceXPath() {
        return this.sourceXPath;
    }
    
    public void setSourceKey(Key key) {
        this.sourceKey = key;
    }
     
    public void setSourceProperty(String name) {
        this.sourceProperty = name;
    }
    
    public void setSourceIndex(int index) {
        this.sourceIndex = index;
    }
    
    public void setSourceXPath(String xpath) {
        this.sourceXPath = xpath;
    }
    
    public void setRhinoCore(RhinoCore core) {
        this.core = core;
        if (core != null) {
            setParentScope(core.global);
            if (core.app.getNodeManager() != null) {
                this.wnmgr = core.app.getWrappedNodeManager();
            }
        }
    }
    
    public RhinoCore getRhinoCore() {
        return this.core;
    }
        
    public String getName() {
        return this.sourceProperty;
    }
    
    public Object getValue() {
        return getTarget();
    }
    
    public int getType() {
        return IProperty.REFERENCE;
    }
    
    public Object getSource() {
        return getObject(getSourceKey(), true);
    }
    
    public Object getTarget() {
        return getObject(targetKey, true);
    }
    
    public Key getTargetKey() {
        return targetKey;
    }
    
    public Key getSourceKey() {
    	if (this.sourceKey == null) {
    		this.sourceKey = this.sourceNode.getKey();
    	}
    	return this.sourceKey;
    }
    
    public boolean getBooleanValue() { 
        return false; 
    }
    
    public Date getDateValue() { 
        return null;
    }
    
    public double getFloatValue() {
        return 0.0;
    }
    
    public long getIntegerValue() {
        return 0;
    }
    
    public MultiValue getMultiValue() {
        return null;
    }
    
    public Reference getReferenceValue() {
        return this;
    }
    
    public Object getXMLValue() {
        return null;
    }
    
    public Object getXHTMLValue() {
        return null;
    }
    
    public String getStringValue() {
        return targetKey == null ? "null" : targetKey.toString();
    }
    
    public Object getJavaObjectValue() {
        return null;
    }
    
    public INode getNodeValue() {
        return null;
    }
    
    private Object getObject(final Object key, final boolean create_js_obj) {
        axiom.objectmodel.db.Node n = null;
        if (key instanceof DbKey && wnmgr != null) {
        	DbKey k = (DbKey) key, ckey;
        	RequestEvaluator reqeval = this.core.app.getCurrentRequestEvaluator();
        	if (reqeval != null) {
        		final int layer = Math.max(reqeval.getLayer(), k.getLayer());
        		ckey = new DbKey(this.core.app.getDbMapping(k.getStorageName()), k.getID(), layer);
        	} else {
        		ckey = k;
        	}
            n = wnmgr.getNode(ckey);
        }
        if (n == null) {
            return null;
        }
        
        if (create_js_obj) {
            return Context.toObject(n, core.global);
        } else {
            return n;
        }
    }
    
    public void setSourceNode(axiom.objectmodel.db.Node node) {
    	this.sourceNode = node;
    	this.sourceKey = null;
    }
    
    public Object clone() {
        Reference ref = new Reference(this.core);
        ref.targetKey = this.targetKey;
        ref.sourceKey = this.sourceKey;
        ref.sourceProperty = this.sourceProperty;
        ref.sourceIndex = this.sourceIndex;
        ref.sourceXPath = this.sourceXPath;
        ref.wnmgr = this.wnmgr;
        return ref;
    }
    
    /**
     * The Reference object's toSource() method.
     * 
     * @return {String} The object's toSource()
     */
    public String jsFunction_toSource() {
        StringBuffer src = new StringBuffer();
        try {
            src.append("new Reference('").append(this.jsGet_targetPath()).append("')");
        } catch (Exception ex) {
            this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "jsFunction_toSource") 
            		+ "Could not compute toSource() on " + this, ex);
        }
        return src.toString();
    }
    
    public boolean equals(Object other) {
    	if (!(other instanceof Reference)) {
    		return super.equals(other);
    	}
    	
    	Reference ref = (Reference) other;
    	if (this.targetKey != null && ref.targetKey != null && 
    			this.targetKey.equals(ref.targetKey)) {
    		return true;
    	}
    	
    	return super.equals(other);
    }
    
}