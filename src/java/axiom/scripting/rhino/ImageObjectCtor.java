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

import java.io.IOException;
import java.lang.reflect.Method;

import javax.media.jai.JAI;
import javax.media.jai.OperationDescriptor;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;

import org.mozilla.javascript.*;

import axiom.objectmodel.INode;
import axiom.util.MimePart;

import com.sun.media.jai.codec.FileSeekableStream;
import com.sun.media.jai.codec.ImageCodec;


public class ImageObjectCtor extends FunctionObject {
    
    RhinoCore core;

    static Method imageObjCtor;

    static {
        try {
            imageObjCtor = ImageObjectCtor.class.getMethod("jsConstructor", new Class[] {
                Context.class, Object[].class, Function.class, Boolean.TYPE });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Error getting ImageObjectCtor.jsConstructor()");
        }
    }

    static final int attr = ScriptableObject.DONTENUM  |
                            ScriptableObject.PERMANENT |
                            ScriptableObject.READONLY;
    /**
     * Create and install a AxiomObject constructor.
     * Part of this is copied from o.m.j.FunctionObject.addAsConstructor().
     *
     * @param prototype
     */
    public ImageObjectCtor(RhinoCore core, Scriptable prototype) {
        super("Image", imageObjCtor, core.global);
        this.core = core;
        addAsConstructor(core.global, prototype);
    }

    /**
     *  This method is used as AxiomObject constructor from JavaScript.
     */
    public static Object jsConstructor(Context cx, Object[] args,
                                       Function ctorObj, boolean inNewExpr)
                         throws Exception {
        ImageObjectCtor ctor = (ImageObjectCtor) ctorObj;
        RhinoCore core = ctor.core;
        String protoname = ctor.getFunctionName();
                
        INode node = new axiom.objectmodel.db.Node("_" + protoname, protoname,
                core.app.getWrappedNodeManager());
        Scriptable proto = core.getPrototype(protoname);
        
        ImageObject iobj = null;
        if (args != null && args.length > 0 && (args[0] instanceof NativeJavaObject || args[0] instanceof MimePart || args[0] instanceof String)){
            Object obj = args[0];
            if (args[0] instanceof NativeJavaObject) {
            	obj = ((NativeJavaObject) args[0]).unwrap();
            }
            if (obj instanceof MimePart || obj instanceof String) {
                iobj = new ImageObject("Image", core, node, proto, false);
                ImageObjectCtor.imageGen(iobj, node, FileObjectCtor.setup(iobj, node, args, core.app, false), core);
                iobj.setupDefaultProperties();
            } else if (args[0] instanceof Scriptable) {
                Scriptable data = (Scriptable) args[0];
                iobj = new ImageObject("Image", core, node, proto, data);               
            }
        } else if(args[0] instanceof Scriptable){
        	Scriptable data = (Scriptable)args[0];
        	iobj = new ImageObject("Image", core, node, proto, data);        		
        }
        return iobj;
    }
    
    static void imageGen(ImageObject iobj, INode node, String filename, RhinoCore core) {
        FileSeekableStream fss = null;
        try {
            fss = new FileSeekableStream(filename);
            RenderedOp source = JAI.create("stream", fss);
            node.setInteger(ImageObject.WIDTH, source.getWidth());
            node.setInteger(ImageObject.HEIGHT, source.getHeight());
            source.dispose();
            source = null;
        } catch (Exception ex) {
            throw new RuntimeException("ImageObjectCtor.imageGen(): Could not determine image's width/height");
        } finally {
            if (fss != null) {
                try { fss.close(); } catch (IOException ioex) { }
                fss = null;
            }
        }
    }
    
}