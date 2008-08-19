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
package axiom.util;

import java.io.InputStream;
import java.util.Iterator;

import org.apache.poi.hslf.extractor.QuickButCruddyTextExtractor;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.pdfbox.pdmodel.PDDocument;
import org.pdfbox.util.PDFTextStripper;

public class TextExtractor {
    
	public static String msExcelExtractor(InputStream is) throws Exception {
		POIFSFileSystem fs = new POIFSFileSystem(is);
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		StringBuffer sb = new StringBuffer();
		
		final int numSheets = wb.getNumberOfSheets();
		for (int k = 0; k < numSheets; k++) {
		    HSSFSheet sheet = wb.getSheetAt(k);
		    Iterator rIt = sheet.rowIterator();
		    while (rIt.hasNext()) {
		        HSSFRow row = (HSSFRow) rIt.next();
		        Iterator cIt = row.cellIterator();
		        while (cIt.hasNext()) {
		            HSSFCell cell  = (HSSFCell) cIt.next();
		            sb.append(cell.toString()).append(" ");
		        }
		    }
		}
        
		return sb.toString();
	}

	public static String msWordExtractor(InputStream is) throws Exception {
	    WordExtractor we = new WordExtractor(is);
	    return we.getText();
	}

	public static String msPowerPointExtractor(InputStream is) throws Exception{
	    QuickButCruddyTextExtractor qbcte = new QuickButCruddyTextExtractor(is);
	    return qbcte.getTextAsString();
	}

	public static String adobePDFExtractor(InputStream is) throws Exception {
        PDDocument doc = null;
        String pdfStr = null;
        try {
            doc = PDDocument.load(is);
            if (doc.isEncrypted()) {
            	//not sure how to handle encrypted
            	//unaccessable pdf document
            	pdfStr = null;
            } else {
            	PDFTextStripper stripper = new PDFTextStripper();
            	pdfStr = stripper.getText(doc);
            }
        } finally {
            if (doc != null) {
                doc.close();
                doc = null;
            }
        }
        
        return pdfStr;
    }
    
}