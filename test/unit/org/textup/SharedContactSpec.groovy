package org.textup

import grails.gorm.DetachedCriteria
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.textup.rest.NotificationStatus
import org.textup.type.ContactStatus
import org.textup.type.NotificationLevel
import org.textup.type.SharePermission
import org.textup.type.VoiceLanguage
import org.textup.util.*
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Unroll

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class SharedContactSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    }

    def cleanup() {
    	cleanupData()
    }

    void "test constraints"() {
    	when: "empty shared contact"
    	SharedContact sc = new SharedContact()

    	then: "invalid"
    	sc.validate() == false
    	sc.errors.errorCount == 5

    	when: "we have a valid SharedContact"
    	sc = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p2,
    		permission:SharePermission.DELEGATE)

    	then:
    	sc.validate() == true
        sc.fromNum.number == p1.number.number
        sc1.status == c1.status // copied contact's status over

    	when: "we try to share a contact that is not our's"
    	sc = new SharedContact(contact:c2, sharedBy:p1, sharedWith:p2,
    		permission:SharePermission.DELEGATE)

    	then:
    	sc.validate() == false
    	sc.errors.errorCount == 1

    	when: "we try to share a contact with ourselves"
    	sc = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p1,
    		permission:SharePermission.DELEGATE)

    	then:
    	sc.validate() == false
    	sc.errors.errorCount == 1

    	when: "we have a SharedContact with two phones belonging to staff on different teams"
    	sc = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p3,
    		permission:SharePermission.DELEGATE)

    	then: "still valid, check for same teams happens when sharing through a phone"
    	sc.validate() == true
        sc.fromNum.number == p1.number.number
        sc1.status == c1.status // copied contact's status over

        when: "we set divergent statuses for a SharedContact that already has a set status"
        sc1.status = ContactStatus.ACTIVE
        c1.status = ContactStatus.UNREAD

        then: "two separate copies are maintained"
        sc.validate() == true
        sc1.status != c1.status
        sc1.status == ContactStatus.ACTIVE
    }

    @Unroll
    void "test getting records for status #status"() {
        given: "valid shared contacts with various permissions"
        SharedContact sc1 = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p3)
        switch (status) {
            case "expired":
                sc1.permission = SharePermission.DELEGATE; sc1.stopSharing(); break;
            case "view":
                sc1.permission = SharePermission.VIEW; break;
            case "delegate":
                sc1.permission = SharePermission.DELEGATE; break;
        }
        assert sc1.validate() == true

        expect:
        !!sc1.record == canGetRecord
        !!sc1.readOnlyRecord == canGetReadOnlyRecord

        where:
        status     | canGetRecord | canGetReadOnlyRecord
        "expired"  | false        | false
        "view"     | false        | true
        "delegate" | true         | true
    }

    void "test sharing permissions and expiration"() {
		given: "two shared contacts"
		sc1.permission = SharePermission.DELEGATE
		sc2.permission = SharePermission.VIEW
		[sc1, sc2]*.save(flush:true, failOnError:true)

		when:
		List<SharedContact> sWithMe = SharedContact.listSharedWithMe(p1)
    	List<SharedContact> sByMe = SharedContact.listSharedByMe(p1)

    	then:
        sWithMe.size() == 1
    	sWithMe[0].id == sc2.id
        sByMe.size() == 1
    	sByMe[0].id == sc1.contact.id
    	sc1.isActive == true
		sc2.isActive == true
		sc1.canModify == true
		sc1.canView == true
		sc2.canModify == false
		sc2.canView == true

		SharedContact.listForContact(c1).size() == 1
    	SharedContact.listForContact(c1)[0] == sc1
    	SharedContact.listForContact(c2).size() == 1
    	SharedContact.listForContact(c2)[0] == sc2
		SharedContact.listForContactAndSharedWith(c1, p2).size() == 1
		SharedContact.listForContactAndSharedWith(c1, p2)[0] == sc1
		SharedContact.listForSharedByAndSharedWith(p2, p1).size() == 1
		SharedContact.listForSharedByAndSharedWith(p2, p1)[0] == sc2

    	when: "we expire sc2"
    	sc2.stopSharing()
    	sc2.save(flush:true, failOnError:true)
    	sWithMe = SharedContact.listSharedWithMe(p1)
		sByMe = SharedContact.listSharedByMe(p1)

		then:
		sWithMe.isEmpty()
        sByMe.size() == 1
        sByMe[0].id == sc1.contact.id
    	sc1.isActive == true
		sc2.isActive == false
		sc1.canModify == true
		sc1.canView == true
		sc2.canModify == false
		sc2.canView == false

		// these three don't exclude expired shared contacts!!!
		SharedContact.listForContact(c1).size() == 1
    	SharedContact.listForContact(c1)[0] == sc1
    	SharedContact.listForContact(c2).size() == 1
    	SharedContact.listForContact(c2)[0] == sc2
		SharedContact.listForContactAndSharedWith(c1, p2).size() == 1
		SharedContact.listForContactAndSharedWith(c1, p2)[0] == sc1
		SharedContact.listForContactAndSharedWith(c2, p1).size() == 1
		SharedContact.listForContactAndSharedWith(c2, p1)[0] == sc2
		SharedContact.listForSharedByAndSharedWith(p2, p1).size() == 1
		SharedContact.listForSharedByAndSharedWith(p2, p1)[0] == sc2

        when: "we deactivate p1, the phone that shared sc1"
        p1.deactivate()
        p1.save(flush:true, failOnError:true)
        assert !p1.isActive
        sWithMe = SharedContact.listSharedWithMe(p1)
        sByMe = SharedContact.listSharedByMe(p1)

        then:
        sWithMe.isEmpty()
        sByMe.isEmpty()
        sc1.isActive == true
        sc2.isActive == false
        sc1.canModify == true
        sc1.canView == true
        sc2.canModify == false
        sc2.canView == false

        // these three don't exclude expired shared contacts!!!
        SharedContact.listForContact(c1).size() == 0
        SharedContact.listForContact(c2).size() == 1
        SharedContact.listForContact(c2)[0] == sc2
        SharedContact.listForContactAndSharedWith(c1, p2).size() == 0
        SharedContact.listForContactAndSharedWith(c2, p1).size() == 1
        SharedContact.listForContactAndSharedWith(c2, p1)[0] == sc2
        SharedContact.listForSharedByAndSharedWith(p2, p1).size() == 1
        SharedContact.listForSharedByAndSharedWith(p2, p1)[0] == sc2
    }

    @FreshRuntime
    void "test finding by contact id and shared with for expiration"() {
    	when: "none are expired"
    	SharedContact sCont = SharedContact.findByContactIdAndSharedWith(c2.id, p1)
		List<SharedContact> scList = SharedContact.findEveryByContactIdsAndSharedWith([c2.id], p1)

    	then: "both show up"
    	sCont == sc2
		scList.size() == 1
		scList[0] == sc2

    	when: "one is expired"
    	sc2.stopSharing()
    	sc2.save(flush:true, failOnError:true)
    	sCont = SharedContact.findByContactIdAndSharedWith(c2.id, p1)
		scList = SharedContact.findEveryByContactIdsAndSharedWith([c2.id], p1)

    	then: "does not show up anymore"
    	sCont == null
		scList.isEmpty() == true
    }

    @FreshRuntime
    void "test finding by contact id and shared with for deactivation"() {
        when: "none are expired"
        SharedContact sCont = SharedContact.findByContactIdAndSharedWith(c2.id, p1)
        List<SharedContact> scList = SharedContact.findEveryByContactIdsAndSharedWith([c2.id], p1)

        then: "both show up"
        sCont == sc2
        scList.size() == 1
        scList[0] == sc2

        when: "the phone shared with is deactivated"
        p1.deactivate()
        p1.save(flush:true, failOnError:true)
        assert !p1.isActive
        sCont = SharedContact.findByContactIdAndSharedWith(c2.id, p1)
        scList = SharedContact.findEveryByContactIdsAndSharedWith([c2.id], p1)

        then: "both show up"
        sCont == sc2
        scList.size() == 1
        scList[0] == sc2

        when: "the phone doing the sharing (sharedBy) is deactivated"
        p2.deactivate()
        p2.save(flush:true, failOnError:true)
        assert !p2.isActive
        sCont = SharedContact.findByContactIdAndSharedWith(c2.id, p1)
        scList = SharedContact.findEveryByContactIdsAndSharedWith([c2.id], p1)

        then: "does not show up anymore"
        sCont == null
        scList.isEmpty() == true
    }

    @FreshRuntime
    void "test finding by contact ids and shared by for expiration"() {
        when: "none are expired"
        SharedContact sCont = SharedContact.findByContactIdAndSharedBy(c2.id, p2)
        List<SharedContact> scList = SharedContact.findEveryByContactIdsAndSharedBy([c2.id], p2)

        then: "both show up"
        sCont == sc2
        scList.size() == 1
        scList[0] == sc2

        when: "one is expired"
        sc2.stopSharing()
        sc2.save(flush:true, failOnError:true)
        sCont = SharedContact.findByContactIdAndSharedBy(c2.id, p2)
        scList = SharedContact.findEveryByContactIdsAndSharedBy([c2.id], p2)

        then: "does not show up anymore"
        sCont == null
        scList.isEmpty() == true
    }

    @FreshRuntime
    void "test finding by contact ids and shared by for deactivation"() {
        when: "none are expired"
        SharedContact sCont = SharedContact.findByContactIdAndSharedBy(c2.id, p2)
        List<SharedContact> scList = SharedContact.findEveryByContactIdsAndSharedBy([c2.id], p2)

        then: "both show up"
        sCont == sc2
        scList.size() == 1
        scList[0] == sc2

        when: "phone shared with is deactivated"
        p1.deactivate()
        p1.save(flush:true, failOnError:true)
        sCont = SharedContact.findByContactIdAndSharedBy(c2.id, p2)
        scList = SharedContact.findEveryByContactIdsAndSharedBy([c2.id], p2)

        then: "both still show up"
        sCont == sc2
        scList.size() == 1
        scList[0] == sc2

        when: "phone doing the sharing (sharedBy) is deactivated"
        p2.deactivate()
        p2.save(flush:true, failOnError:true)
        sCont = SharedContact.findByContactIdAndSharedBy(c2.id, p2)
        scList = SharedContact.findEveryByContactIdsAndSharedBy([c2.id], p2)

        then: "does not show up anymore"
        sCont == null
        scList.isEmpty() == true
    }

    void "test static finders for contact deletion"() {
        given: "two valid phones"
        Phone phone1 = new Phone(numberAsString:TestHelpers.randPhoneNumber())
        phone1.resultFactory = getResultFactory()
        phone1.resultFactory.messageSource = messageSource
        phone1.updateOwner(s1)
        phone1.save(flush:true, failOnError:true)
        Phone phone2 = new Phone(numberAsString:TestHelpers.randPhoneNumber())
        phone2.resultFactory = getResultFactory()
        phone2.resultFactory.messageSource = messageSource
        phone2.updateOwner(s2)
        phone2.save(flush:true, failOnError:true)
        assert phone1.canShare(phone2) == true

        when: "valid contacts and shared contacts"
        Contact contact1 = phone1.createContact([:], [TestHelpers.randPhoneNumber()]).payload
        SharedContact sc1 = phone1.share(contact1, phone2, SharePermission.DELEGATE).payload
        SharedContact.withSession { it.flush() }

        then:
        SharedContact.listForContact(contact1)[0]?.id == sc1.id
        SharedContact.listForContactAndSharedWith(contact1, phone2)[0]?.id == sc1.id
        SharedContact.listForSharedByAndSharedWith(phone1, phone2)[0]?.id == sc1.id
        SharedContact.countSharedWithMe(phone2) == 1
        SharedContact.listSharedWithMe(phone2)[0]?.id == sc1.id
        SharedContact.countSharedByMe(phone1) == 1
        SharedContact.listSharedByMe(phone1)[0]?.id == sc1.contactId
        SharedContact.findByContactIdAndSharedWith(contact1.id, phone2)?.id == sc1.id
        SharedContact.findEveryByContactIdsAndSharedWith([contact1.id], phone2)[0]?.id == sc1.id
        SharedContact.findByContactIdAndSharedBy(contact1.id, phone1)?.id == sc1.id
        SharedContact.findEveryByContactIdsAndSharedBy([contact1.id], phone1)[0]?.id == sc1.id

        when: "contact marked as deleted"
        contact1.isDeleted = true
        contact1.save(flush:true, failOnError:true)

        then:
        SharedContact.listForContact(contact1).isEmpty() == true
        SharedContact.listForContactAndSharedWith(contact1, phone2).isEmpty() == true
        SharedContact.listForSharedByAndSharedWith(phone1, phone2).isEmpty() == true
        SharedContact.countSharedWithMe(phone2) == 0
        SharedContact.listSharedWithMe(phone2).isEmpty() == true
        SharedContact.countSharedByMe(phone1) == 0
        SharedContact.listSharedByMe(phone1).isEmpty() == true
        SharedContact.findByContactIdAndSharedWith(contact1.id, phone2) == null
        SharedContact.findEveryByContactIdsAndSharedWith([contact1.id], phone2).isEmpty() == true
        SharedContact.findByContactIdAndSharedBy(contact1.id, phone1) == null
        SharedContact.findEveryByContactIdsAndSharedBy([contact1.id], phone1).isEmpty() == true
    }

    void "test getting notification statuses for a shared contact"() {
        given: "sharedBy owner with some policies"
        PhoneOwnership sByOwner = sc1.sharedBy.owner
        PhoneOwnership sWithOwner = sc1.sharedWith.owner
        assert sByOwner.policies == null
        assert sWithOwner.policies == null

        Long noNotifyStaffId = sWithOwner.all[0].id
        NotificationPolicy np1 = new NotificationPolicy(staffId:noNotifyStaffId,
            level:NotificationLevel.NONE)
        sByOwner.addToPolicies(np1)
        [sByOwner, np1]*.save(flush:true, failOnError:true)
        assert sByOwner.policies != null

        when: "we ask about notification statuses"
        List<NotificationStatus> statuses = sc1.getNotificationStatuses()
        List<Staff> sWithStaffIds = sWithOwner.all*.id

        then: "we look in the sharedBy owner's policies NOT the sharedWith owner's policies \
            and the staff members we return are the staff members are from the sharedWith owner"
        statuses.isEmpty() == false
        statuses.size() == sWithStaffIds.size()
        statuses.each { NotificationStatus stat1 ->
            if (stat1.staff.id == noNotifyStaffId) {
                assert stat1.canNotify == false
            }
            else {
                assert sWithStaffIds.contains(stat1.staff.id) && stat1.canNotify == true
            }
        }
    }

    void "test building detached criteria for records"() {
        given: "valid contacts and shared contacts"
        Contact contact1 = p1.createContact([:], [TestHelpers.randPhoneNumber()]).payload
        Contact contact2 = p1.createContact([:], [TestHelpers.randPhoneNumber()]).payload

        SharedContact sc1 = p1.share(contact1, p2, SharePermission.DELEGATE).payload
        SharedContact sc2 = p1.share(contact2, p2, SharePermission.DELEGATE).payload

        SharedContact.withSession { it.flush() }

        when: "build detached criteria for these items"
        DetachedCriteria<SharedContact> detachedCrit = SharedContact.buildForContacts([contact1, contact2])
        List<SharedContact> sharedList = detachedCrit.list()
        Collection<Long> targetIds = [sc1, sc2]*.id

        then: "we are able to fetch these items back from the db"
        sharedList.size() == 2
        sharedList.every { it.id in targetIds }

        when: "contacts are marked as deleted"
        [contact1, contact2].each { Contact contact ->
            contact.isDeleted = true
            contact.save(flush:true, failOnError:true)
        }
        detachedCrit = SharedContact.buildForContacts([contact1, contact2])
        sharedList = detachedCrit.list()

        then: "still shows up because this detached criteria (used for bulk operations) cannot have joins"
        sharedList.size() == 2
        sharedList.every { it.id in targetIds }
    }
}
