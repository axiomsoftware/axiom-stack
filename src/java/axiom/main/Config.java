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
 * $RCSfile: Config.java,v $
 * $Author: hannes $
 * $Revision: 1.3 $
 * $Date: 2005/04/11 15:29:47 $
 */

package axiom.main;

import java.io.File;

/**
 * Utility class for server config
 */
 
public class Config {

	/*
	    InetAddrPort rmiPort    = null;
	    InetAddrPort xmlrpcPort = null;
	    InetAddrPort ajp13Port  = null;
	 */
	String websrvPort = null; 
    File         propFile   = null;
    File         homeDir    = null;

    public boolean hasPropFile() {
        return (propFile != null);
    }

    public boolean hasHomeDir() {
        return (homeDir != null);
    }

}
