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
class FutureMessageServiceSpec extends CustomSpec {

    static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
        IOCUtils.metaClass."static".getQuartzScheduler = { -> TestUtils.mockScheduler() }
    	service.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    def cleanup() {
    	cleanupData()
    }

    void "test set from body for future message"() {
    	given: "an unsaved (new) future message with record"
        service.futureMessageJobService = Mock(FutureMessageJobService)
        service.socketService = Mock(SocketService)
    	FutureMessage fMsg = new FutureMessage(record: c1.record)
        assert fMsg.id == null

    	when: "setting properties"
        FutureMessageType fType = FutureMessageType.CALL
        Map info = [
            notifySelf: true,
            type: fType.toString().toLowerCase(),
            message: "hi",
            language: VoiceLanguage.SPANISH.toString(),
            startDate: DateTime.now().minusDays(2),
            endDate: DateTime.now().plusDays(2),
        ]
        Result<FutureMessage> res = service.setFromBody(fMsg, info)

    	then: "success and did schedule"
        1 * service.futureMessageJobService.schedule(*_) >> new Result()
        1 * service.socketService.sendFutureMessages(*_)
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload.notifySelf == info.notifySelf
        res.payload.type == fType
        res.payload.language == VoiceLanguage.SPANISH
        res.payload.message == info.message
        res.payload.startDate.withZone(DateTimeZone.UTC).toString() ==
            info.startDate.withZone(DateTimeZone.UTC).toString()
        res.payload.endDate.withZone(DateTimeZone.UTC).toString() ==
            info.endDate.withZone(DateTimeZone.UTC).toString()
        res.payload.validate() == true

    	when: "update without changing reschedule properties"
        fMsg = res.payload
        fMsg.save(flush:true, failOnError:true)

        info = [message: "YAAAS"]
        res = service.setFromBody(fMsg, info)

    	then: "success and did not call schedule"
        0 * service.futureMessageJobService._
        1 * service.socketService.sendFutureMessages(*_)
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload.message == info.message

    	when: "update and did change reschedule properties"
        info = [startDate: DateTime.now()]
        res = service.setFromBody(fMsg, info, null, ResultStatus.ACCEPTED)

    	then: "success and did call schedule"
        1 * service.futureMessageJobService.schedule(*_) >> new Result()
        1 * service.socketService.sendFutureMessages(*_)
        res.status == ResultStatus.ACCEPTED
        res.payload instanceof FutureMessage
        res.payload.startDate.withZone(DateTimeZone.UTC).toString() ==
            info.startDate.withZone(DateTimeZone.UTC).toString()
    }

