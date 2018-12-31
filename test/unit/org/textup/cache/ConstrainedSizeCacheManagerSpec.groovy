package org.textup.cache

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import grails.plugin.cache.GrailsValueWrapper
import org.springframework.cache.Cache
import org.textup.test.*
import spock.lang.*

class ConstrainedSizeCacheManagerSpec extends Specification {

    void "test getting cache by name"() {
        given:
        String cName = TestUtils.randString()

        when:
        ConstrainedSizeCacheManager cManager = new ConstrainedSizeCacheManager()

        then: "is empty"
        cManager.cacheNames.isEmpty()

        when: "cache does not exist yet"
        Cache c1 = cManager.getCache(cName)

        then:
        c1 instanceof  ConstrainedSizeCache
        cManager.cacheNames.size() == 1

        when: "cache already exists"
        c1 = cManager.getCache(cName)

        then:
        c1 instanceof  ConstrainedSizeCache
        cManager.cacheNames.size() == 1
    }

    void "creating constrained cache"() {
        given:
        String cName1 = TestUtils.randString()
        String cName2 = TestUtils.randString()
        ConstrainedSizeCacheManager cManager = new ConstrainedSizeCacheManager()
        cManager.defaultMaxSize = TestUtils.randIntegerUpTo(100, true)
        cManager.cacheNameToMaxSize = [(cName2): cManager.defaultMaxSize * 2]

        when: "use default size"
        Cache c1 = cManager.getCache(cName1)

        then:
        c1 instanceof  ConstrainedSizeCache
        c1.nativeCache.capacity() == cManager.defaultMaxSize
        cManager.cacheNames.size() == 1

        when: "use specified size"
        c1 = cManager.getCache(cName2)

        then:
        c1 instanceof  ConstrainedSizeCache
        c1.nativeCache.capacity() == cManager.cacheNameToMaxSize[cName2]
        cManager.cacheNames.size() == 2
    }
}
