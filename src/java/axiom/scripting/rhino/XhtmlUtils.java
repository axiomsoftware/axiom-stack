/*
 * Axiom Stack Web Application Framework
 * Copyright (C) 2008  Axiom Software Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */
package axiom.scripting.rhino;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Scriptable;

public class XhtmlUtils {

    public static String[] getXhtmlLinks(Object xhtml) {
    	if (xhtml instanceof Scriptable) {
    		Scriptable sxhtml = (Scriptable) xhtml;
    		String classname = sxhtml.getClassName().toLowerCase();
    		if ("xml".equals(classname) || "xmllist".equals(classname)) {
    			final String XHTML_LINKS_EVAL = 
    				"xhtml..*.@href + xhtml..*.@src";

    			Context cx = Context.getCurrentContext();
    			ImporterTopLevel itl = new ImporterTopLevel(cx);
    			itl.put("xhtml", itl, sxhtml);
    			Object ret = cx.evaluateString(itl, XHTML_LINKS_EVAL, "", 0, null);
    			itl = null;
    			
    			if (ret != null && ret instanceof Scriptable) {
    				Scriptable s = (Scriptable) ret;
    				final int length = s.getIds().length;
    				String[] links = new String[length];
    				for (int i = 0; i < length; i++) {
    					links[i] = s.get(i, s).toString();
    				}

    				return links;
    			}
    		}
    	}
        
        return new String[0];
    }
    
    public static void removeLinkFromXhtml(Object xhtml, String link) {
    	if (xhtml instanceof Scriptable) {
    		Scriptable sxhtml = (Scriptable) xhtml;
    		String classname = sxhtml.getClassName().toLowerCase();
    		if ("xml".equals(classname) || "xmllist".equals(classname)) {
    			final String XHTML_DEL_LINK = 
    				"var link_re = new RegExp('" + link + "/?');"+ 
    				"for each (var x in xhtml..*.(link_re.test(@href.toString() ||  @src.toString()))) { " +
    					"x.parent().replace(x.childIndex(), x.*); " +
    				"}";
    			Context cx = Context.getCurrentContext();
    			ImporterTopLevel itl = new ImporterTopLevel(cx);
    			itl.put("xhtml", itl, sxhtml);
    			cx.evaluateString(itl, XHTML_DEL_LINK, "", 0, null);
    			itl = null;
    		}
    	}
    }
    
    public static void updateLinkInXhtml(Object xhtml, String oldlink, String newlink) {
    	if (xhtml instanceof Scriptable) {
    		Scriptable sxhtml = (Scriptable) xhtml;
    		String classname = sxhtml.getClassName().toLowerCase();
    		if ("xml".equals(classname) || "xmllist".equals(classname)) {
    			final String XHTML_DEL_LINK = 
    				"var oldlink_re = new RegExp('" + oldlink + "/?');"+ 
    				"for each (var x in xhtml..*.(oldlink_re.test(@href.toString() || @src.toString() || @action.toString()))) { " +
    					"for each(var attr in ['href', 'src', 'action']){"+
    						"if(oldlink_re.test(x.@[attr])){" +
    							"x.@[attr] = '"+newlink+"';"+
    						"}"+
    					"}"+
    				"}";
    			Context cx = Context.getCurrentContext();
    			ImporterTopLevel itl = new ImporterTopLevel(cx);
    			itl.put("xhtml", itl, sxhtml);
    			cx.evaluateString(itl, XHTML_DEL_LINK, "", 0, null);
    			itl = null;
    		}
    	}
    }
	
}