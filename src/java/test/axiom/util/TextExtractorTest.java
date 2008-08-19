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
