package org.textup.cache

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import grails.compiler.GrailsTypeChecked
import grails.plugin.cache.*

// Because cache plugin 1.1.9-SNAPSHOT of the cache plugin has a compile error, we need
// to recreate that version's cache capacity limiting functionality here on our own.
// 1.1.9-SNAPSHOT assumes Spring 3.1.x but we are only 4.0.7-RELEASE which has a different Cache interface
// (1) Inspired by the new size-limited cache from 1.1.9: https://github.com/grails-plugins/grails-cache/blob/1.x/src/java/grails/plugin/cache/GrailsConcurrentLinkedMapCache.java
// (2) Based on the naive class from 1.1.8: https://github.com/grails-plugins/grails-cache/blob/v1.1.8/src/java/grails/plugin/cache/GrailsConcurrentMapCache.java
// (3) We cannot just override `getNativeCache()` in Spring's ConcurrentMapCache because it is a
// final method. Therefore, we have to implement the `GrailsCache` interface entirely
// (4) We have a custom object to represent null because Java's `ConcurrentMap` interface
// does not support storing null values by default AND the custom `ConcurrentLinkedHashMap`
// implementation we use does NOT support storing null keys or values.
// see: https://github.com/ben-manes/concurrentlinkedhashmap/blob/concurrentlinkedhashmap-lru-1.4/src/main/java/com/googlecode/concurrentlinkedhashmap/ConcurrentLinkedHashMap.java

@GrailsTypeChecked
class ConstrainedSizeCache implements GrailsCache {

    private static final Object NULL_VALUE = new InternalNullRepresentation()
    private final String name
    private final ConcurrentLinkedHashMap<Object, Object> store

    ConstrainedSizeCache(String name, int maxSize) {
        this.name = name
        this.store = new ConcurrentLinkedHashMap.Builder<Object, Object>()
            .maximumWeightedCapacity(maxSize)
            .build()
    }

    @Override
    void clear() {
        getNativeCache().clear()
    }

    @Override
    void evict(Object key) {
        getNativeCache().remove(replaceNull(key))
    }

    @Override
    Collection<Object> getAllKeys() {
        getNativeCache().keySet()
    }

    @Override
    GrailsValueWrapper get(Object key) {
        Object value = getNativeCache().get(replaceNull(key))
        // only return null if the native cache does not have the key
        // if the native cache has the key, return a ValueWrapper even if the value
        // that is being wrapped is null
        value == null ? null : new GrailsValueWrapper(restoreNull(value), null)
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        Object value = restoreNull(getNativeCache().get(replaceNull(key)))
        value?.asType(type)
    }

    @Override
    String getName() {
        this.name
    }

    @Override
    ConcurrentLinkedHashMap getNativeCache() {
        this.store
    }

    @Override
    void put(Object key, Object value) {
        getNativeCache().put(replaceNull(key), replaceNull(value))
    }

    // Helpers
    // -------

    protected Object replaceNull(Object obj) {
        obj == null ? NULL_VALUE : obj
    }

    protected Object restoreNull(Object obj) {
        obj == NULL_VALUE ? null : obj
    }

    // `ConcurrentLinkedHashMap` does not support storing null keys or values so we have to replace
    private static class InternalNullRepresentation {}
}
