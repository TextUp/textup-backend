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
class IncomingSessionSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test static creation + constraints"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        PhoneNumber invalidNum = PhoneNumber.create(TestUtils.randString())
        PhoneNumber pNum = TestUtils.randPhoneNumber()

        when: "we have an empty session"
        Result res = IncomingSession.tryCreate(null, null)

        then: "invalid"
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = IncomingSession.tryCreate(p1, invalidNum)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = IncomingSession.tryCreate(p1, pNum)

        then:
        res.status == ResultStatus.CREATED
        res.payload.phone == p1
        res.payload.numberAsString == pNum.number
    }

    void "test last sent instructions"() {
    	given: "a valid session"
        Phone p1 = TestUtils.buildStaffPhone()
    	IncomingSession is1 = new IncomingSession(phone: p1, number: TestUtils.randPhoneNumber())
    	DateTime currentTimestamp = is1.lastSentInstructions

    	when: "we update last sent instructions"
    	is1.updateLastSentInstructions()

    	then:
    	!is1.lastSentInstructions.isBefore(currentTimestamp)
    	is1.shouldSendInstructions == false

    	when: "we change last sent to yesterday"
    	is1.lastSentInstructions = DateTime.now().minusDays(2)
    	currentTimestamp = is1.lastSentInstructions

    	then: "we should send info again"
    	is1.shouldSendInstructions == true
    }
}
