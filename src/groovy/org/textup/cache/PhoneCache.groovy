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
        PhoneCache phoneCache = IOCUtils.getBean(PhoneCache)
        Long pId = phoneCache.findAnyPhoneIdForOwner(ownerId, type)
        Phone p1 = Phone.get(pId) // Phone uses second level cache
        p1?.isActive() || includeInactive ? p1 : null
    }

    Result<Long> mustFindAnyPhoneIdForOwner(Long ownerId, PhoneOwnershipType type) {
        PhoneCache phoneCache = IOCUtils.getBean(PhoneCache)
        Long pId = phoneCache.findAnyPhoneIdForOwner(ownerId, type)
        if (pId) {
            IOCUtils.resultFactory.success(pId)
        }
        else { // TODO msg
            IOCUtils.resultFactory.failWithCodeAndStatus("phone.notFound", ResultStatus.NOT_FOUND)
        }
    }

    // Must be a non-static public method for Spring AOP advice to apply
    // Key is a SpEL list, see: https://stackoverflow.com/a/17406598
    @CacheEvict(value = Constants.CACHE_PHONES, key = "{ #p1, #p2 }")
    Result<Phone> tryUpdateOwner(Phone p1, Long ownerId, PhoneOwnershipType type) {
        p1.owner.ownerId = ownerId
        p1.owner.type = type
        DomainUtils.trySave(p1)
    }

    // Must be a non-static public method for Spring AOP advice to apply
    @Cacheable(value = Constants.CACHE_PHONES, key = "{ #p0, #p1 }")
    Long findAnyPhoneIdForOwner(Long ownerId, PhoneOwnershipType type) {
        Phones.buildAnyForOwnerIdAndType(ownerId, type)
            .build(CriteriaUtils.returnsId())
            .list(max: 1)[0] as Long
    }
}
