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
 * $RCSfile: ResourceProperties.java,v $
 * $Author: hannes $
 * $Revision: 1.12 $
 * $Date: 2006/05/26 14:41:50 $
 */

package axiom.util;

import java.io.File;
import java.io.IOException;
import java.util.*;

import axiom.framework.core.*;
import axiom.framework.repository.FileResource;
import axiom.framework.repository.Repository;
import axiom.framework.repository.Resource;
import axiom.framework.repository.ResourceComparator;

/**
 *  A property dictionary that is updated from property resources
 */
public class ResourceProperties extends Properties {

    // Delay between checks
    private final long CACHE_TIME = 1500L;

    // Default properties. Note that in contrast to java.util.Properties,
    // defaultProperties are copied statically to ourselves in update(), so
    // there's no need to check them in retrieval methods.
    protected ResourceProperties defaultProperties;

    // Defines wether keys are case-sensitive or not
    private boolean ignoreCase = true;

    // Cached checksum of last check
    private long lastChecksum = 0;

    // Time of last check
    private long lastCheck = 0;

    // Time porperties were last modified
    private long lastModified = 0;

    // Application where to fetch additional resources
    private Application app;

    // Name of possible resources to fetch from the applications's repositories
    private String resourceName;

    // Sorted map of resources
    private Set<Resource> resources;

    /**
     * Constructs an empty ResourceProperties
     * Resources must be added manually afterwards
     */
    public ResourceProperties() {
        // TODO: we can't use TreeSet because we don't have the app's resource comparator
        // Since resources don't implement Comparable, we can't add them to a "naked" TreeSet
        // As a result, resource ordering is random when updating.
        resources = new HashSet<Resource>();
    }

    /**
     * Constructs an empty ResourceProperties
     * Resources must be added manually afterwards
     */
    public ResourceProperties(Application app) {
    	this.app = app;
        resources = new TreeSet<Resource>(app.getResourceComparator());
    }

    /**
     * Constructs a ResourceProperties retrieving resources from the given
     * application using the given name to fetch resources
     * @param app application to fetch resources from
     * @param resourceName name to use when fetching resources from the application
     */
    public ResourceProperties(Application app, String resourceName) {
        this.app = app;
        this.resourceName = resourceName;
        resources = new TreeSet<Resource>(app.getResourceComparator());
    }

    /**
     * Constructs a ResourceProperties retrieving resources from the given
     * application using the given name to fetch resources and falling back
     * to the given default properties
     * @param app application to fetch resources from
     * @param resourceName name to use when fetching resources from the application
     * @param defaultProperties default properties
     */
    public ResourceProperties(Application app, String resourceName,
                              ResourceProperties defaultProperties) {
        this(app, resourceName);
        this.defaultProperties = defaultProperties;
        forceUpdate();
    }

    /**
     * Constructs a ResourceProperties retrieving resources from the given
     * application using the given name to fetch resources and falling back
     * to the given default properties
     * @param app application to fetch resources from
     * @param resourceName name to use when fetching resources from the application
     * @param defaultProperties default properties
     * @param ignoreCase ignore case for property keys, setting all keys to lower case
     */
    public ResourceProperties(Application app, String resourceName,
                              ResourceProperties defaultProperties,
                              boolean ignoreCase) {
        this(app, resourceName);
        this.defaultProperties = defaultProperties;
        this.ignoreCase = ignoreCase;
        forceUpdate();
    }

    /**
     * Updates the properties regardless of an actual need
     */
    public void forceUpdate() {
        lastChecksum = -1;
        update();
    }

    /**
     * Sets the default properties and updates all properties
     * @param defaultProperties default properties
     */
    public void setDefaultProperties(ResourceProperties defaultProperties) {
        this.defaultProperties = defaultProperties;
        update();
    }

    /**
     * Adds a resource to the list of resources and updates all properties if
     * needed
     * @param resource resource to add
     */
    public void addResource(Resource resource) {
        if (resource != null && !resources.contains(resource)) {
            resources.add(resource);
            forceUpdate();
        }
    }

    /**
     * Removes a resource from the list of resources and updates all properties
     * if needed
     * @param resource resource to remove
     */
    public void removeResource(Resource resource) {
        if (resources.contains(resource)) {
            resources.remove(resource);
            forceUpdate();
        }
    }

    /**
     * Get an iterator over the properties' resources
     * @return iterator over the properties' resources
     */
    public Iterator<Resource> getResources() {
        return resources.iterator();
    }

