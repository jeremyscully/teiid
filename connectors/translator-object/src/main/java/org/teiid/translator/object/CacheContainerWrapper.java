package org.teiid.translator.object;

import java.util.Collection;
import java.util.Map;

/**
 * The CacheContainerWrapper serves to hide the implementation details of the actual cache, because not all caches extend a common interface (i.e, Map).  An implementation
 * will map the required behavior to that being defined by the abstract methods.
 * 
 * @author vhalbert
 *
 */
public abstract class CacheContainerWrapper
{
	
	public CacheContainerWrapper() {
	}
	
	/**
	 * Call to obtain an object from the cache based on the specified key
	 * @param cacheName
	 * @param key to use to get the object from the cache
	 * @return Object
	 */
	public Object get(String cacheName, Object key) {
		Map cache = getCache(cacheName);
		return cache.get(key);
	}
	
	/**
	 * Call to obtain all the objects from the cache
	 * @param cacheName
	 * @return List of all the objects in the cache
	 */
	public Collection<Object> getAll(String cacheName) {
		return getCache(cacheName).values();
	}
	
	/**
	 * Call to obtain the cache object
	 * @param cacheName
	 * @return Object cache
	 */
	public abstract <T extends Map> T getCache(String cacheName);  

}