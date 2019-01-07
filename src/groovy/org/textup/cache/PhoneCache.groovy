package org.textup.cache

import grails.compiler.GrailsTypeChecked
import grails.plugin.cache.*
import grails.transaction.Transactional
import org.textup.*
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
class PhoneCache {

    boolean hasInactivePhone(Long ownerId, PhoneOwnershipType type) {
        Phone p1 = findPhone(ownerId, type, true)
        p1?.isActive == false
    }

    // use Holders.applicationContext.getBean(class) so that internal calls heed AOP advice
    Phone findPhone(Long ownerId, PhoneOwnershipType type, boolean includeInactive = false) {
        Long pId = Holders.applicationContext.getBean(class).findPhoneIdForOwner(ownerId, type)
        Phone p1 = Phone.get(pId) // Phone uses second level cache
        p1.isActive || includeInactive ? p1 : null
    }

    // Key is a SpEL list, see: https://stackoverflow.com/a/17406598
    @CacheEvict(values = "phonesCache", key = "{ #p1, #p2 }")
    Result<Phone> updateOwner(Phone p1, Long ownerId, PhoneOwnershipType type) {
        p1.owner.ownerId = ownerId
        p1.owner.type = type
        if (p1.save()) {
            IOCUtils.resultFactory.success(p1)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(p1.errors) }
    }

    @Cacheable(values = "phonesCache", key = "{ #p0, #p1 }")
    Long findPhoneIdForOwner(Long ownerId, PhoneOwnershipType type) {
        Phone.createCriteria().list(max: 1) {
            projections {
                property("id")
            }
            eq("owner.ownerId", ownerId)
            eq("owner.type", type)
        }[0]
    }
}