    /**
     * Updates all properties if there is a need to update
     */
    public synchronized void update() {
        // set lastCheck first to reduce risk of recursive calls
        lastCheck = System.currentTimeMillis();
        if (getChecksum() != lastChecksum) {
            // First collect properties into a temporary collection,
            // in a second step copy over new properties,
            // and in the final step delete properties which have gone.
            ResourceProperties temp = new ResourceProperties();
            temp.setIgnoreCase(ignoreCase);

            // first of all, properties are load from default properties
            if (defaultProperties != null) {
                defaultProperties.update();
                temp.putAll(defaultProperties);
            }

            // next we try to load properties from the application's
            // repositories, if we belong to any application
            if (resourceName != null) {
            	if (resourceName.equalsIgnoreCase("app.properties")) {
            		int staticDirs = 0;
            		for (Repository repository : app.getRepositories()) {
            			try {
            				Resource res = repository.getResource(resourceName);
                    		ResourceProperties curr = new ResourceProperties();
            				if (res != null && res.exists()) {
            					curr.load(res.getInputStream());
            				}
            				
            				String pathPrefix = "";
            				if (res instanceof FileResource) {
            					 pathPrefix = ((FileResource) res).getFile().getParentFile().getPath();
            					 if (!pathPrefix.endsWith(File.separator)) {
            						 pathPrefix += File.separator;
            					 }
            				}
            				Enumeration e = curr.keys();
            				while (e.hasMoreElements()) {
            					String key = (String) e.nextElement();
            					if (key.startsWith("static.")) {
            						String dir = curr.getProperty(key);
            						if (!new File(dir).isAbsolute()) {
            							dir = pathPrefix + dir; 
            						} 
            						temp.setProperty("static." + staticDirs++, dir);
            					} else if (key.equalsIgnoreCase("onStart")) {
            						String currOnStart = temp.getProperty(key);
            						String value = curr.getProperty(key);
            						if (currOnStart != null) {
            							value = value + "," + currOnStart;
            						}
            						temp.setProperty(key, value);
            					} else {
            						temp.setProperty(key, curr.getProperty(key));
            					}
            				}
            			} catch (IOException iox) {
            				iox.printStackTrace();
            			}
            		}
            	} else {
            		for (Repository repository : app.getRepositories()) {
            			try {
            				Resource res = repository.getResource(resourceName);
            				if (res != null && res.exists()) {
            					temp.load(res.getInputStream());
            				}
            			} catch (IOException iox) {
            				iox.printStackTrace();
            			}
            		}
            		
            	}
            }

            // at last we try to load properties from the resource list
            if (resources != null) {
                for (Resource res : resources) {
                    try {
                        if (res.exists()) {
                            temp.load(res.getInputStream());
                        }
                    } catch (IOException iox) {
                        iox.printStackTrace();
                    }
                }
            }

            // Copy over new properties ...
            putAll(temp);
            // ... and remove properties which have been removed.
            Iterator it = super.keySet().iterator();
            while (it.hasNext()) {
                if (!temp.containsKey(it.next())) {
                    it.remove();
                }
            }

            lastChecksum = getChecksum();
            lastCheck = lastModified = System.currentTimeMillis();
        }
    }

    /**
     * Extract all entries where the key matches the given string prefix from
     * the source map to the target map, cutting off the prefix from the original key.
     * The ignoreCase property is inherited and also considered when matching keys
     * against the prefix.
     *
     * @param prefix the string prefix to match against
     */
    public ResourceProperties getSubProperties(String prefix) {
        if (prefix == null)
            throw new NullPointerException("prefix");
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        ResourceProperties subprops = new ResourceProperties();
        subprops.setIgnoreCase(ignoreCase);
        int prefixLength = prefix.length();
        for (Map.Entry entry : entrySet()) {
            String key = entry.getKey().toString();
            if (key.regionMatches(ignoreCase, 0, prefix, 0, prefixLength)) {
                subprops.put(key.substring(prefixLength), entry.getValue());
            }
        }
        return subprops;
    }

    /**
     * Checks wether the given object is in the value list
     * @param value value to look for
     * @return true if the value is found in the value list
     */
    public boolean contains(Object value) {
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        return super.contains(value.toString());
    }

    /**
     * Checks whether the given object is in the key list
     * @param key key to look for
     * @return true if the key is found in the key list
     */
    public boolean containsKey(Object key) {
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        return super.containsKey(key.toString());
    }

