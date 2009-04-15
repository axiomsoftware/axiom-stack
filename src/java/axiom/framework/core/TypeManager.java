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
 * $RCSfile: TypeManager.java,v $
 * $Author: hannes $
 * $Revision: 1.65 $
 * $Date: 2005/08/09 17:32:39 $
 */

/* 
 * Modified by:
 * 
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */
package axiom.framework.core;


import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import axiom.framework.repository.Repository;
import axiom.framework.repository.Resource;
import axiom.objectmodel.db.DbMapping;
import axiom.util.StringUtils;

/**
 * The type manager periodically checks the prototype definitions for its
 * applications and updates the evaluators if anything has changed.
 */
public final class TypeManager {
    /* added File, Image, Session to standardTypes */
    final static String[] defaultStandardTypes = { "User", "Global", "Root", "AxiomObject", 
                                            "File", "Image", "Session" };
    final static String scriptExtension = ".js";
    final static String talExtension = ".tal"; 

    private ArrayList<String> standardTypes = new ArrayList<String>();
    
    private Application app;
    // map of prototypes
    private HashMap<String, Prototype> prototypes;

    // set of Java archives
    private HashSet<String> jarfiles;

    // set of directory names to ignore
    private HashSet<String> ignoreDirs;

    private long lastCheck = 0;
    private long lastCodeUpdate;
    private long[] lastRepoScan;

    // app specific class loader, includes jar files in the app directory
    private AppClassLoader loader;

    /**
     * Creates a new TypeManager object.
     *
     * @param app ...
     *
     * @throws RuntimeException ...
     */
    public TypeManager(Application app, String ignore) {
        this.app = app;
        prototypes = new HashMap<String, Prototype>();
        jarfiles = new HashSet<String>();
        ignoreDirs = new HashSet<String>();
        // split ignore dirs list and add to hash set
        if (ignore != null) {
            String[] arr = StringUtils.split(ignore, ",");
            for (int i=0; i<arr.length; i++)
                ignoreDirs.add(arr[i].trim());
        }

        URL axiomjar = TypeManager.class.getResource("/");

        if (axiomjar == null) {
            // Axiom classes are in jar file, get axiom.jar URL
            URL[] urls = ((URLClassLoader) TypeManager.class.getClassLoader()).getURLs();

            for (int i = 0; i < urls.length; i++) {
                String url = urls[i].toString().toLowerCase();
                if (url.endsWith("axiom.jar")) {
                    axiomjar = urls[i];
                    break;
                }
            }
        }

        if (axiomjar == null) {
            // throw new RuntimeException("axiom.jar not found in embedding classpath");
            loader = new AppClassLoader(app.getName(), new URL[0]);
        } else {
            loader = new AppClassLoader(app.getName(), new URL[] { axiomjar });
        }
        
        for (int i = 0; i < defaultStandardTypes.length; i++) {
            standardTypes.add(defaultStandardTypes[i]);
        }
        
    }

    /**
     * Run through application's prototype directories and create prototypes, but don't
     * compile or evaluate any scripts.
     */
    public void createPrototypes() throws IOException {
        // create standard prototypes.
        for (int i = 0; i < standardTypes.size(); i++) {
            createPrototype(standardTypes.get(i), null);
        }

        // loop through directories and create prototypes
        checkRepositories();
    }

    /**
     * Run through application's prototype directories and check if anything
     * has been updated.
     * If so, update prototypes and scripts.
     */
    public synchronized void checkPrototypes() throws IOException {
        if ((System.currentTimeMillis() - lastCheck) < 1000L) {
            return;
        }

        checkRepositories();

        lastCheck = System.currentTimeMillis();
    }

    private void checkRepository(Repository repository) throws IOException {
        Repository[] list = repository.getRepositories();
        for (int i = 0; i < list.length; i++) {
 
            // ignore dir name found - compare to shortname (= Prototype name)
            if (ignoreDirs.contains(list[i].getShortName())) {
                // jump this repository
                if (app.debug) {
                    app.logEvent("Repository " + list[i].getName() + " ignored");
                }
                continue;
            }

            if (list[i].isScriptRoot()) {
                // this is an embedded top-level script repository 
                if (app.addRepository(list[i])) {
                    // repository is new, check it
                    checkRepository(list[i]);
                }
            } else {
            	// it's an prototype
            	String name = null;
            	name = list[i].getShortName();
            	
            	if (list[i].hasResource("prototype.properties") || isDefaultStandardType(name) || prototypes.containsKey(name.toLowerCase())) {
            		Prototype proto = getPrototype(name);

            		// if prototype doesn't exist, create it
            		if (proto == null) {
            			// create new prototype if type name is valid
            			if (isValidTypeName(name)) 
            				
            				createPrototype(name, list[i]);
            		} else {
            			proto.addRepository(list[i]);
            		}
            	}
            }
        }

        for (Resource resource : repository.getResources()) {
            // check for jar files to add to class loader
            String name = resource.getName();
            if (name.endsWith(".jar")) {
                if (!jarfiles.contains(name)) {
                    jarfiles.add(name);
                    try {
                        loader.addURL(resource.getUrl());
                    } catch (UnsupportedOperationException x) {
                        // not implemented by all kinds of resources
                    }
                }
            }
        }
    }
    
