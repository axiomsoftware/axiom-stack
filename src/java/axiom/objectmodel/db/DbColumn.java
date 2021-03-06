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
 * $RCSfile: DbColumn.java,v $
 * $Author: hannes $
 * $Revision: 1.4 $
 * $Date: 2003/06/10 13:20:44 $
 */

package axiom.objectmodel.db;


/**
 * A class that encapsulates the Column name and data type of a
 * column in a relational table.
 */
public final class DbColumn {
    private final String name;
    private final int type;
    private final Relation relation;

    private final boolean isId;
    private final boolean isPrototype;
    private final boolean isName;

    private final boolean isMapped;

    /**
     *  Constructor
     *  Changed to take in the tableNumber parameter, allows for multi-keyed
     *  multi-tables
     */
    public DbColumn(String name, int type, Relation rel, DbMapping dbmap, int tableNumber) {
        this.name = name;
        this.type = type;
        this.relation = rel;
        
        if (relation != null) {
            relation.setColumnType(type);
        }

        isId = name.equalsIgnoreCase(dbmap.getTableKey(tableNumber)); 
        isPrototype = name.equalsIgnoreCase(dbmap.getPrototypeField());
        isName = name.equalsIgnoreCase(dbmap.getNameField());

        isMapped = relation != null || isId || isPrototype || isName;
    }

    /**
     *  Get the column name.
     */
    public String getName() {
        return name;
    }

    /**
     *  Get this columns SQL data type.
     */
    public int getType() {
        return type;
    }

    /**
     *  Return the relation associated with this column. May be null.
     */
    public Relation getRelation() {
        return relation;
    }

    /**
     *  Returns true if this column serves as ID field for the prototype.
     */
    public boolean isIdField() {
        return isId;
    }

    /**
     *  Returns true if this column serves as prototype field for the prototype.
     */
    public boolean isPrototypeField() {
        return isPrototype;
    }

    /**
     *  Returns true if this column serves as name field for the prototype.
     */
    public boolean isNameField() {
        return isName;
    }

    /**
     * Returns true if this field is mapped by the prototype's db mapping.
     */
    public boolean isMapped() {
        return isMapped;
    }

}
