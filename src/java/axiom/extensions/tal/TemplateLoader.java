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
import java.util.*;

import axiom.framework.core.Application;
import axiom.framework.core.Prototype;
import axiom.framework.repository.Resource;

/**
 * Application based TemplateLoader that walks through a prototype's
 * Resources and finds the fitting ones
 */
public class TemplateLoader {
    // application for which the TemplateLoader will find resources for
    private Application app = null;
    // cache holds the resources found for the application
    private HashMap cache = new HashMap();

    protected TemplateLoader(Application app) {
        this.app = app;
    }

    public Object findTemplateSource(String name) throws Exception {
    	Resource resource = (Resource) cache.get(name);
        if (resource == null || !resource.exists()) {
            resource = null;
            try {
                int pos = name.indexOf(':');
                Prototype prototype = app.getPrototypeByName(name.substring(0, pos));
                name = name.substring(pos + 1) + ".tal";
                if (prototype != null) 
                    if ((resource = scanForResource(prototype, name)) == null) {
                        prototype = app.getPrototypeByName("AxiomObject");
                        if (prototype != null)
                            resource = scanForResource(prototype, name);
                }
            } catch (Exception ex) {
                throw new Exception("Unable to resolve source: " + name + " " + ex);
            }
        }
        return resource;
    }
    
    public HashMap getAllTemplateSources() throws Exception {
        HashMap templSources = new HashMap();
        ArrayList mylist = new ArrayList(app.getPrototypes());
        final int size = mylist.size();
        for (int i = 0; i < size; i++) {
            Prototype prototype = (Prototype) mylist.get(i);
            String proto = prototype.getName() + ":";
            Resource[] resources = prototype.getResources();
            final int length = resources.length;
            for (int j = 0; j < length; j++) {
                if (resources[j].exists() && resources[j].getShortName().endsWith(".tal")) {
                    templSources.put(proto + resources[j].getBaseName(), resources[j]);
                }
            }
        }
        return templSources;
    }

    public long getLastModified(Object templateSource) {
        return ((Resource) templateSource).lastModified();
    }

    public Reader getReader(Object templateSource, String encoding) throws IOException {
        return new InputStreamReader(((Resource) templateSource).getInputStream());
    }

    public void closeTemplateSource(Object templateSource) throws IOException {
    }
    
    private Resource scanForResource(Prototype prototype, String name) throws Exception {
        Resource resource = null;
        // scan for resources with extension .tal:
        Resource[] resources = prototype.getResources();
        for (int i = 0; i < resources.length; i++) {
            Resource res = resources[i];
            if (res.exists() && res.getShortName().equals(name)) {
                cache.put(name, res);
                resource = res;
                break;
            }
        }
        return resource;
    }
}