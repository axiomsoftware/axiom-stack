package axiom.scripting.rhino;

import java.io.*;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderableImage;

import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;

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
        Object maxw = s.get("maxWidth", s);
        Object maxh = s.get("maxHeight", s);
        if (maxw == null || maxw == Scriptable.NOT_FOUND || maxh == null || maxh == Scriptable.NOT_FOUND) {
            throw new RuntimeException("render(), maxWidth/maxHeight not specified.");
        }
        
        int w = 0, h = 0;
        if (maxw instanceof String) {
            w = Integer.parseInt((String) maxw);
        } else if (maxw instanceof Number) {
            w = ((Number) maxw).intValue();
        } else if (maxw instanceof Scriptable) {
            Scriptable mw = (Scriptable) maxw;
            if (mw.getClassName().equals("Number")) {
                w = (int) ScriptRuntime.toNumber(mw);
            }
        }
        if (maxh instanceof String) {
            h = Integer.parseInt((String) maxh);
        } else if (maxh instanceof Number) {
            h = ((Number) maxh).intValue();
        } else if (maxh instanceof Scriptable) {
            Scriptable mh = (Scriptable) maxh;
            if (mh.getClassName().equals("Number")) {
                h = (int) ScriptRuntime.toNumber(mh);
            }
        }
        
        int cw = (int) node.getInteger(WIDTH);
        int ch = (int) node.getInteger(HEIGHT);

        if (w > 0 && h > 0 && cw > 0 && ch > 0) {
            int[] dims = this.computeResizedDimensions(w, h, cw, ch);
            String aname = dims[0] + "x" + dims[1];
            Object o = null;
            try{
	            o = this.jsFunction_get(aname);
	            if (o instanceof ImageObject) {
	                return (ImageObject) o;
	            }
            } catch(Exception e) {
            	
            }
	            
            try {
                synchronized (this) {
                    while (this.convertOps.contains(aname)) {
                        this.wait();
                    }
                    this.convertOps.add(aname);
                }
                
                try {
                	o = ((axiom.objectmodel.db.Node) this.node).getChildElement(aname, true);
	                if (o instanceof Node) {
	                    return (ImageObject) Context.toObject(o, this.core.global);
	                }
                } catch(Exception e){
                	
                }

                ImageObject computedImg = this.bound(dims[0], dims[1], true);
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
        }
        
        return null;
    }
    
	public ImageObject bound(int w, int h, boolean scaleComputed) {
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
       
        ImageObject scaledImgObj = null;
        try {
            String tmpPath = (String) FileObject.tmpDirs.get(core.app.getName());
            String tmpFileName = FileObjectCtor.generateTmpFileName(fileName, tmpPath);
            if (!tmpPath.endsWith(File.separator)) {
                tmpPath += File.separator;
            }
            tmpPath += tmpFileName;
            tmpPath = FileObjectCtor.normalizePath(tmpPath);
            File tmpFile = new File(tmpPath);
            
            int[] dims = this.resizeImg(w, h, (int) this.node.getInteger(WIDTH), 
                                        (int) this.node.getInteger(HEIGHT), imgPath,
                                        tmpPath, scaleComputed);
            
            if (dims == null) {
                throw new Exception("ImageObject.bound(), resizing the image failed.");
            }
            
            final String protoname = "Image";
            INode node = new axiom.objectmodel.db.Node(protoname, protoname, core.app.getWrappedNodeManager());
            scaledImgObj = new ImageObject("Image", core, node, core.getPrototype(protoname), true);
            
            node.setString(FileObject.FILE_NAME, fileName);
            node.setString(FileObject.ACCESSNAME, FileObjectCtor.generateAccessName(fileName));
            node.setString(FileObject.CONTENT_TYPE, this.node.getString(FileObject.CONTENT_TYPE));
            node.setJavaObject(FileObject.SELF, scaledImgObj);
            node.setInteger(ImageObject.WIDTH, dims[0]);
            node.setInteger(ImageObject.HEIGHT, dims[1]);
            node.setInteger(ImageObject.REQUESTED_WIDTH, w);
            node.setInteger(ImageObject.REQUESTED_HEIGHT, h);
            node.setString(FileObject.RENDERED_CONTENT, "true");
            
            node.setInteger(FileObject.FILE_SIZE, tmpFile.length());
            scaledImgObj.tmpPath = tmpPath;
        } catch (Exception ex) {
        	throw new RuntimeException("ImageObject.jsfunction_bound(): Could not write the image to temporary storage, " + ex.getMessage());
        } 

		return scaledImgObj;
	}
	
    protected int[] resizeImg(int thumbWidth, int thumbHeight, int imageWidth, 
                                int imageHeight, String srcPath, String dstPath, 
                                boolean scaleComputed) {
        if (!scaleComputed) {            
            int[] dims = computeResizedDimensions(thumbWidth, thumbHeight, imageWidth, imageHeight);
            thumbWidth = dims[0];
            thumbHeight = dims[1];
        }
        
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
        
        String cmd = core.app.getProperty("imagemagick");
        if (cmd == null) {
            cmd = "convert";
        } else if (!cmd.endsWith("convert")) {
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
        
        if (exec(command.toString())) {
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
    
	protected PlanarImage resize(int thumbWidth, int thumbHeight, RenderedOp image,
                                    boolean scaleComputed) {
	    if (!scaleComputed) {
	        int imageWidth = image.getWidth();
	        int imageHeight = image.getHeight();
	        
	        int[] dims = computeResizedDimensions(thumbWidth, thumbHeight, imageWidth, imageHeight);
	        thumbWidth = dims[0];
            thumbHeight = dims[1];
	    }
        
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        RenderableImage ren = JAI.createRenderable("renderable", pb);
        pb = new ParameterBlock();
        pb.addSource(ren);
        PlanarImage dst = (PlanarImage)ren.createScaledRendering(thumbWidth, thumbHeight, null);
        ren = null;
        
        return dst;
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