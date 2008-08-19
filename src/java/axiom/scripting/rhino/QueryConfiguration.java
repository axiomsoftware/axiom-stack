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