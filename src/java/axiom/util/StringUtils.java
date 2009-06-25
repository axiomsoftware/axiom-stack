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
 * $RCSfile: StringUtils.java,v $
 * $Author: hannes $
 * $Revision: 1.2 $
 * $Date: 2003/06/10 13:20:44 $
 */

package axiom.util;


import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for String manipulation.
 */
public class StringUtils {
    /**
     *  Split a string into an array of strings. Use comma and space
     *  as delimiters.
     */
    public static String[] split(String str) {
        return split(str, ", \t\n\r\f");
    }

    /**
     *  Split a string into an array of strings.
     */
    public static String[] split(String str, String delim) {
        if (str == null) {
            return new String[0];
        }
        StringTokenizer st = new StringTokenizer(str, delim);
        String[] s = new String[st.countTokens()];
        for (int i=0; i<s.length; i++) {
            s[i] = st.nextToken();
        }
        return s;
    }

    /**
     * Collect items of a string enumeration into a String array.
     * @param en an enumeration of strings
     * @return the enumeration values as string array
     */
    public static String[] collect(Enumeration en) {
        List list = new ArrayList();
        while (en.hasMoreElements()) {
            list.add(en.nextElement());
        }
        return (String[]) list.toArray(new String[list.size()]);
    }
}
