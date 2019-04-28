package org.textup.cache

import grails.compiler.GrailsTypeChecked
import grails.plugin.cache.*
import grails.transaction.Transactional
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@GrailsTypeChecked
class PhoneCache {

    // need to get wired `PhoneCache` bean so that internal calls heed AOP advice
    Phone findPhone(Long ownerId, PhoneOwnershipType type, boolean includeInactive = false) {
        Long pId = IOCUtils.phoneCache.findAnyPhoneIdForOwner(ownerId, type)
        Phone p1 = Phone.get(pId) // Phone uses second level cache
        p1?.isActive() || includeInactive ? p1 : null
    }

    Result<Long> mustFindAnyPhoneIdForOwner(Long ownerId, PhoneOwnershipType type) {
        Long pId = IOCUtils.phoneCache.findAnyPhoneIdForOwner(ownerId, type)
        if (pId) {
            IOCUtils.resultFactory.success(pId)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("phoneCache.notFound",
                ResultStatus.NOT_FOUND, [ownerId, type])
        }
    }

    // Must be a non-static public method for Spring AOP advice to apply
    // Key is a SpEL list, see: https://stackoverflow.com/a/17406598
    // We use a CachePut here because we want to update the cache values
    // immediately to avoid allowing further calls to findAnyPhoneIdForOwner to potentially
    // restore outdated values. This is why `phoneActionService` which calls this method
    // is in its own transaction that flushes immediately when the method is finished.
    @CachePut(value = "phonesCache", key = "{ #p0, #p1 }")
    Long updateOwner(Long ownerId, PhoneOwnershipType type, Long newPhoneId) {
        newPhoneId
    }

    // Must be a non-static public method for Spring AOP advice to apply
    @Cacheable(value = "phonesCache", key = "{ #p0, #p1 }")
    Long findAnyPhoneIdForOwner(Long ownerId, PhoneOwnershipType type) {
        if (ownerId && type) {
            Phones.buildAnyForOwnerIdAndType(ownerId, type)
                .build(CriteriaUtils.returnsId())
                .list(max: 1)[0] as Long
        }
        else { null }
    }
}
