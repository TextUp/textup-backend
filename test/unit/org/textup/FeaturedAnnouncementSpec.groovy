package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.types.PhoneOwnershipType
import org.textup.types.RecordItemType
import org.textup.types.StaffStatus
import org.textup.util.CustomSpec
import spock.lang.Ignore
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt])
@TestMixin(HibernateTestMixin)
class FeaturedAnnouncementSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    }

    def cleanup() {
    	cleanupData()
    }

    protected ResultFactory getResultFactory() {
   		grailsApplication.mainContext.getBean("resultFactory")
    }

    void "test constraints"() {
    	when: "is empty"
    	FeaturedAnnouncement announce = new FeaturedAnnouncement()
    	announce.resultFactory = getResultFactory()

    	then:
    	announce.validate() == false
    	announce.errors.errorCount == 3

    	when: "all fields are filled out"
    	announce = new FeaturedAnnouncement(owner:p1, message:"Hello!",
			expiresAt:DateTime.now().plusDays(2))
    	announce.resultFactory = getResultFactory()

    	then:
    	announce.validate() == true
    }

    void "test adding receipts"() {
    	given: "sessions"
    	IncomingSession ses1 = new IncomingSession(phone:p1, numberAsString:"2223334444"),
    		ses2 = new IncomingSession(phone:p1, numberAsString:"2223334445"),
    		ses3 = new IncomingSession(phone:p1, numberAsString:"2223334446"),
    		ses4 = new IncomingSession(phone:p1, numberAsString:"2223334447")
    	[ses1, ses2, ses3, ses4]*.save(flush:true, failOnError:true)

    	when: "we have a valid announcement"
    	FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
    		message:"Hello!", expiresAt:DateTime.now().plusDays(2))
    	announce.resultFactory = getResultFactory()
    	announce.save(flush:true, failOnError:true)

    	then:
    	FeaturedAnnouncement.listForPhone(p1).size() == 1
    	FeaturedAnnouncement.listForPhone(p1)[0] == announce

    	when: "we add calls"
    	int callBaseline = RecordCall.count(),
            aRecBaseline = AnnouncementReceipt.count()
    	announce.addToReceipts(RecordItemType.CALL, [ses1, ses2, ses3])
    	announce.save(flush:true, failOnError:true)

    	then:
    	RecordCall.count() == callBaseline //no calls added
        AnnouncementReceipt.count() == aRecBaseline + 3
    	announce.numReceipts == 3

    	when: "we add texts"
        int textBaseline = RecordText.count()
        announce.addToReceipts(RecordItemType.TEXT, [ses4, ses2, ses3])
        announce.save(flush:true, failOnError:true)

    	then:
        RecordText.count() == textBaseline //no texts added
        AnnouncementReceipt.count() == aRecBaseline + 6
        announce.numReceipts == 6
    }
}
