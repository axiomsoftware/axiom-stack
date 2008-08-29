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
 * $RCSfile: Node.java,v $
 * $Author: hannes $
 * $Revision: 1.168 $
 * $Date: 2006/04/26 13:23:36 $
 */

/* 
 * Modified by:
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */
package axiom.objectmodel.db;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;

import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;

import axiom.extensions.tal.TALExtension;
import axiom.framework.ErrorReporter;
import axiom.framework.IPathElement;
import axiom.framework.core.Application;
import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.ConcurrencyException;
import axiom.objectmodel.INode;
import axiom.objectmodel.IProperty;
import axiom.objectmodel.TransientNode;
import axiom.scripting.rhino.MultiValue;
import axiom.scripting.rhino.Reference;
import axiom.scripting.rhino.RhinoCore;
import axiom.scripting.rhino.RhinoEngine;
import axiom.util.EmptyEnumeration;

/**
 * An implementation of INode that can be stored in the internal database or
 * an external relational database.
 */
/* Not final anymore, need to subclass for PersistentSession */
public class Node implements INode, Serializable { 
    
    static final long serialVersionUID = -3740339688506633675L;

    // The handle to the node's parent
    protected NodeHandle parentHandle;

    // Ordered list of subnodes of this node
    private Collection<NodeHandle> subnodes;

    // Named subnodes (properties) of this node
    private Hashtable propMap;

    private boolean typeDirty = true;
    protected long created;
    protected long lastmodified;
    private String id;
    private int layer = DbKey.LIVE_LAYER;
    private int layerInStorage = DbKey.LIVE_LAYER;
    private String name;
    
    // is this node's main identity as a named property or an
    // anonymous node in a subnode collection?
    protected boolean anonymous = false;

    // the serialization version this object was read from (see readObject())
    protected short version = 0;
    private transient String prototype;
    private transient NodeHandle handle;
    transient WrappedNodeManager nmgr;
    transient DbMapping dbmap;
    transient Key primaryKey = null;
    transient String subnodeRelation = null;
    transient long lastSubnodeFetch = 0;
    transient long lastSubnodeChange = 0;
    transient long lastNameCheck = 0;
    transient long lastParentSet = 0;
    transient long lastSubnodeCount = 0; // these two are only used
    transient int subnodeCount = -1; // for aggressive loading relational subnodes
    transient private volatile Transactor lock;
    transient private volatile int state;
    
    // has the parent on this object changed?  if so, must update the path index db
    transient boolean hasPathChanged = false; 
    // has a relational db node been added to a lucene node?  if so, we need to keep track
    // of it so entries can be made into lucene
    transient boolean relationalNodeAdded = false;

    /**
     * Creates an empty, uninitialized Node. The init() method must be called on the
     * Node before it can do anything useful.
     */
    protected Node() {
        created = lastmodified = System.currentTimeMillis();
    }

    /**
     * Creates a new Node with the given name. Used by NodeManager for creating "root nodes"
     * outside of a Transaction context, which is why we can immediately mark it as CLEAN.
     * Also used by embedded database to re-create an existing Node.
     */
    public Node(String name, String id, String prototype, WrappedNodeManager nmgr) {
        if (prototype == null) {
            prototype = "AxiomObject";
        }
        init(nmgr.getDbMapping(prototype), id, name, prototype, null, nmgr);
    }

    /**
     * Constructor used to create a Node with a given name from a embedded database.
     */
    public Node(String name, String id, String prototype, WrappedNodeManager nmgr,
                long created, long lastmodified) {
        this(name, id, prototype, nmgr);
        this.created = created;
        this.lastmodified = lastmodified;
    }

    /**
     * Constructor used for virtual nodes.
     */
    public Node(Node home, String propname, WrappedNodeManager nmgr, String prototype) {
        this.nmgr = nmgr;
        setParent(home);
        // generate a key for the virtual node that can't be mistaken for a Database Key
        primaryKey = new SyntheticKey(home.getKey(), propname);
        this.id = primaryKey.getID();
        this.layer = this.layerInStorage = primaryKey.getLayer();
        this.name = propname;
        this.prototype = prototype;
        this.anonymous = false;

        // set the collection's state according to the home node's state
        if (home.state == NEW || home.state == TRANSIENT) {
            this.state = TRANSIENT;
        } else {
            this.state = VIRTUAL;
        }

    }

    /**
     * Creates a new Node with the given name. This is used for ordinary transient nodes.
     */
    public Node(String name, String prototype, WrappedNodeManager nmgr) {
        this.nmgr = nmgr;
        this.prototype = prototype;
        dbmap = nmgr.getDbMapping(prototype);

        // the id is only generated when the node is actually checked into db,
        // or when it's explicitly requested.
        id = null;
       
        this.name = (name == null) ? "" : name;
        created = lastmodified = System.currentTimeMillis();
        state = TRANSIENT;
        
        if (!"__data__".equals(prototype) && !"Session".equals(prototype)) {
        	 try {
             	this.layer = this.layerInStorage = this.nmgr.nmgr.app.getCurrentRequestEvaluator().getLayer();
             } catch (Exception ex) {
             	this.layer = this.layerInStorage = DbKey.LIVE_LAYER;
             }
        }
    }

    /**
     * Initializer used for nodes being instanced from an embedded or relational database.
     */
    public synchronized void init(DbMapping dbm, String id, String name, String prototype,
                Hashtable propMap, WrappedNodeManager nmgr) {
        this.nmgr = nmgr;
        this.dbmap = dbm;
        this.prototype = prototype;
        this.id = id;
        try {
        	this.layer = this.layerInStorage = this.nmgr.nmgr.app.getCurrentRequestEvaluator().getLayer();
        } catch (Exception ex) {
        	this.layer = this.layerInStorage = DbKey.LIVE_LAYER;
        }
        this.name = name;
        // If name was not set from resultset, create a synthetical name now.
        if ((name == null) || (name.length() == 0)) {
            this.name = prototype + " " + id;
        }
        
        this.propMap = propMap;
        
        // set lastmodified and created timestamps and mark as clean
        created = lastmodified = System.currentTimeMillis();

        if (state != CLEAN) {
            markAs(CLEAN);
        }

        // Invoke onInit() if it is defined by this Node's prototype
        /*if (dbm != null) {
            try {
                // We need to reach deap into axiom.framework.core to invoke onInit(),
                // but the functionality is neat so we got to be strong here.
                RequestEvaluator reval = nmgr.nmgr.app.getCurrentRequestEvaluator();
                if (reval != null) {
                    reval.invokeDirectFunction(this, "onInit", RequestEvaluator.EMPTY_ARGS);
                }
            } catch (Exception x) {
                nmgr.nmgr.app.logError("Error invoking onInit()", x);
            }
        }*/
    }

    /**
     * Read this object instance from a stream. This does some smart conversion to
     * update from previous serialization formats.
     */
    private void readObject(ObjectInputStream in) throws IOException {
        try {
            // as a general rule of thumb, if a string can be null use read/writeObject,
            // if not it's save to use read/writeUTF.
            // version indicates the serialization version
            version = in.readShort();

            if (version < 9) {
                throw new IOException("Can't read pre 1.3.0 AxiomObject");
            }

            id = (String) in.readObject();
            layer = in.readInt();
            name = (String) in.readObject();
            state = in.readInt();
            parentHandle = (NodeHandle) in.readObject();
            created = in.readLong();
            lastmodified = in.readLong();

            subnodes = (SubnodeList) in.readObject();
            // left-over from links vector
            in.readObject();
            propMap = (Hashtable) in.readObject();
            anonymous = in.readBoolean();
            prototype = (String) in.readObject();

        } catch (ClassNotFoundException x) {
            throw new IOException(x.toString());
        }
    }

    /**
     * Write out this instance to a stream
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeShort(9); // serialization version
        out.writeObject(id);
        out.writeInt(layer);
        out.writeObject(name);
        out.writeInt(state);
        out.writeObject(parentHandle);
        out.writeLong(created);
        out.writeLong(lastmodified);

        DbMapping smap = (dbmap == null) ? null : dbmap.getSubnodeMapping();

        if ((smap != null) && smap.isRelational()) {
            out.writeObject(null);
        } else {
            out.writeObject(subnodes);
        }

        // left-over from links vector
        out.writeObject(null);
        out.writeObject(propMap);
        out.writeBoolean(anonymous);
        out.writeObject(prototype);
    }

    /**
     * used by Xml deserialization
     */
    public synchronized void setPropMap(Hashtable propMap) {
        this.propMap = propMap;
    }

    /**
     * used by Xml deserialization
     */
    public synchronized void setSubnodes(Collection<NodeHandle> subnodes) {
        this.subnodes = subnodes;
    }

    public void setTypeDirty(boolean value) {
    	this.typeDirty = value;
    }
    
    /**
     * Get the write lock on this node, throwing a ConcurrencyException if the
     * lock is already held by another thread.
     */
    synchronized void checkWriteLock() {
    	//this.checkSecurity("_edit"); Axiom
        if (state == TRANSIENT) {
            return; // no need to lock transient node
        }

        Transactor current = (Transactor) Thread.currentThread();

        if (!current.isActive()) {
            throw new axiom.framework.TimeoutException();
        }

        if (state == INVALID) {
            nmgr.logEvent("*** Got Invalid Node: " + this);
            Thread.dumpStack();
            throw new ConcurrencyException("Node " + this +
                                           " was invalidated by another thread.");
        }

        if ((lock != null) && (lock != current) && lock.isAlive() && lock.isActive()) {
            throw new ConcurrencyException("Tried to modify " + this +
                                           " from two threads at the same time.");
        }

        current.visitNode(this);
        lock = current;
    }
    
    /**
     * Clear the write lock on this node.
     */
    synchronized void clearWriteLock() {
        lock = null;
        this.hasPathChanged = false; 
    }

    /**
     *  Set this node's state, registering it with the transactor if necessary.
     */
    void markAs(int s) {
        if (s == state || state == INVALID || state == VIRTUAL || state == TRANSIENT) {
            return;
        }

        if (s == MODIFIED && this.getLayer() > this.getLayerInStorage()) {
        	s = NEW;
        }
        
        state = s;

        if (Thread.currentThread() instanceof Transactor) {
            Transactor tx = (Transactor) Thread.currentThread();

            if (s == CLEAN) {
                clearWriteLock();
                tx.dropNode(this);
            } else {
                tx.visitNode(this);

                if (s == NEW) {
                    clearWriteLock();
                    tx.visitCleanNode(this);
                }
            }
        }
    }

    /**
     * Register this node as parent node with the transactor so that
     * setLastSubnodeChange is called when the transaction completes.
     */
    void registerSubnodeChange() {
        // we do not fetch subnodes for nodes that haven't been persisted yet or are in
        // the process of being persistified - except if "manual" subnoderelation is set.
        if ((state == TRANSIENT || state == NEW) && subnodeRelation == null) {
            return;
        } else if (Thread.currentThread() instanceof Transactor) {
            Transactor tx = (Transactor) Thread.currentThread();
            tx.visitParentNode(this);
        }
    }

