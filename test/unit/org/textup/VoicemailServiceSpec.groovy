package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import org.joda.time.DateTime
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
@TestFor(VoicemailService)
class VoicemailServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    // Voicemail message
    // -----------------

    void "test updating record call with voicemail info"() {
        given:
        RecordCall rCall1 = new RecordCall(record: rText1.record)
        rCall1.save(flush: true, failOnError: true)
        DateTime originalActivityTimestamp = rCall1.record.lastRecordActivity
        List<MediaElement> eList = []
        10.times { eList << TestHelpers.buildMediaElement() }
        eList*.save(flush: true, failOnError: true)

        int mBaseline = MediaInfo.count()
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()

        when: "call does not have media already"
        int duration = 888
        Result<RecordCall> res = service.updateVoicemailForCall(rCall1, duration, eList)
        RecordCall.withSession { it.flush() }

        then:
        res.status == ResultStatus.OK
        res.payload.media != null
        res.payload.hasAwayMessage == true
        res.payload.voicemailInSeconds == duration
        res.payload.record.lastRecordActivity.isAfter(originalActivityTimestamp)
        MediaInfo.count() == mBaseline + 1
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline

        when: "call has existing media"
        duration = 1000
        originalActivityTimestamp = res.payload.record.lastRecordActivity
        res = service.updateVoicemailForCall(rCall1, duration, eList)
        RecordCall.withSession { it.flush() }

        then:
        res.status == ResultStatus.OK
        res.payload.media != null
        res.payload.hasAwayMessage == true
        res.payload.voicemailInSeconds == duration
        res.payload.record.lastRecordActivity.isAfter(originalActivityTimestamp)
        MediaInfo.count() == mBaseline + 1
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline
    }

    @DirtiesRuntime
    void "test processing voicemail message"() {
        given:
        MediaElement e1 = TestHelpers.buildMediaElement()
        e1.save(flush: true, failOnError: true)
        RecordCall rCall1 = new RecordCall(record: rText1.record)
        RecordItemReceipt rpt = TestHelpers.buildReceipt()
        rCall1.addToReceipts(rpt)
        rCall1.save(flush: true, failOnError: true)

        int mBaseline = MediaInfo.count()
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()
        service.incomingMediaService = Mock(IncomingMediaService)
        service.socketService = Mock(SocketService)
        IncomingRecordingInfo ir1 = Mock(IncomingRecordingInfo)
        MockedMethod updateVoicemailForCall = TestHelpers.mock(service, "updateVoicemailForCall")
            { RecordCall call -> new Result(payload: call) }

        when: "incoming media service does not return any media elements"
        ResultGroup<RecordCall> resGroup = service.processVoicemailMessage(rpt.apiId, 0, ir1)
        RecordCall.withSession { it.flush() }

        then: "recording info is marked as PRIVATE"
        1 * ir1.setIsPublic(false)
        1 * service.incomingMediaService.process(*_) >>
            Result.createError(["hi"], ResultStatus.BAD_REQUEST).toGroup()
        0 * service.socketService._
        updateVoicemailForCall.callCount == 0
        resGroup.anyFailures == true
        MediaInfo.count() == mBaseline
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline

        when: "does return some processed media elements"
        resGroup = service.processVoicemailMessage(rpt.apiId, 0, ir1)
        RecordCall.withSession { it.flush() }

        then: "delegates to update call for voicemail helper method"
        1 * ir1.setIsPublic(false)
        1 * service.incomingMediaService.process(*_) >> new Result(payload:[e1]).toGroup()
        1 * service.socketService.sendItems(*_)
        updateVoicemailForCall.callCount == 1
        resGroup.payload.size() == 1
        resGroup.payload[0].id == rCall1.id
    }

    // Voicemail greeting
    // ------------------

    void "test processing voicemail greeting"() {
        given:
        MediaElement e1 = TestHelpers.buildMediaElement()
        e1.save(flush: true, failOnError: true)

        int mBaseline = MediaInfo.count()
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()
        service.incomingMediaService = Mock(IncomingMediaService)
        service.callService = Mock(CallService)
        service.socketService = Mock(SocketService)
        service.threadService = Mock(ThreadService)
        IncomingRecordingInfo ir1 = Mock()

        when:
        Result<Void> res = service.finishedProcessingVoicemailGreeting(null, null, ir1)

        then:
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "voicemailService.finishedProcessingVoicemailGreeting.phoneNotFound"

        when: "error in processing recordings"
        res = service.finishedProcessingVoicemailGreeting(p1.id, null, ir1)
        RecordCall.withSession { it.flush() }

        then: "recordings are marked as PUBLIC"
        1 * ir1.setIsPublic(true)
        1 * service.incomingMediaService.process(*_) >>
            Result.createError(["hi"], ResultStatus.BAD_REQUEST).toGroup()
        0 * service.socketService._
        0 * service.threadService._
        res.status == ResultStatus.BAD_REQUEST
        p1.media == null
        MediaInfo.count() == mBaseline
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline

        when: "successfully processed recordings -- phone does not have existing media"
        res = service.finishedProcessingVoicemailGreeting(p1.id, null, ir1)
        RecordCall.withSession { it.flush() }

        then:
        1 * ir1.setIsPublic(true)
        1 * service.incomingMediaService.process(*_) >> new Result(payload:[e1]).toGroup()
        1 * service.socketService.sendPhone(*_)
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * service.callService.interrupt(*_) >> new Result()
        res.status == ResultStatus.NO_CONTENT
        p1.media != null
        MediaInfo.count() == mBaseline + 1
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline

        when: "successfully processed recordings -- phone has existing media"
        res = service.finishedProcessingVoicemailGreeting(p1.id, null, ir1)
        RecordCall.withSession { it.flush() }

        then:
        1 * ir1.setIsPublic(true)
        1 * service.incomingMediaService.process(*_) >> new Result(payload:[e1]).toGroup()
        1 * service.socketService.sendPhone(*_)
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * service.callService.interrupt(*_) >> new Result()
        res.status == ResultStatus.NO_CONTENT
        p1.media != null
        MediaInfo.count() == mBaseline + 1
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline
    }
}
