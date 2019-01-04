package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.textup.*

@GrailsTypeChecked
@Log4j
class CollectionUtils {

    static List takeRight(List data, int numToTake) {
        if (!data) return []
        int totalNum = data.size()
        if (numToTake <= 0 || numToTake > totalNum) {
            []
        }
        else if (numToTake == totalNum) {
            data
        }
        else { data[(totalNum - numToTake)..(totalNum - 1)] }
    }
    static boolean inListIgnoreCase(String toFind, Collection<String> options) {
        String lowerCaseToFind = StringUtils.toLowerCaseString(toFind)
        (options
            ?.collect(StringUtils.&toLowerCaseString) as Collection<String>)
            ?.any { String allowed -> allowed == lowerCaseToFind }
    }

    static String joinWithDifferentLast(List list, String delim, String lastDelim) {
        if (!list) {
            return ""
        }
        (list.size() > 2) ? (list[0..-2].join(delim) + lastDelim + list[-1]) : list.join(lastDelim)
    }

    static <T> List<T> ensureNoNull(List<T> list) {
        list?.removeAll { it == null }
        list
    }

    static <T> Collection<T> flattenValues(Map<?, Collection<T>> map) {
        Collection<T> allValues = []
        map.values().each { Collection<T> values -> allValues.addAll(values) }
        allValues
    }
}
