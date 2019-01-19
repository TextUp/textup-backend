package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class OwnerPolicies {

    // TODO consider sharing availability???
    // Policies might not all correspond to staff members that are owners on this phone
    // because we also include staff members that can access one of this phone's contacts
    // through a sharing arrangement. Also, if we transfer phones, then the new owners will not
    // correspond with the staff ids in this list of policies
    static Result<OwnerPolicy> tryFindOrCreateForOwnerAndStaffId(PhoneOwnership own1, Long sId) {
        OwnerPolicy op1 = own1.policies?.find { OwnerPolicy op2 -> op2.staff?.id == staffId }
        if (op1) {
            IOCUtils.resultFactory.success(op1)
        }
        else { OwnerPolicy.tryCreate(own1, staffId) }
    }
}
