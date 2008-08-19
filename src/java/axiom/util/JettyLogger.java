package axiom.util;

public class JettyLogger implements org.mortbay.log.Logger{
	
	private boolean debug;
	private String name;
	
	public JettyLogger(){
		this(null);
	}
	    
	public JettyLogger(String name){    
		this.name = name == null ? "" : name;
	}
	
	public void debug(String msg, Object arg0, Object arg1){
 	
	}
 
 	public void debug(String msg, Throwable th){
	 
 	}
            
 	public org.mortbay.log.Logger getLogger(String name){
 		return new JettyLogger(name);	
 	}
            
 	public void info(String msg, Object arg0, Object arg1){
	 
 	}
            
 	public boolean isDebugEnabled(){
 		return debug;
 	}
            
 	public void setDebugEnabled(boolean enabled){
 		debug = enabled;
	 
 	}

 	public void warn(String msg, Object arg0, Object arg1){
	 
 	}
            
	public void warn(String msg, Throwable th){
	 
	}


}
