/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile: Prototype.java,v $
 * $Author: hannes $
 * $Revision: 1.52 $
 * $Date: 2006/05/11 19:20:31 $
 */

package axiom.framework.core;


import java.io.*;
import java.util.*;

import axiom.framework.ErrorReporter;
import axiom.framework.repository.FileResource;
import axiom.framework.repository.Repository;
import axiom.framework.repository.Resource;
import axiom.framework.repository.ResourceTracker;
import axiom.objectmodel.db.DbMapping;
import axiom.objectmodel.db.Relation;
import axiom.scripting.rhino.AxiomObject;
import axiom.util.ResourceProperties;
import axiom.util.WrappedMap;

/**
 * The Prototype class represents Script prototypes/type defined in an Axiom
 * application. This class manages a prototypes templates, functions and actions
 * as well as optional information about the mapping of this type to a
 * relational database table.
 */
public final class Prototype {
    // the app this prototype belongs to
    Application app;

    // this prototype's name in natural and lower case
    String name;
    String lowerCaseName;

    // this prototype's resources
    Resource[] resources;

    // tells us the checksum of the repositories at the time we last updated them
    long lastChecksum = -1;

    // the time at which any of the prototype's files were found updated the last time
    volatile long lastCodeUpdate = 0;

    ArrayList code;

    HashMap trackers;

    ArrayList repositories;

    DbMapping dbmap;

    private Prototype parent;

    ResourceProperties props;
    ResourceProperties sprops;
    ResourceProperties allprops;
    ResourceProperties cacheprops;
    
    long lastSecurityChange = -1;
    long lastCacheChange = -1;
    
    HashMap securityMap = new HashMap();
    HashMap cacheMap = new HashMap();
    String[] cacheIds = null;
    
    ArrayList<Prototype> childProtos = new ArrayList<Prototype>();
    
    private boolean initialSetup = false;
    
    private boolean omitXMLDeclaration = false;
    
    private boolean staticPublishable = false;
    
    /**
     * Creates a new Prototype object.
     *
     * @param name the prototype's name
     * @param repository the first prototype's repository
     * @param app the application this prototype is a part of
     */
    public Prototype(String name, Repository repository, Application app) {
        this.app = app;
        this.name = name;
        repositories = new ArrayList();
        if (repository != null) {
            repositories.add(repository);
        }
        lowerCaseName = name.toLowerCase();

        // Create and register type properties file
        props = new ResourceProperties(app);
        props.setIgnoreCase(app.isPropertyFilesIgnoreCase());
        sprops = new ResourceProperties(app);
        sprops.setIgnoreCase(false);
        allprops = new ResourceProperties(app);
        allprops.setIgnoreCase(app.isPropertyFilesIgnoreCase());
        cacheprops = new ResourceProperties(app);
        cacheprops.setIgnoreCase(false);
        
        if (repository != null) {
            Resource r = repository.getResource("prototype.properties");
            if (r != null) {
                props.addResource(r);
            } else {
                this.app.logEvent(ErrorReporter.warningMsg(this.getClass(), "ctor") 
                		+ "Prototype " + name + " does not have an " 
                        + "associated prototype.properties");
            }
            sprops.addResource(repository.getResource("security.properties"));  
            cacheprops.addResource(repository.getResource("cache.properties"));
        }

        specialPrototypesSetup(props); 
        
        dbmap = new DbMapping(app, name, props);
        // we don't need to put the DbMapping into proto.updatables, because
        // dbmappings are checked separately in TypeManager.checkFiles() for
        // each request

        code = new ArrayList();

        trackers = new HashMap();
    }
    
