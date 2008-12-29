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

    public static String convertTal(Resource action) throws IOException {
    	return convertTal(action, false);
    }
    
    /*
     * Add a renderTAL call as an action on a particular TAL file, making the TAL file a 
     * URL accessible action
     */
    public static String convertTal(Resource action, boolean debug) throws IOException {
        String baseName = action.getBaseName();
        String functionName = baseName.replace('.', '_');
        String body = "if(data==undefined){data={};}\n" +
        			  (debug ? "app.log('starting renderTAL on " +baseName+"');" +
        					   "var start = (new Date()).getTime();" : "") +
        			  "var rendered = this.renderTAL('"+baseName+"',data);\n" +
        			  (debug ? "app.log('finished rendering "+baseName+" in '+((new Date()).getTime() - start)/1000.0+' seconds');": "")+
        			  "return rendered";
        return composeFunction(functionName, "data", body);
    }
    
}
