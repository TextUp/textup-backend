package org.textup.action

import grails.test.mixin.TestFor
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(PhoneActionService)
class PhoneActionServiceSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
    }

    // TODO From Phone
    // void "test transferring phone"() {
    //     given: "baselines, staff without phone"
    //     int oBaseline = PhoneOwnership.count()
    //     int pBaseline = Phone.count()

    //     Staff noPhoneStaff = new Staff(username:"888-8sta$iterNum",
    //         password:"password", name:"Staff", email:"staff@textup.org",
    //         org:org, personalPhoneAsString:"1112223333", status: StaffStatus.STAFF,
    //         lockCode:Constants.DEFAULT_LOCK_CODE)
    //     noPhoneStaff.save(flush:true, failOnError:true)

    //     when: "transferring to a nonexistent entity"
    //     Result<PhoneOwnership> res = s1.phone.transferTo(-88888,
    //         PhoneOwnershipType.INDIVIDUAL)

    //     then:
    //     res.success == false
    //     res.status == ResultStatus.UNPROCESSABLE_ENTITY
    //     res.errorMessages.size() == 1

    //     when: "transferring to an entity that doesn't already have a phone"
    //     Phone myPhone = s1.phone,
    //         targetPhone = null
    //     def initialOwner = s1,
    //         targetOwner = noPhoneStaff
    //     res = myPhone.transferTo(targetOwner.id, PhoneOwnershipType.INDIVIDUAL)
    //     myPhone.save(flush:true, failOnError:true)

    //     then: "phone is transferred"
    //     res.success == true
    //     res.status == ResultStatus.OK
    //     res.payload instanceof PhoneOwnership
    //     res.payload.ownerId == targetOwner.id
    //     res.payload.type == PhoneOwnershipType.INDIVIDUAL
    //     myPhone.owner.ownerId == targetOwner.id
    //     myPhone.owner.type == PhoneOwnershipType.INDIVIDUAL
    //     PhoneOwnership.count() == oBaseline
    //     Phone.count() == pBaseline

    //     when: "transferring to an entity that has an INactive phone"
    //     initialOwner = targetOwner
    //     targetPhone = s2.phone
    //     targetOwner = s2

    //     targetPhone.deactivate()
    //     targetPhone.save(flush:true, failOnError:true)
    //     res = myPhone.transferTo(targetOwner.id, PhoneOwnershipType.INDIVIDUAL)

    //     then: "phones are swapped"
    //     res.success == true
    //     res.status == ResultStatus.OK
    //     res.payload instanceof PhoneOwnership
    //     res.payload.ownerId == targetOwner.id
    //     res.payload.type == PhoneOwnershipType.INDIVIDUAL
    //     myPhone.owner.ownerId == targetOwner.id
    //     myPhone.owner.type == PhoneOwnershipType.INDIVIDUAL
    //     targetPhone.owner.ownerId == initialOwner.id
    //     targetPhone.owner.type == PhoneOwnershipType.INDIVIDUAL
    //     PhoneOwnership.count() == oBaseline
    //     Phone.count() == pBaseline

    //     when: "transferring to an entity that has an active phone"
    //     initialOwner = targetOwner
    //     targetPhone = t1.phone
    //     targetOwner = t1

    //     assert targetPhone.isActive
    //     res = myPhone.transferTo(targetOwner.id, PhoneOwnershipType.GROUP)

    //     then: "phones are swapped"
    //     res.success == true
    //     res.status == ResultStatus.OK
    //     res.payload instanceof PhoneOwnership
    //     res.payload.ownerId == targetOwner.id
    //     res.payload.type == PhoneOwnershipType.GROUP
    //     myPhone.owner.ownerId == targetOwner.id
    //     myPhone.owner.type == PhoneOwnershipType.GROUP
    //     targetPhone.owner.ownerId == initialOwner.id
    //     targetPhone.owner.type == PhoneOwnershipType.INDIVIDUAL
    //     PhoneOwnership.count() == oBaseline
    //     Phone.count() == pBaseline
    // }
}
