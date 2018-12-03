package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.textup.*

@GrailsTypeChecked
@Log4j
class MapUtils {

    static <T, I> Map<T, I> buildObjectMap(Closure<T> getProp, Collection<I> objs) {
        Map<T, I> idToObject = [:]
        objs?.each { I obj -> if (obj) { idToObject[getProp(obj)] = obj } }
        idToObject
    }

    static <T, I> Map<T, Collection<I>> buildManyObjectsMap(Closure<T> getProp, Collection<I> objs) {
        Map<T, Collection<I>> idToManyObjects = [:].withDefault { [] as Collection<I> }
        objs?.each { I obj -> if (obj) { idToManyObjects[getProp(obj)] << obj } }
        idToManyObjects
    }

    static int countKeys(List<String> keysToLookFor, Map params) {
        int numFound = 0
        keysToLookFor.each { if (params.containsKey(it)) { numFound++ } }
        numFound
    }

    static <K> Map.Entry<K,? extends Comparable> findHighestValue(Map<K,? extends Comparable> map) {
        Map.Entry<K,? extends Comparable> highestEntry
        map?.entrySet().each { Map.Entry<K,? extends Comparable> entry ->
            if (!highestEntry || entry.value > highestEntry.value) {
                highestEntry = entry
            }
        }
        highestEntry
    }
}
