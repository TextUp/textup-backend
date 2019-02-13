package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.apache.commons.collections.CollectionUtils as CommonsCollectionUtils
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

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
        options?.any { String str1 -> StringUtils.equalsIgnoreCase(toFind, str1) }
    }

    static String joinWithDifferentLast(List list, String delim, String lastDelim) {
        if (!list) {
            return ""
        }
        (list.size() > 2) ? (list[0..-2].join(delim) + lastDelim + list[-1]) : list.join(lastDelim)
    }

    static <T extends Collection<?>> T ensureNoNull(T collection) {
        collection?.removeAll { it == null }
        collection
    }

    static <T> Collection<T> difference(Collection<T> obj1, Collection<T> obj2) {
        if (obj1 && obj2) {
            CommonsCollectionUtils.disjunction(obj1, obj2)
        }
        else { CollectionUtils.shallowCopyNoNull(obj1 ?: obj2) }
    }

    static <T> Collection<T> shallowCopyNoNull(Collection<T> obj) {
        Collection<T> newObj = []
        if (obj) {
            newObj.addAll(obj)
        }
        newObj
    }

    static <T> List<T> mergeUnique(Collection<? extends Collection<T>> toBeMerged) {
        List<T> allItems = []
        toBeMerged?.each { Collection<T> items ->
            if (items) {
                allItems.addAll(items)
            }
        }
        CollectionUtils.ensureNoNull(allItems.unique()).toList()
    }
}
