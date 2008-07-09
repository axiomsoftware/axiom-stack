/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile: Property.java,v $
 * $Author: hannes $
 * $Revision: 1.31 $
 * $Date: 2006/03/21 16:52:46 $
 */

package axiom.objectmodel.db;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.w3c.dom.DocumentFragment;

import axiom.framework.ErrorReporter;
import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.INode;
import axiom.objectmodel.IProperty;
import axiom.scripting.rhino.MultiValue;
import axiom.scripting.rhino.Reference;
import axiom.scripting.rhino.RhinoCore;
import axiom.scripting.rhino.RhinoEngine;

/**
 * A property implementation for Nodes stored inside a database. Basically
 * the same as for transient nodes, with a few hooks added.
 */
public final class Property implements IProperty, Serializable, Cloneable, Comparable {
    static final long serialVersionUID = -1022221688349192379L;
    private String propname;
    private Node node;
    private Object value;
    private int type;
    transient boolean dirty;

    /**
     * Creates a new Property object.
     *
     * @param node ...
     */
    public Property(Node node) {
        this.node = node;
        dirty = true;
    }

    /**
     * Creates a new Property object.
     *
     * @param propname ...
     * @param node ...
     */
    public Property(String propname, Node node) {
        this.propname = propname;
        this.node = node;
        dirty = true;
    }

    /**
     * Creates a new Property object.
     *
     * @param propname ...
     * @param node ...
     * @param valueNode ...
     */
    public Property(String propname, Node node, Node valueNode) {
        this(propname, node);
        type = NODE;
        value = (valueNode == null) ? null : valueNode.getHandle();
        dirty = true;
    }

