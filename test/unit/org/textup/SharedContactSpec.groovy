package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.textup.types.SharePermission
import org.textup.util.CustomSpec
import spock.lang.Ignore
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership])
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
    	sc.errors.errorCount == 4

    	when: "we have a valid SharedContact"
    	sc = new SharedContact(contact:c1, sharedBy:p1, sharedWith:p2,
    		permission:SharePermission.DELEGATE)

    	then:
    	sc.validate() == true

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
    	sWithMe == [sc2]
    	sByMe == [sc1.contact]
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
		sWithMe == []
    	sByMe == [sc1.contact]
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
    }

    @FreshRuntime
    void "test finding by contact id and shared with"() {
    	when: "none are expired"
    	SharedContact sCont = SharedContact.findByContactIdAndSharedWith(c2.id, p1)
		List<SharedContact> scList = SharedContact.findByContactIdsAndSharedWith([c2.id], p1)

    	then: "both show up"
    	sCont == sc2
		scList.size() == 1
		scList[0] == sc2

    	when: "one is expired"
    	sc2.stopSharing()
    	sc2.save(flush:true, failOnError:true)
    	sCont = SharedContact.findByContactIdAndSharedWith(c2.id, p1)
		scList = SharedContact.findByContactIdsAndSharedWith([c2.id], p1)

    	then: "does not show up anymore"
    	sCont == null
		scList.isEmpty() == true
    }
}