    /**
     * @param props
     */
    private void specialPrototypesSetup(ResourceProperties props) {
        // Make sure the children (thumbnails) of Image objects have
        // the proper access name used when getting their URLs
        if ("image".equalsIgnoreCase(this.lowerCaseName)) {
            if (props.get("_extends") == null) {
                props.put("_extends", "File");
            }
            props.put("_children", "");
            props.put("_children.type", "Collection(Image)");
            props.put("_children.accessname", axiom.scripting.rhino.FileObject.ACCESSNAME);
            props.put(axiom.scripting.rhino.FileObject.ACCESSNAME, axiom.scripting.rhino.FileObject.ACCESSNAME);
            props.put(axiom.scripting.rhino.FileObject.ACCESSNAME + ".type", "String");
        }

        // omitXmlDeclaration can be defined for quirks mode
        if (props.get("omitXmlDeclaration") != null) {
       		omitXMLDeclaration = new Boolean(props.get("omitXmlDeclaration").toString()).booleanValue();
        } else { 
            omitXMLDeclaration = this.app.getOmitXmlDecl();              
        } 

        if (props.get("_staticPublishable") != null) {
       		staticPublishable = new Boolean(props.get("_staticPublishable").toString()).booleanValue();
        }  
        
    }

    /**
     *  Return the application this prototype is a part of
     */
    public Application getApplication() {
        return app;
    }

    /**
     * Adds an repository to the list of repositories
     * @param repository repository to add
     */
    public void addRepository(Repository repository) {
        if (!repositories.contains(repository)) {
            repositories.add(repository);
            props.addResource(repository.getResource("prototype.properties"));
            specialPrototypesSetup(props);  
            sprops.addResource(repository.getResource("security.properties"));
            cacheprops.addResource(repository.getResource("cache.properties"));
        }
    }

    /**
     * Check a prototype for new or updated resources. After this has been
     * called the code and skins collections of this prototype should be
     * up-to-date and the lastCodeUpdate be set if there has been any changes.
     */
    public synchronized void checkForUpdates() {
        boolean updatedResources = false;

        // check if any resource the prototype knows about has changed or gone
        for (Iterator i = trackers.values().iterator(); i.hasNext();) {
            ResourceTracker tracker = (ResourceTracker) i.next();

            try {
                if (tracker.hasChanged()) {
                    updatedResources = true;
                    // let tracker know we've seen the update
                    tracker.markClean();
                    // if resource has gone remove it
                    if (!tracker.getResource().exists()) {
                        i.remove();
                        String name = tracker.getResource().getName();
                        code.remove(tracker.getResource());
                    }
                }
            } catch (IOException iox) {
                iox.printStackTrace();
            }
        }

        // next we check if resources have been created or removed
        Resource[] resources = getResources();

        for (int i = 0; i < resources.length; i++) {
            String name = resources[i].getName();
            if (!trackers.containsKey(name)) {
                if (name.endsWith(TypeManager.scriptExtension) || 
                		name.endsWith(TypeManager.talExtension)) { 
                    updatedResources = true;
                    code.add(resources[i]);
                    trackers.put(resources[i].getName(), new ResourceTracker(resources[i]));
                }
            }
        }

        if (updatedResources) {
            // mark prototype as dirty and the code as updated
            lastCodeUpdate = System.currentTimeMillis();
            app.typemgr.setLastCodeUpdate(lastCodeUpdate);
        }
    }


    /**
     *  Returns the list of resources in this prototype's repositories. Used
     *  by checkForUpdates() to see whether there is anything new.
     */
    public Resource[] getResources() {
        if (!this.app.autoUpdate && this.lastChecksum != -1) {
            return resources;
        }
        
        long checksum;
        // reload resources if the repositories checksum has changed
        if ((checksum = getRepositoryChecksum()) != lastChecksum) {
            ArrayList list = new ArrayList();

            final int size = repositories.size();
            for (int i = 0; i < size; i++) {
                Repository r = (Repository) repositories.get(i);
                try {
                    list.addAll(r.getAllResources());
                } catch (IOException ioex) {
                    ioex.printStackTrace();
                }
            }

            resources = (Resource[]) list.toArray(new Resource[list.size()]);
            lastChecksum = checksum;
        }
        return resources;
    }

    /**
     * Returns an array of repositories containing code for this prototype.
     */
    public Repository[] getRepositories() {
        return (Repository[]) repositories.toArray(new Repository[repositories.size()]);
    }

