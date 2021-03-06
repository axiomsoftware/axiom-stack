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
 * $RCSfile: Relation.java,v $
 * $Author: hannes $
 * $Revision: 1.59 $
 * $Date: 2006/02/09 16:35:32 $
 */

package axiom.objectmodel.db;


import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Enumeration;
import java.util.Vector;

import axiom.framework.core.Application;
import axiom.objectmodel.INode;
import axiom.objectmodel.IProperty;
import axiom.util.ResourceProperties;
import axiom.util.StringUtils;

/**
 * This describes how a property of a persistent Object is stored in a
 *  relational database table. This can be either a scalar property (string, date, number etc.)
 *  or a reference to one or more other objects.
 */
public final class Relation {
    // these constants define different type of property-to-db-mappings
    // there is an error in the description of this relation
    public final static int INVALID = -1;

    // a mapping of a non-object, scalar type
    public final static int PRIMITIVE = 0;

    // a 1-to-1 relation, i.e. a field in the table is a foreign key to another object
    public final static int REFERENCE = 1;

    // a 1-to-many relation, a field in another table points to objects of this type
    public final static int COLLECTION = 2;

    // a 1-to-1 reference with multiple or otherwise not-trivial constraints
    // this is managed differently than REFERENCE, hence the separate type.
    public final static int COMPLEX_REFERENCE = 3;

    // constraints linked together by OR or AND if applicable?
    public final static String AND = " AND ";
    public final static String OR = " OR ";
    public final static String XOR = " XOR ";
    private String logicalOperator = AND;

    // prefix to use for symbolic names of joined tables. The name is composed
    // from this prefix and the name of the property we're doing the join for
    final static String JOIN_PREFIX = "JOIN_";

    // direct mapping is a very powerful feature:
    // objects of some types can be directly accessed
    // by one of their properties/db fields.
    // public final static int DIRECT = 3;
    // the DbMapping of the type we come from
    DbMapping ownType;

    // the DbMapping of the prototype we link to, unless this is a "primitive" (non-object) relation
    DbMapping otherType;

    // the column type, as defined in java.sql.Types
    int columnType;

    //  if this relation defines a virtual node, we need to provide a DbMapping for these virtual nodes
    DbMapping virtualMapping;
    String propName;
    String columnName;
    int reftype;
    Constraint[] constraints;
    boolean virtual;
    boolean readonly;
    boolean aggressiveLoading;
    boolean aggressiveCaching;
    boolean isPrivate = false;
    boolean referencesPrimaryKey = false;
    String updateCriteria;
    String accessName; // db column used to access objects through this relation
    String order;
    boolean autoSorted = false;
    String groupbyOrder;
    String groupby;
    String prototype;
    String groupbyPrototype;
    String filter;
    private String additionalTables;
    private boolean additionalTablesJoined = false;
    String queryHints;
    Vector filterFragments;
    Vector filterPropertyRefs;
    int maxSize = 0;
    
    // Is a relational node deleted from the db on a remove() call?
    boolean deleteOnRemove = false;
    

    /**
     * This constructor makes a copy of an existing relation. Not all fields are copied, just those
     * which are needed in groupby- and virtual nodes defined by this relation.
     */
    private Relation(Relation rel) {
        // Note: prototype, groupby, groupbyPrototype and groupbyOrder aren't copied here.
        // these are set by the individual get*Relation() methods as appropriate.
        this.ownType =                  rel.ownType;
        this.otherType =                rel.otherType;
        this.propName =                 rel.propName;
        this.columnName =               rel.columnName;
        this.reftype =                  rel.reftype;
        this.order =                    rel.order;
        this.filter =                   rel.filter;
        this.filterFragments =          rel.filterFragments;
        this.filterPropertyRefs =       rel.filterPropertyRefs;
        this.additionalTables =         rel.additionalTables;
        this.additionalTablesJoined =   rel.additionalTablesJoined;
        this.queryHints =               rel.queryHints;
        this.maxSize =                  rel.maxSize;
        this.constraints =              rel.constraints;
        this.accessName =               rel.accessName;
        this.maxSize =                  rel.maxSize;
        this.logicalOperator =          rel.logicalOperator;
        this.aggressiveLoading =        rel.aggressiveLoading;
        this.aggressiveCaching =        rel.aggressiveCaching;
        this.updateCriteria =           rel.updateCriteria;
        this.autoSorted =               rel.autoSorted;
        this.deleteOnRemove =           rel.deleteOnRemove; 
    }