    /**
     * Notify the node's parent that its child collection needs to be reloaded
     * in case the changed property has an affect on collection order or content.
     *
     * @param propname the name of the property being changed
     */
    void notifyPropertyChange(String propname) {
        Node parent = (parentHandle == null) ? null : (Node) getParent();

        if ((parent != null) && (parent.getDbMapping() != null)) {
            // check if this node is already registered with the old name; if so, remove it.
            // then set parent's property to this node for the new name value
            DbMapping parentmap = parent.getDbMapping();
            Relation subrel = parentmap.getSubnodeRelation();
            String dbcolumn = dbmap.propertyToColumnName(propname);
            if (subrel == null || dbcolumn == null)
                return;

            if (subrel.order != null && subrel.order.indexOf(dbcolumn) > -1) {
                parent.registerSubnodeChange();
            }
        }
    }

    /**
     * Called by the transactor on registered parent nodes to mark the
     * child index as changed
     */
    public void setLastSubnodeChange(long t) {
        lastSubnodeChange = t;
    }

    /**
     *  Gets this node's stateas defined in the INode interface
     *
     * @return this node's state
     */
    public int getState() {
        return state;
    }

    /**
     * Sets this node's state as defined in the INode interface
     *
     * @param s this node's new state
     */
    public void setState(int s) {
        state = s;
    }

    /**
     *  Mark node as invalid so it is re-fetched from the database
     */
    public void invalidate() {
        // This doesn't make sense for transient nodes
        if ((state == TRANSIENT) || (state == NEW)) {
            return;
        }

        checkWriteLock();
        nmgr.evictNode(this);
    }

    /**
     *  Check for a child mapping and evict the object specified by key from the cache
     */
    public void invalidateNode(String key) {
        // This doesn't make sense for transient nodes
        if ((state == TRANSIENT) || (state == NEW)) {
            return;
        }

        Relation rel = getDbMapping().getSubnodeRelation();

        if (rel != null) {
            if (rel.usesPrimaryKey()) {
                nmgr.evictNodeByKey(new DbKey(getDbMapping().getSubnodeMapping(), key, this.nmgr.nmgr.app.getCurrentRequestEvaluator().getLayer()));
            } else {
                nmgr.evictNodeByKey(new SyntheticKey(getKey(), key));
            }
        }
    }

    /**
     *  Get the ID of this Node. This is the primary database key and used as part of the
     *  key for the internal node cache.
     */
    public String getID() {
        // if we are transient, we generate an id on demand. It's possible that we'll never need
        // it, but if we do it's important to keep the one we have.
        if ((state == TRANSIENT) && (id == null)) {
            id = TransientNode.generateID();
        }

        return id;
    }

    public void setID(String id){
    	this.id = id;
    }
    
    public void setLayer(int layer) {
    	this.layer = layer;
    	this.primaryKey = null;
    	getKey();
    }
    
    public int getLayer() {
    	return getKey().getLayer();
    }
    
    public void setLayerInStorage(int layer) {
    	this.layerInStorage = layer;
    }
    
    public int getLayerInStorage() {
    	return this.layerInStorage;
    }

    /**
     * Returns true if this node is accessed by id from its aprent, false if it
     * is accessed by name
     */
    public boolean isAnonymous() {
        return anonymous;
    }

    /**
     * Return this node' name, which may or may not have some meaning
     */
    public String getName() {
        return name;
    }

    /**
     * Get something to identify this node within a URL. This is the ID for anonymous nodes
     * and a property value for named properties.
     */
    public String getElementName() {
        // check element name - this is either the Node's id or name.
        long lastmod = lastmodified;

        if (dbmap != null) {
            lastmod = Math.max(lastmod, dbmap.getLastTypeChange());
        }

        if ((parentHandle != null) && (lastNameCheck <= lastmod)) {
            try {
                Node p = parentHandle.getNode(nmgr);
                DbMapping parentmap = p.getDbMapping();
                Relation prel = parentmap.getSubnodeRelation();
                if (prel != null) {
                    if (prel.groupby != null) {
                        setName(getString("groupname"));
                        anonymous = false;
                    } else if (prel.accessName != null) {
                        String propname = dbmap.columnNameToProperty(prel.accessName);
                        String propvalue = getString(propname);

                        if ((propvalue != null) && (propvalue.length() > 0)) {
                            setName(propvalue);
                            anonymous = false;
                        } else if (!anonymous && p.isParentOf(this)) {
                            anonymous = true;
                        }
                    } else if (!anonymous && p.isParentOf(this)) {
                        anonymous = true;
                    } 
                } else if (!anonymous && p.isParentOf(this)) {
                    anonymous = true;
                }
            } catch (Exception ignore) {
                // FIXME: add proper NullPointer checks in try statement
                // just fall back to default method
            }

            lastNameCheck = System.currentTimeMillis();
        }

        return (anonymous || (name == null) || (name.length() == 0)) ? id : name;
    }

    /**
     *
     *
     * @return ...
     */
    public String getFullName() {
        return getFullName(null);
    }

    /**
     *
     *
     * @param root ...
     *
     * @return ...
     */
    public String getFullName(INode root) {
        String divider = null;
        StringBuffer b = new StringBuffer();
        INode p = this;
        int loopWatch = 0;

        while ((p != null) && (p.getParent() != null) && (p != root)) {
            if (divider != null) {
                b.insert(0, divider);
            } else {
                divider = "/";
            }

            b.insert(0, p.getElementName());
            p = p.getParent();

            loopWatch++;

            if (loopWatch > 10) {
                b.insert(0, "...");

                break;
            }
        }

        return b.toString();
    }

    /**
     *
     *
     * @return ...
     */
    public String getPrototype() {
        // if prototype is null, it's a vanilla AxiomObject.
        if (prototype == null) {
            return "AxiomObject";
        }

        return prototype;
    }

    /**
     *
     *
     * @param proto ...
     */
    public void setPrototype(String proto) {
        this.prototype = proto;
        // Note: we mustn't set the DbMapping according to the prototype,
        // because some nodes have custom dbmappings, e.g. the groupby
        // dbmappings created in DbMapping.initGroupbyMapping().
    }

    /**
     *
     *
     * @param dbmap ...
     */
    public void setDbMapping(DbMapping dbmap) {
        this.dbmap = dbmap;
    }

    /**
     *
     *
     * @return ...
     */
    public DbMapping getDbMapping() {
        return dbmap;
    }

    /**
     *
     *
     * @param nmgr
     */
    public void setWrappedNodeManager(WrappedNodeManager nmgr) {
        this.nmgr = nmgr;
    }

    /**
     *
     *
     * @return ...
     */
    public Key getKey() {
    	if (primaryKey == null && state == TRANSIENT) {
            throw new RuntimeException("getKey called on transient Node: " + this);
        }

        if ((dbmap == null) && (prototype != null) && (nmgr != null)) {
            dbmap = nmgr.getDbMapping(prototype);
        }

        if (primaryKey == null) {
        	primaryKey = new DbKey(dbmap, id, this.layer);
        }

        return primaryKey;
    }
    
    /**
    *
    *
    * @return ...
    */
   public Key getKey(final int mode) {
       if (primaryKey == null && state == TRANSIENT) {
           throw new RuntimeException("getKey called on transient Node: " + this);
       }

       if ((dbmap == null) && (prototype != null) && (nmgr != null)) {
           dbmap = nmgr.getDbMapping(prototype);
       }

       primaryKey = new DbKey(dbmap, id, mode);

       return primaryKey;
   }

   public Key getInternalKey() {
       Key primaryKey = null;

       if ((dbmap == null) && (prototype != null) && (nmgr != null)) {
           dbmap = nmgr.getDbMapping(prototype);
       }

       try {
           primaryKey = new DbKey(dbmap, id, this.nmgr.nmgr.app.getCurrentRequestEvaluator().getLayer());
       } catch (Exception ex) {
           primaryKey = new DbKey(dbmap, id, DbKey.LIVE_LAYER);
       }

       return primaryKey;
   }

    /**
    *
    *
    * @return ...
    */
    public void setKey(Key key) {
    	checkWriteLock();
    	
    	this.nmgr.evictKey(this.primaryKey);
    	
    	this.primaryKey = key;
    	
        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    }
    
    /**
     *
     *
     * @return ...
     */
    public NodeHandle getHandle() {
        if (handle == null) {
            handle = new NodeHandle(this);
        }

        return handle;
    }

    /**
     *
     *
     * @param rel ...
     */
    public synchronized void setSubnodeRelation(String rel) {
        if (((rel == null) && (this.subnodeRelation == null)) ||
                ((rel != null) && rel.equalsIgnoreCase(this.subnodeRelation))) {
            return;
        }

        checkWriteLock();
        this.subnodeRelation = rel;

        DbMapping smap = (dbmap == null) ? null : dbmap.getSubnodeMapping();

        if ((smap != null) && smap.isRelational()) {
            subnodes = null;
            subnodeCount = -1;
        }
    }

    /**
     *
     *
     * @return ...
     */
    public synchronized String getSubnodeRelation() {
        return subnodeRelation;
    }

    /**
     *
     *
     * @param name ...
     */
    public void setName(String name) {
        if ((name == null) || (name.trim().length() == 0)) {
            // use id as name
            this.name = id;
        } else if (name.indexOf('/') > -1) {
            // "/" is used as delimiter, so it's not a legal char
            return;            
        } else {
            this.name = name;
        }
    }

    /**
     * Set this node's parent node.
     */
    public void setParent(Node parent) {
        parentHandle = (parent == null) ? null : parent.getHandle();
    }

    /**
     *  Set this node's parent node to the node referred to by the NodeHandle.
     */
    public void setParentHandle(NodeHandle parent) {
        parentHandle = parent;
    }   

    /**
     * This version of setParent additionally marks the node as anonymous or non-anonymous,
     * depending on the string argument. This is the version called from the scripting framework,
     * while the one argument version is called from within the objectmodel classes only.
     */
    public void setParent(Node parent, String propertyName) {
        // we only do that for relational nodes.
        if (!isRelational()) {
            return;
        }

        NodeHandle oldParentHandle = parentHandle;

        parentHandle = (parent == null) ? null : parent.getHandle();

        // mark parent as set, otherwise getParent will try to
        // determine the parent again when called.
        lastParentSet = System.currentTimeMillis();

        if ((parentHandle == null) || parentHandle.equals(oldParentHandle)) {
            // nothing changed, no need to find access property
            return;
        }

        if ((parent != null) && (propertyName == null)) {
            // see if we can find out the propertyName by ourselfes by looking at the
            // parent's property relation
            String newname = null;
            DbMapping parentmap = parent.getDbMapping();

            if (parentmap != null) {
                // first try to retrieve name via generic property relation of parent
                Relation prel = parentmap.getSubnodeRelation();

                if ((prel != null) && (prel.otherType == dbmap) &&
                        (prel.accessName != null)) {
                    // reverse look up property used to access this via parent
                    Relation proprel = dbmap.columnNameToRelation(prel.accessName);

                    if ((proprel != null) && (proprel.propName != null)) {
                        newname = getString(proprel.propName);
                    }
                }
            }

            // did we find a new name for this
            if (newname == null) {
                this.anonymous = true;
            } else {
                this.anonymous = false;
                this.name = newname;
            }
        } else {
            this.anonymous = false;
            this.name = propertyName;
        }
    }