    void "test set from body for simple future message"() {
        given: "an unsaved (new) future message with record"
        service.futureMessageJobService = Mock(FutureMessageJobService)
        service.socketService = Mock(SocketService)
        SimpleFutureMessage sMsg = new SimpleFutureMessage(record:c1.record)
        assert sMsg.id == null

        when: "setting properties"
        FutureMessageType fType = FutureMessageType.CALL
        Map info = [
            notifySelf: true,
            type: fType.toString().toLowerCase(),
            message: "hi",
            startDate: DateTime.now().minusDays(2),
            repeatCount: 12,
            endDate: DateTime.now().plusDays(2),
            repeatIntervalInDays: 8
        ]
        Result<FutureMessage> res = service.setFromBody(sMsg, info)

        then: "success and did schedule"
        1 * service.futureMessageJobService.schedule(*_) >> new Result()
        1 * service.socketService.sendFutureMessages(*_)
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload instanceof SimpleFutureMessage
        res.payload.notifySelf == info.notifySelf
        res.payload.type == fType
        res.payload.message == info.message
        res.payload.startDate.withZone(DateTimeZone.UTC).toString() ==
            info.startDate.withZone(DateTimeZone.UTC).toString()
        res.payload.repeatCount == info.repeatCount
        res.payload.endDate.withZone(DateTimeZone.UTC).toString() ==
            info.endDate.withZone(DateTimeZone.UTC).toString()
        res.payload.repeatIntervalInDays == info.repeatIntervalInDays
        res.payload.validate() == true

        when: "update without changing reschedule properties"
        sMsg = res.payload
        sMsg.save(flush:true, failOnError:true)
        info = [
            message: "YAAAS",
            repeatCount: info.repeatCount,
            endDate: info.endDate,
        ]
        res = service.setFromBody(sMsg, info)

        then: "success and did not call reschedule"
        0 * service.futureMessageJobService._
        1 * service.socketService.sendFutureMessages(*_)
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload instanceof SimpleFutureMessage
        res.payload.message == info.message

        when: "update and null both repeatCount and endDate"
        sMsg = res.payload
        sMsg.save(flush:true, failOnError:true)
        info = [message: "YAAAS"]
        res = service.setFromBody(sMsg, info)

        then: "success and DID call reschedule because repeatCount and endDate both nulled"
        1 * service.futureMessageJobService.schedule(*_) >> new Result()
        1 * service.socketService.sendFutureMessages(*_)
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload instanceof SimpleFutureMessage
        res.payload.message == info.message
        res.payload.repeatCount == null
        res.payload.endDate == null

        when: "update and did change reschedule properties"
        info = [repeatCount: 8]
        res = service.setFromBody(sMsg, info)

        then: "success and did call schedule"
        1 * service.futureMessageJobService.schedule(*_) >> new Result()
        1 * service.socketService.sendFutureMessages(*_)
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload.repeatCount == info.repeatCount
    }

