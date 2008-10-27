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

import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.FileLoadDescriptor;
import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.renderable.ParameterBlock;

import org.mozilla.javascript.*;

import axiom.framework.ErrorReporter;
import axiom.objectmodel.INode;

/**
 * Image is a built-in prototype that extends File. It is instantiated by passing either 
 * a MimePart Object (as submitted via a multipart/formdata form post) or File System path 
 * into the constructor.  The binary contents of the Image object are stored
 * in the application's blob directory.
 * 
 * @jsconstructor Image
 * @param {MimePart|String} object Object to build File object from
 * @param {Boolean} [extractText] Turns off text extraction 
 */
public class ImageObject extends FileObject {

    HashSet convertOps = new HashSet();
    
	static final String WIDTH = "_width";
	static final String HEIGHT = "_height";
	static final String REQUESTED_WIDTH = "_original_width";
	static final String REQUESTED_HEIGHT = "_original_height";
    
    public ImageObject(String className, RhinoCore core) {
        super(className, core);
    }

    public ImageObject(String className, RhinoCore core, INode node, Scriptable scope) {
        super(className, core, node, scope);
    }
    
    public ImageObject(String className, RhinoCore core, INode node, Scriptable scope,
            boolean setupdefault) {
        super(className, core, node, scope, setupdefault);
    }

    public ImageObject(String className, RhinoCore core, INode node, Scriptable scope,
            Scriptable data) {
        super(className, core, node, scope, data);
    }