    /**
     * Get parent, retrieving it if necessary.
     */
    public INode getParent() {
        // check what's specified in the prototype.properties for this node.
        ParentInfo[] parentInfo = null;

        if (isRelational() && lastParentSet <= Math.max(dbmap.getLastTypeChange(), lastmodified)) {
            parentInfo = dbmap.getParentInfo();
        }
        // check if current parent candidate matches presciption,
        // if not, try to get one that does.
        if (parentInfo != null) {

            for (int i = 0; i < parentInfo.length; i++) {

                ParentInfo pinfo = parentInfo[i];
                Node pn = null;

                // see if there is an explicit relation defined for this parent info
                // we only try to fetch a node if an explicit relation is specified for the prop name
                Relation rel = dbmap.propertyToRelation(pinfo.propname);
                if ((rel != null) && (rel.reftype == Relation.REFERENCE ||
                                      rel.reftype == Relation.COMPLEX_REFERENCE)) {
                    pn = (Node) getNode(pinfo.propname);
                }

                // the parent of this node is the app's root node...
                if ((pn == null) && pinfo.isroot) {
                    pn = nmgr.getRootNode();
                }

                // if we found a parent node, check if we ought to use a virtual or groupby node as parent
                if (pn != null) {
                    // see if dbmapping specifies anonymity for this node
                    if (pinfo.virtualname != null) {
                        pn = (Node) pn.getNode(pinfo.virtualname);
                        if (pn == null)
                            System.err.println("Error: Can't retrieve parent "+
                                               "node "+pinfo+" for "+this);
                    }

                    DbMapping dbm = (pn == null) ? null : pn.getDbMapping();

                    try {
                        if ((dbm != null) && (dbm.getSubnodeGroupby() != null)) {
                            // check for groupby
                            rel = dbmap.columnNameToRelation(dbm.getSubnodeGroupby());
                            pn = (Node) pn.getChildElement(getString(rel.propName));
                        }

                        if (pn != null && pn.isParentOf(this)) {
                            setParent(pn);
                            lastParentSet = System.currentTimeMillis();

                            return pn;
                        }
                    } catch (Exception ignore) {
                    }
                }
                if (i == parentInfo.length-1) {
                    // if we came till here and we didn't find a parent.
                    // set parent to null.
                    setParent(null);
                    lastParentSet = System.currentTimeMillis();
                }
            }
        }

        // fall back to heuristic parent (the node that fetched this one from db)
        if (parentHandle == null) {
            return null;
        }

        return parentHandle.getNode(nmgr);
    }

    /**
     * Get parent, using cached info if it exists.
     */
    public Node getCachedParent() {
        if (parentHandle == null) {
            return null;
        }

        return parentHandle.getNode(nmgr);
    }

    /**
     *  INode-related
     */
    public INode addNode(INode elem) {
        return addNode(elem, -1);
    }

    /**
     * Add a node to this Node's subnodes, making the added node persistent if it
     * hasn't been before and this Node is already persistent.
     *
     * @param elem the node to add to this Nodes subnode-list
     * @param where the index-position where this node has to be added
     *
     * @return the added node itselve
     */
    public INode addNode(INode elem, int where) {
        Node node = null;

        if (elem instanceof Node) {
            node = (Node) elem;
        } else {
            throw new RuntimeException("Can't add fixed-transient node to a persistent node");
        }

        Relation subrel;
        if (dbmap != null && (subrel = dbmap.getSubnodeRelation()) != null) {
            String propname = subrel.groupby != null ? "groupname" : subrel.accessName;
            if (propname != null) {
                String name = node.getString(propname);
                if (name != null) {
                    IPathElement child;
                    if ((child = this.getChildElement(name, false)) != null 
                            && child instanceof Node) {
                        Node nchild = (Node) child;
                        DbKey nchildkey = (DbKey) nchild.getKey();
                        DbKey nodekey = (DbKey) node.getInternalKey();
                        if (nchildkey.getLayer() == nodekey.getLayer()) {
                            // dont want to add a node with the same accessname as
                            // another node already in the subnodes list of this node
                            this.nmgr.nmgr.app.logEvent(ErrorReporter.warningMsg(this.getClass(), "addNode")
                            		+ "Attempt to add " + node + " as a child of " 
                            		+ this + " was rejected because it wouldve create an accessname conflict.");
                            return null; 
                        }
                    }
                }
            }
        }
        
        // only lock nodes if parent node is not transient
        if (state != TRANSIENT) {
            // only lock parent if it has to be modified for a change in subnodes
            if (!ignoreSubnodeChange()) {
                checkWriteLock();
            }

            node.checkWriteLock();
        }

        // if subnodes are defined via realation, make sure its constraints are enforced.
        if ((dbmap != null) && (dbmap.getSubnodeRelation() != null)) {
            dbmap.getSubnodeRelation().setConstraints(this, node);
        }

        // if the new node is marked as TRANSIENT and this node is not, mark new node as NEW
        if ((state != TRANSIENT) && (node.state == TRANSIENT)) {
            node.makePersistable();
        }

        // only mark this node as modified if subnodes are not in relational db
        // pointing to this node.
        if (!ignoreSubnodeChange() && ((state == CLEAN) || (state == DELETED))) {
            markAs(MODIFIED);
        }

        if ((node.state == CLEAN) || (node.state == DELETED)) {
            node.markAs(MODIFIED);
        }

        loadNodes();

        // check if this node has a group-by subnode-relation
        if (dbmap != null) {
            Relation srel = dbmap.getSubnodeRelation();

            if ((srel != null) && (srel.groupby != null)) {
                Relation groupbyRel = srel.otherType.columnNameToRelation(srel.groupby);
                String groupbyProp = (groupbyRel != null) ? groupbyRel.propName
                                                              : srel.groupby;
                String groupbyValue = node.getString(groupbyProp);
                INode groupbyNode = (INode) getChildElement(groupbyValue);

                // if group-by node doesn't exist, we'll create it
                if (groupbyNode == null) {
                    groupbyNode = getGroupbySubnode(groupbyValue, true);
                } else {
                    groupbyNode.setDbMapping(dbmap.getGroupbyMapping());
                }

                groupbyNode.addNode(node);
                return node;
            }
        }

        NodeHandle nhandle = node.getHandle();

        if ((subnodes != null) && subnodes.contains(nhandle)) {
            // Node is already subnode of this - just move to new position
            synchronized (subnodes) {
                subnodes.remove(nhandle);
                // check if index is out of bounds when adding
                if(subnodes instanceof HashSet){
                	subnodes.add(nhandle);
                } else if(subnodes instanceof ArrayList){
                	if (where < 0 || where > subnodes.size()) {
                		((ArrayList<NodeHandle>)subnodes).add(nhandle);
                	} else {
                		((ArrayList<NodeHandle>)subnodes).add(where, nhandle);
                	}
                }
            }
        } else {
            // create subnode list if necessary
            if (subnodes == null) {
                subnodes = createSubnodeList();
            }

            // check if subnode accessname is set. If so, check if another node
            // uses the same access name and remove it
            if ((dbmap != null) && (node.dbmap != null)) {
                Relation prel = dbmap.getSubnodeRelation();

                if ((prel != null) && (prel.accessName != null)) {
                    Relation localrel = node.dbmap.columnNameToRelation(prel.accessName);

                    // if no relation from db column to prop name is found,
                    // assume that both are equal
                    String propname = (localrel == null) ? prel.accessName
                                                         : localrel.propName;
                    String prop = node.getString(propname);

                    if ((prop != null) && (prop.length() > 0)) {
                        INode old = (INode) getChildElement(prop, false);

                        if ((old != null) && (old != node)) {
                            // FIXME: we delete the existing node here,
                            // but actually the app developer should prevent this from
                            // happening, so it might be better to throw an exception.
                        	old.remove();
                            this.removeNode(old);
                        }

                        if (state != TRANSIENT) {
                            Transactor tx = (Transactor) Thread.currentThread();
                            SyntheticKey key = new SyntheticKey(this.getKey(), prop);
                            tx.visitCleanNode(key, node);
                            nmgr.registerNode(node, key);
                        }
                    }
                }
            }

            // actually add the new child to the subnode list
            synchronized (subnodes) {
                // check if index is out of bounds when adding
                if(subnodes instanceof HashSet){
                	subnodes.add(nhandle);
                } else if(subnodes instanceof ArrayList){
                	if (where < 0 || where > subnodes.size()) {
                		((ArrayList<NodeHandle>)subnodes).add(nhandle);
                	} else {
                		((ArrayList<NodeHandle>)subnodes).add(where, nhandle);
                	}
                }
            }

            if (node != this && !nmgr.isRootNode(node)) {
                // avoid calling getParent() because it would return bogus results
                // for the not-anymore transient node
                Node nparent = (node.parentHandle == null) ? null
                                                           : node.parentHandle.getNode(nmgr);

                // if the node doesn't have a parent yet, or it has one but it's
                // transient while we are persistent, make this the nodes new parent.
                if ((nparent == null) ||
                        ((state != TRANSIENT) && (nparent.getState() == TRANSIENT))) {
                    node.setParent(this);
                    node.anonymous = true;
                }
            }
        }

        lastmodified = System.currentTimeMillis();
        // we want the element name to be recomputed on the child node
        node.lastNameCheck = 0;
        node.hasPathChanged = true;
        registerSubnodeChange();
        
        // Keeps track if a relational node was just added to a lucene node
        if ((this.dbmap == null || !this.dbmap.isRelational()) 
                && (node.dbmap != null && node.dbmap.isRelational())) {
            this.relationalNodeAdded = true;
        }

        return node;
    }

    /**
     *
     *
     * @return ...
     */
    public INode createNode() {
        // create new node at end of subnode array
        return createNode(null, -1);
    }

    /**
     *
     *
     * @param where ...
     *
     * @return ...
     */
    public INode createNode(int where) {
        return createNode(null, where);
    }

    /**
     *
     *
     * @param nm ...
     *
     * @return ...
     */
    public INode createNode(String nm) {
        // parameter where is  ignored if nm != null so we try to avoid calling numberOfNodes()
        return createNode(nm, -1);
    }

    /**
     *
     *
     * @param nm ...
     * @param where ...
     *
     * @return ...
     */
    public INode createNode(String nm, int where) {
        // checkWriteLock();
        boolean anon = false;

        if ((nm == null) || "".equals(nm.trim())) {
            anon = true;
        }

        String proto = null;

        // try to get proper prototype for new node
        if (dbmap != null) {
            DbMapping childmap = anon ?
                dbmap.getSubnodeMapping() :
                dbmap.getPropertyMapping(nm);
            if (childmap != null) {
                proto = childmap.getTypeName();
            }
        }

        Node n = new Node(nm, proto, nmgr);

        if (anon) {
            addNode(n, where);
        } else {
            setNode(nm, n);
        }

        return n;
    }

    public IPathElement getChildElement(String name) {
        return this.getChildElement(name, true, -1);
    }
    
    public IPathElement getChildElement(String name, boolean searchList) {
        return this.getChildElement(name, searchList, -1);
    }

    public IPathElement getChildElement(String name, final int mode) {
        return this.getChildElement(name, true, mode);
    }
    
