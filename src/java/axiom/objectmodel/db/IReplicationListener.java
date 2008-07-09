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
 * $RCSfile: IReplicationListener.java,v $
 * $Author: hannes $
 * $Revision: 1.3 $
 * $Date: 2003/10/23 11:04:37 $
 */

package axiom.objectmodel.db;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Vector;

/**
 * RMI interface for an application. Currently only execute is used and supported.
 */
public interface IReplicationListener extends Remote {
    /**
     * Update AxiomObjects in this application's cache. This is used to replicate
     * application caches in a distributed app environment
     */
    public void replicateCache(Vector add, Vector delete)
                        throws RemoteException;
}