    public static ImageObject initImageProto(RhinoCore core) throws PropertyException {

        int attributes = READONLY | DONTENUM | PERMANENT;

        // create prototype object
        ImageObject proto = new ImageObject("Image", core);
        proto.setPrototype(getObjectPrototype(core.global));
        
        Method[] methods = ImageObject.class.getMethods();
        for (int i = 0; i< methods.length; i++) {
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
        
        return proto;
    }
    
    public String toString() {
        return "[ImageObject]";
    }
    
    /**
     * Returns the width of the image represented by this Image object.
     * 
     * @returns {Number} The width
     */
    public int jsFunction_getWidth() {
        return getDimension(WIDTH);
    }
    
    /**
     * Returns the height of the image represented by this Image object.
     * 
     * @returns {Number} The height
     */
    public int jsFunction_getHeight() {
        return getDimension(HEIGHT);
    }
    
    private int getDimension(String dim) {
        Object ret = this.get(dim, this);
        int v = 0;

        if (ret != null) {
            if (ret instanceof Number) {
                try {
                    v = ((Number) ret).intValue();
                } catch (Exception ex) {
                    v = 0;
                }
            } else if (ret instanceof String) {
                try {
                    v = Integer.parseInt(ret.toString());
                } catch (Exception ex) {
                    v = 0;
                }
            }
        }
        
        return v;
    }

    /**
     * Adds another Image object as a thumbnail (child) of this Image object, which can be 
     * retrieved using <code> getThumbnail(accessname) </code>.
     * 
     * @param {Image} child The Image object to add as a child of this Image object 
     * @param {String} [accessname] The accessname by which the child Image object may be
     *                              retrieved by calling getThumbnail(accessname).  If 
     *                              this argument is left unspecified, defaults to a String
     *                              "[width]x[height]". So if the width is 200 and the 
     *                              height is 100, the default accessname will be 200x100
     * @returns {Boolean} Whether the operation was a success or not.  If there is already
     *                    an Image object with the same name as accessname located as a
     *                    child of this Image object, then it will fail and return <code> false </code>
     */
    public boolean jsFunction_addThumbnail(Object child, Object accessname) {
        if (child instanceof ImageObject) {
            ImageObject th = (ImageObject) child;

            StringBuffer sb = new StringBuffer();
            if (accessname != null && accessname != Scriptable.NOT_FOUND) {
                sb.append((String) accessname);
            } else {
                sb.append(th.getDimension(WIDTH))
                  .append("x")
                  .append(th.getDimension(HEIGHT));
            }
            
            th.node.setString(FileObject.ACCESSNAME, sb.toString());
            return super.jsFunction_add(th);
        }
        return false;
    }
    
    /**
     * Retrieves the thumbnail (child) from this Image object's thumbnail collection.
     * Thumbnails are added using <code> addThumbnail(child, accessname) </code>.
     * 
     * @param {String} accessname The accessname with which the desired Image object was stored
     * @returns {Image} The requested thumbnail Image object
     */
    public Object jsFunction_getThumbnail(Object accessname) {
        return super.jsFunction_get(accessname);
    }
    
    /**
     * Remove the specified thumbnail (child) from this Image object's children.
     * 
     * @param {Image} child The Image object to remove
     * @returns {Boolean} Whether the removal was a success or not
     * @throws Exception
     */
    public boolean jsFunction_removeThumbnail(Object child) throws Exception{
        return super.jsFunction_remove(child);
    }
    
    /**
     * Take an object from the scripting layer and convert it to an int.
     * @param obj
     * @return int 
     */
    private int toInt(Object obj){
        int result = 0;
    	if (obj instanceof String) {
            result = Integer.parseInt((String) obj);
        } else if (obj instanceof Number) {
            result = ((Number) obj).intValue();
        } else if (obj instanceof Scriptable) {
            Scriptable sobj = (Scriptable) obj;
            if (sobj.getClassName().equals("Number")) {
                result = (int) ScriptRuntime.toNumber(sobj);
            }
        }
    	return result;
    }
    
    /**
     * Creates and returns a new Image object that is a rendering of this Image object.
     * The input object defines the rendering parameters of the new Image object.  
     * Imagemagick's convert program is used to create a scaled bounding box of 
     * this Image with the given dimensions.
     * 
     * @param {Object} input A JavaScript object specifying the rendering parameters.
     *                       For example, <code> {maxWidth:200, maxHeight:100} </code>
     * @returns {Image} The rendered Image object
     * @throws Exception
     */
    public ImageObject jsFunction_render(Object input) throws Exception {
        if (input == null || !(input instanceof Scriptable)) {
            throw new RuntimeException("The first argument to render() must be a scriptable object.");
        }
        
        Scriptable s = (Scriptable) input;
        int maxWidth = toInt(s.get("maxWidth", s));
        int maxHeight = toInt(s.get("maxHeight", s));
        
        int cropWidth = toInt(s.get("cropWidth", s));
        int cropHeight = toInt(s.get("cropHeight", s));
        int cropXOffset = toInt(s.get("cropXOffset", s));
        int cropYOffset = toInt(s.get("cropYOffset", s));

        int currentWidth = (int) node.getInteger(WIDTH);
        int currentHeight = (int) node.getInteger(HEIGHT);
        
        String aname = null;
        if (maxWidth > 0 && maxHeight > 0 && currentWidth > 0 && currentHeight > 0) {
            int[] dims = this.computeResizedDimensions(maxWidth, maxHeight, currentWidth, currentHeight);
            aname = dims[0] + "x" + dims[1];
            maxWidth = dims[0];
            maxHeight = dims[1];
        } else if(cropWidth > 0 && cropHeight > 0 ){
        	aname = cropWidth + "x" + cropHeight+"_"+cropXOffset+"x"+cropYOffset;
        } else {
        	throw new RuntimeException("render(), invalid parameter set.");
        }

        Object o = this.jsFunction_get(aname);
        if (o instanceof ImageObject) {
        	return (ImageObject) o;
        }
	            
        try {
        	synchronized (this) {
        		while (this.convertOps.contains(aname)) {
        			this.wait();
        		}
        		this.convertOps.add(aname);
        	}

        	o = ((axiom.objectmodel.db.Node) this.node).getChildElement(aname, true);
        	if (o instanceof Node) {
        		return (ImageObject) Context.toObject(o, this.core.global);
        	}

        	ImageObject computedImg = null; 
        	String[] paths = getPaths();
    		String imgPath = paths[0];
    		String tmpPath = paths[1];
    		String fileName = node.getString(FileObject.FILE_NAME);

    		try{
                File tmpFile = new File(tmpPath);
                int[] dims = null;
                if(maxWidth > 0 && maxHeight > 0){
                	dims = this.resize(maxWidth, maxHeight, (int) this.node.getInteger(WIDTH), 
                								(int) this.node.getInteger(HEIGHT), imgPath,
                								tmpPath, true);
                } else {
            		dims = this.crop(cropWidth, cropHeight, cropXOffset, cropYOffset, imgPath, tmpPath);
            	}
                
                if (dims == null) {
                    throw new Exception("ImageObject.render(), resizing the image failed.");
                }
                
                final String protoname = "Image";
                INode node = new axiom.objectmodel.db.Node(protoname, protoname, core.app.getWrappedNodeManager());
                computedImg = new ImageObject("Image", core, node, core.getPrototype(protoname), true);
                
                node.setString(FileObject.FILE_NAME, fileName);
                node.setString(FileObject.ACCESSNAME, FileObjectCtor.generateAccessName(fileName));
                node.setString(FileObject.CONTENT_TYPE, this.node.getString(FileObject.CONTENT_TYPE));
                node.setJavaObject(FileObject.SELF, computedImg);
                node.setInteger(ImageObject.WIDTH, dims[0]);
                node.setInteger(ImageObject.HEIGHT, dims[1]);
                node.setString(FileObject.RENDERED_CONTENT, "true");
                
                node.setInteger(FileObject.FILE_SIZE, tmpFile.length());
                computedImg.tmpPath = tmpPath;
            } catch (Exception ex) {
            	throw new RuntimeException("ImageObject.jsfunction_bound(): Could not write the image to temporary storage, " + ex.getMessage());
            } 

        	if (computedImg != null) {
        		this.jsFunction_addThumbnail(computedImg, null);
        		return computedImg;
        	}
        } finally {
        	synchronized (this) {
        		this.convertOps.remove(aname);
        		this.notifyAll();
        	}
        }
        
        return null;
    }
    
    private String[] getPaths(){
    	String fileName = node.getString(FileObject.FILE_NAME);
		String imgPath = null;
		Object tmpObj = node.get(FileObject.SELF);
		if (tmpObj != null) {
			FileObject fileObj = (FileObject)node.get(FileObject.SELF).getJavaObjectValue();
			imgPath = fileObj.tmpPath;
		} 

        if (imgPath == null || !(new File(imgPath).exists())) {
			String repos = this.core.app.getBlobDir();
            
            repos = repos.trim();
            if (!repos.endsWith(File.separator)) {
                repos += File.separator;
            }
			imgPath = repos + node.getID();
        }
		imgPath = FileObjectCtor.normalizePath(imgPath);
        
        String tmpPath = (String) FileObject.tmpDirs.get(core.app.getName());
        String tmpFileName = FileObjectCtor.generateTmpFileName(fileName, tmpPath);
        if (!tmpPath.endsWith(File.separator)) {
        	tmpPath += File.separator;
        }
        tmpPath += tmpFileName;
        tmpPath = FileObjectCtor.normalizePath(tmpPath);
    	
        return new String[]{imgPath, tmpPath};
    }
    
	protected String[] escapePaths(String srcPath, String dstPath){
        srcPath = srcPath.trim();
        dstPath = dstPath.trim();
        String os = System.getProperty("os.name").toLowerCase();
        if (os != null && os.indexOf("windows") > -1) {
            srcPath = "\"" + srcPath + "\"";
            dstPath = "\"" + dstPath + "\"";
        } else {
            int lastidx = -1, curridx;
            StringBuffer buff = new StringBuffer(srcPath);
            while ((curridx = buff.indexOf(" ", lastidx)) > 0) {
                buff.insert(curridx, "\\");
                lastidx = curridx + 2;
            }
            srcPath = buff.toString();
            buff = new StringBuffer(dstPath);
            lastidx = -1;
            while ((curridx = buff.indexOf(" ", lastidx)) > 0) {
                buff.insert(curridx, "\\");
                lastidx = curridx + 2;
            }
            dstPath = buff.toString();
        }
        return new String[]{srcPath, dstPath};
	}

	protected int[] crop(int width, int height, int offsetx, int offsety, String srcPath, String dstPath){
        String[] paths = escapePaths(srcPath, dstPath);
        srcPath = paths[0];
        dstPath = paths[1];
        boolean success = false;	
        
        String cmd = core.app.getProperty("imagemagick");
        if(cmd == null){
        	ParameterBlock args = new ParameterBlock();
        	args.addSource(FileLoadDescriptor.create(srcPath, null, null, null));
        	args.add((float)offsetx);
        	args.add((float)offsety);
        	args.add((float)width);
        	args.add((float)height);
        	RenderedOp croppedImage = JAI.create("crop", args);
            String type = dstPath.substring(dstPath.lastIndexOf(".")+1);
            success = writeImage(dstPath, type, croppedImage);
        } else {
        	 if (!cmd.endsWith("convert")) {
        		 if (!cmd.endsWith(File.separator)) {
        			 cmd += File.separator;
        		 }
        		 cmd += "convert";
        	 }


	        StringBuffer command = new StringBuffer(cmd);
	        command.append(" -crop ");
	        command.append(width).append("x").append(height).append("+").append(offsetx).append("+").append(offsety).append("!");
	        command.append(" ").append(srcPath);
	        command.append(" ").append(dstPath);
	        success = exec(command.toString());
    	}
    
        if (success) {
            return new int[] { width, height };
        }
        
        return null;
	}
	
	protected boolean writeImage(String dstPath, String type, RenderedOp image){
		boolean success = false;
		//		JAI doesn't natively support GIF encoding, but Java ImageIO does.
        if (type.toLowerCase().equals("gif")) {
        	File dst = new File(dstPath);
    		//if the file doesn't exist, create it and make sure we can write to it.
            if (!dst.exists()) {
            	try {
            		dst.createNewFile();
            	} catch (IOException ioe) {
                    this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "resizeImg") + "Failed to create tmp img at \""+dstPath+"\".", ioe);
            	}
            }
            if (!dst.canWrite()) {
            	dst.setWritable(true);
            }
            
            try {
                ImageIO.write(image, type.toUpperCase(), dst);
             } catch (IOException ioe) {
                 this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "resizeImg") + "Failed to write image data to \""+dstPath+"\".", ioe);
             }
             
             //clean up file handle
             dst = null;
        } else {
        	if (type.toLowerCase().equals("jpg")) {
        		type = "jpeg";
        	}
        	
        	JAI.create("filestore", image, dstPath, type);
        }

        if (image != null && new File(dstPath).exists()) {
        	success = true;
        }

        //JAI Cleanup
        image.dispose();
        image = null;
        type = null;
		
		return success;
	}
	
    protected int[] resize(int thumbWidth, int thumbHeight, int imageWidth, 
                                int imageHeight, String srcPath, String dstPath, 
                                boolean scaleComputed) {
        if (!scaleComputed) {            
            int[] dims = computeResizedDimensions(thumbWidth, thumbHeight, imageWidth, imageHeight);
            thumbWidth = dims[0];
            thumbHeight = dims[1];
        }
        
        String[] paths = escapePaths(srcPath, dstPath);
        srcPath = paths[0];
        dstPath = paths[1];
        
        String cmd = core.app.getProperty("imagemagick");
        boolean success = false;
        
        if (cmd == null) {
        	RenderedOp resizedImage = this.resizeJAI(thumbWidth, thumbHeight, FileLoadDescriptor.create(srcPath, null, null, null), false);	//Don't like the pre-calculated scaling. Recalculate it.
            String type = dstPath.substring(dstPath.lastIndexOf(".")+1);
            success = writeImage(dstPath, type, resizedImage);
         } else {
        	 if (!cmd.endsWith("convert")) {
        		 if (!cmd.endsWith(File.separator)) {
        			 cmd += File.separator;
        		 }
        		 cmd += "convert";
        	 }

	        StringBuffer command = new StringBuffer(cmd);
	        command.append(" -geometry ");
	        command.append(thumbWidth).append("x").append(thumbHeight);
	        command.append(" ").append(srcPath);
	        command.append(" ").append(dstPath);
	        success = exec(command.toString());
    	}
    
        if (success) {
            return new int[] { thumbWidth, thumbHeight };
        }
        
        return null;
    }
    
    private boolean exec(String command) {
        Process proc = null;
        Runtime rt = Runtime.getRuntime();
        try {
        	proc = rt.exec(command);
        } catch (IOException e) {
            this.core.app.logError(ErrorReporter.errorMsg(this.getClass(), "exec") + "Failed on command " + command, e);
            return false;
        }

        int exitStatus;        
        
        while (true) {
            try {
            	exitStatus = proc.waitFor();
                break;
            } catch (java.lang.InterruptedException e) {
            } 
        }
    
        rt.gc(); /** GARBAGE COLLECT, image magick takes up memory! */
        
        return (exitStatus == 0);
    }
	
	protected RenderedOp resizeJAI(int thumbWidth, int thumbHeight, RenderedOp image, boolean scaleComputed) {
		double scale = 1.0;
		if (!scaleComputed) {
			int imageWidth = image.getWidth();
			int imageHeight = image.getHeight();
		
			scale = computeEvenResizeScale(thumbWidth, thumbHeight, imageWidth, imageHeight);
		}
		
		RenderingHints qualityHints = new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		return JAI.create("SubsampleAverage", image, scale, scale, qualityHints);
	}
	
	protected double computeEvenResizeScale(int nw, int nh, int iw, int ih) {
		double[] scales = this.computeResizeScale(nw, nh, iw, ih);
		if (scales[0] > scales[1]) {
			return scales[0];
		} else {
			return scales[1];
		}
	}

	protected double[] computeResizeScale(int nw, int nh, int iw, int ih) {
		double widthScale = (double) nw / (double) iw;
		double heightScale = (double) nh / (double) ih;
		
		return new double[] { widthScale, heightScale };
	}

	protected int[] computeResizedDimensions(int nw, int nh, int iw, int ih) {
	    int[] dims = new int[2];

	    // this code keeps thumbnail ratio same as original image
	    double thumbRatio = (double) nw / (double) nh;  
	    double imageRatio = (double) iw / (double) ih;
	    if (iw < nw && ih < nh) {
	        dims[0] = iw;
	        dims[1] = ih;
	    } else if (thumbRatio < imageRatio) {
	        dims[0] = nw;
	        dims[1] = (int) Math.ceil(nw / imageRatio);
	    } else {
	        dims[0] = (int) Math.ceil(nh * imageRatio);
	        dims[1] = nh;
	    }

	    return dims;
	}

	/**
     * Replace the binary image contents represented in this Image object with the input 
     * MimePart object (as submitted via a multipart/formdata form post) or the input
     * file system path.
     * 
     * @param {MimePart|String} file The MimePart object or the path on the file system to
     *                               replace the binary contents of this Image object with
     */
    public void jsFunction_replaceFile(Object mimepart){
    	String path = FileObjectCtor.setup(this, getNode(), new Object[]{mimepart}, core.app, false);
        ImageObjectCtor.imageGen(this, getNode(), path, core);
    }
    
    /**
     * Sets whether this Image object was rendered from another Image object or not,
     * that is, if it was the result of invoking <code> Image.render()</code>.
     * 
     * @param {Boolean} rendered <code> True </code> if it was rendered, <code> false </code> otherwise
     */
    public void jsFunction_setRendered(boolean rendered) {
        String r = rendered ? "true" : "false";
        checkNode();
        this.node.setString(RENDERED_CONTENT, r);
    }
    
}