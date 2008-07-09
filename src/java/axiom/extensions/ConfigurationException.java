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
 * $RCSfile: ConfigurationException.java,v $
 * $Author: hannes $
 * $Revision: 1.2 $
 * $Date: 2003/04/16 16:28:03 $
 */


package axiom.extensions;

/**
 * 
 */
public class ConfigurationException extends RuntimeException {
    /**
     * Creates a new ConfigurationException object.
     *
     * @param msg ...
     */
    public ConfigurationException(String msg) {
        super(msg);
    }
}
