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