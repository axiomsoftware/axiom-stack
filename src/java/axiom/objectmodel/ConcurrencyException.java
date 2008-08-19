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
 * $RCSfile: ConcurrencyException.java,v $
 * $Author: hannes $
 * $Revision: 1.3 $
 * $Date: 2004/04/26 09:08:45 $
 */

package axiom.objectmodel;

/**
 * Thrown when more than one thrad tries to modify a Node. The evaluator
 * will normally catch this and try again after a period of time.
 */
public class ConcurrencyException extends Error {
    /**
     * Creates a new ConcurrencyException object.
     *
     * @param msg ...
     */
    public ConcurrencyException(String msg) {
        super(msg);
    }
}
