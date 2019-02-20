package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import java.util.concurrent.*
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
@TestFor(FutureMessageService)
class FutureMessageServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
        IOCUtils.metaClass."static".getQuartzScheduler = { -> TestUtils.mockScheduler() }
    }

    void "test trying to schedule"() {
        given:
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()

        service.futureMessageJobService = GroovyMock(FutureMessageJobService)
        service.socketService = GroovyMock(SocketService)

        when:
        Result res = service.trySchedule(true, fMsg1)

        then:
        1 * service.futureMessageJobService.trySchedule(true, fMsg1) >> Result.void()
        1 * service.socketService.sendFutureMessages([fMsg1])
        res.status == ResultStatus.OK
        res.payload == fMsg1
    }

    void "test updating fields"() {
        given:
        TypeMap body1 = TypeMap.create(notifySelf: true,
            type: FutureMessageType.CALL,
            message: TestUtils.randString(),
            startDate: DateTime.now().plusDays(1),
            endDate: DateTime.now().plusDays(3),
            language: VoiceLanguage.SPANISH,
            repeatCount: TestUtils.randIntegerUpTo(88, true),
            repeatIntervalInDays: TestUtils.randIntegerUpTo(88, true))
        TypeMap body2 = TypeMap.create(endDate: null)
        SimpleFutureMessage sMsg1 = TestUtils.buildFutureMessage()

        MockedMethod checkScheduleDaylightSavingsAdjustment = MockedMethod.create(sMsg1, "checkScheduleDaylightSavingsAdjustment")

        when:
        Result res = service.trySetFields(sMsg1, body1)

        then:
        res.status == ResultStatus.OK
        res.payload.notifySelf == body1.notifySelf
        res.payload.type == body1.type
        res.payload.message == body1.message
        res.payload.startDate == body1.startDate
        res.payload.endDate == body1.endDate
        res.payload.record.language == body1.language
        res.payload.repeatCount == body1.repeatCount
        res.payload.repeatIntervalInDays == body1.repeatIntervalInDays
        checkScheduleDaylightSavingsAdjustment.notCalled

        when:
        res = service.trySetFields(sMsg1, body2)

        then:
        res.status == ResultStatus.OK
        res.payload.endDate == null

        cleanup:
        checkScheduleDaylightSavingsAdjustment?.restore()
    }

    // We no longer tests that DateTime values are maintained across daylight savings changes
    // because that is the responsibility of the JodaTime library. We don't need to duplicate
    // their tests here.
    void "test updating fields with timezone respects daylight savings time"() {
        given:
        String tzId = "America/Los_Angeles"
        TypeMap body = TypeMap.create(startDate: DateTime.now(),
            endDate: DateTime.now().plusDays(2))
        SimpleFutureMessage sMsg1 = TestUtils.buildFutureMessage()
        MockedMethod checkScheduleDaylightSavingsAdjustment = MockedMethod.create(sMsg1, "checkScheduleDaylightSavingsAdjustment")

        when:
        Result res = service.trySetFields(sMsg1, body, tzId)

        then:
        res.status == ResultStatus.OK
        res.payload.startDate == body.startDate
        res.payload.endDate == body.endDate
        checkScheduleDaylightSavingsAdjustment.latestArgs[0].getID() == tzId

        cleanup:
        checkScheduleDaylightSavingsAdjustment?.restore()
    }

    void "test deleting"() {
        given:
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()

        service.futureMessageJobService = GroovyMock(FutureMessageJobService)

        when:
        Result res = service.tryDelete(fMsg1.id)

        then:
        1 * service.futureMessageJobService.tryUnschedule(fMsg1) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == fMsg1
        fMsg1.isDone
    }

    void "test updating"() {
        given:
        TypeMap body = TypeMap.create(timezone: TestUtils.randString())
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()

        Future fut1 = GroovyMock()
        service.mediaService = GroovyMock(MediaService)
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") {
            Result.createError([], ResultStatus.BAD_REQUEST)
        }
        MockedMethod trySchedule = MockedMethod.create(service, "trySchedule") {
            Result.createSuccess(fMsg1)
        }

        when:
        Result res = service.tryUpdate(fMsg1.id, body)

        then:
        1 * service.mediaService.tryCreateOrUpdate(fMsg1, body) >> Result.createSuccess(fut1)
        trySetFields.callCount == 1
        1 * fut1.cancel(true)
        res.status == ResultStatus.BAD_REQUEST

        when:
        trySetFields = MockedMethod.create(trySetFields) { Result.createSuccess(fMsg1) }
        res = service.tryUpdate(fMsg1.id, body)

        then:
        1 * service.mediaService.tryCreateOrUpdate(fMsg1, body) >> Result.createSuccess(fut1)
        trySetFields.latestArgs == [fMsg1, body, body.timezone]
        trySchedule.latestArgs == [false, fMsg1]
        0 * fut1._
        res.status == ResultStatus.OK
        res.payload == fMsg1

        cleanup:
        trySetFields?.restore()
        trySchedule?.restore()
    }

    void "test creating"() {
        given:
        TypeMap body = TypeMap.create(type: FutureMessageType.TEXT,
            message: TestUtils.randString(),
            timezone: TestUtils.randString())
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        MediaInfo mInfo1 = TestUtils.buildMediaInfo()

        Future fut1 = GroovyMock()
        service.mediaService = GroovyMock(MediaService)
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") {
            Result.createError([], ResultStatus.BAD_REQUEST)
        }
        MockedMethod trySchedule = MockedMethod.create(service, "trySchedule") { isNew, fMsg1 ->
            Result.createSuccess(fMsg1)
        }

        when:
        Result res = service.tryCreate(spr1.id, body)

        then:
        1 * service.mediaService.tryCreate(body) >> Result.createSuccess(Tuple.create(null, fut1))
        trySetFields.callCount == 1
        1 * fut1.cancel(true)
        res.status == ResultStatus.BAD_REQUEST

        when:
        trySetFields = MockedMethod.create(trySetFields) { fMsg1 -> Result.createSuccess(fMsg1) }
        res = service.tryCreate(spr1.id, body)

        then:
        1 * service.mediaService.tryCreate(body) >> Result.createSuccess(Tuple.create(mInfo1, fut1))
        trySetFields.latestArgs[1] == body
        trySetFields.latestArgs[2] == body.timezone
        trySchedule.latestArgs[0] == true
        res.status == ResultStatus.CREATED
        res.payload instanceof SimpleFutureMessage
        res.payload.type == body.type
        res.payload.message == body.message
        res.payload.media == mInfo1

        cleanup:
        trySetFields?.restore()
        trySchedule?.restore()
    }
}
