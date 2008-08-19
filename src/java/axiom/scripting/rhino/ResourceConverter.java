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
 * $RCSfile: HacHspConverter.java,v $
 * $Author: hannes $
 * $Revision: 1.2 $
 * $Date: 2005/03/23 19:28:04 $
 */

package axiom.scripting.rhino;


import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.StringTokenizer;

import axiom.framework.repository.Resource;

/**
 *  Support for .hac (action) and .hsp (template) files
 */
public class ResourceConverter {

    static String composeFunction(String funcname, String args, String body) {
        if ((body == null) || "".equals(body.trim())) {
            body = ";\r\n";
        } else {
            body = body + "\r\n";
        }

        StringBuffer f = new StringBuffer("function ");

        f.append(funcname);
        f.append(" (");
        if (args != null)
            f.append(args);
        f.append(") {\n");
        f.append(body);
        f.append("\n}");

        return f.toString();
    }

    /*
     * Add a renderTAL call as an action on a particular TAL file, making the TAL file a 
     * URL accessible action
     */
    public static String convertTal(Resource action) throws IOException {
        String baseName = action.getBaseName();
        String functionName = baseName.replace('.', '_');
        String body = new StringBuffer("if(data==undefined){data={};}\nreturn this.renderTAL('").append(baseName).append("',data);").toString();
        return composeFunction(functionName, "data", body);
    }
    
}
