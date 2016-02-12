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
class AnnouncementReceiptSpec extends CustomSpec {

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
    	given: "announcement and session"
    	FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
    		message:"Hello!", expiresAt:DateTime.now().plusDays(2))
    	IncomingSession session = new IncomingSession(phone:p1, numberAsString:"2223334444"),
    		otherSess = new IncomingSession(phone:p2, numberAsString:"2223334444")
    	[announce, session, otherSess]*.save(flush:true, failOnError:true)

    	when: "we have an empty announcement receip"
    	AnnouncementReceipt aRec = new AnnouncementReceipt()

    	then: "invalid"
    	aRec.validate() == false
    	aRec.errors.errorCount == 3

    	when: "we fill out required fields from different phones"
    	aRec = new AnnouncementReceipt(announcement:announce, session:otherSess,
			type:RecordItemType.CALL)

    	then: "invalid"
    	aRec.validate() == false
    	aRec.errors.errorCount == 1

    	when: "we fill out required fields from same phone"
    	aRec = new AnnouncementReceipt(announcement:announce, session:session,
			type:RecordItemType.CALL)

    	then: "valid"
    	aRec.validate() == true
    	aRec.save(flush:true, failOnError:true)
    }
}
