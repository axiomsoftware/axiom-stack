package axiom.util;

import java.util.List;
import java.util.Properties;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import axiom.framework.core.Application;
import axiom.objectmodel.ObjectCache;
import axiom.objectmodel.db.DbKey;

public class EhCacheMap implements ObjectCache {

    private Cache cache;
    private String cacheName;
    private CacheManager manager;
    
    public EhCacheMap() {
    }

    public void init(Application app) {
        init(app, null);
    }
    
    public void init(Application app, String cacheName) {
        int cacheSize;
        try {
            cacheSize = Integer.parseInt(app.getProperty("cachesize", "1000"));
        } catch (Exception ex) {
            cacheSize = 1000;
        }
        
        // Create a CacheManager using defaults
        this.manager = CacheManager.create();
        //Create a Cache specifying its configuration.
        if (cacheName != null) {
            this.cacheName = cacheName;
        } else {
            this.cacheName = "default-" + app.getName();
        }
        
        boolean eternal = true;
        long timeoutFromCreation;
        String timeout = app.getProperty("filter.cache.timeout");
        try {
            timeoutFromCreation = ResourceProperties.getMillisFromProperty(timeout);
            eternal = false;
        } catch (Exception ex) {
            timeoutFromCreation = Long.MAX_VALUE;
        }
        
        Cache defaultCache = new Cache(this.cacheName, cacheSize, 
                MemoryStoreEvictionPolicy.LRU, false, null, 
                eternal, timeoutFromCreation, Long.MAX_VALUE, false, 360L, null);
        this.manager.addCache(defaultCache);
        this.cache = this.manager.getCache(this.cacheName);
    }

    public void shutdown() {
        cache.removeAll();
        cache.dispose();
        this.manager.removeCache(this.cacheName);
    }

    public int size() {
        return this.cache.getSize();
    }

    public void updateProperties(Properties props) {
        // does nothing
    }
    
    public boolean clear() {
        this.cache.removeAll();
        return true;
    }

    public boolean containsKey(Object key) {
        return this.cache.isKeyInCache(key);
    }

    public int containsKeys(Object[] keys) {
        int count = 0;
        for (int i = 0; i < keys.length; i++) {
            if (this.cache.isKeyInCache(keys[i])) {
                count++;
            }
        }
        return count;
    }

    public Object get(Object key) {
        Element e = this.cache.get(key);
        Object ret = null;
        if (e != null) {
            ret = e.getObjectValue();
        }
        return ret;
    }
    
    public Object get(Object key, boolean quiet) {
        Element e = quiet ? this.cache.getQuiet(key) : this.cache.get(key);
        Object ret = null;
        if (e != null) {
            ret = e.getObjectValue();
        }
        return ret;
    }

    public Object[] getCachedObjects() {
        List keys = this.cache.getKeys();
        final int size = keys.size();
        Object[] objects = new Object[size];
        for (int i = 0; i < size; i++) {
            Element e = this.cache.get(keys.get(i));
            objects[i] = e != null ? e.getObjectValue() : null;
        }
        return objects;
    }

    public Object put(Object key, Object value) {
        Object ret = null;
        Element old = this.cache.getQuiet(key);
        if (old != null) { 
            ret = old.getObjectValue();
        } 
        this.cache.put(new Element(key, value));
        return ret;
    }

    public Object remove(Object key) {
        Object ret = null;
        Element elem = this.cache.getQuiet(key);
        if (elem != null) {
            ret = elem.getObjectValue();
        }
        this.cache.remove(key);
        return ret;
    }

}