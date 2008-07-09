package axiom.scripting.rhino;


import org.apache.lucene.store.Directory;

import axiom.framework.core.Application;
import axiom.objectmodel.dom.LuceneManager;

public class QueryConfiguration {
    
    Application app;
    Directory directory;
    LuceneManager lmgr;
    RhinoCore core;
    
    public QueryConfiguration() {
    }
    
    public QueryConfiguration(Application app, Directory dir, LuceneManager lmgr, RhinoCore core) {
        this.app = app;
        this.directory = dir;
        this.lmgr = lmgr;
        this.core = core;
    }

    public Application getApplication() {
        return app;
    }

    public void setApplication(Application app) {
        this.app = app;
    }

    public RhinoCore getCore() {
        return core;
    }

    public void setCore(RhinoCore core) {
        this.core = core;
    }

    public Directory getDirectory() {
        return directory;
    }

    public void setDirectory(Directory directory) {
        this.directory = directory;
    }

    public LuceneManager getLuceneMgr() {
        return lmgr;
    }

    public void setLuceneMgr(LuceneManager lmgr) {
        this.lmgr = lmgr;
    }
    
}