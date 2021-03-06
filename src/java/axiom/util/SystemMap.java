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
 * $RCSfile: SystemMap.java,v $
 * $Author: hannes $
 * $Revision: 1.2 $
 * $Date: 2004/02/18 11:20:09 $
 */

package axiom.util;


import java.util.*;

/**
 * Map class used internally by Axiom. We use this class to be able to
 *  wrap maps as native objects within a scripting engine rather
 *  than exposing them through Java reflection. 
 */
public class SystemMap extends HashMap {


    /**
     *  Construct an empty SystemMap.
     */
    public SystemMap() {
        super();
    }

    /**
     *  Construct an empty SystemMap with the given initial capacity.
     */
    public SystemMap(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     *  Construct a SystemMap with the contents of Map map.
     */
    public SystemMap(Map map) {
        super(map);
    }

}
