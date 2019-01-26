package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
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

    void "test constraints"() {
    	when: "is empty"
    	FeaturedAnnouncement announce = new FeaturedAnnouncement()

    	then:
    	announce.validate() == false
    	announce.errors.errorCount == 3

    	when: "all fields are filled out"
    	announce = new FeaturedAnnouncement(owner:p1, message:"Hello!",
			expiresAt:DateTime.now().plusDays(2))

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
    	announce.save(flush:true, failOnError:true)

    	then:
    	FeaturedAnnouncement.listForPhone(p1).size() == 1
        FeaturedAnnouncement.countForPhone(p1) == 1
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
        announce.numCallReceipts == 3
        announce.numTextReceipts == 0

    	when: "we add texts"
        int textBaseline = RecordText.count()
        announce.addToReceipts(RecordItemType.TEXT, [ses4, ses2, ses3])
        announce.save(flush:true, failOnError:true)

    	then:
        RecordText.count() == textBaseline //no texts added
        AnnouncementReceipt.count() == aRecBaseline + 6
        announce.numReceipts == 6
        announce.numCallReceipts == 3
        announce.numTextReceipts == 3
    }
}
