package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.quartz.SimpleTrigger
import org.textup.type.FutureMessageType
import org.textup.type.RecordItemType
import org.textup.util.CustomSpec
import org.textup.validator.BasePhoneNumber
import org.textup.validator.BasicNotification
import org.textup.validator.OutgoingMessage
import org.textup.validator.TempRecordReceipt
import spock.lang.Ignore
import spock.lang.Shared
import static org.springframework.http.HttpStatus.*

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, FutureMessage, SimpleFutureMessage, NotificationPolicy])
@TestMixin(HibernateTestMixin)
@TestFor(FutureMessageService)
class FutureMessageServiceSpec extends CustomSpec {

    static doWithSpring = {
		resultFactory(ResultFactory)
	}

	FutureMessage fMsg1
    int _numTextsSent

    def setup() {
        _numTextsSent = 0
    	setupData()
    	service.resultFactory = getResultFactory()
        service.messageSource = messageSource
        service.notificationService = [
            build: { Phone targetPhone, List<Contact> contacts, List<ContactTag> tags ->
                [new BasicNotification()]
            }
        ] as NotificationService
        service.tokenService = [
            notifyStaff:{ BasicNotification bn1, Boolean outgoing, String msg,
                String instructions ->
                _numTextsSent++
                new Result(status:ResultStatus.NO_CONTENT, payload:null)
            }
        ]  as TokenService
        service.socketService = [
            sendItems:{ List<RecordItem> items,
                String eventName=Constants.SOCKET_EVENT_RECORDS ->
                new Result(status:ResultStatus.OK).toGroup()
            },
            sendFutureMessages: { List<FutureMessage> fMsgs,
                String eventName=Constants.SOCKET_EVENT_FUTURE_MESSAGES ->
                new Result(status:ResultStatus.OK).toGroup()
            }
        ] as SocketService

        FutureMessage.metaClass.refreshTrigger = { -> null }
        SimpleFutureMessage.metaClass.constructor = { Map m->
            def instance = new SimpleFutureMessage()
            instance.properties = m
            instance.quartzScheduler = mockScheduler()
            instance
        }
        OutgoingMessage.metaClass.getMessageSource = { -> mockMessageSource() }

    	fMsg1 = new FutureMessage(record:c1.record, type:FutureMessageType.CALL,
    		message:"hi")
    	fMsg1.save(flush:true, failOnError:true)
    }

    def cleanup() {
    	cleanupData()
    }

    // Test job execution
    // ------------------

    void "test mark done"() {
    	when: "passed in a nonexistent keyName"
        addToMessageSource("futureMessageService.markDone.messageNotFound")
    	Result<FutureMessage> res = service.markDone("nonexistent")

    	then: "not found"
    	res.success == false
    	res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "futureMessageService.markDone.messageNotFound"

    	when: "passed in an existing keyName"
    	assert fMsg1.isDone == false
    	res = service.markDone(fMsg1.keyName)

    	then:
    	res.success == true
    	res.payload instanceof FutureMessage
    	res.payload.keyName == fMsg1.keyName
    	res.payload.isDone == true
    }
    void "test execute"() {
        given: "overrides and baselines"
        Phone.metaClass.sendMessage = { OutgoingMessage msg, Staff staff = null,
            boolean skipCheck = false ->
            new Result(status:ResultStatus.OK).toGroup()
        }
        addToMessageSource(["futureMessageService.execute.messageNotFound",
            "futureMessageService.notifyStaff.notification"])

    	when: "nonexistent keyName"
        _numTextsSent = 0
        ResultGroup resGroup = service.execute("nonexistent", s1.id)

    	then: "not found"
        resGroup.anySuccesses == false
        resGroup.failures[0].errorMessages[0] == "futureMessageService.execute.messageNotFound"
        _numTextsSent == 0

    	when: "existing message with notify staff"
        fMsg1.notifySelf = true
        fMsg1.save(flush:true, failOnError:true)
        resGroup = service.execute(fMsg1.keyName, s1.id)

    	then:
        _numTextsSent == 1
        resGroup.anySuccesses == true
    }

    // Test CRUD
    // ---------

