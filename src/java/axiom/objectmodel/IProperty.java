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
 * $RCSfile: IProperty.java,v $
 * $Author: hannes $
 * $Revision: 1.2 $
 * $Date: 2003/04/16 16:28:02 $
 */

package axiom.objectmodel;


import java.util.Date;

import axiom.scripting.rhino.MultiValue;
import axiom.scripting.rhino.Reference;

/**
 * Interface that is implemented by node properties.
 */
public interface IProperty {
    public static final int STRING = 1;
    public static final int BOOLEAN = 2;
    public static final int DATE = 3;
    public static final int INTEGER = 4;
    public static final int FLOAT = 5;
    public static final int NODE = 6;
    public static final int JAVAOBJECT = 7;
    public static final int REFERENCE = 8; 
    public static final int MULTI_VALUE = 9;
    public static final int XML = 10;
    public static final int XHTML = 11;
    public static final int TIME = 12;
    public static final int TIMESTAMP = 13;
    public static final int SMALLINT = 14;
    public static final int SMALLFLOAT = 15;
    /**
     *
     *
     * @return ...
     */
    public String getName();

    /**
     *
     *
     * @return ...
     */
    public int getType();

    /**
     *
     *
     * @return ...
     */
    public Object getValue();

    /**
     *
     *
     * @return ...
     */
    public INode getNodeValue();

    /**
     *
     *
     * @return ...
     */
    public String getStringValue();

    /**
     *
     *
     * @return ...
     */
    public boolean getBooleanValue();

    /**
     *
     *
     * @return ...
     */
    public long getIntegerValue();

    /**
     *
     *
     * @return ...
     */
    public double getFloatValue();

    /**
     *
     *
     * @return ...
     */
    public Date getDateValue();

    /**
     *
     *
     * @return ...
     */
    public Object getJavaObjectValue();
    
    public MultiValue getMultiValue();
    
    public Reference getReferenceValue();

    public Object getXMLValue();
    
    public Object getXHTMLValue();

}