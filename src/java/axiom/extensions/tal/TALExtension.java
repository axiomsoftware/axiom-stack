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
package axiom.extensions.tal;

import java.util.*;
import java.io.*;

import org.mozilla.javascript.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import axiom.extensions.ConfigurationException;
import axiom.extensions.AxiomExtension;
import axiom.framework.ErrorReporter;
import axiom.framework.RequestBean;
import axiom.framework.core.Application;
import axiom.framework.core.Prototype;
import axiom.framework.core.RequestEvaluator;
import axiom.framework.core.SessionBean;
import axiom.framework.repository.Resource;
import axiom.main.Server;
import axiom.scripting.ScriptingEngine;
import axiom.scripting.rhino.AxiomObject;
import axiom.scripting.rhino.RhinoCore;
import axiom.scripting.rhino.RhinoEngine;
import axiom.util.ExecutionCache;

import com.sun.org.apache.xerces.internal.parsers.*;

public class TALExtension extends AxiomExtension {	

    private Application app;
    static HashMap templates = new HashMap();
    
    private static final String TAL_INVOCATION = "gl_talresult = " +
            "renderTAL(template_document, template_data);"; 
    
    public TALExtension() {
    }
    
    public TALExtension(Application app) {
        this.app = app;
    }
    
	public String getName()	{
		return "TALExtension";
	}
	
	public void init(Server server) throws ConfigurationException {
    }

    public void applicationStarted(Application app) throws ConfigurationException { 
    }

    public void applicationStopped(Application app) {
    	if (templates.remove(app) != null) {
            app.logEvent("Dectivating TALExtension for " + app.getName());
        }
    }

    public void applicationUpdated(Application app) {    
    	if ("true".equalsIgnoreCase(app.getProperty("tal"))) {
           if (retrieveTemplatesForApp(app) == null) {
               app.logEvent("Creating TALExtension for " + app.getName());
           }
        } else {
            applicationStopped(app);
        }
    }

    public HashMap initScripting(Application app, ScriptingEngine engine) throws ConfigurationException {
        try {
            loadTemplates(app);
        } catch (Exception ex) {
            throw new ConfigurationException(ex.getMessage());
        }
        
        if (templates.get(app) != null) {
    		// initialize prototypes and global vars here
    		RhinoCore core = ((RhinoEngine) engine).getCore();
    		Scriptable scope = core.getScope();
    		// add renderTAL to the AxiomObject prototype
    		ScriptableObject prototype = (ScriptableObject) ScriptableObject.getClassPrototype(scope, "AxiomObject");
    		String[] funcs = new String[] { "getTALDom",
                                            "isTALFunction" };
            try {
    			prototype.defineFunctionProperties(funcs, TALExtension.class, 0);
    		} catch (PropertyException e) {
    			throw new ConfigurationException(e.getMessage());
    		} 
    	}    	
        
    	return null;
    }
    
    private static Document _getTALDom(String name, String proto, Application app) 
    throws Exception {
        Document doc = getTALDoc(app, proto, name);
        return (doc != null) ? (Document) doc.cloneNode(true) : null;
    }
    
    public Document talDom(Object o, Object proto) throws Exception {
        // separate prototype name and template name by :. this will be used again in TemplateLoader
       return _getTALDom(o.toString(), proto.toString(), app);
    }
    
    public static Object getTALDom(Context cx, Scriptable thisObj, Object[] args, Function funObj) 
    throws Exception {
        String name = args[0].toString();
        String proto = thisObj.getClassName();
        RhinoCore core = ((RhinoEngine) cx.getThreadLocal("engine")).getCore();
        Application app = core.getApplication();
        // separate prototype name and template name by :. this will be used again in TemplateLoader
        return Context.toObject(_getTALDom(name, proto, app), core.getScope());
    }
    
    // different name so we don't get name conflicts in the js -- Thomas
    public static boolean isTALAction(String actionName, String className, Application app) throws Exception{
        return (_getTALDom(actionName, className, app) != null);
    }
    
    public static boolean isTALFunction(Context cx, Scriptable thisObj, Object[] args, Function funObj) 
    throws Exception {
        RhinoCore core = ((RhinoEngine) cx.getThreadLocal("engine")).getCore();
        Application app = core.getApplication();
        return (_getTALDom(args[0].toString(), thisObj.getClassName(), app) != null);
    }
    
