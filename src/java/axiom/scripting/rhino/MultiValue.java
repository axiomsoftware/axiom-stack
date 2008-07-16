package axiom.scripting.rhino;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;

import org.mozilla.javascript.*;

import axiom.objectmodel.INode;
import axiom.objectmodel.IProperty;

/**
 * The class that encapsulates the behaviors of the Axiom MultiValue type
 * 
 * @jsconstructor
 */
public class MultiValue extends ScriptableObject implements Serializable {
    
    private int valueType = IProperty.STRING;
    private int length = 0;
        
    public MultiValue() {
        super();
    }
    
    public MultiValue(int type) {
        super();
        this.valueType = type;
    }
    
    public MultiValue(Object[] args) {
        super();
        int len;
        if (args != null && (len = args.length) > 0) {
        	this.length = len;
            for (int i = 0; i < len; i++) {
            	this.put(i, this, args[i]);
            }
            this.valueType = determineType(args[0]);
        }
    }
    
    public static MultiValue init(RhinoCore core) throws PropertyException {
        MultiValue proto = new MultiValue(null);
        proto.setPrototype(getObjectPrototype(core.global));
        
        final int ATTRS = DONTENUM;
        // install JavaScript methods and properties
        Method[] methods = MultiValue.class.getDeclaredMethods();
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
        return "MultiValue";
    }
    
    public String toString() {
        return "[MultiValue]";
    }
    
    public void setValues(ArrayList values) {
        synchronized (this) {
        	this.clearAll();
        	final int size = values.size();
        	for (int i = 0; i < size; i++) {
        		this.put(i, this, values.get(i));
        	}
        	this.length = size;
        }
    }
    
    public void addValue(Object o) {
        synchronized (this) {
        	this.put(this.length++, this, o);
        }
    }
    
    public void setValue(Object o, int where) {
        synchronized (this) {
        	this.put(where, this, o);
        }
    }
    
    public Object[] getValues() {
        synchronized (this) {
        	final int size = this.length;
            Object[] vals = new Object[size];
            for (int i = 0; i < size; i++) {
            	vals[i] = this.get(i, this);
            }
            
            return vals;
        }
    }
    
    public Object get(int index) {
    	return this.get(index, this);
    }
    
    public Object[] remove(int idx) {
    	Object[] newArr = null;
    	Object oldVal = null;
    	if (idx > -1 && idx < this.length) {
    		Object[] oldArr = this.getValues();
    		newArr = new Object[oldArr.length - 1];
    		for (int i = 0, count = 0; i < oldArr.length; i++) {
    			if (i != idx) {
    				newArr[count++] = oldArr[i];
    			} else {
    				oldVal = oldArr[i];
    			}
    		}
    	}
    	
    	Object theMV = this;
    	if (newArr != null) {
    		try {
    			Object mv = Context.getCurrentContext().newObject(this, "MultiValue", newArr);
    			MultiValue mvobj = (MultiValue) mv;
    			mvobj.setValueType(this.valueType);
    			theMV = mvobj;
    		} catch (Exception ex) {
    		}
    	}
    	
    	Object[] ret = new Object[2];
    	ret[0] = theMV;
    	ret[1] = oldVal;
    	
    	return ret;
    }
    
    public int getValueType() {
        return valueType;
    }
    
    public void setValueType(int type) {
        this.valueType = type;
    }
    
    public String jsFunction_type() {
        switch (valueType) {
        case IProperty.BOOLEAN: return "Boolean";
        case IProperty.DATE: return "Date";
        case IProperty.FLOAT: return "Number";
        case IProperty.INTEGER: return "Number";
        case IProperty.JAVAOBJECT: return "JavaObject";
        case IProperty.REFERENCE: return "Reference";
        case IProperty.STRING: return "String";
        default: return "Unknown";
        }
    }
    
    public int jsGet_length() {
        return this.length;
    }
    
    public boolean jsFunction_contains(final Object o) {
        return this.contains(o) > -1;
    }
    
    public void clearAll() {
        synchronized (this) {
        	final int size = this.length;
        	for (int i = 0; i < size; i++) {
        		this.delete(0);
        	}
        	this.length = 0;
        }
    }
    
    public int contains(final Object o) {
        if (o == null) {
            return -1;
        }
        
        final Object[] values;
        final int valueType;
        
        synchronized (this) {
            values = getValues();
            valueType = this.valueType;
        }
        
        final int length = values.length;
        if (valueType == IProperty.REFERENCE && o instanceof AxiomObject) {
        	String code = null;
        	INode n = ((AxiomObject) o).node;
        	code = n.getPrototype() + "/" + n.getID();
            
            for (int i = 0; i < length; i++) {
                AxiomObject hobj = (AxiomObject) ((Reference) values[i]).getTarget();
                INode node = hobj.node;
                String ccode = node.getString("_v_copied_code");
                String ncode = node.getPrototype() + "/" + node.getID();
                if (code.equals(ccode) || code.equals(ncode)) {
                    return i;
                }
            }
        } else {
            for (int i = 0; i < length; i++) {
                if (o.equals(values[i])) {
                    return i;
                }
            }
        }
        
        return -1;
    }
    
