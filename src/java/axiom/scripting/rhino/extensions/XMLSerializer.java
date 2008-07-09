package axiom.scripting.rhino.extensions;

import java.io.*;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import org.mozilla.javascript.*;
import org.w3c.dom.*;
import com.sun.org.apache.xml.internal.serialize.*;

public class XMLSerializer extends ScriptableObject {
    
    public String getClassName() {
        return "XMLSerializer";
    }
    
    public String toString() {
        return "[XMLSerializer]";
    }
    
    public static XMLSerializer XMLSerializerObjCtor(Context cx, Object[] args, Function ctorObj, boolean inNewExpr) {
        return new axiom.scripting.rhino.extensions.XMLSerializer();
    }
    
    public static void init(Scriptable scope) {
        Method[] methods = XMLSerializer.class.getDeclaredMethods();
        ScriptableObject proto = new XMLSerializer();
        proto.setPrototype(getObjectPrototype(scope));
        Member ctorMember = null;
        
        final int length = methods.length;
        for (int i = 0; i < length; i++) {
            if ("XMLSerializerObjCtor".equals(methods[i].getName())) {
                ctorMember = methods[i];
                break;
            }
        }
        
        FunctionObject ctor = new FunctionObject("XMLSerializer", ctorMember, scope);
        ctor.addAsConstructor(scope, proto);
        String[] szFuncs = { "serialize", "toXMLObject" };
        
        try {
            proto.defineFunctionProperties(szFuncs, XMLSerializer.class, 0);
        } catch (Exception ignore) {
            System.err.println ("Error defining function properties: "+ignore);
        }
    }
    
    public String serialize(Object doc, Object omitXmlDecl) throws Exception {
    	boolean omit = true;
    	if(omitXmlDecl != null){
    		if(omitXmlDecl instanceof Boolean){
    			omit = ((Boolean)omitXmlDecl).booleanValue();
    		}
    	}
    	Object document = null;
    	if(doc != null){
    		if(doc instanceof Document){
    			document = (Document)doc;
    		}
    		else if(doc instanceof NativeJavaObject){
    			document = ((NativeJavaObject)doc).unwrap();
    		}
    	}
    	return serializeDOM((Document)document, omit);
    }
    
    public static String serializeFragment(DocumentFragment fragment) throws Exception {
        OutputFormat format = new OutputFormat();
        format.setOmitXMLDeclaration(true);
        format.setLineSeparator(LineSeparator.Windows);
        format.setIndenting(true);
        format.setIndent(1);
        format.setLineWidth(0);             
        format.setPreserveSpace(true);
        StringWriter outputString = new StringWriter();
        com.sun.org.apache.xml.internal.serialize.XMLSerializer serializer 
            = new com.sun.org.apache.xml.internal.serialize.XMLSerializer(outputString, format);
        serializer.asDOMSerializer().serialize(fragment);
        return outputString.toString();
    }
    
    public static String serializeDOM(Document doc, boolean omitXmlDecl) throws Exception {
        OutputFormat format = new OutputFormat(doc);
        format.setOmitXMLDeclaration(omitXmlDecl);
        format.setLineSeparator(LineSeparator.Windows);
        format.setIndenting(true);
        format.setIndent(1);
        format.setLineWidth(0);             
        format.setPreserveSpace(true);
        StringWriter outputString = new StringWriter();
        com.sun.org.apache.xml.internal.serialize.XMLSerializer serializer 
            = new com.sun.org.apache.xml.internal.serialize.XMLSerializer(outputString, format);
        serializer.asDOMSerializer().serialize(doc);
        return outputString.toString();
    }
    
      public Object toXMLObject(Object doc) throws Exception {
        String xml = null;
        boolean fragment = false;
		
        if(doc instanceof NativeJavaObject){
        	doc = ((NativeJavaObject)doc).unwrap();
        }

        if (doc instanceof org.w3c.dom.Document) {
            xml = serializeDOM((org.w3c.dom.Document) doc, true);
        } else if (doc instanceof org.w3c.dom.DocumentFragment) {
            xml = serializeFragment((org.w3c.dom.DocumentFragment) doc);
            fragment = true;
        }
        
        Object ret = null;
        if (xml != null) {
            String ctor = "XML";
            if (fragment) {
                ctor = "XMLList";
            }
            
            Context cx = Context.getCurrentContext();
            xml = xml.replaceAll("\\\"", "\\\\\"").replaceAll("\n", "").replaceAll("\r", "");
            ret = cx.evaluateString(this.getTopLevelScope(this), 
                                    "new " + ctor + "(\"" + xml+ "\");", 
                                    "XMLSerliazer.toXMLObject()", 1, null); 
        }
        
        return ret;
    }
    
}