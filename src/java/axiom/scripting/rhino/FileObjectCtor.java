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
import java.util.HashSet;

import javax.activation.MimetypesFileTypeMap;

import org.mozilla.javascript.*;

import eu.medsea.util.MimeUtil;

import axiom.framework.ErrorReporter;
import axiom.framework.core.Application;
import axiom.objectmodel.INode;
import axiom.util.MimePart;
import axiom.util.TextExtractor;


public class FileObjectCtor extends FunctionObject {
    
    RhinoCore core;

    static Method fileObjCtor;

    static {
        try {
            fileObjCtor = FileObjectCtor.class.getMethod("jsConstructor", new Class[] {
                Context.class, Object[].class, Function.class, Boolean.TYPE });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Error getting FileObjectCtor.jsConstructor()");
        }
    }
    
    static HashSet tmpfiles = new HashSet();

    static final int attr = ScriptableObject.DONTENUM  |
                            ScriptableObject.PERMANENT |
                            ScriptableObject.READONLY;
    /**
     * Create and install a AxiomObject constructor.
     * Part of this is copied from o.m.j.FunctionObject.addAsConstructor().
     *
     * @param prototype
     */
    public FileObjectCtor(RhinoCore core, Scriptable prototype) {
        super("File", fileObjCtor, core.global);
        this.core = core;
        addAsConstructor(core.global, prototype);
    }

    /**
     *  This method is used as AxiomObject constructor from JavaScript.
     */
    public static Object jsConstructor(Context cx, Object[] args,
                                       Function ctorObj, boolean inNewExpr)
                         throws Exception {
    	if(args == null || (args != null && args.length == 0)){
    		throw new Exception("Invalid constructor parameters, new File(String|Mimepart, [optional] Boolean)");
    	}

        FileObjectCtor ctor = (FileObjectCtor) ctorObj;
        RhinoCore core = ctor.core;
        String protoname = ctor.getFunctionName();
                
        INode node = new axiom.objectmodel.db.Node("_" + protoname, protoname,
                core.app.getWrappedNodeManager());
        Scriptable proto = core.getPrototype(protoname);

        FileObject fobj = null; 

        if (args != null && args.length > 0 && (args[0] instanceof NativeJavaObject || args[0] instanceof MimePart || args[0] instanceof String)){
            Object obj = args[0];
            if (args[0] instanceof NativeJavaObject) {
                obj = ((NativeJavaObject) args[0]).unwrap();
            }
            if (obj instanceof MimePart || obj instanceof String) {
                fobj = new FileObject("File", core, node, proto, true);
            	boolean extractText = false;
                if(args.length > 1){
                	if(args[1] instanceof Scriptable){
        				Scriptable s = (Scriptable) args[1];
        				String className = s.getClassName();
        				if("Boolean".equals(className)) {
        					extractText = ScriptRuntime.toBoolean(s);
        				}
                	} else if(args[1] instanceof Boolean){
                		extractText = ((Boolean)args[1]).booleanValue();
                	}
                }
                FileObjectCtor.setup(fobj, node, args, core.app, !extractText);
            } else if (args[0] instanceof Scriptable) {
                Scriptable data = (Scriptable) args[0];
                fobj = new FileObject("File", core, node, proto, data);
            }
        } else if (args[0] instanceof Scriptable) {
        	Scriptable data = (Scriptable) args[0];
        	fobj = new FileObject("File", core, node, proto, data);
        }

        return fobj;
    }
    
