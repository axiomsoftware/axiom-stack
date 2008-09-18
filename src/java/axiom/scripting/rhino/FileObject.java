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

import java.io.*;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.mozilla.javascript.*;

import axiom.framework.ErrorReporter;
import axiom.framework.ResponseTrans;
import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.INode;

/**
 * File is a built-in prototype. It is instantiated by passing either 
 * a MimePart Object (as submitted via a multipart/formdata form post) or File System path 
 * into the constructor.  The binary contents of the File object are stored
 * in the application's blob directory. 
 * 
 * @jsconstructor File
 * @param {MimePart|String} object Object to build File object from
 * @param {Boolean} [extractText] Turns off text extraction 
 */
public class FileObject extends AxiomObject {
    
    String tmpPath = null;
    
    public static final String FILE_SIZE = "_size";
    public static final String FILE_NAME = "_filename";
    public static final String CONTENT_TYPE = "_contentType";
    public static final String CONTENT = "_content";
    public static final String SELF = "_FILEOBJ_SELF";
    public static final String ACCESSNAME = "accessname"; 
    public static final String RENDERED_CONTENT = "_rendered";
    public static final String FILE_UPLOAD = "_fileupload";
    
    protected static HashMap tmpDirs = new HashMap();
    
    public FileObject(String className, RhinoCore core) {
        super(className, core);
    }
    
    public FileObject(String className, RhinoCore core, INode node, Scriptable proto) {
        super(className, core, node, proto);
    }
    
    public FileObject(String className, RhinoCore core, INode node, Scriptable proto,
            boolean setupdefault) {
        super(className, core, node, proto, setupdefault);
    }

    public FileObject(String className, RhinoCore core,
            INode node, Scriptable proto, Scriptable data) {
    	super(className, core, node, proto, data);
    }
  
    /**
     * Initialize FileObject prototype for Rhino scope.
     *
     * @param core the RhinoCore
     * @return the AxiomObject prototype
     * @throws PropertyException
     */
    public static FileObject initFileProto(RhinoCore core) throws PropertyException {
        
        final int attributes = READONLY | DONTENUM | PERMANENT;

        // create prototype object
        FileObject proto = new FileObject("File", core);
        proto.setPrototype(getObjectPrototype(core.global));

        // install JavaScript methods and properties
        Method[] methods = FileObject.class.getMethods();
        for (int i=0; i<methods.length; i++) {
            String methodName = methods[i].getName();
            
            if (methodName.startsWith("jsFunction_")) {
                methodName = methodName.substring(11);
                FunctionObject func = new FunctionObject(methodName, methods[i], proto);
                proto.defineProperty(methodName, func, attributes);
            } else if (methodName.startsWith("jsGet_")) {
                methodName = methodName.substring(6);
                proto.defineProperty(methodName, null, methods[i], null, attributes);
            }
        }
        

        String tmp = core.app.getProperty("tmpdir");
        if (tmp == null) {
            tmp = System.getProperty("java.io.tmpdir");
        }
        if (tmp != null && !tmp.endsWith(File.separator)) {
            tmp += File.separator;
        }
        
        if (tmp != null) {
            synchronized (tmpDirs) {
                tmpDirs.put(core.app.getName(), tmp);
            }
        }
        
        return proto;
    } 
    
    /**
     * Returns the size of the file in bytes.
     * 
     * @returns {Number} The file size in bytes
     */
    public int jsFunction_getFileSize() {
        Object ret = this.get(FILE_SIZE, this);
        int size = 0;

        if (ret != null) {
            if (ret instanceof Number) {
                try {
                    size = ((Number) ret).intValue();
                } catch (Exception ex) {
                    size = 0;
                }
            } else if (ret instanceof String) {
                try {
                    size = Integer.parseInt(ret.toString());
                } catch (Exception ex) {
                    size = 0;
                }
            }
        }
        
        return size;
    }
    