    public static Object renderTAL(Context cx, Scriptable thisObj, Object[] args, Function funObj) 
    throws Exception {
        Object result = null;
    	
    	if (args.length >= 1 && args.length <= 2) {
            RhinoCore core = ((RhinoEngine) cx.getThreadLocal("engine")).getCore();
            Application app = core.getApplication();
            Object talTemplates = templates.get(app);
            if (talTemplates != null) {
                Scriptable scope = core.getScope();
                String proto = thisObj.getClassName();
                String name = args[0].toString();
                Scriptable param = null;
                if (args.length >= 2 && args[1] instanceof Scriptable) {
                    param = (Scriptable) args[1];
                } else {
                    param = cx.newObject(core.getScope()); // empty javascript object 
                }

                try {
                    Document d = getTALDoc(app, proto, name);
                    if (d == null) {
                        throw new RuntimeException("Could not find the TAL file " + name);
                    }
                    Object dom = d.cloneNode(true);
                    scope.put("template_document", scope, dom);
                    scope.put("template_data", scope, param);
                    param.put("this", param, thisObj);
                    Object req = new RequestBean(app.getCurrentRequestEvaluator().getRequest());
                    param.put("req", param, Context.toObject(req, core.getScope()));
                    param.put("root", param, core.getNodeWrapper(app.getNodeManager().getRootNode()));
                    Object session = new SessionBean(app.getCurrentRequestEvaluator().getSession());
                    param.put("session", param, Context.toObject(session, scope));
                    result = Context.toObject(cx.evaluateString(scope, TAL_INVOCATION, "tal.js", 1, null), core.getScope());
                } catch (Exception ex) {
                	throw new Exception("ERROR in renderTAL() on " + name + ": " + ex.getMessage());
                }
            }
        } else {
            throw new RuntimeException("Wrong parameter count in renderTAL");
        }  	
        
    	return result;
    }
    
    private static Document getTALDoc(Application app, String proto, String name) 
    throws Exception {
        Prototype prototype = app.getPrototypeByName(proto);
        while (prototype != null) {
            // separate prototype name and template name by :. this will be used again in TemplateLoader
            String curr = new StringBuffer().append(prototype.getName())
                                     .append(':').append(name).toString();
            Document d = retrieveDocument(app, curr);
            if (d != null) {
                return d;
            }
            prototype = prototype.getParentPrototype();
        }
        return null;
    }
    
    private static void loadTemplates(Application app) throws Exception {
        TemplateLoader loader = new TemplateLoader(app);
        HashMap map = retrieveTemplatesForApp(app);
        HashMap templSources = loader.getAllTemplateSources();
        
        synchronized (map) {
            TALTemplate template = null;
            Iterator keys = templSources.keySet().iterator();
            int count = 0;
            while (keys.hasNext()) {
                count++;
                String name = keys.next().toString();
                Resource templateSource = (Resource) templSources.get(name);
                Document doc = null;
                if ((template = (TALTemplate) map.get(name)) != null 
                        && (!app.autoUpdate() ||
                            template.getLastModified() >= templateSource.lastModified()))
                    doc = template.getDocument();
                else {
                    boolean success = true;
                    Reader reader = null;
                    try {
                        reader = loader.getReader(templateSource, null);
                        org.xml.sax.InputSource isource = new org.xml.sax.InputSource(reader);
                        isource.setEncoding("UTF-8");
                        doc = getDOMFromSource(isource);
                    } catch (Exception ex) {
                        success = false;
                        app.logError(ErrorReporter.errorMsg(TALExtension.class, "loadTemplates") 
                        		+ "Could not load the TAL for " + name, ex);
                    } finally {
                        if (reader != null) {
                            reader.close();
                            reader = null;
                        }
                    }
                    if (success) {
                        if (template != null) {
                            template.setDocument(doc);
                            template.setLastModified(templateSource.lastModified());
                        } else {
                            map.put(name, new TALTemplate(name, doc, templateSource.lastModified()));
                        }
                    }
                }
            }
        }
    }

    private static Document retrieveDocumentFromString(String markup) throws Exception {
        Document doc = null;
        Reader reader = null;
        try {
        	reader = new StringReader(markup);
        	org.xml.sax.InputSource isource = new org.xml.sax.InputSource(reader);
        	isource.setEncoding("UTF-8");
            doc = getDOMFromSource(isource);
        } finally {
            if (reader != null) {
                reader.close();
                reader = null;
            }
        }
        return doc;
    }    
    