    protected static String setup(FileObject fobj, INode node, Object[] args, 
    								Application app, boolean extractContent) {
        String ret = null;        
        
        if (args != null && args.length > 0) {
            if (args[0] == null) {
                throw new RuntimeException("FileObjectCtor: first argument cannot be null!");
            }
            
            if (args[0] instanceof NativeJavaObject || args[0] instanceof MimePart) {
                MimePart mp;
                if (args[0] instanceof NativeJavaObject) {
                    mp = (MimePart) ((NativeJavaObject) args[0]).unwrap();
                } else {
                    mp = (MimePart) args[0];
                }
                
                String filename = mp.getName();
                node.setString(FileObject.FILE_NAME, filename);
                node.setString(FileObject.ACCESSNAME, generateAccessName(filename));
                
                final String tmpdir = (String) FileObject.tmpDirs.get(app.getName());
                
                filename = generateTmpFileName(filename, tmpdir);
                
                mp.writeToFile(tmpdir, filename);
                fobj.tmpPath = tmpdir + filename; 
                                
                node.setInteger(FileObject.FILE_SIZE, mp.contentLength);
                String mimetype = mp.contentType;
                if (mimetype == null || mimetype.equals("application/octet-stream")) {
                	mimetype = MimeUtil.getMimeType(new File(fobj.tmpPath));
                	if (mimetype == "application/x-unknown-mime-type") {
                		mimetype = "application/octet-stream";
                	}
                }
                node.setString(FileObject.CONTENT_TYPE, mimetype);
                node.setString(FileObject.RENDERED_CONTENT, "false");
                node.setJavaObject(FileObject.SELF, fobj);
                node.setString(FileObject.FILE_UPLOAD, "true");
                
                String text = extractContent ? extractText(fobj.tmpPath, app) : null;
                if (text != null) {
                    node.setString(FileObject.CONTENT, text);
                }
                
                ret = fobj.tmpPath;
            } else if (args[0] instanceof String) {
                final String path = (String) args[0];
                final File file = new File(path);
                
                if (!file.exists() || !file.isFile()) {
                    throw new RuntimeException("FileObjectCtor: " + path + " does not point to a readable file");
                }
                
                String filename = file.getName();
                node.setString(FileObject.FILE_NAME, filename);
                node.setString(FileObject.ACCESSNAME, FileObjectCtor.generateAccessName(filename));
                
                fobj.tmpPath = file.getAbsolutePath(); 
                                
                node.setInteger(FileObject.FILE_SIZE, file.length());
                String mimetype = guessContentType(file);
                if (mimetype == null || mimetype.equals("application/octet-stream")) {
                	mimetype = MimeUtil.getMimeType(new File(fobj.tmpPath));
                	if (mimetype == "application/x-unknown-mime-type") {
                		mimetype = "application/octet-stream";
                	}
                }
                node.setString(FileObject.CONTENT_TYPE, mimetype);
                node.setString(FileObject.RENDERED_CONTENT, "false");
                node.setJavaObject(FileObject.SELF, fobj);
                node.setString(FileObject.FILE_UPLOAD, "false");
                
                String text = extractContent ? extractText(fobj.tmpPath, app) : null;
                if (text != null) {
                    node.setString(FileObject.CONTENT, text);
                }
                
                ret = fobj.tmpPath;
            } else {
                throw new RuntimeException("FileObjectCtor: first argument to constructor must be the MimePart object or the location of a file");
            }
        } else {
            throw new RuntimeException("FileObjectCtor: must specify arguments to the File constructor!");
        }    

        return ret;
    }
    
    public static void removeTmp(String tmpFile) {
        synchronized (tmpfiles) {
            tmpfiles.remove(tmpFile);
        }
    }
    
    public static String generateTmpFileName(final String filename, final String tmpdir) {
        StringBuffer tmpBuffer = new StringBuffer(tmpdir);
        tmpBuffer.append(Long.toString(System.currentTimeMillis()))
                 .append(".")
                 .append(filename);
        
        int tmpcount = 0;
        String appender = "";
        String tmpFile = tmpBuffer.toString();
        synchronized (tmpfiles) {
            while (tmpfiles.contains(tmpFile + appender)) {
                appender = Integer.toString(tmpcount++);
            }
            tmpfiles.add(tmpBuffer.append(appender).toString());
        }
        
        return tmpBuffer.delete(0, tmpdir.length()).toString().replaceAll(" ", "_");
    }
    
    
    public static String normalizePath(String path) {
        String separator = File.separator;
        String wrong;
        if (separator.equals("\\")) {
            wrong = "/";
        } else {
            wrong = "\\";
        }
        
        StringBuffer pathBuffer = new StringBuffer(path);
        int index = -1;
        while ((index = pathBuffer.indexOf(wrong, index)) > -1) {
            pathBuffer.replace(index, index + 1, separator);
        }
        
        return pathBuffer.toString();
    }
    