    public MultiValue jsFunction_concat(Object o) {
        MultiValue mv = null;
        int objType = IProperty.STRING;
        if (o instanceof MultiValue) {
        	mv = (MultiValue) o;
        } else {
        	final int type = this.valueType;
        	switch (type) {
        	case IProperty.REFERENCE:
        		if (this.length > 0 && !(o instanceof Reference || (o instanceof Scriptable && "Reference".equalsIgnoreCase(((Scriptable) o).getClassName())))) {
        			return this;
        		}
        		objType = IProperty.REFERENCE;
        		break;
        	case IProperty.DATE:
        	case IProperty.TIME:
        	case IProperty.TIMESTAMP:
        		if (this.length > 0 && !(o instanceof Date || (o instanceof Scriptable && "Date".equalsIgnoreCase(((Scriptable) o).getClassName())))) {
        			return this;
        		}
        		objType = IProperty.DATE;
        		break;
        	case IProperty.STRING:
        		if (this.length > 0 && !(o instanceof String || (o instanceof Scriptable && "String".equalsIgnoreCase(((Scriptable) o).getClassName())))) {
        			return this;
        		}
        		objType = IProperty.STRING;
        		break;
        	case IProperty.BOOLEAN:
        		if (this.length > 0 && !(o instanceof Boolean || (o instanceof Scriptable && "Boolean".equalsIgnoreCase(((Scriptable) o).getClassName())))) {
        			return this;
        		}
        		objType = IProperty.BOOLEAN;
        		break;
        	case IProperty.FLOAT:
        	case IProperty.SMALLFLOAT:
        		if (this.length > 0 && !(o instanceof Float || (o instanceof Scriptable && "Float".equalsIgnoreCase(((Scriptable) o).getClassName())))) {
        			return this;
        		}
        		objType = IProperty.FLOAT;
        		break;
        	case IProperty.INTEGER:
        	case IProperty.SMALLINT:
        		if (this.length > 0 && !(o instanceof Integer || o instanceof Float || (o instanceof Scriptable && "Float".equalsIgnoreCase(((Scriptable) o).getClassName())))) {
        			return this;
        		}
        		objType = IProperty.INTEGER;
        		break;
        	}
        }
        
        if (mv != null) {
        	if (mv.getValueType() != this.valueType && this.length > 0) {
        		return this;
        	}

        	final Object[] mvArr = mv.getValues();
        	final int size = mvArr.length;

        	synchronized (this) {
        		if (this.length > 0) {
        			this.valueType = mv.getValueType();
        		}
        		for (int i = 0; i < size; i++) {
        			this.addValue(mvArr[i]);
        		}
        	}
        } else {
        	synchronized (this) {
        		if (this.length > 0) {
        			this.valueType = objType;
        		}
        		this.addValue(o);
        	}
        }
        
        return this;
    }
    
    public MultiValue jsFunction_remove(Object o) {
    	Object[] newArr = null;
    	int idx;
    	if ((idx = this.contains(o)) > -1) {
    		Object[] oldArr = this.getValues();
    		newArr = new Object[oldArr.length - 1];
    		for (int i = 0, count = 0; i < oldArr.length; i++) {
    			if (i != idx) {
    				newArr[count++] = oldArr[i];
    			}
    		}
    	}
    	
    	if (newArr != null) {
    		try {
    			Object mv = Context.getCurrentContext().newObject(this, "MultiValue", newArr);
    			MultiValue mvobj = (MultiValue) mv;
    			mvobj.setValueType(this.valueType);
    			return mvobj;
    		} catch (Exception ex) {
    		}
    	}
    	
    	return this;
    }
    
    public Object jsFunction_join(Object on) {
    	StringBuffer joined = new StringBuffer();
    	Object[] values = getValues();
    	int last = values.length - 1;
    	for (int i = 0; i < values.length; i++) {
    		joined.append(values[i]);
    		if (i != last) { joined.append(on); }
    	}
    	return joined.toString();
    }
    
    public Object jsFunction_slice(Object start, Object end) {
        Object[] values = getValues();
        final int len = values.length;
        final int s = (int) ScriptRuntime.toNumber(start);
        int e = end == null ? len : (int) ScriptRuntime.toNumber(end);
        if (e > len) {
            e = len;
        }
        Object[] slice = new Object[e-s];
        
        for (int i = s; i < e; i++) {
            slice[i-s] = values[i];
        } 
        
        try {
            Object o = Context.getCurrentContext().newObject(this, "MultiValue", slice);
            MultiValue mvobj = (MultiValue) o;
            mvobj.setValueType(this.valueType);
            return mvobj;
        } catch (Exception ex) {
        }
        
        return null;
    }    
    