    public static Document retrieveDocument(Application app, String name) 
    throws Exception {
        TemplateLoader loader = new TemplateLoader(app);
        HashMap map = retrieveTemplatesForApp(app);
        Document doc = null;      
        Resource templateSource = (Resource) loader.findTemplateSource(name);
        if (templateSource == null) {
          	// throw new Exception("ERROR in renderTAL(): Could not find the TAL template file indicated by '" + name + "'.");
        	return null;
        }
        TALTemplate template = null;
        
        synchronized (map) {
            if ((template = getTemplateByName(map, name)) != null && template.getLastModified() >= templateSource.lastModified()) 
                doc = template.getDocument();
            else {
                Reader reader = null;
                try {
                    reader = loader.getReader(templateSource, null);
                    org.xml.sax.InputSource isource = new org.xml.sax.InputSource(reader);
                    isource.setEncoding("UTF-8");
                    doc = getDOMFromSource(isource);
                } finally {
                    if (reader != null) {
                        reader.close();
                        reader = null;
                    }
                }
                if (template != null) {
                    template.setDocument(doc);
                    template.setLastModified(templateSource.lastModified());
                } else {
                    map.put(name, new TALTemplate(name, doc, templateSource.lastModified()));
                }
            }    
        }
        
        return doc;
    }
    
    public static Document getDOMFromSource(InputSource source) throws Exception {
        DOMParser parser = new DOMParser();
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        parser.parse(source);
        Document doc = parser.getDocument();
        parser = null;
        return doc;
    }
    
    private static TALTemplate getTemplateByName(HashMap map, String name) {
        return (TALTemplate) map.get(name);
    }
    
    private static HashMap retrieveTemplatesForApp(Application app) {
        HashMap map = null;
        synchronized (templates) {
            Object ret = templates.get(app);
            if (ret != null)
                map = (HashMap) ret;
            else {
                map = new HashMap();
                templates.put(app, map);
            }
        }
        return map;
    }

    public static Object stringToXHTMLObject(String xmlStr, Scriptable scope, Application app){
    	Object xml = null;
    	try {
    		Context cx = Context.getCurrentContext();
    		cx.evaluateString(scope, "XML.ignoreWhitespace=false;", "", 1, null);
    		cx.evaluateString(scope, "XML.prettyPrinting=false;", "", 1, null);
    		xml = cx.newObject(scope, "XHTML", new Object[]{ xmlStr });
    	}  catch (Exception ex) {
    		app.logError(ErrorReporter.errorMsg(TALExtension.class, "stringToXHTMLObject") 
    				+ "Could not create the XHTML object for " + xmlStr, ex);
    	}
    	return xml;
    }
    
    public static Object stringToXmlObject(String xmlStr, Scriptable scope, Application app) {
    	Object xml = null;
    	if (xmlStr.startsWith("<?")) {
    		xmlStr = xmlStr.substring(xmlStr.indexOf("?>")+2).trim();
    	}
    	if (xmlStr.startsWith("<!DOCTYPE")) {
    		xmlStr = xmlStr.substring(xmlStr.indexOf('>')+1).trim();
    	}

    	try {
    		Context cx = Context.getCurrentContext();
    		cx.evaluateString(scope, "XML.ignoreWhitespace=false;", "", 1, null);
    		cx.evaluateString(scope, "XML.prettyPrinting=false;", "", 1, null);
    		xml = cx.newObject(scope, "XMLList", new Object[]{ xmlStr });
    	}  catch (Exception ex) {
    		app.logError(ErrorReporter.errorMsg(TALExtension.class, "stringToXmlObject") 
    				+ "Could not create the XML object for " + xmlStr, ex);
    	}
    	return xml;
    }

    public static String xmlObjectToString(Scriptable xml, Application app) {
    	Context cx = Context.getCurrentContext();
    	Object func = ScriptableObject.getProperty(xml, "toXMLString");
    	Object ret = ((Function) func).call(cx, xml, xml, new Object[]{});
    	if (ret != null) {
    		return ret.toString();
    	} else {
    		return null;
    	}
    }
    
    public static Object cloneXmlObject(Scriptable xml, Scriptable scope, Application app) {
    	return stringToXmlObject(xmlObjectToString(xml, app), scope, app);
    }

}