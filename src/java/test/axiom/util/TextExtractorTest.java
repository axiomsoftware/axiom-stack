package test.axiom.util;


import java.io.FileInputStream;
import java.io.IOException;

import axiom.util.TextExtractor;

import junit.framework.TestCase;

public class TextExtractorTest extends TestCase {
  	public void testGetExcelText()throws Exception{
		String extractedText = null;
		try{
			extractedText = TextExtractor.msExcelExtractor(new FileInputStream("c:\\test.xls"));
		}catch(IOException ioe){
			ioe.printStackTrace();
		}
		System.out.println(extractedText);
		assertNotNull(extractedText);
	}
	
	public void testGetWordText() throws Exception{
		String extractedText = null;
		try{
			extractedText = TextExtractor.msWordExtractor(new FileInputStream("c:\\test.doc"));
		}catch(IOException ioe){
			ioe.printStackTrace();
		}
		System.out.println(extractedText);
		assertNotNull(extractedText);
	}
	
	public void testGetPowerPointText() throws Exception{
		String extractedText = null;
		try{
			extractedText = TextExtractor.msPowerPointExtractor(new FileInputStream("c:\\test.ppt"));
		}catch(IOException ioe){
			ioe.printStackTrace();
		}
		System.out.println(extractedText);
		assertNotNull(extractedText);
	}
	
	public void testGetAdobePDFText() throws Exception{
		String extractedText = null;
		try{
			extractedText = TextExtractor.adobePDFExtractor(new FileInputStream("c:\\test.pdf"));
		}catch(IOException ioe){
			ioe.printStackTrace();
		}
		System.out.println(extractedText);
		assertNotNull(extractedText);
	}
}