    protected static String extractText(String file, Application app) {
        String text = null;
        int index = file.lastIndexOf(File.separator);
  
        if (index > -1) {
            String fileExt = file.substring(index);
            if (fileExt != null) {
                fileExt = fileExt.toLowerCase();
                FileInputStream fis = null;
                
                try {
                    fis = new FileInputStream(file);

                    if (fileExt.indexOf(".pdf") > -1) {
                        text = TextExtractor.adobePDFExtractor(fis);
                    } else if (fileExt.indexOf(".doc") > -1) {
                        text = TextExtractor.msWordExtractor(fis);
                    } else if (fileExt.indexOf(".xls") > -1) {
                        text = TextExtractor.msExcelExtractor(fis);
                    } else if (fileExt.indexOf(".ppt") > -1) {
                        text = TextExtractor.msPowerPointExtractor(fis);
                    } else if (isTextFile(fileExt)) {
                        text = extractText(fis);
                    }
                } catch (Exception ex) {
                    app.logError(ErrorReporter.errorMsg(FileObjectCtor.class, "extractText") 
                    		+ "Failed for the file " + file, ex);
                    text = null;
                } finally {
                    if (fis != null) {
                        try { 
                        	fis.close(); 
                        } catch (Exception ignore) { 
                        	app.logError(ErrorReporter.errorMsg(FileObjectCtor.class, "extractText"), ignore);
                        }
                        fis = null;
                    }
                }
            }
        }
        
        return text;
    }
    
    private static boolean isTextFile(String fileExt) {
        int idx = fileExt.lastIndexOf(".");
        String ext = idx > 0 ? fileExt.substring(idx + 1).trim().toLowerCase() : null;

    	if(ext.equals("txt") || ext.equals("properties") || ext.equals("java") || 
    			ext.equals("html") || ext.equals("xml") || ext.equals("css")){
    		return true;
    	} else {
    		return false;
    	}

    }
    
    private static String extractText(FileInputStream fis) {
        String str = null, line = null;
        
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(fis.getFD()));
            StringBuffer sb = new StringBuffer();
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            str = sb.toString();
        } catch (IOException ioex) {
            str = null;
        } finally {
            if (br != null) {
                try { br.close(); } catch (IOException ignore) { }
                br = null;
            }
        }
        
        return str;
    }
    
    protected static String generateAccessName(String filename) {
        String accessName = filename;
        int indexOfExt;
        if ((indexOfExt = accessName.lastIndexOf(".")) > 0) {
            accessName = accessName.substring(0, indexOfExt);
        }
        return accessName;
    }
    
    protected static String guessContentType(File file) {
        String name = file.getName();
        int idx = name.lastIndexOf(".");
        String ext = null;
        if (idx > 0) {
            ext = name.substring(idx + 1).trim().toLowerCase();
        }
        
        if (ext != null) {
            if ("doc".equals(ext) || "dot".equals(ext)) {
                return "application/msword";
            } else if ("pdf".equals(ext)) {
                return "application/pdf";
            } else if ("xls".equals(ext)) { 
                return "application/excel";
            } else if ("ppt".equals(ext)) {
                return "application/ppt";
            } else if ("rtf".equals(ext)) {
                return "application/rtf";
            } else if ("ps".equals(ext) || "ai".equals(ext) || "eps".equals(ext)) {
                return "application/postscript";
            }
        }
        
        return new MimetypesFileTypeMap().getContentType(file);
    }
    
}