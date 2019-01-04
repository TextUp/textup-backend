package org.textup.util

import groovy.util.logging.Log4j

// [NOTE] Several parts of the Grails Criteria DSL do not have custom type checking available

@Log4j
class CriteriaUtils {

    // if the Collection passed to the "in" Criteria clause is empty, then MySQL (but not H2)
    // will throw an exception
    static void inList(Object delegate, String propName, Collection<?> objList,
        boolean optional = false) {

        Closure inCriteria = {
            if (objList) {
                "in"(propName, objList)
            }
            else if (!optional) {
                eq(propName, null)
            }
        }
        inCriteria.delegate = delegate
        inCriteria.call()
    }
}