    public Object jsFunction_splice(Object start, Object howmany, Object arg) {
        if (start == null || start == Undefined.instance) {
            return this;
        }
        
        Object[] values = getValues();
        final int len = values.length;
        int s = (int) ScriptRuntime.toNumber(start);
        final int hm;
        if (howmany != null && howmany != Undefined.instance) {
        	hm = (int) ScriptRuntime.toNumber(howmany);
        } else {
        	hm = len - s;
        }
        
        int newlen = len - hm;
        if (newlen < 0) {
            newlen = s < len ? s : len;
        }
        
        Object[] splice = null;
        if (arg instanceof MultiValue) {
            MultiValue mv = (MultiValue) arg;
            if (this.valueType != mv.getValueType()) {
            	return this;
            }
            Object[] varr = mv.getValues();
            int varrlen = varr.length;
            splice = new Object[newlen + varrlen];
            int count = 0;
            for (int i = 0; i < s; i++) {
                splice[count++] = values[i];
            }
            for (int i = 0; i < varrlen; i++) {
                splice[count++] = varr[i];
            }
            s += hm;
            for (int i = s; i < len; i++) {
                splice[count++] = values[i];
            }
        } else {
            boolean arg_undefined = 
                (arg == null || arg == Undefined.instance || arg == Scriptable.NOT_FOUND ||
                    ((Scriptable) arg).getClassName().equals("undefined")); 
            splice = new Object[newlen + (arg_undefined ? 0 : 1)];
            int count = 0;
            System.out.println("s = " + s + ", hw = " + hm);
            for (int i = 0; i < s; i++) {
                splice[count++] = values[i];
                System.out.println("Assigned " + values[i] + " for " + i + ", count = " + count);
            }
            if (!arg_undefined) {
                splice[count++] = arg;
            }
            s += hm;
            for (int i = s; i < len; i++) {
                splice[count++] = values[i];
                System.out.println("Assigned " + values[i] + " for " + i+ ", count = " + count);
            }	
        }
        
        try {
            Object o = Context.getCurrentContext().newObject(this, "MultiValue", splice);
            MultiValue mvobj = (MultiValue) o;
            mvobj.setValueType(this.valueType);
            return mvobj;
        } catch (Exception ex) {
        }
        
        return null;
    }
    
    private int determineType(Object o) {
        int type = IProperty.STRING;
        
        if (o instanceof Scriptable) {
            Scriptable s = (Scriptable) o;
            String className = s.getClassName();
            if ("Date".equals(className)) {
                type = IProperty.DATE;
            } else if ("String".equals(className)) {
                type = IProperty.STRING;
            } else if ("Number".equals(className)) {
                type = IProperty.FLOAT;
            } else if ("Boolean".equals(className)) {
                type = IProperty.BOOLEAN;
            } else if ("Reference".equals(className)) { 
                type = IProperty.REFERENCE;
            } else {
                type = IProperty.JAVAOBJECT;
            }
        } else if (o instanceof Reference) {
            type = IProperty.REFERENCE;
        } else if (o instanceof Boolean) {
            type = IProperty.BOOLEAN;
        } else if (o instanceof Date) {
            type = IProperty.DATE;
        } else if (o instanceof Float || o instanceof Double) {
            type = IProperty.FLOAT;
        } else if (o instanceof Integer || o instanceof Long) {
            type = IProperty.INTEGER;
        } else if (o instanceof String) {
            type = IProperty.STRING;
        } else {
            type = IProperty.JAVAOBJECT;
        }
        
        return type;
    }
    
    public Object clone() {
        MultiValue mv = new MultiValue();
        mv.valueType = this.valueType;
        final int size = this.length;
        int type = IProperty.STRING;
        if (size > 0) {
        	type = determineType(this.get(0, this));
        }
        for (int i = 0; i < size; i++) {
        	Object o = this.get(i, this);
            if (type == IProperty.REFERENCE) {
            	mv.addValue(((Reference) o).clone()); 
            } else {
            	mv.addValue(o);
            }
        }
        return mv;
    }
    
    public String jsFunction_toSource() {
        StringBuffer src = new StringBuffer();
        if (this.valueType == IProperty.REFERENCE) {
            src.append("new MultiValue(");
            final int size = this.length;
            for (int i = 0; i < size; i++) {
            	src.append(((Reference) this.get(i, this)).jsFunction_toSource());
                if (i != size - 1) {
                    src.append(", ");
                }
            }
            src.append(")");
        } else {
            src.append(this.toString());
        }
        return src.toString();
    }
    
}