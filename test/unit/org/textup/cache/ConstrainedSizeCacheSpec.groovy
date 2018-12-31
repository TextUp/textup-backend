package org.textup.cache

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import grails.plugin.cache.GrailsValueWrapper
import org.textup.test.*
import spock.lang.*

class ConstrainedSizeCacheSpec extends Specification {

    void "test constructor"() {
        given:
        String name = TestUtils.randString()
        int size = TestUtils.randIntegerUpTo(100, true)

        when:
        ConstrainedSizeCache cache = new ConstrainedSizeCache(null, -1)

        then:
        thrown IllegalArgumentException

        when:
        cache = new ConstrainedSizeCache(name, size)

        then:
        notThrown IllegalArgumentException
    }

    void "test name"() {
        given:
        String name = TestUtils.randString()

        when:
        ConstrainedSizeCache cache = new ConstrainedSizeCache(name, 88)

        then:
        cache.name == name
    }

    void "test getting underlying data structure"() {
        when:
        ConstrainedSizeCache cache = new ConstrainedSizeCache(TestUtils.randString(), 88)

        then:
        cache.nativeCache instanceof ConcurrentLinkedHashMap
    }

    void "test adding and reading"() {
        given:
        String key1 = TestUtils.randString()
        String key3 = TestUtils.randString()
        String value1 = TestUtils.randString()
        String value2 = TestUtils.randString()

        when:
        ConstrainedSizeCache cache = new ConstrainedSizeCache(TestUtils.randString(), 88)

        then:
        cache.allKeys.isEmpty()
        cache.get("blah") == null

        when: "add"
        cache.put(key1, value1)

        then:
        cache.allKeys.size() == 1
        cache.get("blah") == null
        cache.get(key1) instanceof GrailsValueWrapper
        cache.get(key1).get() == value1

        when: "add null key"
        cache.put(null, value2)

        then:
        cache.allKeys.size() == 2
        cache.get(null) instanceof GrailsValueWrapper
        cache.get(null).get() == value2

        when: "add null value"
        cache.put(key3, null)

        then: "null values are properly handled -- internal null object doesn't leak out"
        cache.allKeys.size() == 3
        cache.get(key3) instanceof GrailsValueWrapper
        cache.get(key3).get() == null
    }

    void "test typing converting getter"() {
        given:
        String key1 = TestUtils.randString()
        String key2 = TestUtils.randString()
        String key3 = TestUtils.randString()
        String value1 = TestUtils.randString()
        Integer value2 = TestUtils.randIntegerUpTo(88)

        when:
        ConstrainedSizeCache cache = new ConstrainedSizeCache(TestUtils.randString(), 88)
        cache.put(key1, value1)
        cache.put(key2, value2)
        cache.put(key3, null)

        then: "values are directly available, not wrapped in a ValueWrapper"
        cache.allKeys.size() == 3
        cache.get(key1, String) == value1
        cache.get(key2, Integer) == value2

        and: "null values are properly handled -- internal null object doesn't leak out"
        cache.get(key3, String) == null
    }

    void "test evicting"() {
        given:
        String key1 = TestUtils.randString()
        String value1 = TestUtils.randString()
        String value2 = TestUtils.randString()

        when:
        ConstrainedSizeCache cache = new ConstrainedSizeCache(TestUtils.randString(), 88)
        cache.put(key1, value1)
        cache.put(null, value2)

        then:
        cache.allKeys.size() == 2

        when: "evicting null"
        cache.evict(null)

        then:
        cache.allKeys.size() == 1

        when: "evicting key"
        cache.evict(key1)

        then:
        cache.allKeys.size() == 0
    }

    void "test clearing"() {
        given:
        String key1 = TestUtils.randString()
        String value1 = TestUtils.randString()
        String value2 = TestUtils.randString()

        when:
        ConstrainedSizeCache cache = new ConstrainedSizeCache(TestUtils.randString(), 88)
        cache.put(key1, value1)
        cache.put(null, value2)

        then:
        cache.allKeys.size() == 2

        when:
        cache.clear()

        then:
        cache.allKeys.size() == 0
    }
}
