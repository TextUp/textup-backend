package org.textup.rest

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
@TestFor(AnnouncementCallbackService)
class AnnouncementCallbackServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test see announcement via text"() {
        given:
        Phone p1 = TestUtils.buildActiveStaffPhone()
        Phone p2 = TestUtils.buildActiveStaffPhone()

        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(p2)
        IncomingSession is1 = TestUtils.buildSession(p1)
        IncomingSession is2 = TestUtils.buildSession(p2)

        int aRptBaseline = AnnouncementReceipt.count()

        int fallbackCallCount = 0
        Closure fallbackAction = { ++fallbackCallCount; Result.void(); }

        when: "no announcements"
        Result res = service.textSeeAnnouncements(p1, is1, fallbackAction)

        then:
        fallbackCallCount == 1
        res.status == ResultStatus.NO_CONTENT
        AnnouncementReceipt.count() == aRptBaseline

        when:
        res = service.textSeeAnnouncements(p2, is2, fallbackAction)

        then:
        fallbackCallCount == 1
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twilioUtils.announcement")
        AnnouncementReceipt.count() == aRptBaseline + 1
        AnnouncementReceipt.findByAnnouncementAndSession(fa1, is2).type == RecordItemType.TEXT
    }

    void "test change announcement subscription for texts"() {
        given:
        IncomingSession is1 = GroovyMock(IncomingSession)

        when: "is unsubscribed"
        Result res = service.textToggleSubscribe(is1)

        then: "subscribe"
        1 * is1.isSubscribedToText >> false
        1 * is1.setIsSubscribedToText(true)
        TestUtils.buildXml(res.payload).contains("textTwiml.subscribed")

        when: "is subscribed"
        res = service.textToggleSubscribe(is1)

        then: "unsubscribe"
        1 * is1.isSubscribedToText >> true
        1 * is1.setIsSubscribedToText(false)
        TestUtils.buildXml(res.payload).contains("textTwiml.unsubscribed")
    }

    void "test try building text instructions"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(tp1)

        IncomingSession is1 = GroovyMock()

        when: "no announcements"
        Result res = service.tryBuildTextInstructions(p1, is1)

        then:
        res.status == ResultStatus.OK
        res.payload == []

        when: "has announcements, but should NOT send instructions"
        res = service.tryBuildTextInstructions(tp1, is1)

        then:
        1 * is1.shouldSendInstructions >> false
        res.status == ResultStatus.OK
        res.payload == []

        when: "has announcement and should send instructions"
        res = service.tryBuildTextInstructions(tp1, is1)

        then:
        1 * is1.shouldSendInstructions >> true
        1 * is1.updateLastSentInstructions()
        1 * is1.isSubscribedToText >> true
        res.status == ResultStatus.OK
        res.payload == ["announcementCallbackService.instructionsSubscribed"]

        when: "has announcement and should send instructions"
        res = service.tryBuildTextInstructions(tp1, is1)

        then:
        1 * is1.shouldSendInstructions >> true
        1 * is1.updateLastSentInstructions()
        1 * is1.isSubscribedToText >> false
        res.status == ResultStatus.OK
        res.payload == ["announcementCallbackService.instructionsUnsubscribed"]
    }

    void "test toggle subscription for call"() {
        given:
        IncomingSession is1 = GroovyMock()

        when:
        Result res = service.callToggleSubscribe(is1)

        then:
        1 * is1.isSubscribedToCall >> true
        1 * is1.setIsSubscribedToCall(false)
        TestUtils.buildXml(res.payload).contains("callTwiml.unsubscribed")

        when:
        res = service.callToggleSubscribe(is1)

        then:
        1 * is1.isSubscribedToCall >> false
        1 * is1.setIsSubscribedToCall(true)
        TestUtils.buildXml(res.payload).contains("callTwiml.subscribed")
    }

    void "test hear announcements over call"() {
        given:
        Phone tp1 = TestUtils.buildActiveTeamPhone()
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(tp1)
        IncomingSession is1 = TestUtils.buildSession(tp1)

        int aRptBaseline = AnnouncementReceipt.count()

        MockedMethod hearAnnouncements = MockedMethod.create(CallTwiml, "hearAnnouncements") {
            Result.void()
        }

        when:
        Result res = service.callHearAnnouncements(tp1.id, is1)

        then:
        hearAnnouncements.latestArgs == [[fa1], is1.isSubscribedToCall]
        res.status == ResultStatus.NO_CONTENT
        AnnouncementReceipt.count() == aRptBaseline + 1
        AnnouncementReceipt.findByAnnouncementAndSession(fa1, is1).type == RecordItemType.CALL

        cleanup:
        hearAnnouncements?.restore()
    }

    void "test handling call announcements overall"() {
        given:
        String randDigits = TestUtils.randString()

        Phone p1 = TestUtils.buildActiveStaffPhone()
        Phone p2 = TestUtils.buildStaffPhone()
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(p1)

        IncomingSession is1 = GroovyStub() { getIsSubscribedToCall() >> true }
        MockedMethod callHearAnnouncements = MockedMethod.create(service, "callHearAnnouncements") {
            Result.void()
        }
        MockedMethod callToggleSubscribe = MockedMethod.create(service, "callToggleSubscribe") {
            Result.void()
        }
        MockedMethod announcementGreeting = MockedMethod.create(CallTwiml, "announcementGreeting") {
            Result.void()
        }
        int fallbackCallCount = 0
        Closure fallbackAction = { ++fallbackCallCount; Result.void(); }

        when:
        Result res = service.handleAnnouncementCall(null, is1, null, fallbackAction)

        then:
        fallbackCallCount == 1
        callHearAnnouncements.notCalled
        callToggleSubscribe.notCalled
        announcementGreeting.notCalled

        when:
        res = service.handleAnnouncementCall(p1, is1, null, fallbackAction)

        then:
        fallbackCallCount == 1
        callHearAnnouncements.notCalled
        callToggleSubscribe.notCalled
        announcementGreeting.latestArgs == [p1.buildName(), is1.isSubscribedToCall]

        when:
        res = service.handleAnnouncementCall(p1, is1, randDigits, fallbackAction)

        then:
        fallbackCallCount == 2
        callHearAnnouncements.notCalled
        callToggleSubscribe.notCalled
        announcementGreeting.hasBeenCalled

        when:
        res = service.handleAnnouncementCall(p1, is1, CallTwiml.DIGITS_HEAR_ANNOUNCEMENTS, fallbackAction)

        then:
        fallbackCallCount == 2
        callHearAnnouncements.latestArgs == [p1.id, is1]
        callToggleSubscribe.notCalled
        announcementGreeting.hasBeenCalled

        when:
        res = service.handleAnnouncementCall(p1, is1, CallTwiml.DIGITS_TOGGLE_SUBSCRIBE, fallbackAction)

        then:
        fallbackCallCount == 2
        callHearAnnouncements.hasBeenCalled
        callToggleSubscribe.latestArgs == [is1]
        announcementGreeting.hasBeenCalled

        cleanup:
        callHearAnnouncements?.restore()
        callToggleSubscribe?.restore()
        announcementGreeting?.restore()
    }

    void "test complete call announcement"() {
        given:
        TypeMap params = TestUtils.randTypeMap()
        String digits1 = CallTwiml.DIGITS_ANNOUNCEMENT_UNSUBSCRIBE
        String randDigits = TestUtils.randString()

        IncomingSession is1 = GroovyMock()

        MockedMethod announcementAndDigits = MockedMethod.create(CallTwiml, "announcementAndDigits") {
            Result.void()
        }

        when: "unsubscribe"
        Result res = service.completeCallAnnouncement(is1, digits1, params)

        then:
        1 * is1.setIsSubscribedToCall(false)
        announcementAndDigits.notCalled
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("callTwiml.unsubscribed")

        when: "digits do not match unsubscribe"
        res = service.completeCallAnnouncement(is1, randDigits, params)

        then:
        0 * is1._
        announcementAndDigits.latestArgs == [params]
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        announcementAndDigits?.restore()
    }
}