    public IPathElement getChildElement(String name, final boolean searchList, int mode) {
    	if (dbmap != null) {
            // if a dbmapping is provided, check what it tells us about
            // getting this specific child element
            Relation rel = dbmap.getExactPropertyRelation(name);
            
            if (rel != null && !rel.isPrimitive()) {
                return getNode(name);
            }

            rel = dbmap.getSubnodeRelation();
            
            if ((rel != null) && (rel.groupby != null || rel.accessName != null)) {
                if (state != TRANSIENT && rel.otherType != null && rel.otherType.isRelational()) {
                    return nmgr.getNode(this, name, rel);
                } else {
                    // Do what we have to do: loop through subnodes and
                    // check if any one matches
                    String propname = rel.groupby != null ? "groupname" : rel.accessName; 
                    if (mode == -1) {
                        mode = DbKey.LIVE_LAYER;
                        RequestEvaluator re = this.nmgr.nmgr.app.getCurrentRequestEvaluator();
                        if (re != null) {
                            mode = re.getLayer();
                        }
                    }
                    
                    Key key = this.nmgr.getChildKey(propname, name, this.getID(), mode);
                    INode node = null;
                    
                    /*
                     *  If its not in the embedded storage yet, and it does exist as a child
                     *  it means it was added in the current transaction, so look through
                     *  the subnodes list to find it. 
                     */
                    
                    if (key == null) {
                        if (searchList) {
                            RequestEvaluator re = this.nmgr.nmgr.app.getCurrentRequestEvaluator();
                            Transactor tr = null;
                            if (re != null) {
                                tr = re.getThread();
                            }
                            if (tr != null) {
                                ArrayList list = tr.getVisitedChildren(this);
                                final int sizeOfList = list.size();
                                for (int i = 0; i < sizeOfList; i++) {
                                    Node n = (Node) list.get(i);
                                    if (name.equalsIgnoreCase(n.getString(propname))) {
                                        node = n;
                                        break;
                                    }
                                }
                            } 
                        }
                        if (node == null && searchList) {
                            Enumeration e = getSubnodes();
                            while (e.hasMoreElements()) {
                                Node n = (Node) e.nextElement();
                                if (n == null) {
                                    continue;
                                }
                                if (((DbKey) n.getKey()).getLayer() != mode) {
                                    continue;
                                }
                                if (name.equalsIgnoreCase(n.getString(propname))) {
                                    node = n;
                                    break;
                                }
                            }
                        }
                    } else { 
                        Thread t = Thread.currentThread();
                        if (t instanceof Transactor) {
                            Transactor tx = (Transactor) t;
                            Node tn = tx.getVisitedNode(key);
                            if (tn != null) {
                                node = tn;
                            } else {
                                tn = this.nmgr.getNode(key);
                                if (tn != null) {
                                    node = tn;
                                }
                            }
                        }
                        
                        if (node != null && node.getState() == Node.MODIFIED && node.getParent() == null 
                                && node instanceof Node
                                && !((Node) node).isRelational()) {
                            return null;
                        }
                    }

                    // set DbMapping for embedded db group nodes
                    if (node != null && rel.groupby != null) {
                         node.setDbMapping(dbmap.getGroupbyMapping());
                    }

                    return node;
                }
            }
            
            return getSubnode(name);
        } else {
            // no dbmapping - just try child collection first, then named property.
            INode child = getSubnode(name);

            if (child == null) {
                child = getNode(name);
            }

            return child;
        }
    }

    /**
     * This implements the getParentElement() method of the IPathElement interface
     */
    public IPathElement getParentElement() {
        return getParent();
    }

    /**
     *
     *
     * @param subid ...
     *
     * @return ...
     */
    public INode getSubnode(String subid) {
        if (subid == null || subid.length() == 0) {
            return null;
        }

        Node retval = null;

        if (subid != null) {
            loadNodes();

            if ((subnodes == null) || (subnodes.size() == 0)) {
                return null;
            }

            NodeHandle nhandle = null;

            for(NodeHandle nh : subnodes){
            	try{
	            	if(subid.equals(nh.getID())){
	            		nhandle = nh;
	            		break;
	            	}
            	} catch(Exception e){
            		break;
            	}
            	
            }

            if (nhandle != null) {
                retval = nhandle.getNode(nmgr);
            }

            // This would be an alternative way to do it, without loading the subnodes:
            //    if (dbmap != null && dbmap.getSubnodeRelation () != null)
            //         retval = nmgr.getNode (this, subid, dbmap.getSubnodeRelation ());
            if ((retval != null) && (retval.parentHandle == null) &&
                    !nmgr.isRootNode(retval)) {
                retval.setParent(this);
                retval.anonymous = true;
            }
        }

        return retval;
    }

    /**
     *
     *
     * @param index ...
     * @deprecated 8/29/2008, when changing subnodes to HashSet from ArrayList.
     * @return ...
     */
    public INode getSubnodeAt(int index) {
        loadNodes();

        if (subnodes == null) {
            return null;
        }
        nmgr.logEvent("Node.getSubNodeAt(int index) has been deprecated and may return unexpected results");

        Node retval = null;

        if (subnodes.size() > index) {
            if(subnodes instanceof ArrayList){
                retval = ((NodeHandle) ((ArrayList<NodeHandle>)subnodes).get(index)).getNode(nmgr);            	
            } else{
                retval = subnodes.iterator().next().getNode(nmgr);
            }
            if ((retval != null) && (retval.parentHandle == null) &&
                    !nmgr.isRootNode(retval)) {
                retval.setParent(this);
                retval.anonymous = true;
            }
        }

        return retval;
    }

    /**
     *
     *
     * @param sid ...
     * @param create ...
     *
     * @return ...
     */
    protected Node getGroupbySubnode(String sid, boolean create) {
        if (sid == null) {
            throw new IllegalArgumentException("Can't create group by null");
        }

        if (state == TRANSIENT) {
            throw new RuntimeException("Can't add grouped child on transient node. "+
                                       "Make parent persistent before adding grouped nodes.");
        }

        loadNodes();

        if (subnodes == null) {
            subnodes = new SubnodeList(nmgr, dbmap.getSubnodeRelation());
        }

        if (create || subnodes.contains(new NodeHandle(new SyntheticKey(getKey(), sid)))) {
            try {
                DbMapping groupbyMapping = dbmap.getGroupbyMapping();
                boolean relational = groupbyMapping.getSubnodeMapping().isRelational();

                if (relational || create) {
                    Node node = relational ? new Node(this, sid, nmgr, null)
                                           : new Node(sid, null, nmgr);

                    // set "groupname" property to value of groupby field
                    node.setString("groupname", sid);

                    node.setDbMapping(groupbyMapping);

                    if (!relational) {
                        // if we're not transient, make new node persistable
                        if (state != TRANSIENT) {
                            node.makePersistable();
                            node.checkWriteLock();
                        }
                        subnodes.add(node.getHandle());
                    }

                    // Set the dbmapping on the group node
                    node.setPrototype(groupbyMapping.getTypeName());
                    // If we created the group node, we register it with the
                    // nodemanager. Otherwise, we just evict whatever was there before
                    if (create) {
                        // register group node with transactor
                        Transactor tx = (Transactor) Thread.currentThread();
                        tx.visitCleanNode(node);
                        nmgr.registerNode(node);
                    } else {
                        nmgr.evictKey(node.getKey());
                    }

                    return node;
                }
            } catch (Exception noluck) {
                nmgr.nmgr.app.logError("Error creating group-by node for " + sid, noluck);
            }
        }

        return null;
    }

    /**
     *
     *
     * @return ...
     */
    public boolean remove() {
        INode parent = getParent();
        if (parent != null) {
            parent.removeNode(this);
        }
        deepRemoveNode();
        return true;
    }

    /**
     *
     *
     * @param node ...
     */
    public void removeNode(INode node) {
        Node n = (Node) node;
        releaseNode(n);
        // added so that when parent.removeNode(child) is called on a 
        // relational node, it is set to be marked for deletion.  We dont do this in the 
        // embedded db case because that node may be attached to another parent later on.
        if (n.isRelational()) {
            Relation rel = null;
            if (dbmap != null && (rel = dbmap.getSubnodeRelation()) != null) {
                if (rel.deleteOnRemove) {
                    n.deepRemoveNode();
                } else {
                    rel.unsetConstraints(this, node);
                }
            }
        }
    }