    boolean _didSchedule = false
    boolean _didUnschedule = false
    protected void mockForCRUD() {
        service.metaClass.schedule = { FutureMessage fMsg ->
            _didSchedule = true
            new Result(status:ResultStatus.NO_CONTENT, payload:null)
        }
        service.metaClass.unschedule = { FutureMessage fMsg ->
            _didUnschedule = true
            new Result(status:ResultStatus.NO_CONTENT, payload:null)
        }
    }
    void "test set from body for future message"() {
    	given: "an unsaved (new) future message with record"
        mockForCRUD()
    	FutureMessage fMsg = new FutureMessage(record:c1.record)
        assert fMsg.id == null

    	when: "setting properties"
        _didSchedule = false
        FutureMessageType fType = FutureMessageType.CALL
        Map info = [
            notifySelf: true,
            type: fType.toString().toLowerCase(),
            message: "hi",
            startDate: DateTime.now().minusDays(2),
            endDate: DateTime.now().plusDays(2),
        ]
        Result<FutureMessage> res = service.setFromBody(fMsg, info)

    	then: "success and did schedule"
        res.success == true
        res.payload instanceof FutureMessage
        res.payload.notifySelf == info.notifySelf
        res.payload.type == fType
        res.payload.message == info.message
        res.payload.startDate.withZone(DateTimeZone.UTC).toString() ==
            info.startDate.withZone(DateTimeZone.UTC).toString()
        res.payload.endDate.withZone(DateTimeZone.UTC).toString() ==
            info.endDate.withZone(DateTimeZone.UTC).toString()
        _didSchedule == true
        res.payload.validate() == true

    	when: "update without changing reschedule properties"
        fMsg = res.payload
        fMsg.save(flush:true, failOnError:true)

        _didSchedule = false
        info = [message: "YAAAS"]
        res = service.setFromBody(fMsg, info)

    	then: "success and did not call schedule"
        res.success == true
        res.payload instanceof FutureMessage
        res.payload.message == info.message
        _didSchedule == false

    	when: "update and did change reschedule properties"
        _didSchedule = false
        info = [startDate: DateTime.now()]
        res = service.setFromBody(fMsg, info)

    	then: "success and did call schedule"
        res.success == true
        res.payload instanceof FutureMessage
        res.payload.startDate.withZone(DateTimeZone.UTC).toString() ==
            info.startDate.withZone(DateTimeZone.UTC).toString()
        _didSchedule == true
    }
    void "test set from body for simple future message"() {
        given: "an unsaved (new) future message with record"
        mockForCRUD()
        SimpleFutureMessage sMsg = new SimpleFutureMessage(record:c1.record)
        assert sMsg.id == null

        when: "setting properties"
        _didSchedule = false
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
        res.success == true
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
        _didSchedule == true
        res.payload.validate() == true

        when: "update without changing reschedule properties"
        sMsg = res.payload
        sMsg.save(flush:true, failOnError:true)

        _didSchedule = false
        info = [
            message: "YAAAS",
            repeatCount: info.repeatCount,
            endDate: info.endDate,
        ]
        res = service.setFromBody(sMsg, info)

        then: "success and did not call reschedule"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload instanceof SimpleFutureMessage
        res.payload.message == info.message
        _didSchedule == false

        when: "update and null both repeatCount and endDate"
        sMsg = res.payload
        sMsg.save(flush:true, failOnError:true)

        _didSchedule = false
        info = [message: "YAAAS"]
        res = service.setFromBody(sMsg, info)

        then: "success and DID call reschedule because repeatCount and endDate both nulled"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload instanceof SimpleFutureMessage
        res.payload.message == info.message
        res.payload.repeatCount == null
        res.payload.endDate == null
        _didSchedule == true

        when: "update and did change reschedule properties"
        _didSchedule = false
        info = [repeatCount: 8]
        res = service.setFromBody(sMsg, info)

        then: "success and did call schedule"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload.repeatCount == info.repeatCount
        _didSchedule == true
    }
    void "test set from body for a specific timezone"() {
        given: "an unsaved (new) future message with record"
        mockForCRUD()
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
        res.success == true
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
        res.success == true
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
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload.whenAdjustDaylightSavings != null
        res.payload.whenAdjustDaylightSavings.year > DateTime.now().year
        res.payload.hasAdjustedDaylightSavings == false
        res.payload.daylightSavingsZone == targetZone
    }
    void "test appropriate status codes when creating, updating, and deleting"() {
        given:
        mockForCRUD()
        int fBaseline = FutureMessage.count()

        when: "creating"
        Result<FutureMessage> res = service.createForContact(c1.id,
            [type:FutureMessageType.TEXT, message:"hello"])
        FutureMessage.withSession { it.flush() }

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload instanceof FutureMessage
        FutureMessage.count() == fBaseline + 1

        when: "updating"
        Long id = res.payload.id
        String msg = "so soft"
        res = service.update(id, [message:msg])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof FutureMessage
        res.payload.message == msg
        FutureMessage.count() == fBaseline + 1

        when: "deleting"
        service.metaClass.deleteHelper = { FutureMessage fMsg ->
            new Result(status:ResultStatus.NO_CONTENT)
        }
        res = service.delete(id)

        then:
        res.success == true
        res.status == ResultStatus.NO_CONTENT
        FutureMessage.get(id).isDone == false // didn't actually do delete, just mocked it
        FutureMessage.count() == fBaseline + 1
    }
    void "test create errors"() {
    	when: "no record"
        addToMessageSource("futureMessageService.create.noRecord")
        Result res = service.create(null, [:])

    	then: "unprocessable entity"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "futureMessageService.create.noRecord"
    }
    void "test update errors"() {
        when: "nonexistent future message id"
        addToMessageSource("futureMessageService.update.notFound")
        Result res = service.update(-88L, [:])

        then: "not found"
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "futureMessageService.update.notFound"
    }
    void "test delete errors"() {
        when: "nonexistent future message id"
        addToMessageSource("futureMessageService.delete.notFound")
        Result res = service.delete(-88L)

        then: "not found"
        res.success == false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "futureMessageService.delete.notFound"
    }
}