    void "test set from body for a specific timezone"() {
        given: "an unsaved (new) future message with record"
        service.futureMessageJobService = Stub(FutureMessageJobService) { schedule(*_) >> new Result() }
        service.socketService = Stub(SocketService)
        FutureMessage fMsg = new FutureMessage(record:c1.record,
            type:FutureMessageType.CALL, message: "hi")
        assert fMsg.id == null

        String targetTz = "America/Los_Angeles",
            payloadTz = "America/New_York"
        DateTimeZone targetZone = DateTimeZone.forID(targetTz),
            payloadZone = DateTimeZone.forID(payloadTz)
        // Need to declare an explicit time zone for the payload end time.
        // On MacOS High Sierra, the DateTime seems to have an implicit time zone. That is,
        // even if we don't explicitly set, the DateTime will make the appropriate adjustments
        // to respect daylight saving time. However, on Ubuntu 14.04, this does not take place
        // unless we explictly set the payload time zone here.
        DateTime payloadNow = DateTime.now().withZone(payloadZone),
            targetNow = DateTime.now().withZone(targetZone)
        // Need to calculate the change in offset (if any) for the end date
        // for the edge case where we are running this test within two days of reaching
        // a daylight savings time transition point. During the two days leading up
        // when daylight savings time starts or ends, the hourOfDay assertions for the end
        // time will fail because `endUTCDateTime` is not sensitive to daylight saving time
        long payloadCurrentOffset = payloadZone.getOffset(payloadNow),
            payloadFutureOffset = payloadZone.getOffset(payloadNow.plusDays(2)),
            payloadChangeInOffset = TimeUnit.MILLISECONDS.toHours(payloadFutureOffset - payloadCurrentOffset);

        DateTime startCustomDateTime = DateTime.now()
                .withZone(targetZone),
            startUTCDateTime = DateTime.now()
                .withZone(DateTimeZone.UTC),
            endCustomDateTime = targetNow.plusDays(2),
            // UTC doesn't care about daylight savings so we need to adjust the offset
            // when comparing with a DateTime with a zone that does care about daylight saving time
            // in order for the assertion to pas
            endUTCDateTime = DateTime.now()
                .withZone(DateTimeZone.UTC).plusDays(2)

        when: "setting date properties without timezone"
        Map info = [
            startDate: DateTime.now(),
            endDate: payloadNow.plusDays(2)
        ]
        Result<FutureMessage> res = service.setFromBody(fMsg, info)

        then: "all are converted to UTC and retain their actual values"
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload.startDate.withZone(DateTimeZone.UTC).hourOfDay ==
            startCustomDateTime.withZone(DateTimeZone.UTC).hourOfDay
        res.payload.startDate.withZone(DateTimeZone.UTC).hourOfDay ==
            startUTCDateTime.withZone(DateTimeZone.UTC).hourOfDay
        // both payload and target here are sensitive to daylight saving time so we don't need
        // to manually adjust with offset
        res.payload.endDate.withZone(DateTimeZone.UTC).hourOfDay ==
            endCustomDateTime.withZone(DateTimeZone.UTC).hourOfDay
        // the payload is sensitive to daylight saving time, but UTC target is not so we DO
        // need to manually adjust to calculated daylight saving time offset here
        ((res.payload.endDate.withZone(DateTimeZone.UTC).hourOfDay + payloadChangeInOffset) % 24) ==
            endUTCDateTime.withZone(DateTimeZone.UTC).hourOfDay

        when: "setting date properties WITH timezone for start time BEFORE DST change point"
        res = service.setFromBody(fMsg, info, targetTz)

        then: "all date values have their values preserved no matter initial timezone"
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        // daylight savings time adjust info populated because start time is
        // far enough into the future
        res.payload.whenAdjustDaylightSavings == null
        res.payload.hasAdjustedDaylightSavings == false
        res.payload.daylightSavingsZone == null

        res.payload.startDate.withZone(DateTimeZone.UTC).hourOfDay ==
            startCustomDateTime.withZone(DateTimeZone.UTC).hourOfDay
        res.payload.startDate.withZone(DateTimeZone.UTC).hourOfDay ==
            startUTCDateTime.withZone(DateTimeZone.UTC).hourOfDay
        // both payload and target here are sensitive to daylight saving time so we don't need
        // to manually adjust with offset
        res.payload.endDate.withZone(DateTimeZone.UTC).hourOfDay ==
            endCustomDateTime.withZone(DateTimeZone.UTC).hourOfDay
        // the payload is sensitive to daylight saving time, but UTC target is not so we DO
        // need to manually adjust to calculated daylight saving time offset here
        ((res.payload.endDate.withZone(DateTimeZone.UTC).hourOfDay + payloadChangeInOffset) % 24) ==
            endUTCDateTime.withZone(DateTimeZone.UTC).hourOfDay

        when: "setting with time zone and start date far into the future"
        info.startDate = DateTime.now().plusYears(2)
        info.endDate = DateTime.now().plusYears(5)
        res = service.setFromBody(fMsg, info, targetZone.getID())

        then:
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload.whenAdjustDaylightSavings != null
        res.payload.whenAdjustDaylightSavings.year > DateTime.now().year
        res.payload.hasAdjustedDaylightSavings == false
        res.payload.daylightSavingsZone == targetZone
    }

    void "test handling language for creation versus updating"() {
        given:
        service.futureMessageJobService = Stub(FutureMessageJobService) { schedule(*_) >> new Result() }
        service.socketService = Stub(SocketService)
        c1.phone.language = VoiceLanguage.ENGLISH
        c1.phone.save(flush:true, failOnError:true)
        service.mediaService = Mock(MediaService)
        Future fut1 = Mock()

        when: "creating withOUT specified language"
        Map info = [
            type: FutureMessageType.CALL.toString(),
            message: TestUtils.randString(),
            startDate: DateTime.now().plusDays(2)
        ]
        Result<FutureMessage> res = service.create(c1.record, info)

        then: "use phone default"
        1 * service.mediaService.tryProcess(*_) >> new Result(payload: Tuple.create(null, fut1))
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.language == c1.phone.language

        when: "creating with specified language"
        info.language = VoiceLanguage.SPANISH.toString()
        res = service.create(c1.record, info)

        then: "use specified language"
        1 * service.mediaService.tryProcess(*_) >> new Result(payload: Tuple.create(null, fut1))
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload.language != c1.phone.language
        res.payload.language == VoiceLanguage.SPANISH

        when: "updating without specified language"
        info = [message: TestUtils.randString()]
        VoiceLanguage originalLang = res.payload.language
        res = service.update(res.payload.id, info)

        then: "do not change the language"
        1 * service.mediaService.tryProcess(*_) >> new Result(payload: Tuple.create(null, fut1))
        res.success == true
        res.status == ResultStatus.OK
        res.payload.message == info.message
        res.payload.language == originalLang

        when: "updating with specified language"
        info = [message: TestUtils.randString(), language: VoiceLanguage.CHINESE.toString()]
        res = service.update(res.payload.id, info)

        then: "update the language"
        1 * service.mediaService.tryProcess(*_) >> new Result(payload: Tuple.create(null, fut1))
        res.success == true
        res.status == ResultStatus.OK
        res.payload.message == info.message
        res.payload.language != originalLang
        res.payload.language == VoiceLanguage.CHINESE
    }