    /**
     *  Get a checksum over this prototype's repositories. This tells us
     *  if any resources were added or removed.
     */
    long getRepositoryChecksum() {
        long checksum = 0;
        Iterator iterator = repositories.iterator();

        while (iterator.hasNext()) {
            try {
                checksum += ((Repository) iterator.next()).getChecksum();
            } catch (IOException iox) {
                iox.printStackTrace();
            }
        }

        return checksum;
    }

    /**
     *  Set the parent prototype of this prototype, i.e. the prototype this
     *  prototype inherits from.
     */
    public void setParentPrototype(Prototype parent) {
        // this is not allowed for the axiomobject and global prototypes
        if ("axiomobject".equals(lowerCaseName) || "global".equals(lowerCaseName)) {
            return;
        }

        Prototype currParent = this.parent;
        if (currParent != null) {
        	currParent.childProtos.remove(this);
        }
        this.parent = parent;
        if (parent != null) {
        	parent.childProtos.add(this);
        }
    }

    /**
     *  Get the parent prototype from which we inherit, or null
     *  if we are top of the line.
     */
    public Prototype getParentPrototype() {
        return parent;
    }

    /**
     * Check if the given prototype is within this prototype's parent chain.
     */
    public final boolean isInstanceOf(String pname) {
        if (name.equalsIgnoreCase(pname)) {
            return true;
        }

        if (parent != null) {
            return parent.isInstanceOf(pname);
        }

        return false;
    }

    /**
     * Register an object as handler for all our parent prototypes, but only if
     * a handler by that prototype name isn't registered yet. This is used to
     * implement direct over indirect prototype precedence and child over parent
     *  precedence.
     */
    public final void registerParents(Map handlers, Object obj) {

        Prototype p = parent;

        while ((p != null) && !"axiomobject".equals(p.getLowerCaseName())) {
            Object old = handlers.put(p.name, obj);
            // if an object was already registered by this name, put it back in again.
            if (old != null) {
                handlers.put(p.name, old);
            }
            // same with lower case name
            old = handlers.put(p.lowerCaseName, obj);
            if (old != null) {
                handlers.put(p.lowerCaseName, old);
            }

            p = p.parent;
        }
    }

    /**
     * Get the DbMapping associated with this prototype
     */
    public DbMapping getDbMapping() {
        return dbmap;
    }

    /**
     * Return this prototype's name
     *
     * @return ...
     */
    public String getName() {
        return name;
    }

    /**
     * Return this prototype's name in lower case letters
     *
     * @return ...
     */
    public String getLowerCaseName() {
        return lowerCaseName;
    }

    /**
     *  Get the last time any script has been re-read for this prototype.
     */
    public long lastCodeUpdate() {
        return lastCodeUpdate;
    }

    /**
     *  Signal that some script in this prototype has been
     *  re-read from disk and needs to be re-compiled by
     *  the evaluators.
     */
    public void markUpdated() {
        lastCodeUpdate = System.currentTimeMillis();
    }

    /**
     * Get the prototype's aggregated prototype.properties
     *
     * @return prototype.properties
     */
    public ResourceProperties getTypeProperties() {
        return props;
    }

    /**
     * 
     * @return
     */
    public ResourceProperties getSecurityProperties() {
        return sprops;
    }
    
    public ResourceProperties getCacheProperties() {
        return this.cacheprops;
    }
    
    /**
     *  Return an iterator over this prototype's code resoruces. Synchronized
     *  to not return a collection in a transient state where it is just being
     *  updated by the type manager.
     */
    public synchronized Iterator getCodeResources() {
        return code.iterator();
    }
    
    public synchronized ArrayList getCodeResourceList() {
        return new ArrayList(code);
    }

    /**
     *  Return a string representing this prototype.
     */
    public String toString() {
        return "[Prototype " + app.getName() + "/" + name + "]";
    }
    
    public void initialSetup() {
        this.setupSecurity();
        this.setupCaching();
        this.setupProtoChain();
        this.initialSetup = true;
    }
    
    public void setupSecurity() {
        HashMap securityMap = new HashMap();
        Stack s = new Stack();
        Prototype p = this;
        while (p != null) {
            s.push(p);
            p = p.getParentPrototype();
        }
        final int size = s.size();
        for (int i = 0; i < size; i++) {
            securityMap.putAll(((Prototype) s.pop()).sprops);
        }
        this.securityMap = securityMap; 
        this.lastSecurityChange = this.sprops.lastModified();
    }
    
