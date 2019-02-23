package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.quartz.*
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
class SimpleFutureMessageSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
        IOCUtils.metaClass."static".getQuartzScheduler = { -> TestUtils.mockScheduler() }
    }

    void "test constraints"() {
    	when: "an empty future message"
    	SimpleFutureMessage sMsg = new SimpleFutureMessage()

    	then:
    	sMsg.validate() == false
    	sMsg.errors.errorCount == 3
    	sMsg.errors.getFieldErrorCount("record") == 1
    	sMsg.errors.getFieldErrorCount("media") == 1
    	sMsg.errors.getFieldErrorCount("type") == 1

    	when: "all required fields filled out"
    	sMsg.type = FutureMessageType.TEXT
    	sMsg.record = TestUtils.buildRecord()
    	sMsg.message = TestUtils.randString()

    	then: "ok"
    	sMsg.validate() == true

    	when: "repeat interval too small"
    	sMsg.message = "hi"
    	assert sMsg.validate()
    	sMsg.repeatIntervalInMillis = 2000

    	then:
    	sMsg.validate() == false
    	sMsg.errors.errorCount == 1
    	sMsg.errors.getFieldErrorCount("repeatIntervalInMillis") == 1

    	when: "try to repeat infinitely"
    	sMsg.repeatIntervalInDays = 1
    	assert sMsg.validate()
    	sMsg.repeatCount = SimpleTrigger.REPEAT_INDEFINITELY

    	then:
    	sMsg.validate() == false
    	sMsg.errors.getFieldErrorCount("repeatCount") == 1
    }

    void "test static creation"() {
        given:
        String message = TestUtils.randString()

        Record rec1 = TestUtils.buildRecord()
        rec1.language = VoiceLanguage.ITALIAN
        MediaInfo mInfo = TestUtils.buildMediaInfo()

        when:
        Result res = SimpleFutureMessage.tryCreate(null, null, null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = SimpleFutureMessage.tryCreate(rec1, FutureMessageType.CALL, message, mInfo)

        then:
        res.status == ResultStatus.CREATED
        res.payload.type == FutureMessageType.CALL
        res.payload.record == rec1
        res.payload.message == message
        res.payload.media == mInfo
        res.payload.language == rec1.language
    }

    void "test repeating"() {
    	given: "an empty future message"
    	SimpleFutureMessage sMsg = new SimpleFutureMessage()

    	when: "neither repeatCount nor end date specified"
    	assert sMsg.repeatCount == null
    	assert sMsg.endDate == null

    	then:
    	sMsg.isRepeating == false

    	when: "repeat count specified"
    	sMsg.repeatCount = 2

    	then: "end after repeat count"
    	sMsg.isRepeating == true

    	when: "repeat count AND end date specified"
    	assert sMsg.repeatCount != null
    	sMsg.endDate = DateTime.now().plusDays(1)

    	then: "end after end date (takes precedence over repeatCount)"
    	sMsg.isRepeating == true
    }
}
