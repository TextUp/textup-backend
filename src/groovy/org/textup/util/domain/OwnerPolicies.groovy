package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
class OwnerPolicies {

    // Policies might not all correspond to staff members that are owners on this phone
    // because we also include staff members that can access one of this phone's contacts
    // through a sharing arrangement. Also, if we transfer phones, then the new owners will not
    // correspond with the staff ids in this list of policies
    static Result<OwnerPolicy> tryFindOrCreateForOwnerAndStaffId(PhoneOwnership own1, Long sId) {
        OwnerPolicy op1 = own1.policies?.find { OwnerPolicy op2 -> op2.staff?.id == sId }
        if (op1) {
            IOCUtils.resultFactory.success(op1)
        }
        else { OwnerPolicy.tryCreate(own1, sId) }
    }
}
