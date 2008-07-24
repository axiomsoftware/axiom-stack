package axiom.util;

import com.sun.javadoc.*;
import java.io.*;
import java.util.*;

public class JSDoclet extends Doclet {
	static StringBuffer buffer = new StringBuffer();
	static final String JSFUNCTION = "jsFunction_";
	static final String JSPROPERTY = "jsGet_";
	
	public static boolean start(RootDoc root){
		for(ClassDoc clazz : root.classes()){
			Tag[] jsInstanceTags = clazz.tags("@jsinstance");
			Tag[] jsConstructorTags = clazz.tags("@jsconstructor");
			if(jsInstanceTags.length != 0 || jsConstructorTags.length != 0){
				String className = clazz.name();
				boolean isInstance = false;
				if(jsInstanceTags.length > 0){
					className = jsInstanceTags[0].text();
					isInstance = true;
				} else if (jsConstructorTags.length > 0 && jsConstructorTags[0].text() != ""){
					className = jsConstructorTags[0].text();
				}
				
				ArrayList<String> commentTags = new ArrayList<String>();
				commentTags.add(clazz.commentText());
				commentTags.add("@constructor");
				for(Tag t: clazz.tags()){
					commentTags.add(t.name()+ " " +t.text());
				}
				emitComment(commentTags.toArray());
				emit("function "+className+"(){}");

				for(MethodDoc method: clazz.methods()){
					ArrayList<String> methodTags = new ArrayList<String>();
					methodTags.add(method.commentText());
					for(Tag t: method.tags()){
						methodTags.add(t.name()+ " " +t.text());
					}
					if(method.tags("@returns").length == 0){
						methodTags.add("@returns "+method.returnType());
					}
					String jsForm = className+(method.isStatic()?".":".prototype.");
					String name = method.name();
					
					Tag[] jsfunctionTags = method.tags("@jsfunction");
					Tag[] jspropertyTags = method.tags("@jsproperty");
					Tag[] jsomitTags = method.tags("@jsomit");
					if(jspropertyTags.length > 0 || (name.startsWith(JSPROPERTY) && jsomitTags.length == 0) 
							|| (isInstance && name.startsWith("get") && jsfunctionTags.length == 0 && jsomitTags.length == 0 && name.length() > 4)){
						if(jspropertyTags.length > 0){
							name = jspropertyTags[0].text();
						} else if (name.startsWith(JSPROPERTY)) { 
							name = name.substring(JSPROPERTY.length());
						} else{
							name = (name.charAt(3)+"").toLowerCase() + name.substring(4);
						}
						emitComment(methodTags.toArray());
						emit(jsForm+name+" = {};");
					} else {
						if(jsfunctionTags.length > 0 && jsfunctionTags[0].text().length() > 0){
							name = jsfunctionTags[0].text();
						} else if(!isInstance){
							Tag[] deprecatedTags = method.tags("@deprecated");
							if(name.startsWith(JSFUNCTION) && deprecatedTags.length == 0){
								name = name.substring(JSFUNCTION.length());
							} else if(jsfunctionTags.length == 0){
								continue;
							}
						} 
						emitComment(methodTags.toArray());
						emit(jsForm+name+" = function("+inlineParams(method.parameters())+"){};");
					}
				}
			}
		}
		emitComment(new String[]{"axiom utilities namespace", "@constructor"});
		emit("function axiom(){};");
		writeToFile("alldoc.js");
		return true;
	}

	public static String inlineParams(Parameter[] params){
		String args = "";
		for(int i =0; i< params.length; i++){
			Parameter p = params[i];
			args += "/**"+p.typeName()+"*/"+ p.name();
			if(i+1 != params.length){
				args += ",";
			}		
		}
		return args;
	}
	
	public static void emitComment(Object[] lines){
		emit("/**");
		for(Object line: lines){
			emit( " * "+line);
		}
		emit(" */");
	}

	public static void writeToFile(String filename){
		FileWriter writer = null;
		try{
			writer = new FileWriter(new File(filename));
			writer.write(buffer.toString());
		} catch(IOException e){
			e.printStackTrace();
		} finally{
			if(writer != null){
				try{
					writer.close();
				} catch(IOException e){
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void emit(String str){
		buffer.append(str+"\n");
	}
}
