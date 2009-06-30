package axiom.util;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnicodeUtils {	
	public static HashMap<String, String> getControlCodeMap(boolean as_key) {
		final int[] EXTRA_CONTROL_CODES = { 0x0007f, 0xfff9, 0xfffa, 0xfffb, 0xfffe, 0xffff, 0x2028, 0x2029, 0x200e, 0x200f,
				0x202a,	0x202b,	0x202c, 0x202d,	0x202e};
		
		HashMap<String, String> ccmap = new HashMap<String, String>();
		for (int i = 0x0000; i < 0x0020; i++) {
			String to = i + "";
			for (int j = (4-to.length()); j > 0; j--) {
				to = "0" + to;
			}
			String key;
			String value;
			if (as_key) {
				key = (char)i+"";
				value = "\\\\u"+to;
			} else {
				key = "\\\\u"+to;
				value = (char)i+"";
			}
			ccmap.put(key,value);
		}
		
		for (int i = 0x0080; i < 0x009f; i++) {
			String to = i + "";
			for (int j = (4-to.length()); j > 0; j--) {
				to = "0" + to;
			}

			String key;
			String value;
			if (as_key) {
				key = (char)i+"";
				value = "\\\\u"+to;
			} else {
				key = "\\\\u"+to;
				value = (char)i+"";
			}
			ccmap.put(key,value);
		}

		for (int i : EXTRA_CONTROL_CODES) {
			String to = i + "";
			for (int j = (4-to.length()); j > 0; j--) {
				to = "0" + to;
			}

			String key;
			String value;
			if (as_key) {
				key = (char)i+"";
				value = "\\\\u"+to;
			} else {
				key = "\\\\u"+to;
				value = (char)i+"";
			}
			ccmap.put(key,value);
		}
		
		
		return ccmap;
	}
	
	public static String escapeControlCodes(String str) {
		return escapeControlCodes(str, getControlCodeMap(true));
	}

    public static String escapeControlCodes(String str, HashMap<String, String> codepoints) {
    	String pattern_str = "";
    	for (String key : codepoints.keySet()) {
    		pattern_str += ((pattern_str == "")?"":"|") + "(["+key+"])";
    	}
    	
    	Pattern pattern = Pattern.compile(pattern_str);
    	Matcher matcher = pattern.matcher(str);
    	while(matcher.find()) {
    		String group = matcher.group();
    		String ucodepoint = codepoints.get(group);
    		if (group == null) {
    			ucodepoint = "\\\\u0000";
    		}
    		str = matcher.replaceAll(ucodepoint);
    	}
    	
    	return str;
    }
    
	public static String unescapeControlCodes(String str) {
		return unescapeControlCodes(str, getControlCodeMap(true));
	}

    public static String unescapeControlCodes(String str, HashMap<String, String> codepoints) {
    	String pattern_str = "";
    	for (String key : codepoints.keySet()) {
    		pattern_str += ((pattern_str == "")?"":"|") + "("+key+")";
    	}
    	
    	Pattern pattern = Pattern.compile(pattern_str);
    	Matcher matcher = pattern.matcher(str);
    	while(matcher.find()) {
    		String group = matcher.group();
    		String ucodepoint = codepoints.get("\\"+group);
    		if (group == null) {
    			ucodepoint = "\\u0000";
    		}
    		try {
    			str = matcher.replaceAll(ucodepoint);
    		} catch(NullPointerException npe) {
    	    	System.out.println("pattern_str: " + pattern_str);
    			System.out.println("group: " + group);
    			System.out.println("ucodepoint: " + ucodepoint);
    			npe.printStackTrace();
    		}
    	}
    	
    	return str;
    }
}
