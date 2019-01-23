package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class MapUtils {

    static <T, I> Map<T, I> buildObjectMap(Collection<I> objs, Closure<T> getProp) {
        Map<T, I> idToObject = [:]
        objs?.each { I obj -> if (obj) { idToObject[getProp(obj)] = obj } }
        idToObject
    }

    static <T, I> Map<T, Collection<I>> buildManyObjectsMap(Collection<I> objs, Closure<T> getProp) {
        Map<T, Collection<I>> idToManyObjects = [:].withDefault { new HashSet<I>() }
        objs?.each { I obj -> if (obj) { idToManyObjects[getProp(obj)] << obj } }
        idToManyObjects
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
