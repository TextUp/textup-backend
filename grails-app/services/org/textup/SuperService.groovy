package org.textup

import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.domain.*

@Transactional
class SuperService {

    Collection<Staff> getAdminsForOrgId(Long orgId) {
        Staffs.buildForOrgIdAndOptions(orgId, null, [StaffStatus.ADMIN]).list()
    }
}
