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
class AnnouncementReceiptSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test static creation + constraints"() {
    	given: "announcement and session"
        Phone p1 = TestUtils.buildStaffPhone()
    	FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(p1)
        IncomingSession is1 = TestUtils.buildSession(p1)
        IncomingSession is2 = TestUtils.buildSession()

    	when: "we have an empty announcement receipt"
        Result res = AnnouncementReceipt.tryCreate(null, null, null)

    	then: "invalid"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

    	when: "we fill out required fields from different phones"
        res = AnnouncementReceipt.tryCreate(fa1, is2, RecordItemType.CALL)

    	then: "invalid"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

    	when: "we fill out required fields from same phone"
        res = AnnouncementReceipt.tryCreate(fa1, is1, RecordItemType.CALL)

    	then: "valid"
        res.status == ResultStatus.CREATED
        res.payload instanceof AnnouncementReceipt
        res.payload.type == RecordItemType.CALL
        res.payload.session == is1
        res.payload.announcement == fa1
    }
}
