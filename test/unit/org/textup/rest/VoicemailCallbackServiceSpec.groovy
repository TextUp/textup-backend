package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import org.joda.time.DateTime
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
@TestFor(VoicemailCallbackService)
class VoicemailCallbackServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test processing voicemail message"() {
        given:
        int duration = TestUtils.randIntegerUpTo(88)
        RecordCall rCall1 = TestUtils.buildRecordCall()
        RecordItemReceipt rpt1 = TestUtils.buildReceipt()
        rCall1.addToReceipts(rpt1)
        MediaElement el1 = TestUtils.buildMediaElement()

        service.incomingMediaService = GroovyMock(IncomingMediaService)
        service.socketService = GroovyMock(SocketService)
        IncomingRecordingInfo ir1 = GroovyMock()
        MockedMethod tryUpdateVoicemail = MockedMethod.create(RecordCall, "tryUpdateVoicemail")
            { RecordCall call -> Result.createSuccess(call) }

        when: "incoming media service does not return any media elements"
        Result res = service.processVoicemailMessage(rpt1.apiId, duration, ir1)

        then: "recording info is marked as PRIVATE"
        1 * ir1.setIsPublic(false)
        1 * service.incomingMediaService.process([ir1]) >> Result.createError([], ResultStatus.BAD_REQUEST)
        tryUpdateVoicemail.notCalled
        0 * service.socketService._
        res.status == ResultStatus.BAD_REQUEST

        when: "does return some processed media elements"
        res = service.processVoicemailMessage(rpt1.apiId, duration, ir1)

        then: "delegates to update call for voicemail helper method"
        1 * ir1.setIsPublic(false)
        1 * service.incomingMediaService.process([ir1]) >> Result.createSuccess([el1])
        tryUpdateVoicemail.latestArgs == [rCall1, duration, [el1]]
        1 * service.socketService.sendItems([rCall1])
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryUpdateVoicemail?.restore()
    }

    void "test processing voicemail greeting"() {
        given:
        String callId = TestUtils.randString()

        Phone p1 = TestUtils.buildActiveStaffPhone()
        CustomAccountDetails cad1 = TestUtils.buildCustomAccountDetails()
        p1.customAccount = cad1
        MediaElement el1 = TestUtils.buildMediaElement()

        int mBaseline = MediaInfo.count()
        int eBaseline = MediaElement.count()
        int vBaseline = MediaElementVersion.count()

        IncomingRecordingInfo ir1 = GroovyMock()
        service.incomingMediaService = GroovyMock(IncomingMediaService)
        service.socketService = GroovyMock(SocketService)
        service.threadService = GroovyMock(ThreadService)
        service.callService = GroovyMock(CallService)

        when:
        Result res = service.finishProcessingVoicemailGreeting(null, null, null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when: "error in processing recordings"
        res = service.finishProcessingVoicemailGreeting(p1.id, callId, ir1)
        RecordCall.withSession { it.flush() }

        then: "recordings are marked as PUBLIC"
        1 * ir1.setIsPublic(true)
        1 * service.incomingMediaService.process([ir1]) >> Result.createError([], ResultStatus.BAD_REQUEST)
        res.status == ResultStatus.BAD_REQUEST
        p1.media == null
        MediaInfo.count() == mBaseline
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline

        when: "successfully processed recordings -- phone does not have existing media"
        res = service.finishProcessingVoicemailGreeting(p1.id, callId, ir1)
        RecordCall.withSession { it.flush() }

        then:
        1 * ir1.setIsPublic(true)
        1 * service.incomingMediaService.process([ir1]) >> Result.createSuccess([el1])
        1 * service.socketService.sendPhone(p1)
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * service.callService.interrupt(callId, _, cad1.accountId) >> Result.void()
        res.status == ResultStatus.NO_CONTENT
        p1.media != null
        el1 in p1.media.mediaElements
        MediaInfo.count() == mBaseline + 1
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline

        when: "successfully processed recordings -- phone has existing media"
        res = service.finishProcessingVoicemailGreeting(p1.id, callId, ir1)
        RecordCall.withSession { it.flush() }

        then:
        1 * ir1.setIsPublic(true)
        1 * service.incomingMediaService.process([ir1]) >> Result.createSuccess([el1])
        1 * service.socketService.sendPhone(p1)
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * service.callService.interrupt(callId, _, cad1.accountId) >> Result.void()
        res.status == ResultStatus.NO_CONTENT
        p1.media != null
        el1 in p1.media.mediaElements
        MediaInfo.count() == mBaseline + 1
        MediaElement.count() == eBaseline
        MediaElementVersion.count() == vBaseline
    }
}
