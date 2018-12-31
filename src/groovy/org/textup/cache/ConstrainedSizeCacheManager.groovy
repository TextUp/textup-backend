package org.textup.cache

import grails.compiler.GrailsTypeChecked
import grails.plugin.cache.*
import org.springframework.cache.Cache
import org.textup.*

// Superclass: https://github.com/grails-plugins/grails-cache/blob/v1.1.8/src/java/grails/plugin/cache/GrailsConcurrentMapCacheManager.java

@GrailsTypeChecked
class ConstrainedSizeCacheManager extends GrailsConcurrentMapCacheManager {

    // these properties can be customized when this class is declared in `conf/spring/resources.groovy`
    // See: https://grails.github.io/grails2-doc/2.4.4/guide/spring.html
    int defaultMaxSize = Constants.DEFAULT_CACHE_MAX_SIZE
    Map<String, Integer> cacheNameToMaxSize = [:]

    // Need to override this method instead of more-convenient `createConcurrentMapCache` method
    // because `createConcurrentMapCache` has a concrete type instead of an interface return type
    // Besides the call to `createConstrainedCache`, the below method is copied from
    // https://github.com/grails-plugins/grails-cache/blob/v1.1.8/src/java/grails/plugin/cache/GrailsConcurrentMapCacheManager.java
    @Override
    Cache getCache(String name) {
        Cache cache = cacheMap.get(name)
        if (cache == null) {
            cache = createConstrainedCache(name)
            // this is a concurrent implementation so as we are creating and inserting in
            // our cache, another thread may be doing the same. If another thread beat us to
            // creating this cache for this given name, then we just use the cache that was
            // first stored into the cache map
            Cache existing = cacheMap.putIfAbsent(name, cache)
            if (existing != null) {
                cache = existing
            }
        }
        cache
    }

    protected Cache createConstrainedCache(String name) {
        int maxSize = cacheNameToMaxSize[name] ?: defaultMaxSize
        new ConstrainedSizeCache(name, maxSize)
    }
}
