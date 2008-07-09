package axiom.util;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import axiom.framework.core.Application;
import axiom.framework.core.Prototype;
import axiom.objectmodel.INode;
import axiom.objectmodel.db.DbKey;
import axiom.objectmodel.db.Node;
import axiom.scripting.rhino.AxiomObject;

public class ExecutionCache {
    
    private Cache cache;
    private Application app;
    private CacheManager manager;
    private String cacheName;
    
    public ExecutionCache(Application app, String usage) {
        int cacheSize;
        try {
            cacheSize = Integer.parseInt(app.getProperty("cachesize", "1000"));
        } catch (Exception ex) {
            cacheSize = 1000;
        }

        // Create a CacheManager using defaults
        this.manager = CacheManager.create();
        this.cacheName = usage + "-" + app.getName();
        //Create a Cache specifying its configuration.
        Cache defaultCache = new Cache(this.cacheName, cacheSize, 
                MemoryStoreEvictionPolicy.LRU, 
                false, null, true, Long.MAX_VALUE, Long.MAX_VALUE, false, 360L, null);
        this.manager.addCache(defaultCache);
        this.cache = this.manager.getCache(this.cacheName);
        this.app = app;
    }

    public void shutdown() {
        this.cache.removeAll();
        this.cache.dispose();
        this.manager.removeCache(this.cacheName);
    }
    
    public int size() {
        return this.cache.getSize();
    }
    
    public boolean clear() {
        this.cache.removeAll();
        return true;
    }
    
    public boolean containsKey(Object key) {
        return this.cache.isKeyInCache(key);
    }

    public synchronized Object getFunctionResult(Object obj, String func) {
        Object ret = null;
        if (obj instanceof AxiomObject) {
            INode n = ((AxiomObject) obj).getNode();
            if (n == null) {
                return null;
            }
            String key;
            if (n instanceof Node) {
                DbKey dbkey = (DbKey) ((Node) n).getKey();
                key = n.getPrototype() + "/" + n.getID() + "/" + dbkey.getLayer() + "/" + func;
            } else {
                key = n.getPrototype() + "/" + n.getID() + "/" + func;
            }
            
            Element e = this.cache.get(key);
            if (e != null) {
                CachedResult cresult = (CachedResult) e.getObjectValue();
                long expiration = -1L;
                if (cresult.prototype != null) {
                    expiration = cresult.prototype.getFunctionCacheExpiration(func);
                }
                if (System.currentTimeMillis() - cresult.time < expiration) {
                    ret = cresult.result;
                } else {
                    this.cache.remove(key);
                }
            }
        }
        return ret;
    }
    
    public synchronized void putResultInCache(Object obj, String func, Object result) {
        if (obj instanceof AxiomObject) {
            INode n = ((AxiomObject) obj).getNode();
            String prototype = n.getPrototype();
            Prototype p = this.app.getPrototypeByName(prototype);
            String key;
            if (n instanceof Node) {
                DbKey dbkey = (DbKey) ((Node) n).getKey();
                key = prototype + "/" + n.getID() + "/" + dbkey.getLayer() + "/" + func;
            } else {
                key = prototype + "/" + n.getID() + "/" + func;
            }
            
            CachedResult cresult = new CachedResult(p, func, result, System.currentTimeMillis());
            this.cache.put(new Element(key, cresult));   
        }
    }
    
    public synchronized void invalidateAll(Object obj) {
        if (obj instanceof AxiomObject) {
            INode n = ((AxiomObject) obj).getNode();
            String prototype = n.getPrototype();
            Prototype p = this.app.getPrototypeByName(prototype);
            String[] funcs = p.getCacheIds();
            
            for (int i = 0; i < funcs.length; i++) {
                String key;
                if (n instanceof Node) {
                    DbKey dbkey = (DbKey) ((Node) n).getKey();
                    key = prototype + "/" + n.getID() + "/" + dbkey.getLayer() + "/" + funcs[i];
                } else {
                    key = prototype + "/" + n.getID() + "/" + funcs[i];
                }

                this.cache.remove(key);
            }
        }
    }
    
    public synchronized void invalidate(Object obj, String func) {
        if (obj instanceof AxiomObject) {
            INode n = ((AxiomObject) obj).getNode();
            String prototype = n.getPrototype();
            String key;
            if (n instanceof Node) {
                DbKey dbkey = (DbKey) ((Node) n).getKey();
                key = prototype + "/" + n.getID() + "/" + dbkey.getLayer() + "/" + func;
            } else {
                key = prototype + "/" + n.getID() + "/" + func;
            }

            this.cache.remove(key);
        }
    }
    
    
    class CachedResult {
        Prototype prototype;
        String functionName;
        Object result;
        long time;
        
        public CachedResult(Prototype prototype, String func, Object result, long time) {
            this.prototype = prototype;
            this.functionName = func;
            this.result = result;
            this.time = time;
        }
    }
    
}