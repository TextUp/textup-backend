package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

// [NOTE} this class exists and has two separate criteria for finding staff and team phones because
// subqueries cannot include an `or` clause or else results in an NPE because of an existing bug.
// Therefore, we move `or` out of the subquery and return a closure instead of a `DetachedCriteria`
// see: https://github.com/grails/grails-data-mapping/issues/655

class PhoneOwnerships {

    static DetachedCriteria<PhoneOwnership> buildAnyStaffPhonesForStaffId(Long staffId) {
        new DetachedCriteria(PhoneOwnership).build {
            eq("type", PhoneOwnershipType.INDIVIDUAL)
            eq("ownerId", staffId)
        }
    }

    static DetachedCriteria<PhoneOwnership> buildAnyTeamPhonesForStaffId(Long staffId) {
        new DetachedCriteria(PhoneOwnership).build {
            eq("type", PhoneOwnershipType.GROUP)
            "in"("ownerId", Teams
                .buildActiveForStaffIds([staffId])
                .build(CriteriaUtils.returnsId()))
        }
    }

    static Closure returnsPhoneId() {
        return {
            projections {
                property("phone.id")
            }
        }
    }
}