    private static boolean isDefaultStandardType(String name) {
    	for (int i = 0; i < defaultStandardTypes.length; i++) {
    		if (defaultStandardTypes[i].equals(name)) {
    			return true;
    		}
    	}
    	return false;
    }

    /**
     * Run through application's prototype sources and check if
     * there are any prototypes to be created.
     */
    private void checkRepositories() throws IOException {
        List<Repository> list = app.getRepositories();
        // first check if we need to create or adapt our array of last scans
        if (lastRepoScan == null || lastRepoScan.length != list.size()) {
            lastRepoScan = new long[list.size()];
        }

        // walk through repositories and check if any of them have changed.
        for (int i = lastRepoScan.length - 1; i > -1; i--) {
            Repository repository = list.get(i);
            if (repository.lastModified() != lastRepoScan[i]) {
                lastRepoScan[i] = repository.lastModified();

                checkRepository(repository);
            }
        }

        // loop through prototypes and check if prototype.properties needs updates
        // it's important that we do this _after_ potentially new prototypes
        // have been created in the previous loop.
        for (Prototype proto : prototypes.values()) {
            // update prototype's type mapping
            DbMapping dbmap = proto.getDbMapping();

            if ((dbmap != null) && dbmap.needsUpdate()) {
                // call dbmap.update(). This also checks the
                // parent prototype for prototypes other than
                // global and AxiomObject, which is a bit awkward...
                // I mean we're the type manager, so this should
                // be part of our job, right?
                proto.props.update();
                dbmap.update();
                proto.setupProtoChain();
            } 
            if (proto.securityNeedsUpdate()) {
            	setupSecurity(proto);
            }
            if (proto.cacheNeedsUpdate()) {
                setupCaching(proto);
            }
            if (!proto.isInitSetupComplete()) {
                proto.initialSetup();
            }
        }
    }
    
    private void setupSecurity(Prototype proto) {
        proto.setupSecurity();
        ArrayList<Prototype> childprotos = proto.getChildPrototypes();
        for (Prototype childproto : childprotos) {
        	childproto.setupSecurity();
        }
    }
    
    private void setupCaching(Prototype proto) {
        proto.setupCaching();
        ArrayList<Prototype> childprotos = proto.getChildPrototypes();
        for (Prototype childproto : childprotos) {
        	childproto.setupCaching();
        }
    }

    private boolean isValidTypeName(String str) {
        if (str == null) {
            return false;
        }

        char[] c = str.toCharArray();

        for (int i = 0; i < c.length; i++)
            if (!Character.isJavaIdentifierPart(c[i])) {
                return false;
            }

        return true;
    }

    /**
     *  Returns the last time any resource in this app was modified.
     *  This can be used to find out quickly if any file has changed.
     */
    public long getLastCodeUpdate() {
        return lastCodeUpdate;
    }

    /**
     *  Set the last time any resource in this app was modified.
     */
    public void setLastCodeUpdate(long update) {
        lastCodeUpdate = update;
    }

    /**
     * Return the class loader used by this application.
     *
     * @return the ClassLoader
     */
    public ClassLoader getClassLoader() {
        return loader;
    }

    /**
     * Return a collection containing the prototypes defined for this type
     * manager.
     *
     * @return a collection containing the prototypes
     */
    public Collection<Prototype> getPrototypes() {
        return Collections.unmodifiableCollection(prototypes.values());
    }

    /**
     *   Get a prototype defined for this application
     */
    public Prototype getPrototype(String typename) {
        if (typename == null) {
            return null;
        }
        return (Prototype) prototypes.get(typename.toLowerCase());
    }

    /**
     * Create and register a new Prototype.
     *
     * @param typename the name of the prototype
     * @param repository the first prototype source
     * @return the newly created prototype
     */
    public Prototype createPrototype(String typename, Repository repository) {
        Prototype proto = new Prototype(typename, repository, app);

        // put the prototype into our map
        prototypes.put(proto.getLowerCaseName(), proto);

        return proto;
    }

}
