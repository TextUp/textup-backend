package org.textup.util

import grails.gorm.DetachedCriteria
import groovy.util.logging.Log4j
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

// [NOTE] Several parts of the Grails Criteria DSL do not have custom type checking available

@Log4j
class CriteriaUtils {

    // if the Collection passed to the "in" Criteria clause is empty, then MySQL (but not H2)
    // will throw an exception
    static void inList(Object delegate, String propName, Collection<?> objList,
        boolean optional = false) {

        ClosureUtils.compose(delegate) {
            if (objList) {
                "in"(propName, objList)
            }
            else if (!optional) {
                eq(propName, null)
            }
        }
    }

    static Closure returnsId() {
        return {
            projections {
                property("id")
            }
        }
    }

    static Closure forNotIdIfPresent(Long thisId) {
        return {
            if (thisId != null) {
                ne("id", thisId)
            }
        }
    }

    static Closure<Integer> countAction(DetachedCriteria<?> criteria) {
        return { criteria ? criteria.count() : 0 }
    }

    // Ensures a no-join query when attempting to batch update
    static Number updateAll(Class clazz, Map properties, Closure<Collection<Long>> getIds) {
        new DetachedCriteria(clazz)
            .build { CriteriaUtils.inList(delegate, "id", getIds.call()) }
            .updateAll(properties)
    }
}