    /**
     * Reads a relation entry from a line in a properties file.
     */
    public Relation(String propName, DbMapping ownType) {
        this.ownType = ownType;
        this.propName = propName;
        otherType = null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    // parse methods for new file format
    ////////////////////////////////////////////////////////////////////////////////////////////
    public void update(ResourceProperties props) {
        Application app = ownType.getApplication();
        ResourceProperties config = props.getSubProperties(propName + '.');
        String type = config.getProperty("type");
        
        if (type != null) {
            type = type.trim().toLowerCase();
        }
        
        if ((type == null) || !(type.startsWith("collection") || type.startsWith("object"))) {
        	String desc =props.getProperty(propName);
        	if (desc != null && !"".equals(desc.trim())) {
        	    virtual = false;
        	    columnName = desc;
        	    reftype = PRIMITIVE;
        	} else if (propName != null) {
                reftype = PRIMITIVE;
                columnName = propName;
            } else {
                reftype = INVALID;
                columnName = propName;
            }
        } else {
            type = type.trim();

            int open = type.indexOf("(");
            int close = type.indexOf(")");

            if ((open > -1) && (close > open)) {
                String ref = type.substring(0, open).trim();
                String proto = type.substring(open + 1, close).trim();
                if ("collection".equalsIgnoreCase(ref)) {
                    virtual = !"_children".equalsIgnoreCase(propName);
                    reftype = COLLECTION;
                } else if ("mountpoint".equalsIgnoreCase(ref)) {
                    virtual = true;
                    reftype = COLLECTION;
                    prototype = proto;
                } else if ("object".equalsIgnoreCase(ref)) {
                    virtual = false;
                    if (reftype != COMPLEX_REFERENCE) {
                        reftype = REFERENCE;
                    }
                } else {
                    if (!"reference".equalsIgnoreCase(ref)) 
                    	throw new RuntimeException("Invalid property Mapping: " + type);
                }

                otherType = app.getDbMapping(proto);

                if (otherType == null) {
                    throw new RuntimeException("DbMapping for " + proto +
                                               " not found from " + ownType.getTypeName());
                }

                // make sure the type we're referring to is up to date!
                if (otherType.needsUpdate()) {
                    otherType.update();
                }

            } 
        }

        readonly = "true".equalsIgnoreCase(config.getProperty("readonly"));

        isPrivate = "true".equalsIgnoreCase(config.getProperty("private"));

        // the following options only apply to object and collection relations
        if ((reftype != PRIMITIVE) && (reftype != INVALID)) {
            Vector newConstraints = new Vector();

            parseOptions(newConstraints, config);

            constraints = new Constraint[newConstraints.size()];
            newConstraints.copyInto(constraints);


            if (reftype == REFERENCE || reftype == COMPLEX_REFERENCE) {
                if (constraints.length == 0) {
                    referencesPrimaryKey = true;
                } else {
                    boolean rprim = false;
                    for (int i=0; i<constraints.length; i++) {
                        // Added a check that the constraint includes 
                        // the primary key of the local table for the local key constraint
                        // because otherwise, objects that are properties of other objects
                        // are not retrieved if the local key refers to something other than
                        // the parent node's primary key
                        if (constraints[0].foreignKeyIsPrimary() 
                                && constraints[0].localKeyIsPrimary(this.ownType)) {
                            rprim = true;
                        }
                    }
                    referencesPrimaryKey = rprim;
                }
                
                // check if this is a non-trivial reference
                if (constraints.length > 0 /*&& !usesPrimaryKey()*/) { 
                    reftype = COMPLEX_REFERENCE;
                } else {
                    reftype = REFERENCE;
                }
            }

            if (reftype == COLLECTION) {
                referencesPrimaryKey = (accessName == null) ||
                        accessName.equalsIgnoreCase(otherType.getIDField());
            }

            // if DbMapping for virtual nodes has already been created,
            // update its subnode relation.
            // FIXME: needs to be synchronized?
            if (virtualMapping != null) {
                virtualMapping.lastTypeChange = ownType.lastTypeChange;
                virtualMapping.subRelation = getVirtualSubnodeRelation();
                virtualMapping.propRelation = getVirtualPropertyRelation();
            }
        } else {
            referencesPrimaryKey = false;
        }
    }

    protected void parseOptions(Vector cnst, Properties config) {
    	
        Application app = ownType.getApplication();
    	
        String loading = config.getProperty("loadmode");

        aggressiveLoading = (loading != null) &&
                            "aggressive".equalsIgnoreCase(loading.trim());

        String caching = config.getProperty("cachemode");

        aggressiveCaching = (caching != null) &&
                            "aggressive".equalsIgnoreCase(caching.trim());

        // get order property
        order = config.getProperty("order");

        if ((order != null) && (order.trim().length() == 0)) {
            order = null;
        }

        // get the criteria(s) for updating this collection
        updateCriteria = config.getProperty("updatecriteria");

        // get the autosorting flag
        autoSorted = "auto".equalsIgnoreCase(config.getProperty("sortmode"));

        // get additional filter property
        filter = config.getProperty("filter");

        if (filter != null) {
            if (filter.trim().length() == 0) {
                filter = null;
                filterFragments = filterPropertyRefs = null;
            } else {
                // parenthesise filter
                Vector fragments = new Vector();
                Vector propertyRefs = new Vector();
                parsePropertyString(filter, fragments, propertyRefs);
                // if no references where found, just use the filter string
                // otherwise use the filter fragments and proeprty refs instead
                if (propertyRefs.size() > 0) {
                    filterFragments = fragments;
                    filterPropertyRefs = propertyRefs;
                } else {
                    filterFragments = filterPropertyRefs = null;
                }
            }
        }

        // get additional tables
        additionalTables = config.getProperty("filter.additionalTables");

        if (additionalTables != null) {
            if (additionalTables.trim().length() == 0) {
                additionalTables = null;
            } else {
                String ucTables = additionalTables.toUpperCase();
                // create dependencies implied by additional tables
                DbSource dbsource = otherType.getDbSource();
                if (dbsource != null) {
                    String[] tables = StringUtils.split(ucTables, ", ");
                    for (int i=0; i<tables.length; i++) {
                        // Skip some join-related keyworks we might encounter here
                        if ("AS".equals(tables[i]) || "ON".equals(tables[i])) {
                            continue;
                        }
                        DbMapping dbmap = dbsource.getDbMapping(tables[i]);
                        if (dbmap != null) {
                            dbmap.addDependency(otherType);
                        }
                    }
                }
                // see wether the JOIN syntax is used. look for " join " with whitespaces on both sides
                // and for "join " at the beginning:
                additionalTablesJoined = (ucTables.indexOf(" JOIN ") != -1 ||
                        ucTables.startsWith("STRAIGHT_JOIN ") || ucTables.startsWith("JOIN "));
            }
        }

        // get query hints
        queryHints = config.getProperty("hints");

        // get max size of collection
        String max = config.getProperty("maxSize");

        if (max != null) {
            try {
                maxSize = Integer.parseInt(max);
            } catch (NumberFormatException nfe) {
                maxSize = 0;
            }
        } else {
            maxSize = 0;
        }

        // get group by property
        groupby = config.getProperty("group");

        if ((groupby != null) && (groupby.trim().length() == 0)) {
            groupby = null;
        }

        if (groupby != null) {
            groupbyOrder = config.getProperty("group.order");

            if ((groupbyOrder != null) && (groupbyOrder.trim().length() == 0)) {
                groupbyOrder = null;
            }

            groupbyPrototype = config.getProperty("group.prototype");

            if ((groupbyPrototype != null) && (groupbyPrototype.trim().length() == 0)) {
                groupbyPrototype = null;
            }

            // aggressive loading and caching is not supported for groupby-nodes
            // aggressiveLoading = aggressiveCaching = false;
        }

        // check if subnode condition should be applied for property relations
        accessName = config.getProperty("accessname");
        if (accessName != null && "_id".equals(accessName)) {
            accessName = null;
        }

        // parse contstraints
        String local = config.getProperty("local");
        String foreign = config.getProperty("foreign");

        if ((local != null) && (foreign != null)) {
            cnst.addElement(new Constraint(local, foreign, false));
            columnName = local;
        }

        // parse additional contstraints from *.1 to *.9
        for (int i=1; i<10; i++) {
            local = config.getProperty("local."+i);
            foreign = config.getProperty("foreign."+i);

            if ((local != null) && (foreign != null)) {
                cnst.addElement(new Constraint(local, foreign, false));
            }
        }

        // parse constraints logic
        if (cnst.size() > 1) {
            String logic = config.getProperty("logicalOperator");
            if ("and".equalsIgnoreCase(logic)) {
                logicalOperator = AND;
            } else if ("or".equalsIgnoreCase(logic)) {
                logicalOperator = OR;
            } else if ("xor".equalsIgnoreCase(logic)) {
                logicalOperator = XOR;
            } else {
                logicalOperator = AND;
            }
        } else {
            logicalOperator = AND;
        }
        
        // Get the option to determine if a relational node
        // should be deleted from the database on a remove() call.
        this.deleteOnRemove = Boolean.parseBoolean(config.getProperty("deleteOnRemove"));

    }

    /**
     * Get the configuration properties for this relation.
     */
    public ResourceProperties getConfig() {
        return ownType.getProperties().getSubProperties(propName + '.');
    }

    /**
     * Does this relation describe a virtual (collection) node?
     */
    public boolean isVirtual() {
        return virtual;
    }

    /**
     * Return the target type of this relation, or null if this is a primitive mapping.
     */
    public DbMapping getTargetType() {
        return otherType;
    }

    /**
     * Get the reference type of this relation.
     */
    public int getRefType() {
        return reftype;
    }

    /**
     * Tell if this relation represents a primitive (scalar) value mapping.
     */
    public boolean isPrimitive() {
        return reftype == PRIMITIVE;
    }

    /**
     *  Returns true if this Relation describes an object reference property
     */
    public boolean isReference() {
        return reftype == REFERENCE;
    }

    /**
     *  Returns true if this Relation describes a collection.
     *  <b>NOTE:</b> this will return true both for collection objects
     *  (aka virtual nodes) and direct child object relations, so
     *  isVirtual() should be used to identify relations that define
     *  <i>collection properties</i>!
     */
    public boolean isCollection() {
        return reftype == COLLECTION;
    }

    /**
     *  Returns true if this Relation describes a complex object reference property
     */
    public boolean isComplexReference() {
        return reftype == COMPLEX_REFERENCE;
    }

    /**
     *  Tell wether the property described by this relation is to be handled as private, i.e.
     *  a change on it should not result in any changed object/collection relations.
     */
    public boolean isPrivate() {
        return isPrivate;
    }

    /**
     *  Check whether aggressive loading is set for this relation
     */
    public boolean loadAggressively() {
        return aggressiveLoading;
    }

    /**
     *  Returns the number of constraints for this relation.
     */
    public int countConstraints() {
        if (constraints == null)
            return 0;
        return constraints.length;
    }

    /**
     *  Returns true if the object represented by this Relation has to be
     *  created on demand at runtime by the NodeManager. This is true for:
     *
     *  - collection (aka virtual) nodes
     *  - nodes accessed via accessname
     *  - group nodes
     *  - complex reference nodes
     */
    public boolean createOnDemand() {
        if (otherType == null) {
            return false;
        }

        return virtual ||
            (otherType.isRelational() && accessName != null) ||
            (groupby != null) || isComplexReference();
    }

    /**
     *  Returns true if the object represented by this Relation has to be
     *  persisted in the internal db in order to be functional. This is true if
     *  the subnodes contained in this collection are stored in the embedded
     *  database. In this case, the collection itself must also be an ordinary
     *  object stored in the db, since a virtual collection would lose its
     *  its content after restarts.
     */
    public boolean needsPersistence() {
        if (!virtual) {
            // ordinary object references always need to be persisted
            return true;
        }

        // collections/mountpoints need to be persisted if the
        // child object type is non-relational.
        if (prototype == null) {
            // Modified because we want to store the collection object
            // in the embedded database, even if the child type is relational
            return !otherType.isRelational() || !ownType.isRelational(); 
        }

        DbMapping sub = otherType.getSubnodeMapping();

        return (sub != null) && !sub.isRelational();
    }

    /**
     * Return the prototype to be used for object reached by this relation
     */
    public String getPrototype() {
        return prototype;
    }

    /**
     * Return the name of the local property this relation is defined for
     */
    public String getPropName() {
        return propName;
    }

    /**
     *
     *
     * @param ct ...
     */
    public void setColumnType(int ct) {
        columnType = ct;
    }

    /**
     *
     *
     * @return ...
     */
    public int getColumnType() {
        return columnType;
    }

    /**
     *  Get the group for a collection relation, if defined.
     *
     * @return the name of the column used to group child objects, if any.
     */
    public String getGroup() {
        return groupby;
    }

    /**
     * Add a constraint to the current list of constraints
     */
    protected void addConstraint(Constraint c) {
        if (constraints == null) {
            constraints = new Constraint[1];
            constraints[0] = c;
        } else {
            Constraint[] nc = new Constraint[constraints.length + 1];

            System.arraycopy(constraints, 0, nc, 0, constraints.length);
            nc[nc.length - 1] = c;
            constraints = nc;
        }
    }

    /**
     *
     *
     * @return true if the foreign key used for this relation is the
     * other object's primary key.
     */
    public boolean usesPrimaryKey() {
        return referencesPrimaryKey;
    }

    /**
     *
     *
     * @return ...
     */
    public boolean hasAccessName() {
        return accessName != null;
    }

    /**
     *
     *
     * @return ...
     */
    public String getAccessName() {
        return accessName;
    }

    /**
     *
     *
     * @return ...
     */
    public Relation getSubnodeRelation() {
        // return subnoderelation;
        return null;
    }

    /**
     * Return the local field name for updates.
     */
    public String getDbField() {
        return columnName;
    }

    /**
     * This is taken from org.apache.tools.ant ProjectHelper.java
     * distributed under the Apache Software License, Version 1.1
     *
     * Parses a string containing <code>${xxx}</code> style property
     * references into two lists. The first list is a collection
     * of text fragments, while the other is a set of string property names.
     * <code>null</code> entries in the first list indicate a property
     * reference from the second list.
     *
     * @param value     Text to parse. Must not be <code>null</code>.
     * @param fragments List to add text fragments to.
     *                  Must not be <code>null</code>.
     * @param propertyRefs List to add property names to.
     *                     Must not be <code>null</code>.
     */
    protected void parsePropertyString(String value, Vector fragments, Vector propertyRefs) {
        int prev = 0;
        int pos;
        //search for the next instance of $ from the 'prev' position
        while ((pos = value.indexOf("$", prev)) >= 0) {

            //if there was any text before this, add it as a fragment
            //TODO, this check could be modified to go if pos>prev;
            //seems like this current version could stick empty strings
            //into the list
            if (pos > 0) {
                fragments.addElement(value.substring(prev, pos));
            }
            //if we are at the end of the string, we tack on a $
            //then move past it
            if (pos == (value.length() - 1)) {
                fragments.addElement("$");
                prev = pos + 1;
            } else if (value.charAt(pos + 1) != '{') {
                //peek ahead to see if the next char is a property or not
                //not a property: insert the char as a literal
                /*
                fragments.addElement(value.substring(pos + 1, pos + 2));
                prev = pos + 2;
                */
                if (value.charAt(pos + 1) == '$') {
                    //backwards compatibility two $ map to one mode
                    fragments.addElement("$");
                    prev = pos + 2;
                } else {
                    //new behaviour: $X maps to $X for all values of X!='$'
                    fragments.addElement(value.substring(pos, pos + 2));
                    prev = pos + 2;
                }

            } else {
                //property found, extract its name or bail on a typo
                int endName = value.indexOf('}', pos);
                if (endName < 0) {
                    throw new RuntimeException("Syntax error in property: "
                                                 + value);
                }
                String propertyName = value.substring(pos + 2, endName);
                fragments.addElement(null);
                propertyRefs.addElement(propertyName);
                prev = endName + 1;
            }
        }
        //no more $ signs found
        //if there is any tail to the file, append it
        if (prev < value.length()) {
            fragments.addElement(value.substring(prev));
        }
    }

    /**
     *  get a DbMapping to use for virtual aka collection nodes.
     */
    public DbMapping getVirtualMapping() {
        // return null unless this relation describes a virtual/collection node.
        if (!virtual) {
            return null;
        }

        // if the collection node is prototyped, return the app's DbMapping
        // for that prototype
        if (prototype != null) {
            return otherType;
        }

        // create a synthetic DbMapping that describes how to fetch the
        // collection's child objects.
        if (virtualMapping == null) {
            virtualMapping = new DbMapping(ownType.app);
            virtualMapping.subRelation = getVirtualSubnodeRelation();
            virtualMapping.propRelation = getVirtualPropertyRelation();
        }

        return virtualMapping;
    }

    /**
     * Return a Relation that defines the subnodes of a virtual node.
     */
    Relation getVirtualSubnodeRelation() {
        if (!virtual) {
            throw new RuntimeException("getVirtualSubnodeRelation called on non-virtual relation");
        }

        Relation vr = new Relation(this);

        vr.groupby = groupby;
        vr.groupbyOrder = groupbyOrder;
        vr.groupbyPrototype = groupbyPrototype;

        return vr;
    }

    /**
     * Return a Relation that defines the properties of a virtual node.
     */
    Relation getVirtualPropertyRelation() {
        if (!virtual) {
            throw new RuntimeException("getVirtualPropertyRelation called on non-virtual relation");
        }

        Relation vr = new Relation(this);

        vr.groupby = groupby;
        vr.groupbyOrder = groupbyOrder;
        vr.groupbyPrototype = groupbyPrototype;

        return vr;
    }

    /**
     * Return a Relation that defines the subnodes of a group-by node.
     */
    Relation getGroupbySubnodeRelation() {
        if (groupby == null) {
            throw new RuntimeException("getGroupbySubnodeRelation called on non-group-by relation");
        }

        Relation vr = new Relation(this);

        vr.prototype = groupbyPrototype;
        vr.addConstraint(new Constraint(null, groupby, true));

        return vr;
    }

    /**
     * Return a Relation that defines the properties of a group-by node.
     */
    Relation getGroupbyPropertyRelation() {
        if (groupby == null) {
            throw new RuntimeException("getGroupbyPropertyRelation called on non-group-by relation");
        }

        Relation vr = new Relation(this);

        vr.prototype = groupbyPrototype;
        vr.addConstraint(new Constraint(null, groupby, true));

        return vr;
    }

    /**
     *  Build the second half of an SQL select statement according to this relation
     *  and a local object.
     */
    public String buildQuery(INode home, INode nonvirtual, String kstr, String pre,
                             boolean useOrder) throws SQLException {
        StringBuffer q = new StringBuffer();
        String prefix = pre;

        if (kstr != null && !isComplexReference()) {
            q.append(prefix);
            
            String accessColumn = (accessName == null) ? otherType.getIDField() : accessName;
            
            if (accessColumn.indexOf('(') == -1 && accessColumn.indexOf('.') == -1) {
                q.append(otherType.getTableName(0));
                q.append(".");
            }
            q.append(accessColumn);
            q.append(" = ");

            // check if column is string type and value needs to be quoted
            if (otherType.needsQuotes(accessColumn)) {
                q.append("'");
                q.append(escape(kstr));
                q.append("'");
            } else {
                q.append(escape(kstr));
            }

            prefix = " AND ";
        }

        // render the constraints and filter
        renderConstraints(q, home, nonvirtual, prefix);
        
        // add joined fetch constraints
        ownType.addJoinConstraints(q, prefix);
        
        // add group and order clauses
        if (groupby != null) {
            q.append(" GROUP BY ").append(groupby);

            if (useOrder && (groupbyOrder != null)) {
                q.append(" ORDER BY ").append(groupbyOrder);
            }
        } else if (useOrder && (order != null)) {
            q.append(" ORDER BY ").append(order);
        }

        return q.toString();
    }

    protected void appendAdditionalTables(StringBuffer q) {
        if (additionalTables != null) {
            q.append(additionalTablesJoined ? ' ' : ',');
            q.append(additionalTables);
        }
    }

    /**
     *  Build the filter.
     */
    protected void appendFilter(StringBuffer q, INode nonvirtual, String prefix) {
        q.append(prefix);
        q.append('(');
        if (filterFragments == null) {
            q.append(filter);
        } else {
            Enumeration i = filterFragments.elements();
            Enumeration j = filterPropertyRefs.elements();
            while (i.hasMoreElements()) {
                String fragment = (String) i.nextElement();
                if (fragment == null) {
                    // begin column version
                    String columnName = (String) j.nextElement();
                    Object value = null;
                    if (columnName != null) {
                        DbMapping dbmap = nonvirtual.getDbMapping();
                        String propertyName = dbmap.columnNameToProperty(columnName);
                        if (propertyName != null) {
                            IProperty property = nonvirtual.get(propertyName);
                            if (property != null) {
                                value = property.getStringValue();
                            }
                        }
                        if (value == null) {
                            if (columnName.equals(dbmap.getIDField())) {
                                value = nonvirtual.getID();
                            } else if (columnName.equals(dbmap.getNameField())) {
                                value = nonvirtual.getName();
                            } else if (columnName.equals(dbmap.getPrototypeField())) {
                                value = nonvirtual.getPrototype();
                            }
                        }
                    }
                    // end column version
                    if (value != null) {
                        q.append(escape(value.toString()));
                    } else {
                        q.append("NULL");
                    }
                } else {
                    q.append(fragment);
                }
            }
        }
        q.append(')');
    }

    /**
     * Render contraints and filter conditions to an SQL query string buffer
     *
     * @param q the query string
     * @param home our home node
     * @param nonvirtual our non-virtual home node
     * @param prefix the prefix to use to append to the existing query (e.g. " AND ")
     *
     * @throws SQLException ...
     */
    public void renderConstraints(StringBuffer q, INode home,
                                  INode nonvirtual, String prefix)
                             throws SQLException {

        if (constraints.length > 1 && logicalOperator != AND) {
            q.append(prefix);
            q.append("(");
            prefix = "";
        }

        for (int i = 0; i < constraints.length; i++) {
            q.append(prefix);
            constraints[i].addToQuery(q, home, nonvirtual);
            prefix = logicalOperator;
        }

        if (constraints.length > 1 && logicalOperator != AND) {
            q.append(")");
            prefix = " AND ";
        }

        if (filter != null) {
            appendFilter(q, nonvirtual, prefix);
        }
    }

    /**
     *  Render the constraints for this relation for use within
     *  a left outer join select statement for the base object.
     *
     * @param select the string buffer to write to
     * @param isOracle create Oracle pre-9 style left outer join
     */
    public void renderJoinConstraints(StringBuffer select, boolean isOracle) {
        for (int i = 0; i < constraints.length; i++) {
            select.append(ownType.getTableName());
            select.append(".");
            select.append(constraints[i].localName);
            select.append(" = ");
            select.append(JOIN_PREFIX);
            select.append(propName);
            select.append(".");
            select.append(constraints[i].foreignName);
            if (isOracle) {
                // create old oracle style join - see
                // http://www.praetoriate.com/oracle_tips_outer_joins.htm
                select.append("(+)");
            }
            if (i == constraints.length-1) {
                select.append(" ");
            } else {
                select.append(" AND ");
            }
        }

    }

    /**
     * Get the order section to use for this relation
     */
    public String getOrder() {
        if (groupby != null) {
            return groupbyOrder;
        } else {
            return order;
        }
    }

    /**
     *  Tell wether the property described by this relation is to be handled
     *  as readonly/write protected.
     */
    public boolean isReadonly() {
        return readonly;
    }

    /**
     * Check if the child node fullfills the constraints defined by this relation.
     * FIXME: This always returns false if the relation has a filter value set,
     * since we can't determine if the filter constraints are met without
     * querying the database.
     *
     * @param parent the parent object - may be a virtual or group node
     * @param child the child object
     * @return true if all constraints are met
     */
    public boolean checkConstraints(Node parent, Node child) {
        // problem: if a filter property is defined for this relation,
        // i.e. a piece of static SQL-where clause, we'd have to evaluate it
        // in order to check the constraints. Because of this, if a filter
        // is defined, we return false as soon as the modified-time is greater
        // than the create-time of the child, i.e. if the child node has been
        // modified since it was first fetched from the db.
        if (filter != null && child.lastModified() > child.created()) {
            return false;
        }

        // counter for constraints and satisfied constraints
        int count = 0;
        int satisfied = 0;

        INode nonvirtual = parent.getNonVirtualParent();

        for (int i = 0; i < constraints.length; i++) {
            String propname = constraints[i].foreignProperty();

            if (propname != null) {
                INode home = constraints[i].isGroupby ? parent
                                                      : nonvirtual;
                String value = null;

                if (constraints[i].localKeyIsPrimary(home.getDbMapping())) {
                    value = home.getID();
                } else if (ownType.isRelational()) {
                    value = home.getString(constraints[i].localProperty());
                } else {
                    value = home.getString(constraints[i].localName);
                }

                count++;

                if (value != null && value.equals(child.getString(propname))) {
                    satisfied++;
                }
            }
        }

        // check if enough constraints are met depending on logical operator
        if (logicalOperator == OR) {
            return satisfied > 0;
        } else if (logicalOperator == XOR) {
            return satisfied == 1;
        } else {
            return satisfied == count;
        }
    }

    /**
     * Make sure that the child node fullfills the constraints defined by this relation by setting the
     * appropriate properties
     */
    public void setConstraints(Node parent, Node child) {
        // if logical operator is OR or XOR we just return because we
        // wouldn't know what to do anyway
        if (logicalOperator != AND) {
            return;
        }

        Node home = parent.getNonVirtualParent();

        for (int i = 0; i < constraints.length; i++) {
            // don't set groupby constraints since we don't know if the
            // parent node is the base node or a group node
            if (constraints[i].isGroupby) {
                continue;
            }

            // check if we update the local or the other object, depending on
            // whether the primary key of either side is used.

            if (constraints[i].foreignKeyIsPrimary()) {
                String localProp = constraints[i].localProperty();
                if (localProp == null) {
                    System.err.println ("Error: column "+constraints[i].localName+
                       " must be mapped in order to be used as constraint in "+
                       Relation.this);
                } else {
                	if (this.ownType.app.debug()) this.ownType.app.logEvent("Relation.setConstraincts() calling setString");
                    home.setString(localProp, child.getID());
                }
                continue;
            }
            Relation crel = otherType.columnNameToRelation(constraints[i].foreignName);
            if (crel != null) {
                // INode home = constraints[i].isGroupby ? parent : nonVirtual;

                if (constraints[i].localKeyIsPrimary(home.getDbMapping())) {
                    // only set node if property in child object is defined as reference.
                    if (crel.reftype == REFERENCE) {
                        INode currentValue = child.getNode(crel.propName);

                        // we set the backwards reference iff the reference is currently unset, if
                        // is set to a transient object, or if the new target is not transient. This
                        // prevents us from overwriting a persistent refererence with a transient one,
                        // which would most probably not be what we want.
                        if ((currentValue == null) ||
                                ((currentValue != home) &&
                                ((currentValue.getState() == Node.TRANSIENT) ||
                                (home.getState() != Node.TRANSIENT)))) try {
                            child.setNode(crel.propName, home);
                        } catch (Exception ignore) {
                        	// in some cases, getNonVirtualParent() doesn't work
                            // correctly for transient nodes, so this may fail.
                        }
                    } else if (crel.reftype == PRIMITIVE) {
                    	if (this.ownType.app.debug()) this.ownType.app.logEvent("Relation.setConstraincts() 2 calling setString");
                        child.setString(crel.propName, home.getID());
                    }
                } else if (crel.reftype == PRIMITIVE) {
                    Property prop = null;

                    if (ownType.isRelational()) {
                        prop = home.getProperty(constraints[i].localProperty());
                    } else {
                        prop = home.getProperty(constraints[i].localName);
                    }

                    if (prop != null) {
                        child.set(crel.propName, prop.getValue(), prop.getType());
                    }
                }
            }
        }
    }

    /**
     * Unset the constraints that link two objects together.
     */
    public void unsetConstraints(Node parent, INode child) {
        Node home = parent.getNonVirtualParent();

        for (int i = 0; i < constraints.length; i++) {
            // don't set groupby constraints since we don't know if the
            // parent node is the base node or a group node
            if (constraints[i].isGroupby) {
                continue;
            }

            // check if we update the local or the other object, depending on
            // whether the primary key of either side is used.

            if (constraints[i].foreignKeyIsPrimary()) {
                String localProp = constraints[i].localProperty();
                if (localProp != null) {
                	if (this.ownType.app.debug()) this.ownType.app.logEvent("Relation.unsetConstraincts() calling setString");
                    home.setString(localProp, null);
                }
                continue;
            }

            Relation crel = otherType.columnNameToRelation(constraints[i].foreignName);
            if (crel != null) {
                // INode home = constraints[i].isGroupby ? parent : nonVirtual;
                if (constraints[i].localKeyIsPrimary(home.getDbMapping())) {
                    // only set node if property in child object is defined as reference.
                    if (crel.reftype == REFERENCE) {
                        INode currentValue = child.getNode(crel.propName);

                        if ((currentValue == home)) {
                        	if (this.ownType.app.debug()) this.ownType.app.logEvent("Relation.unsetConstraincts() 2 calling setString");
                            child.setString(crel.propName, null);
                        }
                    } else if (crel.reftype == PRIMITIVE) {
                    	if (this.ownType.app.debug()) this.ownType.app.logEvent("Relation.unsetConstraincts() 3 calling setString");
                        child.setString(crel.propName, null);
                    }
                } else if (crel.reftype == PRIMITIVE) {
                    Property prop = null;

                    if (ownType.isRelational()) {
                        prop = home.getProperty(constraints[i].localProperty());
                    } else {
                        prop = home.getProperty(constraints[i].localName);
                    }

                    if (prop != null) {
                    	if (this.ownType.app.debug()) this.ownType.app.logEvent("Relation.unsetConstraincts() 4 calling setString");
                        child.setString(crel.propName, null);
                    }
                }
            }
        }
    }

    /**
     *  Returns a map containing the key/value pairs for a specific Node
     */
    public Map getKeyParts(INode home) {
        Map map = new HashMap();
        for (int i=0; i<constraints.length; i++) {
            if (ownType.getIDField().equals(constraints[i].localName)) {
                map.put(constraints[i].foreignName, home.getID());
            } else {
                map.put(constraints[i].foreignName, home.getString(constraints[i].localProperty()));
            }
        }
        return map;
    }

    // a utility method to escape single quotes
    String escape(String str) {
        if (str == null) {
            return null;
        }

        if (str.indexOf("'") < 0) {
            return str;
        }

        int l = str.length();
        StringBuffer sbuf = new StringBuffer(l + 10);

        for (int i = 0; i < l; i++) {
            char c = str.charAt(i);

            if (c == '\'') {
                sbuf.append('\'');
            }

            sbuf.append(c);
        }

        return sbuf.toString();
    }

    /**
     *
     *
     * @return ...
     */
    public String toString() {
        String c = "";
        String spacer = "";

        if (constraints != null) {
            c = " constraints: ";
            for (int i = 0; i < constraints.length; i++) {
                c += spacer;
                c += constraints[i].toString();
                spacer = ", ";
            }
        }

        String target = otherType == null ? columnName : otherType.toString();

        return "Relation " + ownType+"."+propName + " -> " + target + c;
    }
    
    public boolean deleteOnRemove() {
        return this.deleteOnRemove;
    }

    /**
     * The Constraint class represents a part of the where clause in the query used to
     * establish a relation between database mapped objects.
     */
    class Constraint {
        String localName;
        String foreignName;
        boolean isGroupby;

        Constraint(String local, String foreign, boolean groupby) {
            localName = local;
            foreignName = foreign;
            isGroupby = groupby;
        }

        public void addToQuery(StringBuffer q, INode home, INode nonvirtual)
                        throws SQLException {
            String local = null;
            INode ref = isGroupby ? home : nonvirtual;
            
            if ((localName == null) ||
                    localName.equalsIgnoreCase(ref.getDbMapping().getIDField())) {
                local = ref.getID();
            } else {
                String homeprop = ownType.columnNameToProperty(localName);
                
                local = ref.getString(homeprop);
            }

            if (foreignName.indexOf('(') == -1 && foreignName.indexOf('.') == -1) {
                q.append(otherType.getTableName());
                q.append(".");
            }
            q.append(foreignName);
            q.append(" = ");

            if (otherType.needsQuotes(foreignName)) {
                q.append("'");
                q.append(escape(local));
                q.append("'");
            } else {
                q.append(escape(local));
            }
        }

        public boolean foreignKeyIsPrimary() {
            return (foreignName == null) ||
                   foreignName.equalsIgnoreCase(otherType.getIDField());
        }

        public boolean localKeyIsPrimary(DbMapping homeMapping) {
            return (homeMapping == null) || (localName == null) ||
                   localName.equalsIgnoreCase(homeMapping.getIDField());
        }

        public String foreignProperty() {
            return otherType.columnNameToProperty(foreignName);
        }

        public String localProperty() {
            return ownType.columnNameToProperty(localName);
        }

        public String toString() {
            return localName + "=" + otherType.getTypeName() + "." + foreignName;
        }
    }
}
