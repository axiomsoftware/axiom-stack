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
 * $RCSfile: HelmaShutdownHook.java,v $
 * $Author: hannes $
 * $Revision: 1.9 $
 * $Date: 2004/10/19 12:45:30 $
 */

package axiom.main;

import org.apache.commons.logging.LogFactory;

import axiom.util.*;

/**
 * ShutdownHook that shuts down all running Axiom applications on exit.
 */
public class AxiomShutdownHook extends Thread {


    /**
     *
     */
    public void run() {
        System.err.println("Shutting down Axiom - please stand by...");

        Server server = Server.getServer();
        if (server != null) {
            server.stop();
        }
     }
}