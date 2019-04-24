package org.textup.rest

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
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
@TestFor(IncomingTextService)
class IncomingTextServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test building texts"() {
        given:
        String apiId = TestUtils.randString()
        String message = TestUtils.randString()
        Integer numSegments = TestUtils.randIntegerUpTo(88, true)

        Phone tp1 = TestUtils.buildTeamPhone()
        IncomingSession is1 = TestUtils.buildSession()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        spr1.permission = SharePermission.NONE
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord()
        spr2.permission = SharePermission.DELEGATE

        PhoneRecord.withSession { it.flush() }

        int textBaseline = RecordText.count()

        service.socketService = GroovyMock(SocketService)
        MockedMethod tryMarkUnread = MockedMethod.create(PhoneRecordUtils, "tryMarkUnread") {
            Result.createSuccess([ipr1, spr1, spr2]*.toWrapper())
        }

        when:
        Result res = service.buildTexts(tp1, is1, apiId, message, numSegments)

        then: "errors are ignored + create record items ONLY FOR OWNED CONTACTS"
        tryMarkUnread.latestArgs == [tp1, is1.number]
        1 * service.socketService.sendIndividualWrappers([ipr1, spr1, spr2]*.toWrapper())
        res.status == ResultStatus.CREATED
        res.payload instanceof Collection
        res.payload.size() == 1
        res.payload[0] instanceof RecordText
        res.payload[0].record == ipr1.record
        res.payload[0].contents == message
        res.payload[0].author == Author.create(is1)
        res.payload[0].outgoing == false
        res.payload[0].receipts.size() == 1
        res.payload[0].receipts[0].apiId == apiId
        res.payload[0].receipts[0].contactNumber == is1.number
        res.payload[0].receipts[0].numBillable == numSegments
        RecordText.count() == textBaseline + 1

        cleanup:
        tryMarkUnread?.restore()
    }

    void "test building text response"() {
        given:
        String msg1 = TestUtils.randString()
        Phone p1 = TestUtils.buildStaffPhone()
        IncomingSession is1 = TestUtils.buildSession()
        RecordText rText1 = TestUtils.buildRecordText()

        NotificationGroup notifGroup1 = GroovyMock()
        service.announcementCallbackService = GroovyMock(AnnouncementCallbackService)

        when:
        Result res = service.buildTextResponse(p1, is1, null, notifGroup1)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("textTwiml.blocked")

        when:
        res = service.buildTextResponse(p1, is1, [rText1], notifGroup1)

        then:
        1 * notifGroup1.canNotifyAnyAllFrequencies() >> false
        1 * service.announcementCallbackService.tryBuildTextInstructions(p1, is1) >>
            Result.createSuccess([msg1])
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains(p1.buildAwayMessage().replaceAll(/\s+/, ""))
        TestUtils.buildXml(res.payload).contains(msg1)
        rText1.hasAwayMessage
    }

    void "test processing incoming text media"() {
        given:
        MediaElement el1 = TestUtils.buildMediaElement()

        int mBaseline = MediaInfo.count()

        IncomingMediaInfo im1 = GroovyMock()
        DehydratedNotificationGroup dng1 = GroovyMock()
        NotificationGroup notifGroup1 = GroovyMock()
        service.incomingMediaService = GroovyMock(IncomingMediaService)
        MockedMethod finishProcessing = MockedMethod.create(service, "finishProcessing") {
            Result.void()
        }

        when:
        Result res = service.processMedia(null, dng1)

        then:
        1 * dng1.tryRehydrate() >> Result.createSuccess(notifGroup1)
        finishProcessing.latestArgs == [notifGroup1, null]
        res.status == ResultStatus.NO_CONTENT
        MediaInfo.count() == mBaseline

        when:
        res = service.processMedia([im1], dng1)

        then:
        1 * dng1.tryRehydrate() >> Result.createSuccess(notifGroup1)
        1 * service.incomingMediaService.process([im1]) >> Result.createSuccess([el1])
        finishProcessing.latestArgs[0] == notifGroup1
        finishProcessing.latestArgs[1] instanceof MediaInfo
        el1 in finishProcessing.latestArgs[1].mediaElements
        res.status == ResultStatus.NO_CONTENT
        MediaInfo.count() == mBaseline + 1

        cleanup:
        finishProcessing?.restore()
    }

    void "test finish processing incoming text after done processing media"() {
        given:
        int numNotified = TestUtils.randIntegerUpTo(88, true)

        MediaInfo mInfo1 = TestUtils.buildMediaInfo()
        RecordItem rItem1 = TestUtils.buildRecordItem()

        NotificationGroup notifGroup1 = GroovyMock()
        service.notificationService = GroovyMock(NotificationService)
        service.socketService = GroovyMock(SocketService)

        when:
        Result res = service.finishProcessing(notifGroup1, mInfo1)

        then:
        1 * notifGroup1.eachItem(_ as Closure) >> { args -> args[0].call(rItem1) }
        1 * notifGroup1.getNumNotifiedForItem(rItem1, NotificationFrequency.IMMEDIATELY) >> numNotified
        rItem1.media == mInfo1
        rItem1.numNotified == numNotified
        1 * service.notificationService.send(notifGroup1, NotificationFrequency.IMMEDIATELY) >> Result.void()
        1 * service.socketService.sendItems([rItem1])
        res.status == ResultStatus.NO_CONTENT
    }

    void "test processing overall"() {
        given:
        String apiId = TestUtils.randString()
        String message = TestUtils.randString()
        Integer numSegments = TestUtils.randIntegerUpTo(88, true)

        Phone p1 = TestUtils.buildStaffPhone()
        IncomingSession is1 = TestUtils.buildSession()
        RecordText rText1 = TestUtils.buildRecordText()

        IncomingMediaInfo im1 = GroovyMock()
        NotificationGroup notifGroup1 = GroovyMock()
        DehydratedNotificationGroup dng1 = GroovyMock()
        service.threadService = GroovyMock(ThreadService)
        MockedMethod buildTexts = MockedMethod.create(service, "buildTexts") {
            Result.createSuccess([rText1])
        }
        MockedMethod tryBuildNotificationGroup = MockedMethod.create(NotificationUtils, "tryBuildNotificationGroup") {
            Result.createSuccess(notifGroup1)
        }
        MockedMethod tryCreate = MockedMethod.create(DehydratedNotificationGroup, "tryCreate") {
            Result.createSuccess(dng1)
        }
        MockedMethod processMedia = MockedMethod.create(service, "processMedia") {
            Result.void()
        }
        MockedMethod buildTextResponse = MockedMethod.create(service, "buildTextResponse") {
            Result.void()
        }

        when:
        Result res = service.process(p1, is1, apiId, message, numSegments, [im1])

        then:
        buildTexts.latestArgs == [p1, is1, apiId, message, numSegments]
        tryBuildNotificationGroup.latestArgs == [[rText1]]
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        tryCreate.latestArgs == [notifGroup1]
        processMedia.latestArgs == [[im1], dng1]
        buildTextResponse.latestArgs == [p1, is1, [rText1], notifGroup1]
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        buildTexts?.restore()
        tryBuildNotificationGroup?.restore()
        tryCreate?.restore()
        processMedia?.restore()
        buildTextResponse?.restore()
    }
}
