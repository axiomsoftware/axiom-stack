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
 * $RCSfile: IRemoteApp.java,v $
 * $Author: hannes $
 * $Revision: 1.6 $
 * $Date: 2003/10/22 16:29:25 $
 */

package axiom.framework;

import java.rmi.*;

/**
 * RMI interface for an application. Currently only execute is used and supported.
 */
public interface IRemoteApp extends Remote {
    /**
     *
     *
     * @param param ...
     *
     * @return ...
     *
     * @throws RemoteException ...
     */
    public ResponseTrans execute(RequestTrans param) throws RemoteException;

    /**
     *
     *
     * @throws RemoteException ...
     */
    public void ping() throws RemoteException;
}