    /**
     * Returns an enumeration of all values
     * @return values enumeration
     */
    public Enumeration elements() {
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        return super.elements();
    }

    /**
     * Returns a value in this list fetched by the given key
     * @param key key to use for fetching the value
     * @return value belonging to the given key
     */
    public Object get(Object key) {
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        return (String) super.get(ignoreCase ? key.toString().toLowerCase() : key.toString());
    }

    /**
     * Returns the date the resources were last modified
     * @return last modified date
     */
    public long lastModified() {
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        return lastModified;
    }

    /**
     * Returns a checksum for all resources
     * @return checksum
     */
    public long getChecksum() {
        long checksum = 0;

        if (resourceName != null) {
            for (Repository repository : app.getRepositories()) {
                Resource resource = repository.getResource(resourceName);
                if (resource != null) {
                    checksum += resource.lastModified();
                }
            }
        }

        if (resources != null) {
            for (Resource res : resources) {
                checksum += res.lastModified();
            }
        }

        if (defaultProperties != null) {
            checksum += defaultProperties.getChecksum();
        }

        return checksum;
    }

    /**
     * Returns a value in the list fetched by the given key or a default value
     * if no corresponding key is found
     * @param key key to use for fetching the value
     * @param defaultValue default value to return if key is not found
     * @return spiecific value or default value if not found
     */
    public String getProperty(String key, String defaultValue) {
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        return super.getProperty(ignoreCase ? key.toLowerCase() : key, defaultValue);
    }

    /**
     * Returns a value in this list fetched by the given key
     * @param key key to use for fetching the value
     * @return value belonging to the given key
     */
    public String getProperty(String key) {
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        return super.getProperty(ignoreCase ? key.toLowerCase() : key);
    }

    /**
     * Checks wether the properties list is empty
     * @return true if the properties list is empty
     */
    public boolean isEmpty() {
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        return super.isEmpty();
    }

    /**
     * Checks wether case-sensitivity is ignored for keys
     * @return true if case-sensitivity is ignored for keys
     */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    /**
     * Returns an enumeration of all keys
     * @return keys enumeration
     */
    public Enumeration keys() {
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        return super.keys();
    }

    /**
     * Returns a set of all keys
     * @return keys set
     */
    public Set keySet() {
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        return super.keySet();
    }

    /**
     * Puts a new key-value pair into the properties list
     * @param key key
     * @param value value
     * @return the old value, if an old value got replaced
     */
    public Object put(Object key, Object value) {
        if (value != null) {
            value = value.toString().trim();
        }
        return super.put(ignoreCase ? key.toString().toLowerCase() : key.toString(), value);
    }

    /**
     * Removes a key-value pair from the properties list
     * @param key key
     * @return the old value
     */
    public Object remove(Object key) {
        return super.remove(ignoreCase ? key.toString().toLowerCase() : key.toString());
    }

    /**
     * Changes how keys are handled
     * @param ignore true if to ignore case-sensitivity for keys
     */
    public void setIgnoreCase(boolean ignore) {
        if (!super.isEmpty()) {
            throw new RuntimeException("setIgnoreCase() can only be called on empty Properties");
        }
        ignoreCase = ignore;
    }

    /**
     * Returns the number of peroperties in the list
     * @return number of properties
     */
    public int size() {
        if ((System.currentTimeMillis() - lastCheck) > CACHE_TIME) {
            update();
        }
        return super.size();
    }

    /**
     * Returns a string-representation of the class
     * @return string
     */
    public String toString() {
        return super.toString();
    }

    public static long getMillisFromProperty(String time) 
    throws NumberFormatException, NullPointerException {
        
        if (time == null) {
            throw new NullPointerException();
        }
        
        char unit = 'm';
        if (!Character.isDigit(time.charAt(time.length() - 1))) {
            unit = time.charAt(time.length() - 1);
            time = time.substring(0, time.length() - 1);
        }
        
        long ltime = Long.parseLong(time);
        if (unit == 's' || unit == 'S') {
            ltime *= 1000L; 
        } else if (unit == 'm' || unit == 'M') {
            ltime *= 60000L;
        } else if (unit == 'h' || unit == 'H') {
            ltime *= 3600000L;
        } else if (unit == 'd' || unit == 'D') {
            ltime *= 86400000L;
        }
        
        return ltime;
    }
    
    public Application getApplication() {
    	return this.app;
    }
    
}