package org.textup.cache

import grails.compiler.GrailsTypeChecked
import grails.plugin.cache.*
import grails.transaction.Transactional
import org.textup.*
import org.textup.type.*
import org.textup.util.*

@GrailsTypeChecked
class PhoneCache {

    // TODO remove?
    // TODO propagate? -- need to decide once we redefine when a phone is active or not
    boolean hasInactivePhone(Long ownerId, PhoneOwnershipType type) {
        Phone p1 = findPhone(ownerId, type, true)
        p1?.isActive == false
    }

    // use Holders.applicationContext.getBean(class) so that internal calls heed AOP advice
    Phone findPhone(Long ownerId, PhoneOwnershipType type, boolean includeInactive = false) {
        Long pId = IOCUtils.getBean(this).findPhoneIdForOwner(ownerId, type)
        Phone p1 = Phone.get(pId) // Phone uses second level cache
        p1?.isActive || includeInactive ? p1 : null
    }

    Result<Long> mustFindPhoneIdForOwner(Long ownerId, PhoneOwnershipType type) {
        Long pId = IOCUtils.getBean(this).findPhoneIdForOwner(ownerId, type)
        if (pId) {
            IOCUtils.resultFactory.success(pId)
        }
        else { // TODO msg
            IOCUtils.resultFactory.failWithCodeAndStatus("phone.notFound", ResultStatus.NOT_FOUND)
        }
    }

    // Must be a non-static public method for Spring AOP advice to apply
    // Key is a SpEL list, see: https://stackoverflow.com/a/17406598
    @CacheEvict(values = "phonesCache", key = "{ #p1, #p2 }")
    Result<Phone> tryUpdateOwner(Phone p1, Long ownerId, PhoneOwnershipType type) {
        p1.owner.ownerId = ownerId
        p1.owner.type = type
        DomainUtils.trySave(p1)
    }

    // Must be a non-static public method for Spring AOP advice to apply
    @Cacheable(values = "phonesCache", key = "{ #p0, #p1 }")
    Long findPhoneIdForOwner(Long ownerId, PhoneOwnershipType type) {
        Phones.buildForOwnerIdAndType(ownerId, type)
            .build(CriteriaUtils.returnsId())
            .list(max: 1)[0]
    }
}