    /**
     * Replace the binary file contents represented in this File object with the input 
     * MimePart object (as submitted via a multipart/formdata form post) or the input
     * file system path.
     * 
     * @param {MimePart|String} file The MimePart object or the path on the file system to
     *                               replace the binary contents of this File object with
     */
    public void jsFunction_replaceFile(Object mimepart){
    	FileObjectCtor.setup(this, getNode(), new Object[]{mimepart}, core.app, true);
    }
    
    /**
     * Get the URL of this object within the application.
	 *
	 * @param {String} [action] The action name, or null/undefined for the "main" action
	 * @returns {String} The URL
	 * @throws UnsupportedEncodingException
	 * @throws IOException
     */
    public String jsFunction_getURI(Object action) throws UnsupportedEncodingException, IOException{
    	String result = super.jsFunction_getURI(action);
    	if (result.endsWith("/")) {
    		return result.substring(0, result.length()-1);
    	}
    	return result;
    }
    
    /**
     * Get the content type of this File object (text, xml, doc, jpg, etc).
     * 
     * @returns {String} The content type
     */
    public String jsFunction_getContentType() {
        Object ret = this.get(CONTENT_TYPE, this);
        if (ret != null) {
            return ret.toString();
        } else {
            return null;
        }
    }
    
    /**
     * Set the file name on this File object.
     * 
     * @param {String} filename The file name to assign to this File object.
     */
    public void jsFunction_setFileName(String filename) {
    	this.put(FILE_NAME, this, filename);
    }
    
    /**
     * Get the extracted textual content from this File.  For example, the text contained
     * in an MS Word document.
     * 
     * @returns {String} The text in the file
     */
    public String jsFunction_getContent() {
        Object ret = this.get(CONTENT, this);
        if (ret != null) {
            return ret.toString();
        } else {
            return null;
        }
    }
    
    /**
     * Get the name of the file (e.g. foo.txt)
     * 
     * @returns {String} The name of the file
     */
    public String jsFunction_getFileName() {
        Object ret = this.get(FILE_NAME, this);
        if (ret != null) {
            return ret.toString();
        } else {
            return null;
        }
    }
    
    /**
     * @deprecated replaced by getPath()
     */
    public String jsFunction_path() {
        return this.jsFunction_getPath();
    }
    
    /**
     * Get the File's path, that is, the location of the binary content of 
     * this File object on the file system.
     * 
     * @returns {String} The File's path
     */
    public String jsFunction_getPath() {
    	if (node != null) {
            checkNode();
            
            String path = this.core.app.getBlobDir();
            
            if (path != null) {
                if (!path.endsWith(File.separator)) {
                    path += File.separator;
                }
                return path + node.getID();
            }
        }
        
        return null;
    }
    
    public String getTmpPath() {
        return this.tmpPath;
    }
    
    public String toString() {
        return "[FileObject]";
    }
    
    /**  
     * Invoked to serve the static binary content of this File from inside Axiom.
     */
    public void jsFunction_main() throws Exception {
        final String path = this.jsFunction_path();
        if (path != null) {
            File f = new File(path);
            if (f.exists() && f.canRead()) {
                try {
                    RequestEvaluator reqeval = this.core.app.getCurrentRequestEvaluator();
                    if (reqeval == null) {
                        throw new RuntimeException("main() on File/Image not being executed "
                                + "inside a request.");
                    }
                    
                    ResponseTrans res = reqeval.getResponse();
                    res.setInputStream(new FileInputStream(f));
                    res.setInputStreamLength((int) f.length());
                    res.setContentType(this.jsFunction_getContentType());
                    String filename = "\"" + this.node.getString(FILE_NAME) + "\"";
                    if (!this.getClassName().equals("Image")) {
                    	res.getServletResponse().setHeader("Content-Disposition", 
                            "attachment; filename=" + filename);
                    }
                    res.setETag(this.node.getID() + "-" + this.node.lastModified());
                    res.setLastModified(this.node.lastModified());
                } catch (Exception ex) {
                    this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "jsFunction_main") 
                    		+ "File/Image object at " + path 
                            + " could not be served ", ex);
                    throw ex;
                } 
            } else {
                throw new RuntimeException("Could not find the static content to serve for " 
                        + this.jsFunction_href(null));
            }
        }
    }
    
}