package axiom.scripting.rhino;

import java.io.*;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.mozilla.javascript.*;

import axiom.framework.ErrorReporter;
import axiom.framework.ResponseTrans;
import axiom.framework.core.RequestEvaluator;
import axiom.objectmodel.INode;
import axiom.util.MimePart;
import axiom.util.ResourceProperties;

/**
 * @jsconstructor File
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
    
    public String jsFunction_replaceFile(Object mimepart){
    	return FileObjectCtor.setup(this, getNode(), new Object[]{mimepart}, core.app);
    }
    
    public String jsFunction_getURI(Object action) throws UnsupportedEncodingException, IOException{
    	String result = super.jsFunction_getURI(action);
    	if (result.endsWith("/")) {
    		return result.substring(0, result.length()-1);
    	}
    	return result;
    }
    
    public String jsFunction_getContentType() {
        Object ret = this.get(CONTENT_TYPE, this);
        if (ret != null) {
            return ret.toString();
        } else {
            return null;
        }
    }
    
    public String jsFunction_getContent() {
        Object ret = this.get(CONTENT, this);
        if (ret != null) {
            return ret.toString();
        } else {
            return null;
        }
    }
    
    public String jsFunction_getFileName() {
        Object ret = this.get(FILE_NAME, this);
        if (ret != null) {
            return ret.toString();
        } else {
            return null;
        }
    }
    
    /*public String jsFunction_fileHref() {
        if (node != null) {
            String fileName;
            if ((fileName = node.getString(FILE_NAME)) != null) {
                StringBuffer href = new StringBuffer(this.core.app.getStaticMountpoint());
                return href.append(fileName).toString();
            } else {
                String href = null;
                try {
                    href = (String) super.jsFunction_href(null);
                } catch (Exception ex) {
                    href = null;
                }
                return href;
            }
        }
        
        return null;
    }*/
    
    public String jsFunction_path() {
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
    
    public void jsFunction_setRendered(boolean rendered) {
        String r = rendered ? "true" : "false";
        checkNode();
        this.node.setString(RENDERED_CONTENT, r);
    }
    
    public String getTmpPath() {
        return this.tmpPath;
    }
    
    public String toString() {
        return "[FileObject]";
    }
    
    /**  
     * Default main method for serving static binary content from Axiom
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