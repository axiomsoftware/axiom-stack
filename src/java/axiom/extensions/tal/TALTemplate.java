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
package axiom.extensions.tal;

import java.io.*;

import org.w3c.dom.*;

public class TALTemplate {
    // the name of the TAL template file, in the form of prototype:name
    private String name = null;
    // a DOM representation of the TAL template file
    private Document document = null;
    // last time the document representation of the TAL template file was updated
    private long lastModified = 0L;
    
    public TALTemplate(String name) {
        this.name = name;
    }
    
    public TALTemplate(String name, Document document) {
        this.name = name;
        this.document = document;
    }
    
    public TALTemplate(String name, Document document, long lastModified) {
        this.name = name;
        this.document = document;
        this.lastModified = lastModified;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Document getDocument() {
        return document;
    }
    
    public void setDocument(Document document) {
        this.document = document;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
}