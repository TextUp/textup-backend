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