    /**
     * "Locally" remove a subnode from the subnodes table.
     *  The logical stuff necessary for keeping data consistent is done in removeNode().
     */
    protected void releaseNode(Node node) {
        INode parent = node.getParent();

        checkWriteLock();
        node.checkWriteLock();

        boolean removed = false;

        // load subnodes in case they haven't been loaded.
        // this is to prevent subsequent access to reload the
        // index which would potentially still contain the removed child
        loadNodes();

        if (subnodes != null) {
            synchronized (subnodes) {
                removed = subnodes.remove(node.getHandle());
            }
        }

        if (removed) {
            registerSubnodeChange();
        }

        // check if subnodes are also accessed as properties. If so, also unset the property
        if ((dbmap != null) && (node.dbmap != null)) {
            Relation prel = dbmap.getSubnodeRelation();

            if ((prel != null) && (prel.accessName != null)) {
                Relation localrel = node.dbmap.columnNameToRelation(prel.accessName);

                // if no relation from db column to prop name is found, assume that both are equal
                String propname = (localrel == null) ? prel.accessName : localrel.propName;
                String prop = node.getString(propname);

                if ((prop != null) && (getNode(prop) == node)) {
                    unset(prop);
                }
            }
        }

        if (parent != null && parent instanceof Node 
        		&& ((Node) parent).getKey().equals(this.getKey())) {
            // we must change the last modified time
            node.lastmodified = System.currentTimeMillis(); 

            // Our child nodes in lucene must be aware of and store their current parent
            if (!node.isRelational()) {
                node.markAs(MODIFIED);  
            }
            node.setParentHandle(null);
        }

        // If subnodes are relational no need to mark this node as modified
        if (ignoreSubnodeChange()) {
            return;
        }

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED); 
        }
    }

    /**
     * Delete the node from the db. This mainly tries to notify all nodes referring to this that
     * it's going away. For nodes from the embedded db it also does a cascading delete, since
     * it can tell which nodes are actual children and which are just linked in.
     */
    protected void deepRemoveNode() {
        // tell all nodes that are properties of n that they are no longer used as such
        if (propMap != null) {
            for (Enumeration en = propMap.elements(); en.hasMoreElements();) {
                Property p = (Property) en.nextElement();

                if ((p != null) && (p.getType() == Property.NODE)) {
                    Node n = (Node) p.getNodeValue();
                    if (n != null && !n.isRelational() && n.getParent() == this) {
                        n.deepRemoveNode();
                    } 
                }
            }
        }

        // cascading delete of all subnodes. This is never done for relational subnodes, because
        // the parent info is not 100% accurate for them.
        if (subnodes != null) {
            Vector v = new Vector();
            // remove modifies the Vector we are enumerating, so we are extra careful.
            for (Enumeration en = getSubnodes(); en.hasMoreElements();) {
                v.add(en.nextElement());
            }

            int m = v.size();

            for (int i = 0; i < m; i++) {
                // getParent() is heuristical/implicit for relational nodes, so we don't base
                // a cascading delete on that criterium for relational nodes.
                Node n = (Node) v.get(i);

                if (!n.isRelational() && n.getParent() == this) {
                    n.deepRemoveNode();
                }
            }
        }

        // mark the node as deleted
        setParent(null);
        markAs(DELETED);
    }

    /**
     * Check if the given node is contained in this node's child list.
     * If it is contained return its index in the list, otherwise return -1.
     *
     * @param n a node
     *
     * @return the node's index position in the child list, or -1
     */
    public boolean contains(INode n) {
        // if the node contains relational groupby subnodes, the subnodes vector
        // contains the names instead of ids.
        if (n == null || !(n instanceof Node)) {
            return false;
        }

        loadNodes();

        if (subnodes == null) {
            return false;
        }

        return subnodes.contains(((Node)n).getHandle());
    }

    /**
     * Check if the given node is contained in this node's child list. This
     * is similar to <code>contains(INode)</code> but does not load the
     * child index for relational nodes.
     *
     * @param n a node
     * @return true if the given node is contained in this node's child list
     */
    public boolean isParentOf(Node n) {
        if (dbmap != null) {
            Relation subrel = dbmap.getSubnodeRelation();
            // if we're dealing with relational child nodes use
            // Relation.checkConstraints to avoid loading the child index.
            // Note that we only do that if no filter is set, since
            // Relation.checkConstraints() would always return false
            // if there was a filter property.
            if (subrel != null && subrel.otherType != null
                               && subrel.otherType.isRelational()
                               && subrel.filter == null) {
                // first check if types are stored in same table
                if (!subrel.otherType.isStorageCompatible(n.getDbMapping())) {
                    return false;
                }
                // if they are, check if constraints are met
                return subrel.checkConstraints(this, n);
            }
        }
        // just fall back to contains() for non-relational nodes
        return contains(n);
    }

    /**
     * Count the subnodes of this node. If they're stored in a relational data source, we
     * may actually load their IDs in order to do this.
     */
    public int numberOfNodes() {
        // If the subnodes are loaded aggressively, we really just
        // do a count statement, otherwise we just return the size of the id index.
        // (after loading it, if it's coming from a relational data source).
        DbMapping smap = (dbmap == null) ? null : dbmap.getSubnodeMapping();

        if ((smap != null) && smap.isRelational()) {
            // check if subnodes need to be rechecked
            Relation subRel = dbmap.getSubnodeRelation();

            // do not fetch subnodes for nodes that haven't been persisted yet or are in
            // the process of being persistified - except if "manual" subnoderelation is set.
            if (subRel.aggressiveLoading && subRel.getGroup() == null &&
                    (((state != TRANSIENT) && (state != NEW)) ||
                    (subnodeRelation != null))) {
                // we don't want to load *all* nodes if we just want to count them
                long lastChange = subRel.aggressiveCaching ? lastSubnodeChange
                                                           : smap.getLastDataChange();

                // also reload if the type mapping has changed.
                lastChange = Math.max(lastChange, dbmap.getLastTypeChange());

                if ((lastChange < lastSubnodeFetch) && (subnodes != null)) {
                    // we can use the nodes vector to determine number of subnodes
                    subnodeCount = subnodes.size();
                    lastSubnodeCount = System.currentTimeMillis();
                } else if ((lastChange >= lastSubnodeCount) || (subnodeCount < 0)) {
                    // count nodes in db without fetching anything
                    subnodeCount = nmgr.countNodes(this, subRel);
                    lastSubnodeCount = System.currentTimeMillis();
                }
                return subnodeCount;
            }
        }

        loadNodes();

        return (subnodes == null) ? 0 : subnodes.size();
    }

    /**
     * Make sure the subnode index is loaded for subnodes stored in a relational data source.
     *  Depending on the subnode.loadmode specified in the prototype.properties, we'll load just the
     *  ID index or the actual nodes.
     */
    public void loadNodes() {
        // Don't do this for transient nodes which don't have an explicit subnode relation set
        if (((state == TRANSIENT) || (state == NEW)) && (subnodeRelation == null)) {
            return;
        }

        DbMapping smap = (dbmap == null) ? null : dbmap.getSubnodeMapping();

        // Added a condition to check if dbmap.isRelational(), because if an embedded
        // lucene node contains a relational db node as a subnode, its reference is stored
        // in lucene and we do not need to query for it from the relational data source
        if ((smap != null) && smap.isRelational() && 
                (dbmap.isRelational() || this.isNamedRelationalProperty())) {
            // check if subnodes need to be reloaded
            Relation subRel = dbmap.getSubnodeRelation();

            synchronized (this) {
                long lastChange = subRel.aggressiveCaching ? lastSubnodeChange
                                                           : smap.getLastDataChange();

                // also reload if the type mapping has changed.
                lastChange = Math.max(lastChange, dbmap.getLastTypeChange());

                if ((lastChange >= lastSubnodeFetch && !subRel.autoSorted) ||
                        (subnodes == null)) {
                    if (subRel.updateCriteria!=null) {
                        // updateSubnodeList is setting the subnodes directly returning an integer
                        nmgr.updateSubnodeList(this, subRel);
                    } else if (subRel.aggressiveLoading) {
                        subnodes = nmgr.getNodes(this, subRel);
                    } else {
                        subnodes = nmgr.getNodeIDs(this, subRel);
                    }

                    lastSubnodeFetch = System.currentTimeMillis();
                }
            }
        }
    }

    /**
     * Retrieve an empty subnodelist. This empty List is an instance of the Class
     * used for this Nodes subnode-list
     * @return List an empty List of the type used by this Node
     */
    public Collection<NodeHandle> createSubnodeList() {
    	Relation rel = this.dbmap == null ? null : this.dbmap.getSubnodeRelation();
        if (rel != null && rel.updateCriteria != null) {
            subnodes = new UpdateableSubnodeList(nmgr, rel);
        } else if (rel != null && rel.autoSorted) {
            subnodes = new OrderedSubnodeList(nmgr, rel);
        } else {
        	subnodes = new HashSet<NodeHandle>();
        }
        return subnodes;
    }

    /**
     *
     *
     * @param startIndex ...
     * @param length ...
     *
     * @throws Exception ...
     */
    public void prefetchChildren(int startIndex, int length)
                          throws Exception {
        if (length < 1) {
            return;
        }

        if (startIndex < 0) {
            return;
        }

        loadNodes();

        if (subnodes == null) {
            return;
        }

        if (startIndex >= subnodes.size()) {
            return;
        }

        int l = Math.min(subnodes.size() - startIndex, length);

        if (l < 1) {
            return;
        }

        Key[] keys = new Key[l];
        Iterator<NodeHandle> nhs = subnodes.iterator();
        for (int i = 0; i < l; i++) {
        	keys[i] = nhs.next().getKey();
        }

        prefetchChildren (keys);
    }

    public void prefetchChildren (Key[] keys) throws Exception {
        nmgr.nmgr.prefetchNodes(this, dbmap.getSubnodeRelation(), keys);
    }

    /**
     *
     *
     * @return ...
     */
    public Enumeration<Node> getSubnodes() {
        loadNodes();
        
        if(subnodes == null){
        	subnodes = createSubnodeList();
        }
        
        class Enum implements Enumeration<Node> {
            int count = 0;
            Iterator<NodeHandle> it = subnodes.iterator();

            public boolean hasMoreElements() {
                return count < numberOfNodes();
            }

            public Node nextElement() {
            	count++;
            	return it.next().getNode(nmgr);
            }
        }

        return new Enum();
    }

    public boolean getTypeDirty() {
    	if ((dbmap != null) && (dbmap.getPrototypeField() != null) && (this.get(dbmap.getPrototypeField()) != null)) {
    		return this.typeDirty;
    	}
    	return false;
    }
    
    /**
     * Return this Node's subnode list
     *
     * @return the subnode list
     */
    public Collection<NodeHandle> getSubnodeList() {
        return subnodes;
    }

   /**
    * Return true if a change in subnodes can be ignored because it is
    * stored in the subnodes themselves.
    * 
    * Changed because we are now storing references to relational child 
    * nodes in the embedded data source, so we need to know of any changes
    */
    private boolean ignoreSubnodeChange() {
        Relation rel = (dbmap == null) ? null : dbmap.getSubnodeRelation();
        
        /*return ((rel != null) && (rel.otherType != null) 
                && ((rel.otherType.isRelational() && rel.ownType.isRelational())));*/
        return !(rel != null && rel.otherType != null && !rel.ownType.isRelational() &&
                        rel.otherType.isRelational());
    }

    /**
     *  Get all properties of this node.
     */
    public Enumeration properties() {
        if ((dbmap != null) && dbmap.isRelational()) {
            // return the properties defined in prototype.properties, if there are any
            return dbmap.getPropertyEnumeration();
        }

        Relation prel = (dbmap == null) ? null : dbmap.getSubnodeRelation();

        if ((prel != null) && prel.hasAccessName() && (prel.otherType != null) &&
                prel.otherType.isRelational()) {
            // return names of objects from a relational db table
            return nmgr.getPropertyNames(this, prel).elements();
        } else if (propMap != null) {
            // return the actually explicitly stored properties
            return propMap.keys();
        }

        // sorry, no properties for this Node
        return new EmptyEnumeration();

        // NOTE: we don't enumerate node properties here
        // return propMap == null ? new Vector ().elements () : propMap.elements ();
    }

    /**
     *
     *
     * @return ...
     */
    public Hashtable getPropMap() {
        return propMap;
    }

    /**
     *
     *
     * @param propname ...
     *
     * @return ...
     */
    public IProperty get(String propname) {
        return getProperty(propname);
    }

    /**
     *
     *
     * @return ...
     */
    public String getParentInfo() {
        return "anonymous:" + anonymous + ",parentHandle" + parentHandle + ",parent:" +
               getParent();
    }

    /**
     *
     *
     * @param propname ...
     *
     * @return ...
     */
    protected Property getProperty(String propname) {
        if (propname == null) {
            return null;
        }

        Relation rel = dbmap == null ?
                             null :
                             dbmap.getExactPropertyRelation(propname);
        
        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }
        
        // 1) check if the property is contained in the propMap
        Property prop = propMap == null ? null :
                        (Property) propMap.get(propname);
        
        if (prop != null) {
            if (rel != null) {
                // Is a relational node stored by id but things it's a string or int. Fix it.
                if (rel.otherType != null && prop.getType() != Property.NODE) {
                    prop.convertToNodeReference(rel.otherType, this.nmgr.nmgr.app.getCurrentRequestEvaluator().getLayer());
                }
                if (rel.isVirtual()) {
                    // property was found in propMap and is a collection - this is
                    // a collection holding non-relational objects. set DbMapping and
                    // NodeManager
                    Node n = (Node) prop.getNodeValue();
                    if (n != null) {
                        // do set DbMapping for embedded db collection nodes
                        n.setDbMapping(rel.getVirtualMapping());
                        // also set node manager in case this is a mountpoint node
                        // that came in through replication
                        n.nmgr = nmgr;
                    }
                }
            }
            return prop;
        } else if (state == TRANSIENT && rel != null && rel.isVirtual()) {
            // When we get a collection from a transient node for the first time, or when
            // we get a collection whose content objects are stored in the embedded
            // XML data storage, we just want to create and set a generic node without
            // consulting the NodeManager about it.
            Node n = new Node(propname, rel.getPrototype(), nmgr);
            n.setDbMapping(rel.getVirtualMapping());
            n.setParent(this);
            setNode(propname, n);
            return (Property) propMap.get(propname);
        }

        // 2) check if this is a create-on-demand node property
        if (rel != null && (rel.isVirtual() || rel.isComplexReference())) {
            if (state != TRANSIENT) {
                Node n = nmgr.getNode(this, propname, rel);

                if (n != null) {
                    if ((n.parentHandle == null) &&
                            !nmgr.isRootNode(n)) {
                        n.setParent(this);
                        n.name = propname;
                        n.anonymous = false;
                    }
                    return new Property(propname, this, n);
                }
            }
        }

        // 4) nothing to be found - return null
        return null;
    }

    /**
     *
     *
     * @param propname ...
     *
     * @return ...
     */
    public String getString(String propname) {
        // propname = propname.toLowerCase ();
        Property prop = getProperty(propname);
        if(prop != null){
        	try {
        		return prop.getStringValue();
        	} catch (Exception ignore) {
        	}
        }
        return null;
    }

    /**
     *
     *
     * @param propname ...
     *
     * @return ...
     */
    public long getInteger(String propname) {
        // propname = propname.toLowerCase ();
        Property prop = getProperty(propname);
        if(prop != null){
        	try {
        		return prop.getIntegerValue();
        	} catch (Exception ignore) {
        	}
        }
        return 0;
    }

    /**
     *
     *
     * @param propname ...
     *
     * @return ...
     */
    public double getFloat(String propname) {
        // propname = propname.toLowerCase ();
        Property prop = getProperty(propname);
        if(prop != null){
        	try {
        		return prop.getFloatValue();
        	} catch (Exception ignore) {
        	}	
        }
        return 0.0;
    }
    
    /**
     *
     *
     * @param propname ...
     *
     * @return ...
     */
    public Date getDate(String propname) {
        // propname = propname.toLowerCase ();
        Property prop = getProperty(propname);
        if(prop != null){
        	try {
        		return prop.getDateValue();
        	} catch (Exception ignore) {
        	}
    	}
    	return null;
    }
    
    /**
     *
     *
     * @param propname ...
     *
     * @return ...
     */
    public boolean getBoolean(String propname) {
        // propname = propname.toLowerCase ();
        Property prop = getProperty(propname);
        if(prop != null){
        	try {
        		return prop.getBooleanValue();
        	} catch (Exception ignore) {
        	}
        }
        return false;
    }

    /**
     *
     *
     * @param propname ...
     *
     * @return ...
     */
    public INode getNode(String propname) {
        // propname = propname.toLowerCase ();
        Property prop = getProperty(propname);
        if(prop != null){
        	try {
        		return prop.getNodeValue();
        	} catch (Exception ignore) {
        	}
        }
        return null;
    }

    /**
     *
     *
     * @param propname ...
     *
     * @return ...
     */
    public Object getJavaObject(String propname) {
        // propname = propname.toLowerCase ();
        Property prop = getProperty(propname);
        if(prop != null){
        	try {
        		return prop.getJavaObjectValue();
        	} catch (Exception ignore) {
        	}
        }
        return null;
    }

    /**
     * Directly set a property on this node
     *
     * @param propname ...
     * @param value ...
     */
    protected void set(String propname, Object value, int type) {
        checkWriteLock();

        if (propMap == null) {
            propMap = new Hashtable();
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }        
        
        propname = propname.trim();

        String p2 = propname;

        Property prop = (Property) propMap.get(p2);

        if (prop != null) {
            prop.setValue(value, type);
        } else {
            prop = new Property(propname, this);
            prop.setValue(value, type);
            propMap.put(p2, prop);
        }

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    }

    /**
     *
     *
     * @param propname ...
     * @param value ...
     */
    public void setString(String propname, String value) {
        checkWriteLock();

        if (propMap == null) {
            propMap = new Hashtable();
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }           
        
        propname = propname.trim();

        String p2 = propname;

        Property prop = (Property) propMap.get(p2);
        String oldvalue = null;

        if (prop != null) {
            oldvalue = prop.getStringValue();

            // check if the value has changed
            if ((value != null) && value.equals(oldvalue)) {
                return;
            }

            prop.setStringValue(value);
        } else {
            prop = new Property(propname, this);
            prop.setStringValue(value);
            propMap.put(p2, prop);
        }

        if (dbmap != null) {

            // check if this may have an effect on the node's parerent's child collection
            // in combination with the accessname or order field.
            Node parent = (parentHandle == null) ? null : (Node) getParent();

            if ((parent != null) && (parent.getDbMapping() != null)) {
                DbMapping parentmap = parent.getDbMapping();
                Relation subrel = parentmap.getSubnodeRelation();
                String dbcolumn = dbmap.propertyToColumnName(propname);

                if (subrel != null && dbcolumn != null) {
                    // inlined version of notifyPropertyChange();
                    if (subrel.order != null && subrel.order.indexOf(dbcolumn) > -1) {
                        parent.registerSubnodeChange();
                    }
                    // check if accessname has changed
                    if (subrel.accessName != null &&
                            subrel.accessName.equals(dbcolumn)) {
                    	
                    	this.hasPathChanged = true;
                        // if any other node is contained with the new value, remove it
                        INode n = (INode) parent.getChildElement(value);

                        if ((n != null) && (n != this) && (this.layer == ((Node) n).getLayerInStorage())) {
                            parent.unset(value);
                            parent.removeNode(n);
                        }

                        // check if this node is already registered with the old name;
                        // if so, remove it, then add again with the new acessname
                        if (oldvalue != null) {
                            n = (INode) parent.getChildElement(oldvalue);

                            if (n == this) {
                                parent.unset(oldvalue);
                                parent.addNode(this);

                                // let the node cache know this key's not for this node anymore.
                                nmgr.evictKey(new SyntheticKey(parent.getKey(), oldvalue));
                            }
                        }

                        setName(value);
                    }
                }
            }

            // check if the property we're setting specifies the prototype of this object.
            if (dbmap.getPrototypeField() != null &&
                    propname.equals(dbmap.columnNameToProperty(dbmap.getPrototypeField()))) {
                DbMapping newmap = nmgr.getDbMapping(value);

                if (newmap != null) {
                    // see if old and new prototypes have same storage - otherwise type change is ignored
                    String oldStorage = dbmap.getStorageTypeName();
                    String newStorage = newmap.getStorageTypeName();

                    if (((oldStorage == null) && (newStorage == null)) ||
                            ((oldStorage != null) && oldStorage.equals(newStorage))) {
                        long now = System.currentTimeMillis();
                        dbmap.setLastDataChange(now);
                        newmap.setLastDataChange(now);
                        this.dbmap = newmap;
                        this.prototype = value;
                    }
                }
            }
        }

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    }

    /**
     *
     *
     * @param propname ...
     * @param value ...
     */
    public void setInteger(String propname, long value) {
        checkWriteLock();

        if (propMap == null) {
            propMap = new Hashtable();
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }        
        
        propname = propname.trim();

        String p2 = propname;

        Property prop = (Property) propMap.get(p2);

        if (prop != null) {
            prop.setIntegerValue(value);
        } else {
            prop = new Property(propname, this);
            prop.setIntegerValue(value);
            propMap.put(p2, prop);
        }

        notifyPropertyChange(propname);

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    }

    /**
     *
     *
     * @param propname ...
     * @param value ...
     */
    public void setFloat(String propname, double value) {
        checkWriteLock();

        if (propMap == null) {
            propMap = new Hashtable();
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }   
        
        propname = propname.trim();

        String p2 = propname;

        Property prop = (Property) propMap.get(p2);

        if (prop != null) {
            prop.setFloatValue(value);
        } else {
            prop = new Property(propname, this);
            prop.setFloatValue(value);
            propMap.put(p2, prop);
        }

        notifyPropertyChange(propname);

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    }

    /**
     *
     *
     * @param propname ...
     * @param value ...
     */
    public void setBoolean(String propname, boolean value) {
        checkWriteLock();

        if (propMap == null) {
            propMap = new Hashtable();
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }   
        
        propname = propname.trim();

        String p2 = propname;

        Property prop = (Property) propMap.get(p2);

        if (prop != null) {
            prop.setBooleanValue(value);
        } else {
            prop = new Property(propname, this);
            prop.setBooleanValue(value);
            propMap.put(p2, prop);
        }

        notifyPropertyChange(propname);

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    }
    
    /**
    *
    *
    * @param propname ...
    * @param value ...
    */
   public void setNodeHandle(String propname, NodeHandle handle) {
       checkWriteLock();

       if (propMap == null) {
           propMap = new Hashtable();
       }

       if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
           propname = propname.toLowerCase();
       }   
       
       propname = propname.trim();

       String p2 = propname;

       Property prop = (Property) propMap.get(p2);

       if (prop != null) {
           prop.setNodeHandle(handle);
       } else {
           prop = new Property(propname, this);
           prop.setNodeHandle(handle);
           propMap.put(p2, prop);
       }

       notifyPropertyChange(propname);

       lastmodified = System.currentTimeMillis();

       if (state == CLEAN) {
           markAs(MODIFIED);
       }
   }

    /**
     *
     *
     * @param propname ...
     * @param value ...
     */
    public void setDate(String propname, Date value) {
        checkWriteLock();

        if (propMap == null) {
            propMap = new Hashtable();
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }   
        
        propname = propname.trim();

        String p2 = propname;

        Property prop = (Property) propMap.get(p2);

        if (prop != null) {
            prop.setDateValue(value);
        } else {
            prop = new Property(propname, this);
            prop.setDateValue(value);
            propMap.put(p2, prop);
        }

        notifyPropertyChange(propname);

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    }
    
    /**
     *
     *
     * @param propname ...
     * @param value ...
     */
    public void setJavaObject(String propname, Object value) {
        checkWriteLock();

        if (propMap == null) {
            propMap = new Hashtable();
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }   
        
        propname = propname.trim();

        String p2 = propname;

        Property prop = (Property) propMap.get(p2);

        if (prop != null) {
            prop.setJavaObjectValue(value);
        } else {
            prop = new Property(propname, this);
            prop.setJavaObjectValue(value);
            propMap.put(p2, prop);
        }

        notifyPropertyChange(propname);

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    }

    /**
     *
     *
     * @param propname ...
     * @param value ...
     */
    public void setNode(String propname, INode value) {
        // check if types match, otherwise throw exception
        DbMapping nmap = (dbmap == null) ? null : dbmap.getExactPropertyMapping(propname);

        if ((nmap != null) && (nmap != value.getDbMapping())) {
            if (value.getDbMapping() == null) {
                value.setDbMapping(nmap);
            } else if (!nmap.isStorageCompatible(value.getDbMapping())) {
                throw new RuntimeException("Can't set " + propname +
                                           " to object with prototype " +
                                           value.getPrototype() + ", was expecting " +
                                           nmap.getTypeName());
            }
        }

        if (state != TRANSIENT) {
            checkWriteLock();
        }

        Node n = null;

        if (value instanceof Node) {
            n = (Node) value;
        } else {
            throw new RuntimeException("Can't add fixed-transient node to a persistent node");
        }

        // if the new node is marked as TRANSIENT and this node is not, mark new node as NEW
        if ((state != TRANSIENT) && (n.state == TRANSIENT)) {
            n.makePersistable();
        }

        if (state != TRANSIENT) {
            n.checkWriteLock();
        }

        // check if the main identity of this node is as a named property
        // or as an anonymous node in a collection
        if (n != this && !nmgr.isRootNode(n)) {
            // avoid calling getParent() because it would return bogus results
            // for the not-anymore transient node
            Node nparent = (n.parentHandle == null) ? null
                                                    : n.parentHandle.getNode(nmgr);

            // if the node doesn't have a parent yet, or it has one but it's
            // transient while we are persistent, make this the nodes new parent.
            if ((nparent == null) ||
               ((state != TRANSIENT) && (nparent.getState() == TRANSIENT))) {
                n.setParent(this);
                n.name = propname;
                n.anonymous = false;
            }
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }   
        
        propname = propname.trim();

        String p2 = propname;

        Relation rel = (dbmap == null) ? null : dbmap.getPropertyRelation(propname);

        if (rel != null && (rel.countConstraints() > 1 || rel.isComplexReference())) {
            rel.setConstraints(this, n);
            if (rel.isComplexReference()) {
                Key key = new MultiKey(n.getDbMapping(), rel.getKeyParts(this));
                nmgr.nmgr.registerNode(n, key);
                return;
            }
        }

        Property prop = (propMap == null) ? null : (Property) propMap.get(p2);

        if (prop != null) {
            if ((prop.getType() == IProperty.NODE) &&
                    n.getHandle().equals(prop.getNodeHandle())) {
                // nothing to do, just clean up locks and return
                if (state == CLEAN) {
                    clearWriteLock();
                }

                if (n.state == CLEAN) {
                    n.clearWriteLock();
                }

                return;
            }
        } else {
            prop = new Property(propname, this);
        }

        prop.setNodeValue(n);

        if ((rel == null) ||
                rel.reftype == Relation.REFERENCE ||
                state == TRANSIENT ||
                rel.otherType == null ||
                !rel.otherType.isRelational() ||
                // Add the below additional condition so that
                // lucene objects can have as properties a collection of relational db nodes
                (!rel.ownType.isRelational() && rel.otherType.isRelational())) {
            // the node must be stored as explicit property
            if (propMap == null) {
                propMap = new Hashtable();
            }

            propMap.put(p2, prop);

            if (state == CLEAN) {
                markAs(MODIFIED);
            }
        }

        // don't check node in transactor cache if node is transient -
        // this is done anyway when the node becomes persistent.
        if (n.state != TRANSIENT) {
            // check node in with transactor cache
            Transactor tx = (Transactor) Thread.currentThread();

            // tx.visitCleanNode (new DbKey (dbm, nID), n);
            // UPDATE: using n.getKey() instead of manually constructing key. HW 2002/09/13
            tx.visitCleanNode(n.getKey(), n);

            // if the field is not the primary key of the property, also register it
            if ((rel != null) && (rel.accessName != null) && (state != TRANSIENT)) {
                Key secKey = new SyntheticKey(getKey(), propname);
                nmgr.registerNode(n, secKey);
                tx.visitCleanNode(secKey, n);
            }
        }

        lastmodified = System.currentTimeMillis();

        if (n.state == DELETED) {
            n.markAs(MODIFIED);
        }
    }

    /**
     * Remove a property. Note that this works only for explicitly set properties, not for those
     * specified via property relation.
     */
    public void unset(String propname) {
        if (propMap == null) {
            return;
        }

        try {
            // if node is relational, leave a null property so that it is
            // updated in the DB. Otherwise, remove the property.
            Property p;
            boolean relational = (dbmap != null) && dbmap.isRelational();

            if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
                propname = propname.toLowerCase();
            }   
            
            if (relational) {
                p = (Property) propMap.get(propname);
            } else {
                p = (Property) propMap.remove(propname);
            }

            if (p != null) {
                checkWriteLock();

                if (relational) {
                    p.setStringValue(null);
                    notifyPropertyChange(propname);
                }

                lastmodified = System.currentTimeMillis();

                if (state == CLEAN) {
                    markAs(MODIFIED);
                }
                
                if (p.getType() == IProperty.NODE) {
                    Node node = (Node) p.getNodeValue();
                    node.checkWriteLock();
                    if (!node.isRelational()) {
                        node.markAs(MODIFIED);  
                    }
                    node.setParentHandle(null);
                }
            } else if (dbmap != null) {
                // check if this is a complex constraint and we have to
                // unset constraints.
                Relation rel = dbmap.getExactPropertyRelation(propname);

                if (rel != null && (rel.isComplexReference())) {
                    p = getProperty(propname);
                    // If deleteOnRemove set to true, actually 
                    // delete the node from the db instead of just nulling out the foreign key
                    if (rel.deleteOnRemove) {
                        INode pval = p.getNodeValue();
                        if (pval != null) {
                            pval.remove();
                        }
                    } else {
                        rel.unsetConstraints(this, p.getNodeValue());
                    }
                }
            }
        } catch (Exception ignore) {
        }
    }
    
    public void unsetInternalProperty(String propname) {
        if (propMap == null) {
            return;
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }
        propMap.remove(propname);
    }

    /**
     *
     *
     * @return ...
     */
    public long lastModified() {
        return lastmodified;
    }

    /**
     *
     *
     * @return ...
     */
    public long created() {
        return created;
    }

    /**
    *
    *
    * @return ...
    */
    public void setCreated(long created) {
    	this.created = created;
    }

    /**
     *
     *
     * @return ...
     */
    public String toString() {
        return "AxiomObject " + name;
    }

    /**
     * Tell whether this node is stored inside a relational db. This doesn't mean
     * it actually is stored in a relational db, just that it would be, if the node was
     * persistent
     */
    public boolean isRelational() {
        return (dbmap != null) && dbmap.isRelational();
    }

    /**
     * Public method to make a node persistent.
     */
    public void persist() {
        makePersistable();
    }

    /**
     * Turn node status from TRANSIENT to NEW so that the Transactor will
     * know it has to insert this node. Recursively persistifies all child nodes
     * and references.
     */
    private void makePersistable() {
        // if this isn't a transient node, do nothing.
        if (state != TRANSIENT) {
            return;
        }

        // mark as new
        setState(NEW);

        // generate a real, persistent ID for this object
        id = nmgr.generateID(dbmap);
        
        getHandle().becomePersistent();

        // register node with the transactor
        Transactor current = (Transactor) Thread.currentThread();
        current.visitNode(this);
        current.visitCleanNode(this);

        // recursively make children persistable
        makeChildrenPersistable();
    }

    /**
     * Recursively turn node status from TRANSIENT to NEW on child nodes
     * so that the Transactor knows they are to be persistified.
     */
    private void makeChildrenPersistable() {
        for (Enumeration e = getSubnodes(); e.hasMoreElements();) {
            Node n = (Node) e.nextElement();

            if (n.state == TRANSIENT) {
                n.makePersistable();
            }
        }

        for (Enumeration e = properties(); e.hasMoreElements();) {
            String propname = (String) e.nextElement();
            IProperty next = get(propname);

            if ((next != null) && (next.getType() == IProperty.NODE)) {

                // check if this property actually needs to be persisted.
                Node n = (Node) next.getNodeValue();

                if (n == null) {
                    continue;
                }

                if (dbmap != null) {
                    Relation rel = dbmap.getExactPropertyRelation(next.getName());
                    if (rel != null && rel.isVirtual() && !rel.needsPersistence()) {
                        // temporarilly set state to TRANSIENT to avoid loading anything from db
                        n.setState(TRANSIENT);
                        n.makeChildrenPersistable();
                        // make this a virtual node. what we do is basically to
                        // replay the things done in the constructor for virtual nodes.
                        // NOTE: setting the primaryKey may not be necessary since this
                        // isn't managed by the nodemanager but rather an actual property of
                        // its parent node.
                        n.setState(VIRTUAL);
                        n.primaryKey = new SyntheticKey(getKey(), propname);
                        n.id = propname;
                        continue;
                    }
                }

                n.makePersistable();
            }
        }
    }

    /**
     * This method walks down node path to the first non-virtual node and return it.
     *  limit max depth to 5, since there shouldn't be more then 2 layers of virtual nodes.
     */
    public Node getNonVirtualParent() {
        Node node = this;

        for (int i = 0; i < 5; i++) {
            if (node == null) {
                break;
            }

            if (node.getState() == Node.TRANSIENT) {
                DbMapping map = node.getDbMapping();
                if (map == null || map.getTypeName() != null)
                    return node;
            } else if (node.getState() != Node.VIRTUAL) {
                return node;
            }

            node = (Node) node.getParent();
        }

        return null;
    }

    /**
     *  Instances of this class may be used to mark an entry in the object cache as null.
     *  This method tells the caller whether this is the case.
     */
    public boolean isNullNode() {
        return nmgr == null;
    }

    /**
     * We overwrite hashCode to make it dependant from the prototype. That way, when the prototype
     * changes, the node will automatically get a new ESNode wrapper, since they're cached in a hashtable.
     * You gotta love these hash code tricks ;-)
     */
    public int hashCode() {
        if (prototype == null) {
            return super.hashCode();
        } else {
            return super.hashCode() + prototype.hashCode();
        }
    }

    /**
     *
     */
    public void dump() {
        System.err.println("subnodes: " + subnodes);
        System.err.println("properties: " + propMap);
    }

    /**
     * This method get's called from the JavaScript environment
     * (AxiomObject.updateSubnodes() or AxiomObject.collection.updateSubnodes()))
     * The subnode-collection will be updated with a selectstatement getting all
     * Nodes having a higher id than the highest id currently contained within
     * this Node's subnoderelation. If this subnodelist has a special order
     * all nodes will be loaded honoring this order.
     * Example:
     *  order by somefield1 asc, somefieled2 desc
     * gives a where-clausel like the following:
     *   (somefiled1 > theHighestKnownValue value and somefield2 < theLowestKnownValue)
     * @return the number of loaded nodes within this collection update
     */
    public int updateSubnodes () {
        // FIXME: what do we do if this.dbmap is null
        if (this.dbmap == null) {
            throw new RuntimeException (this + " doesn't have a DbMapping");
}
        Relation rel = this.dbmap.getSubnodeRelation();
        synchronized (this) {
            lastSubnodeFetch = System.currentTimeMillis();
            return this.nmgr.updateSubnodeList(this, rel);
        }
    }
    
    public Node cloneNode(Node copy, final boolean deep) {
        if (copy == null) {
            copy = new Node(this.getPrototype(), this.getPrototype(), this.nmgr);
        }
        
        Enumeration props = this.getPropMap().keys();
        
        Hashtable propMap = new Hashtable();
        copy.setPropMap(propMap);
        
        while (props.hasMoreElements()) {
            String propname = props.nextElement().toString();
            IProperty prop = this.get(propname);
            axiom.objectmodel.db.Property cp = new axiom.objectmodel.db.Property(propname, copy);
            
            switch (prop.getType()) {
            case IProperty.BOOLEAN: 
                cp.setBooleanValue(prop.getBooleanValue()); 
                break;
            case IProperty.STRING: 
                cp.setStringValue(prop.getStringValue());
                break;
            case IProperty.DATE:
                cp.setDateValue((Date) prop.getDateValue().clone());
                break;
            case IProperty.FLOAT:
                cp.setFloatValue(prop.getFloatValue());
                break;
            case IProperty.INTEGER:
                cp.setIntegerValue(prop.getIntegerValue());
                break;
            case IProperty.JAVAOBJECT:
                try {
                    cp.setJavaObjectValue(prop.getJavaObjectValue().getClass().newInstance());
                } catch (Exception ex) {
                }
                break;
            case IProperty.NODE:
                cp.setNodeValue(((Node) prop.getNodeValue()).cloneNode(null, deep));
                break;
            case IProperty.MULTI_VALUE:
                if (prop instanceof Property) {
                    cp.setMultiValue((MultiValue) ((Property) prop).getMultiValue().clone());
                }
                break;
            case IProperty.REFERENCE:
                if (prop instanceof Property) {
                    cp.setReferenceValue((Reference) ((Property) prop).getReferenceValue().clone());
                }
                break;
            case IProperty.XML:
            case IProperty.XHTML:
            	cp.setXMLValue(prop.getValue().toString());
            	break;
            default: 
                break;
            }
            
            propMap.put(propname, cp);
        }
        
        if (deep) {
        	Enumeration e = this.getSubnodes();
        	while (e.hasMoreElements()) {
        		Node child = (Node) e.nextElement();
        		copy.addNode(child.cloneNode(null, deep));
        	}
        }
        
        return copy;
    }
    
    /*
     * Allows for setting of a property on a node that is of a Reference type
     */
    
    public void setReference(String propname, Reference value) {
        checkWriteLock();

        if (propMap == null) {
            propMap = new Hashtable();
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }   
        
        propname = propname.trim();

        String p2 = propname;
        
        Property prop = (Property) propMap.get(p2);

        if (prop != null) {
            prop.setReferenceValue(value);
        } else {
            prop = new Property(propname, this);
            prop.setReferenceValue(value);
            propMap.put(p2, prop);
        }

        notifyPropertyChange(propname);

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    }
    
    /*
     * Allows for setting of a property on a node that is of a MultiValue type
     */
    
    public void setMultiValue(String propname, MultiValue value) {
        checkWriteLock();

        if (propMap == null) {
            propMap = new Hashtable();
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }   
        
        propname = propname.trim();

        String p2 = propname;
        
        Property prop = (Property) propMap.get(p2);

        if (prop != null) {
            prop.setMultiValue(value);
        } else {
            prop = new Property(propname, this);
            for (int i = 0; i < value.jsGet_length(); i++) {
                if (value.getValueType() == IProperty.REFERENCE) {
                    Reference r = (Reference) value.get(i);
                    axiom.scripting.rhino.AxiomObject h = (axiom.scripting.rhino.AxiomObject) r.jsFunction_getTarget();
                }
            }
            prop.setMultiValue(value);
            propMap.put(p2, prop);
        }

        notifyPropertyChange(propname);

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    }
    
    /*
     * Allows for setting of a property on a node that is of a DocumentFragment type
     */
    
    public void setXML(String propname, Object xml) {
        checkWriteLock();

        if (propMap == null) {
            propMap = new Hashtable();
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }   
        
        propname = propname.trim();

        String p2 = propname;
        
        Property prop = (Property) propMap.get(p2);
        
        if (prop != null) {
            prop.setXMLValue(xml);
        } else {
            prop = new Property(propname, this);
            prop.setXMLValue(xml);
            propMap.put(p2, prop);
        }
        notifyPropertyChange(propname);

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    } 
    
    /*
     * Allows for setting of a property on a node that is of a XHTML type
     */
    
    public void setXHTML(String propname, Object xml) {
        checkWriteLock();

        if (propMap == null) {
            propMap = new Hashtable();
        }

        if (this.nmgr.nmgr.app.isPropertyFilesIgnoreCase()) {
            propname = propname.toLowerCase();
        }   
        
        propname = propname.trim();

        String p2 = propname;
        
        Property prop = (Property) propMap.get(p2);
        
        if (prop != null) {
            prop.setXHTMLValue(xml);
        } else {
            prop = new Property(propname, this);
            prop.setXHTMLValue(xml);
            propMap.put(p2, prop);
        }
        notifyPropertyChange(propname);

        lastmodified = System.currentTimeMillis();

        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    } 

    public String getElementNameField() {
        // get element name - this is either the Node's id or name.
    	// Removed lastmod check, we always want the name if we call this!
    	
        if (parentHandle != null) {
            try {
                Node p = parentHandle.getNode(nmgr);
                DbMapping parentmap = p.getDbMapping();
                Relation prel = parentmap.getSubnodeRelation();

                if (prel != null) {
                    if (prel.accessName != null) {
                        return dbmap.columnNameToProperty(prel.accessName);
                    }
                }
            } catch (Exception ignore) {
                // FIXME: add proper NullPointer checks in try statement
                // just fall back to default method
            }
        }

        return null;
    }
        
    public boolean hasPathChanged() {
        return this.hasPathChanged;
    }
    
    public void resetPathChanged() {
        this.hasPathChanged = false;
    }
    
    public void pathChanged() {
        this.hasPathChanged = true;
    }
        
    public void setLastModified(long time) {
    	this.lastmodified = time;
    }
    
    public synchronized boolean relationalNodeAdded() {
        return this.relationalNodeAdded;
    }
    
    public synchronized void resetRelationalNodeAdded() {
        this.relationalNodeAdded = false;
    }
    
    protected boolean isNamedRelationalProperty() {
        if (!this.isAnonymous()) {
            INode parent = this.getParent();
            if (parent != null) {
                DbMapping dbmap = parent.getDbMapping();
                if (dbmap != null) {
                    return dbmap.isRelational();
                }
            }
        }
        
        return false;
    }

    public void changePrototype(String prototype) {
        checkWriteLock();

        this.prototype = prototype;
        
        if (state == CLEAN) {
            markAs(MODIFIED);
        }
    }
    
    public ArrayList<Node> getSubChildren() {
    	ArrayList<Node> children = new ArrayList<Node>();
    	this.getSubChildren(children);
    	return children;
    }
    
    private void getSubChildren(ArrayList<Node> children) {
        // tell all nodes that are properties of n that they are no longer used as such
        for (Enumeration en = this.properties(); en.hasMoreElements();) {
            Property p = (Property) this.get(en.nextElement().toString());
            
            if ((p != null) && (p.getType() == Property.NODE)) {
                Node n = (Node) p.getNodeValue();
                if (n != null && !n.isRelational() && n.getParent() == this) {
                    children.add(n);
                    n.getSubChildren(children);
                } 
            }
        }

        // cascading delete of all subnodes. This is never done for relational subnodes, because
        // the parent info is not 100% accurate for them.
        ArrayList v = new ArrayList();
        // remove modifies the Vector we are enumerating, so we are extra careful.
        for (Enumeration en = this.getSubnodes(); en.hasMoreElements();) {
            v.add(en.nextElement());
        }
        
        final int m = v.size();
        for (int i = 0; i < m; i++) {
            // getParent() is heuristical/implicit for relational nodes, so we don't base
            // a cascading delete on that criterium for relational nodes.
            Node n = (Node) v.get(i);
            if (!n.isRelational() && n.getParent() == this) {
                children.add(n);
                n.getSubChildren(children);
            }
        }

    }
    
    public void cloneProperties(Node node) throws Exception {
    	try {
    		Enumeration e = node.properties();

    		while (e.hasMoreElements()) {
    			String str = e.nextElement().toString();
    			IProperty property = node.get(str);    

    			switch (property.getType()) {
    			case IProperty.STRING:
    				this.setString(str, property.getStringValue());                			
    				break;
    			case IProperty.BOOLEAN:
    				this.setBoolean(str, property.getBooleanValue());
    				break;
    			case IProperty.DATE:
    			case IProperty.TIME:
    			case IProperty.TIMESTAMP:
    				this.setDate(str, new Date(property.getDateValue().getTime()));
    				break;
    			case IProperty.INTEGER:
    			case IProperty.SMALLINT:
    				this.setInteger(str, property.getIntegerValue());
    				break;
    			case IProperty.FLOAT:
    			case IProperty.SMALLFLOAT:
    				this.setFloat(str, property.getFloatValue());
    				break;
    			case IProperty.NODE:
    				Node pnode = ((Node) property.getNodeValue());
    				Key nkey = pnode.getKey();
    				NodeHandle newHandle = new NodeHandle(new DbKey(pnode.dbmap, nkey.getID(), this.layer));
    				this.setNodeHandle(str, newHandle);
    				break;
    			case IProperty.JAVAOBJECT:
    				break;
    			case IProperty.REFERENCE:
    				Reference oref = property.getReferenceValue();
    				Key targetKey = oref.getTargetKey();
    				Key sourceKey = oref.getSourceKey();
    				RequestEvaluator reqeval = this.nmgr.nmgr.app.getCurrentRequestEvaluator();
    				RhinoCore core = null;
    				if (reqeval != null) {
    					core = ((RhinoEngine) reqeval.getScriptingEngine()).getCore();
    				}
    				Reference ref = new Reference(core, new Object[] { new DbKey(this.nmgr.getDbMapping(targetKey.getStorageName()), targetKey.getID(), this.layer) } );
    				ref.setSourceKey(new DbKey(this.nmgr.getDbMapping(sourceKey.getStorageName()), sourceKey.getID(), this.layer));
    				this.setReference(str, ref);
    				break;
    			case IProperty.MULTI_VALUE:
    				int mvType = property.getMultiValue().getValueType();
    				MultiValue mv = new MultiValue(mvType);
    				switch (mvType) {
    				case IProperty.REFERENCE:
    					reqeval = this.nmgr.nmgr.app.getCurrentRequestEvaluator();
        				core = null;
        				if (reqeval != null) {
        					core = ((RhinoEngine) reqeval.getScriptingEngine()).getCore();
        				}
    					Object pModeReferences[] = property.getMultiValue().getValues();
    					for (int i = 0; i < pModeReferences.length; i++) {
    						Reference reference = (Reference) pModeReferences[i];
    						targetKey = reference.getTargetKey();
    						sourceKey = reference.getSourceKey();
    						ref = new Reference(core, new Object[] { new DbKey(this.nmgr.getDbMapping(targetKey.getStorageName()), targetKey.getID(), this.layer) } );
    						ref.setSourceKey(new DbKey(this.nmgr.getDbMapping(sourceKey.getStorageName()), sourceKey.getID(), this.layer));
    						mv.addValue(ref);
    					}
    					this.setMultiValue(str, mv);
    					break;
    				default:
    					MultiValue omv = property.getMultiValue();
    					Object[] ovalues = omv.getValues();
    					for (int i = 0; i < ovalues.length; i++) {
    						if (mvType == IProperty.DATE) {
    							if (ovalues[i] instanceof Date) {
    								ovalues[i] = ((Date) ovalues[i]).clone();
    							} else if (ovalues[i] instanceof Scriptable && "Date".equals(((Scriptable) ovalues[i]).getClassName())) {
    								ovalues[i] = new Date((long) ScriptRuntime.toNumber(ovalues[i]));
    							}
    						}
    						mv.addValue(ovalues[i]);
    					}
    					this.setMultiValue(str, mv);
    					break;
    				}
    				break;
    			case IProperty.XML:
    			case IProperty.XHTML:
    				RhinoEngine re = (RhinoEngine) this.nmgr.nmgr.app.getCurrentRequestEvaluator().getScriptingEngine();
    				Object clonedXml = TALExtension.cloneXmlObject((Scriptable) property.getXMLValue(), re.getGlobal(), this.nmgr.nmgr.app);
    				this.setXML(str, clonedXml);
    				break;
    			}
    		}
    	} catch (Exception ex) {
    		throw new Exception("Error calling cloneProperties() on " + this, ex);
    	} 
    }
    
    protected void updateLayersOnReferences(int layer) {
    	Enumeration e = this.properties();
    	while (e.hasMoreElements()) {
    		String propname = (String) e.nextElement();
    		IProperty prop = this.get(propname);
    		if (prop.getType() == IProperty.REFERENCE) {
    			Reference r = prop.getReferenceValue();
    			synchronized (r) {
    				r.changeCurrentLayer(layer);
    			}
    		} else if (prop.getType() == IProperty.MULTI_VALUE) {
    			MultiValue mv = prop.getMultiValue();
				if (mv.getValueType() == IProperty.REFERENCE) {
					synchronized (mv) {
						Object[] values = mv.getValues();
						for (int i = 0; i < values.length; i++) {
							Reference r = (Reference) values[i];
							r.changeCurrentLayer(layer);
						}
    				}
    			}
    		}
    	}
    }
    
}