    void "test appropriate status codes when creating, updating, and deleting"() {
        given:
        service.futureMessageJobService = Mock(FutureMessageJobService) { schedule(*_) >> new Result() }
        service.socketService = Stub(SocketService)
        int fBaseline = FutureMessage.count()
        service.mediaService = Mock(MediaService)
        Future fut1 = Mock()

        when: "creating"
        Result<FutureMessage> res = service.createForContact(c1.id,
            [type:FutureMessageType.TEXT, message:"hello"])
        FutureMessage.withSession { it.flush() }

        then:
        1 * service.mediaService.tryProcess(*_) >> new Result(payload: Tuple.create(null, fut1))
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload instanceof FutureMessage
        FutureMessage.count() == fBaseline + 1

        when: "updating"
        Long id = res.payload.id
        String msg = "so soft"
        res = service.update(id, [message:msg])

        then:
        1 * service.mediaService.tryProcess(*_) >> new Result(payload: Tuple.create(null, fut1))
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload.message == msg
        FutureMessage.count() == fBaseline + 1

        when: "deleting"
        res = service.delete(id)

        then:
        1 * service.futureMessageJobService.unschedule(*_) >> new Result()
        res.status == ResultStatus.NO_CONTENT
        FutureMessage.get(id).isDone == true
        FutureMessage.count() == fBaseline + 1
    }

    @DirtiesRuntime
    void "test cancelling future processing on error during creation or updating"() {
        given:
        service.mediaService = Mock(MediaService)
        Future fut1 = Mock()
        MockedMethod setFromBody = MockedMethod.create(service, "setFromBody")
            { new Result(status: ResultStatus.UNPROCESSABLE_ENTITY) }

        when: "error during creation"
        Result<FutureMessage> res = service.create(Mock(Record), null, null)

        then:
        1 * service.mediaService.tryProcess(*_) >> new Result(payload: Tuple.create(null, fut1))
        setFromBody.callCount == 1
        1 * fut1.cancel(true)

        when: "error when updating"
        FutureMessage.metaClass."static".get = { Long id -> Mock(FutureMessage) }
        res = service.update(88, null, null)

        then:
        1 * service.mediaService.tryProcess(*_) >> new Result(payload: Tuple.create(null, fut1))
        setFromBody.callCount == 2
        1 * fut1.cancel(true)
    }

    void "test create errors"() {
    	when: "no record"
        Result res = service.create(null, [:])

    	then: "unprocessable entity"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "futureMessageService.create.noRecordOrInsufficientPermissions"

        when: "attempting to create for a view-only shared contact"
        sc1.permission = SharePermission.VIEW
        sc1.save(flush:true, failOnError:true)
        res = service.createForSharedContact(sc1.id, [:])

        then: "unprocessable entity"
        res.success == false
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages[0] == "sharedContact.insufficientPermission"
    }

    void "test update errors"() {
        when: "nonexistent future message id"
        Result res = service.update(-88L, [:])

        then: "not found"
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "futureMessageService.update.notFound"
    }

    void "test delete errors"() {
        when: "nonexistent future message id"
        Result res = service.delete(-88L)

        then: "not found"
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "futureMessageService.delete.notFound"
    }
}