    public void setupCaching() {
        HashMap cacheMap = new HashMap();
        Stack s = new Stack();
        Prototype p = this;
        while (p != null) {
            s.push(p);
            p = p.getParentPrototype();
        }
        final int size = s.size();
        for (int i = 0; i < size; i++) {
            cacheMap.putAll(((Prototype) s.pop()).cacheprops);
        }
        this.cacheMap = cacheMap; 
        this.cacheIds = null;
        this.lastCacheChange = this.cacheprops.lastModified();
    }
    
    public String getCacheProperty(String key) {
        return (String) this.cacheMap.get(key);
    }
    
    public long getFunctionCacheExpiration(String functionName) {
        long ret;
        try {
            ret = Long.parseLong((String) this.cacheMap.get(functionName)) * 1000L;
        } catch (Exception ex) {
            ret = -1L;
        }
        return ret;
    }
    
    public boolean isFunctionResponseCachable(String functionName) {
        if (this.cacheMap.get(functionName) != null) {
            String mode = (String) this.cacheMap.get(functionName + ".mode");
            if (mode == null || mode.indexOf("response") > -1 || mode.indexOf("RESPONSE") > -1) {
                return true;
            }
        }
        return false;
    }
    
    public boolean isFunctionReturnCachable(String functionName) {
        if (this.cacheMap.get(functionName) != null) {
            String mode = (String) this.cacheMap.get(functionName + ".mode");
            if (mode != null && (mode.indexOf("method") > -1 || mode.indexOf("METHOD") > -1)) {
                return true;
            }
        }
        return false;
    }
    
    public HashMap getSecurityMap() {
        return this.securityMap;
    }
    
    public HashMap getCacheMap() {
        return this.cacheMap;
    }
    
    public String[] getCacheIds() {
        if (this.cacheIds != null) {
            return this.cacheIds;
        }
        
        ArrayList<String> ids = new ArrayList<String>();
        Iterator iter = this.cacheMap.keySet().iterator();
        while (iter.hasNext()) {
            String curr = (String) iter.next();
            if (curr.indexOf(".") < 0) {
                ids.add(curr);
            }
        }
        
        this.cacheIds = new String[ids.size()];
        ids.toArray(this.cacheIds);
        
        return this.cacheIds;
    }
    
    public void setupProtoChain() {
        Prototype proto = this;
        Stack proto_stack = new Stack();
        while (proto != null) {
            proto_stack.push(proto);
            proto = proto.getParentPrototype();
        }

        ResourceProperties props = new ResourceProperties(this.app);
        props.setIgnoreCase(app.isPropertyFilesIgnoreCase());
        final int stackSize = proto_stack.size();
        for (int j = 0; j < stackSize; j++) {
            props.putAll(((Prototype) proto_stack.pop()).getTypeProperties());
        }

        this.allprops = props;
    }
    
    public ResourceProperties getAllProps() {
        return this.allprops;
    }

    public boolean getOmitXMLDeclaration(){
    	return omitXMLDeclaration;
    }
    
    public boolean getStaticPublishable(){
    	return staticPublishable;
    }

    public boolean isInitSetupComplete() {
        return this.initialSetup;
    }
    
    public boolean securityNeedsUpdate() {
        return this.sprops.lastModified() != this.lastSecurityChange;
    }
    
    public boolean cacheNeedsUpdate() {
        return this.cacheprops.lastModified() != this.lastCacheChange;
    }
    
    public String getAccessname() {
        String accessName = null;
        DbMapping dbmap = this.getDbMapping();
        if (dbmap != null) {
            Relation rel = dbmap.getSubnodeRelation();
            if (rel != null) {
                accessName = rel.getAccessName();
            }
        }
        return accessName;
    }
    
    public String getProperty(String name) {
        return (String) this.allprops.get(name);
    }
    
    public ArrayList<Prototype> getChildPrototypes() {
    	return this.childProtos;
    }
    
}