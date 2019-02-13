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
class PhoneSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        given:
        Staff s1 = TestUtils.buildStaff()

        when:
        Result res = Phone.tryCreate(null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = Phone.tryCreate(s1.id, PhoneOwnershipType.INDIVIDUAL)

        then:
        res.status == ResultStatus.CREATED
        res.payload.isActive() == false
        res.payload.owner instanceof PhoneOwnership
        res.payload.owner.ownerId == s1.id
        res.payload.owner.type == PhoneOwnershipType.INDIVIDUAL
        res.payload.numberAsString == null
    }

    void "test unique phone number constraint"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Team t1 = TestUtils.buildTeam()
        String num1 = TestUtils.randPhoneNumberString()
        String num2 = TestUtils.randPhoneNumberString()

        when:
        Phone p1 = Phone.tryCreate(s1.id, PhoneOwnershipType.INDIVIDUAL).payload
        p1.numberAsString = num1

        then:
        p1.save(flush: true, failOnError: true)

        when:
        Phone p2 = Phone.tryCreate(t1.id, PhoneOwnershipType.GROUP).payload
        p2.numberAsString = num1

        then:
        p2.validate() == false
        p2.errors.getFieldErrorCount("numberAsString") == 1

        when:
        p2.numberAsString = num2

        then:
        p2.save(flush: true, failOnError: true)
    }

    void "test away message constraints"() {
        given: "a phone"
        Phone p1 = TestUtils.buildStaffPhone()

        when: "a phone with blank away message"
        p1.awayMessage = ""

        then: "invalid"
        p1.validate() == false
        p1.errors.getFieldErrorCount("awayMessage") == 1

        when: "too long away message"
        p1.awayMessage = TestUtils.buildVeryLongString()

        then:
        p1.validate() == false
        p1.errors.getFieldErrorCount("awayMessage") == 1

        when: "away message within length constraints"
        p1.awayMessage = TestUtils.randString()

        then:
        p1.validate()
    }

    void "test building away message"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        s1.org.awayMessageSuffix = TestUtils.randString()
        Phone p1 = TestUtils.buildStaffPhone(s1)
        p1.awayMessage = TestUtils.randString()

        expect:
        p1.buildAwayMessage() == p1.awayMessage + " " + s1.org.awayMessageSuffix
    }

    void "test custom account details"() {
        given:
        String accountId = TestUtils.randString()

        when: "no custom account details"
        Phone p1 = new Phone()

        then:
        p1.customAccountId == null

        when: "has custom account details"
        p1.customAccount = Stub(CustomAccountDetails) { getAccountId() >> accountId }

        then:
        p1.customAccountId == accountId
    }

    void "test cascading validation and saving to media object"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        MediaElement e1 = TestUtils.buildMediaElement()
        MediaInfo mInfo = new MediaInfo()
        mInfo.addToMediaElements(e1)
        assert mInfo.validate()

        int miBaseline = MediaInfo.count()
        int meBaseline = MediaElement.count()

        when:
        p1.media = mInfo

        then:
        p1.validate() == true
        MediaInfo.count() == miBaseline
        MediaElement.count() == meBaseline

        when:
        e1.whenCreated = null

        then:
        p1.validate() == false
        p1.errors.getFieldErrorCount("media.mediaElements.0.whenCreated") == 1
        MediaInfo.count() == miBaseline
        MediaElement.count() == meBaseline

        when:
        e1.whenCreated = DateTime.now()
        p1.save(flush: true, failOnError: true)

        then:
        MediaInfo.count() == miBaseline + 1
        MediaElement.count() == meBaseline + 1
    }

    void "test getting voicemail greeting url"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        MediaInfo mInfo = new MediaInfo()
        MediaElement el1 = TestUtils.buildMediaElement()
        el1.sendVersion.type = MediaType.AUDIO_MP3
        mInfo.addToMediaElements(el1)

        String randUrlString = TestUtils.randUrl()
        MockedMethod getLink = TestUtils.mock(el1.sendVersion, "getLink") { new URL(randUrlString) }

        when:
        p1.media = null
        URL greetingUrl = p1.voicemailGreetingUrl

        then:
        getLink.callCount == 0
        greetingUrl == null

        when:
        p1.media = mInfo
        greetingUrl = p1.voicemailGreetingUrl

        then:
        getLink.callCount == 1
        greetingUrl.toString() == randUrlString

        cleanup:
        getLink.restore()
    }

    void "test activating/deactivate phone for numbers and adding to history"() {
        given:
        DateTime dt = JodaUtils.utcNow()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()
        String apiId1 = TestUtils.randString()
        String apiId2 = TestUtils.randString()

        when:
        Phone p1 = TestUtils.buildStaffPhone()

        then:
        p1.numberHistoryEntries == null
        p1.isActive() == false
        p1.buildNumbersForMonth(dt.monthOfYear, dt.year) == []

        when:
        Result res = p1.tryActivate(pNum1, apiId1)

        then: "persistent value for apiId is null"
        res.status == ResultStatus.OK
        res.payload == null
        p1.isActive() == true
        p1.numberHistoryEntries.size() == 1
        p1.numberHistoryEntries[0].numberAsString == pNum1.number
        p1.buildNumbersForMonth(dt.monthOfYear, dt.year) == [pNum1]

        when:
        Phone.withSession { it.flush() }
        res = p1.tryActivate(pNum2, apiId2)

        then:
        res.status == ResultStatus.OK
        res.payload == apiId1
        p1.isActive() == true
        p1.numberHistoryEntries.size() == 2
        p1.numberHistoryEntries[1].numberAsString == pNum2.number
        p1.buildNumbersForMonth(dt.monthOfYear, dt.year).size() == 2
        p1.buildNumbersForMonth(dt.monthOfYear, dt.year).every { it in [pNum1, pNum2] }

        when:
        Phone.withSession { it.flush() }
        res = p1.tryDeactivate()

        then:
        res.status == ResultStatus.OK
        res.payload == apiId2
        p1.isActive() == false
        p1.numberHistoryEntries.size() == 3
        p1.numberHistoryEntries[2].numberAsString == null
        p1.buildNumbersForMonth(dt.monthOfYear, dt.year).size() == 2
        p1.buildNumbersForMonth(dt.monthOfYear, dt.year).every { it in [pNum1, pNum2] }

        when:
        Phone.withSession { it.flush() }
        res = p1.tryActivate(pNum1, apiId1)

        then:
        res.status == ResultStatus.OK
        res.payload == null
        p1.isActive() == true
        p1.numberHistoryEntries.size() == 4
        p1.numberHistoryEntries[3].numberAsString == pNum1.number
        p1.buildNumbersForMonth(dt.monthOfYear, dt.year).size() == 2
        p1.buildNumbersForMonth(dt.monthOfYear, dt.year).every { it in [pNum1, pNum2] }
    }
}