    private void readObject(ObjectInputStream in) throws IOException {
        try {
            propname = in.readUTF();
            node = (Node) in.readObject();
            type = in.readInt();

            switch (type) {
                case STRING:
                    value = in.readObject();

                    break;

                case BOOLEAN:
                    value = in.readBoolean() ? Boolean.TRUE : Boolean.FALSE;

                    break;

                case INTEGER:
                    value = new Long(in.readLong());

                    break;

                case DATE:
                    value = new Date(in.readLong());

                    break;

                case FLOAT:
                    value = new Double(in.readDouble());

                    break;

                case NODE:
                    value = in.readObject();

                    break;

                case JAVAOBJECT:
                    value = in.readObject();

                    break;
                   
                default:    
                    value = in.readObject();
                
                    break;
            }
        } catch (ClassNotFoundException x) {
            throw new IOException(x.toString());
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeUTF(propname);
        out.writeObject(node);
        out.writeInt(type);

        switch (type) {
            case STRING:
                out.writeObject(value);

                break;

            case BOOLEAN:
                out.writeBoolean(((Boolean) value).booleanValue());

                break;

            case INTEGER:
                out.writeLong(((Long) value).longValue());

                break;

            case DATE:
                out.writeLong(((Date) value).getTime());

                break;

            case FLOAT:
                out.writeDouble(((Double) value).doubleValue());

                break;

            case NODE:
                out.writeObject(value);

                break;

            case JAVAOBJECT:

                if ((value != null) && !(value instanceof Serializable)) {
                    out.writeObject(null);
                } else {
                    out.writeObject(value);
                }

                break;
                
            default:    
                if ((value != null) && !(value instanceof Serializable)) {
                    out.writeObject(null);
                } else {
                    out.writeObject(value);
                }

                break;
        }
    }

    /**
     *  Get the name of the property
     *
     * @return this property's name
     */
    public String getName() {
        return propname;
    }

    /**
     *  Set the name of the property
     */
    protected void setName(String name) {
        this.propname = name;
    }

    /**
     *
     *
     * @return the property's value in its native class
     */
    public Object getValue() {
        return value;
    }

    /**
     *
     *
     * @return the property's type as defined in axiom.objectmodel.IProperty.java
     */
    public int getType() {
        return type;
    }

    /**
     * Directly set the value of this property.
     */
    protected void setValue(Object value, int type) {
        this.value = value;
        this.type = type;
        dirty = true;
    }

    /**
     *
     *
     * @param str ...
     */
    public void setStringValue(String str) {
        type = STRING;
        value = str;
        dirty = true;
    }

    /**
     *
     *
     * @param l ...
     */
    public void setIntegerValue(long l) {
        type = INTEGER;
        value = new Long(l);
        dirty = true;
    }

    /**
     *
     *
     * @param d ...
     */
    public void setFloatValue(double d) {
        type = FLOAT;
        value = new Double(d);
        dirty = true;
    }

    /**
     *
     *
     * @param date ...
     */
    public void setDateValue(Date date) {
        type = DATE;
        // normalize from java.sql.* Date subclasses
        if (date != null && date.getClass() != Date.class) {
            value = new Date(date.getTime());
        } else {
            value = date;
        }
        dirty = true;
    }

    /**
     *
     *
     * @param bool ...
     */
    public void setBooleanValue(boolean bool) {
        type = BOOLEAN;
        value = bool ? Boolean.TRUE : Boolean.FALSE;
        dirty = true;
    }

    /**
     *
     *
     * @param node ...
     */
    public void setNodeValue(Node node) {
        type = NODE;
        value = (node == null) ? null : node.getHandle();
        dirty = true;
    }

    /**
     *
     *
     * @param handle ...
     */
    public void setNodeHandle(NodeHandle handle) {
        type = NODE;
        value = handle;
        dirty = true;
    }

    /**
     *
     *
     * @return ...
     */
    public NodeHandle getNodeHandle() {
        if (type == NODE) {
            return (NodeHandle) value;
        }

        return null;
    }

    /**
     *
     *
     * @param dbm ...
     */
    public void convertToNodeReference(DbMapping dbm) {
        if (type == REFERENCE)
            return;
        
        if ((value != null) && !(value instanceof NodeHandle)) {
            value = new NodeHandle(new DbKey(dbm, value.toString(), DbKey.LIVE_LAYER));
        }
        type = NODE;
    }
    
    public void convertToNodeReference(DbMapping dbm, int mode) {
        if (type == REFERENCE)
            return;

        if ((value != null) && !(value instanceof NodeHandle)) {
            value = new NodeHandle(new DbKey(dbm, value.toString(), mode));
        }

        type = NODE;
    }

    /**
     *
     *
     * @param obj ...
     */
    public void setJavaObjectValue(Object obj) {
        type = JAVAOBJECT;
        value = obj;
    }


    /**
     *
     *
     * @return ...
     */
    public String getStringValue() {
        if (value == null) {
            return null;
        }

        switch (type) {
            case STRING:
            case BOOLEAN:
            case INTEGER:
            case FLOAT:
            case JAVAOBJECT:
                return value.toString();

            case DATE:

                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                return format.format((Date) value);

            case NODE:
                return ((NodeHandle) value).getID();
        }

        return "";
    }

    /**
     *
     *
     * @return ...
     */
    public String toString() {
        return getStringValue();
    }

    /**
     *
     *
     * @return ...
     */
    public long getIntegerValue() {
        if (type == INTEGER || type == SMALLINT) {
            return ((Long) value).longValue();
        }

        if (type == FLOAT || type == SMALLFLOAT) {
            return ((Double) value).longValue();
        }

        if (type == BOOLEAN) {
            return ((Boolean) value).booleanValue() ? 1 : 0;
        }

        try {
            return Long.parseLong(getStringValue());
        } catch (Exception x) {
            return 0;
        }
    }

    /**
     *
     *
     * @return ...
     */
    public double getFloatValue() {
        if (type == FLOAT || type == SMALLFLOAT) {
            return ((Double) value).doubleValue();
        }

        if (type == INTEGER || type == SMALLINT) {
            return ((Long) value).doubleValue();
        }

        try {
            return Double.parseDouble(getStringValue());
        } catch (Exception x) {
            return 0.0;
        }
    }

    /**
     *
     *
     * @return ...
     */
    public Date getDateValue() {
        if (type == DATE || type == TIME || type == TIMESTAMP) {
            return (Date) value;
        }

        return null;
    }

    /**
     *
     *
     * @return ...
     */
    public Timestamp getTimestampValue() {
        if ((type == DATE) && (value != null)) {
            return new Timestamp(((Date) value).getTime());
        }

        return null;
    }

    /**
     *
     *
     * @return ...
     */
    public boolean getBooleanValue() {
        if (type == BOOLEAN) {
            return ((Boolean) value).booleanValue();
        }

        if (type == INTEGER) {
            return !(0 == getIntegerValue());
        }

        return false;
    }

    /**
     *
     *
     * @return ...
     */
    public INode getNodeValue() {
        if ((type == NODE) && (value != null)) {
            NodeHandle nhandle = (NodeHandle) value;
            return nhandle.getNode(node.nmgr);
        }

        return null;
    }

    /**
     *
     *
     * @return ...
     */
    public Object getJavaObjectValue() {
        if (type == JAVAOBJECT) {
            return value;
        }

        return null;
    }

    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     *
     * The following cases throw a ClassCastException
     * - Properties of a different type
     * - Properties of boolean or node type
     */
    public int compareTo(Object obj) {
        Property p = (Property) obj;
        int ptype = p.getType();
        Object pvalue = p.getValue();

        if (type==NODE || ptype==NODE ||
                type == BOOLEAN || ptype == BOOLEAN) {
            throw new ClassCastException("uncomparable values " + this + "(" + type + ") : " + p + "(" + ptype + ")");
        }
        if (value==null && pvalue == null) {
            return 0;
        } else if (value == null) {
            return 1;
        } if (pvalue == null) {
            return -1;
        }
        if (type != ptype) {
            throw new ClassCastException("uncomparable values " + this + "(" + type + ") : " + p + "(" + ptype + ")");

        }
        if (!(value instanceof Comparable)) {
            throw new ClassCastException("uncomparable value " + value + "(" + value.getClass() + ")");
        }
        // System.err.println("COMPARING: " + value.getClass() + " TO " + pvalue.getClass());
        return ((Comparable) value).compareTo(pvalue);
    }

    /**
     * Return true if object o is equal to this property.
     *
     * @param obj the object to compare to
     * @return true if this equals obj
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Property))
            return false;
        Property p = (Property) obj;
        return value == null ? p.value == null : value.equals(p.value);
    }
    
    /*
     * for reference, multi value, and File/Image implementations
     */
    
    public void setReferenceValue(Reference ref) {
        type = REFERENCE;
        try {
        	ref.setSourceNode(this.node);
        } catch (RuntimeException e) {
        	this.node.dbmap.app.logError(ErrorReporter.errorMsg(this.getClass(), "setReferenceValue"), e);
        }
        ref.setSourceProperty(this.propname);
        value = ref;
        dirty = true;
    }
    
    public Reference getReferenceValue() {
        if (type == REFERENCE) {
            return (Reference) value;
        }

        return null;
    }
    
    public void setMultiValue(MultiValue mv) {
        type = MULTI_VALUE;
        Object[] refs = mv.getValues();
        int length = refs.length;
        if (mv.getValueType() == REFERENCE && refs.length > 0) {
            String propname = this.propname;
            ArrayList list = new ArrayList(length);
            boolean invalid = false;
            for (int i = 0; i < length; i++) {
                Reference r = (Reference) refs[i];
                if (r == null) {
                    invalid = true;
                    continue;
                }
                list.add(r);
                if (this.node != null) { 
                	r.setSourceNode(this.node);
                }
                r.setSourceProperty(propname);
                r.setSourceIndex(Integer.toString(i));
            }
            if (invalid) {
                mv.setValues(list);
            }
        }
        value = mv;
        dirty = true;
    }
    
    public MultiValue getMultiValue() {
        if (type == MULTI_VALUE) {
            return (MultiValue) value;
        }
        
        return null;
    }
    
    public Object getXMLValue() {
    	if (type == XML || type == XHTML) {
            if (value instanceof String) {
            	RequestEvaluator reqeval = this.node.dbmap.app.getCurrentRequestEvaluator();
            	if (reqeval != null) {
                    RhinoCore core = ((RhinoEngine) reqeval.getScriptingEngine()).getCore();
                    value = (Scriptable) Context.getCurrentContext().newObject(((RhinoEngine) reqeval.getScriptingEngine()).getGlobal(), "XMLList", new Object[]{value});
                }
            }
            return value;
        }
        return null;
    }

    public void setXMLValue(Object xml) {
        type = XML;
        value = xml;
        dirty = true;
    }
    
    public Object getXHTMLValue() {
        return getXMLValue();
    }
    
    public void setXHTMLValue(Object xml) {
        type = XHTML;
        value = xml;
        dirty = true;
    }
    
}