package axiom.framework;

public class ErrorReporter {

	public static String errorMsg(Class theClass, String method) {
		return "Error in " + theClass.getName() + "." + method + "(): ";
	}
	
	public static String warningMsg(Class theClass, String method) {
		return "Warning in " + theClass.getName() + "." + method + "(): ";
	}
	
	public static String fatalErrorMsg(Class theClass, String method) {
		return "Fatal Error in " + theClass.getName() + "." + method + "(): ";
	}
	
	public static String ctorErrorMsg(Class theClass) {
		return "Error in " + theClass.getName() + ".ctor: ";
	}
	
}