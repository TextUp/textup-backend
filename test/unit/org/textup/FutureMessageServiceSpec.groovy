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
import org.textup.types.FutureMessageType
import org.textup.types.RecordItemType
import org.textup.types.ResultType
import org.textup.util.CustomSpec
import org.textup.validator.BasePhoneNumber
import org.textup.validator.OutgoingMessage
import org.textup.validator.TempRecordReceipt
import spock.lang.Ignore
import spock.lang.Shared
import static org.springframework.http.HttpStatus.*

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, FutureMessage, SimpleFutureMessage])
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
        service.messageSource = mockMessageSource()
        service.tokenService = [notifyStaff:{ Phone p1, Staff s1, Long recordId,
            Boolean outgoing, String msg, String instructions ->
            _numTextsSent++
            new Result(type:ResultType.SUCCESS, success:true)
        }]  as TokenService
        service.socketService = [
            sendItems:{ List<RecordItem> items,
                String eventName=Constants.SOCKET_EVENT_RECORDS ->
                new ResultList(new Result(success:true))
            },
            sendFutureMessages: { List<FutureMessage> fMsgs,
                String eventName=Constants.SOCKET_EVENT_FUTURE_MESSAGES ->
                new ResultList(new Result(success:true))
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

    // void "test mark done"() {
    // 	when: "passed in a nonexistent keyName"
    // 	Result<FutureMessage> res = service.markDone("nonexistent")

    // 	then: "not found"
    // 	res.success == false
    // 	res.type == ResultType.MESSAGE_STATUS
    // 	res.payload.status == NOT_FOUND
    // 	res.payload.code == "futureMessageService.markDone.messageNotFound"

    // 	when: "passed in an existing keyName"
    // 	assert fMsg1.isDone == false
    // 	res = service.markDone(fMsg1.keyName)

    // 	then:
    // 	res.success == true
    // 	res.payload instanceof FutureMessage
    // 	res.payload.keyName == fMsg1.keyName
    // 	res.payload.isDone == true
    // }
    // void "test execute"() {
    //     given: "overrides and baselines"
    //     Phone.metaClass.sendMessage = { OutgoingMessage msg, Staff staff = null,
    //         boolean skipCheck = false ->
    //         new ResultList(new Result(success:true))
    //     }
    //     Phone.metaClass.getPhonesToAvailableNowForContactIds =
    //         { Collection<Long> cIds -> [(p1):[s1, s2]] }


    // 	when: "nonexistent keyName"
    //     _numTextsSent = 0
    //     ResultList resList = service.execute("nonexistent", s1.id)

    // 	then: "not found"
    //     resList.isAnySuccess == false
    //     resList.failures[0].payload.code == "futureMessageService.execute.messageNotFound"
    //     _numTextsSent == 0

    // 	when: "existing message with notify staff"
    //     fMsg1.notifySelf = true
    //     fMsg1.save(flush:true, failOnError:true)
    //     resList = service.execute(fMsg1.keyName, s1.id)

    // 	then:
    //     _numTextsSent == 2
    //     resList.isAnySuccess == true
    // }

    // Test CRUD
    // ---------

    boolean _didSchedule = false
    boolean _didUnschedule = false
    protected void mockForCRUD() {
        service.metaClass.schedule = { FutureMessage fMsg ->
            _didSchedule = true
            new Result(success:true )
        }
        service.metaClass.unschedule = { FutureMessage fMsg ->
            _didUnschedule = true
            new Result(success:true )
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
        String tz = "America/Los_Angeles"
        DateTimeZone myZone = DateTimeZone.forID(tz)
        DateTime startCustomDateTime = DateTime.now()
                .withZone(myZone),
            startUTCDateTime = DateTime.now()
                .withZone(DateTimeZone.UTC),
            endCustomDateTime = DateTime.now()
                .withZone(myZone).plusDays(2),
            endUTCDateTime = DateTime.now()
                .withZone(DateTimeZone.UTC).plusDays(2)

        when: "setting date properties without timezone"
        Map info = [
            startDate: DateTime.now(),
            endDate: DateTime.now().plusDays(2)
        ]
        Result<FutureMessage> res = service.setFromBody(fMsg, info)

        then: "all are converted to UTC and retain their actual values"
        res.success == true
        res.payload instanceof FutureMessage
        res.payload.startDate.withZone(DateTimeZone.UTC).hourOfDay ==
            startCustomDateTime.withZone(DateTimeZone.UTC).hourOfDay
        res.payload.startDate.withZone(DateTimeZone.UTC).hourOfDay ==
            startUTCDateTime.withZone(DateTimeZone.UTC).hourOfDay
        res.payload.endDate.withZone(DateTimeZone.UTC).hourOfDay ==
            endCustomDateTime.withZone(DateTimeZone.UTC).hourOfDay
        res.payload.endDate.withZone(DateTimeZone.UTC).hourOfDay ==
            endUTCDateTime.withZone(DateTimeZone.UTC).hourOfDay

        when: "setting date properties WITH timezone"
        res = service.setFromBody(fMsg, info, tz)

        then: "all date values have their values preserved no matter initial timezone"
        res.success == true
        res.payload instanceof FutureMessage
        res.payload.startDate.withZone(DateTimeZone.UTC).hourOfDay ==
            startCustomDateTime.withZone(DateTimeZone.UTC).hourOfDay
        res.payload.startDate.withZone(DateTimeZone.UTC).hourOfDay ==
            startUTCDateTime.withZone(DateTimeZone.UTC).hourOfDay
        res.payload.endDate.withZone(DateTimeZone.UTC).hourOfDay ==
            endCustomDateTime.withZone(DateTimeZone.UTC).hourOfDay
        res.payload.endDate.withZone(DateTimeZone.UTC).hourOfDay ==
            endUTCDateTime.withZone(DateTimeZone.UTC).hourOfDay
    }
    void "test create errors"() {
    	when: "no record"
        Result res = service.create(null, [:])

    	then: "unprocessable entity"
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == UNPROCESSABLE_ENTITY
        res.payload.code == "futureMessageService.create.noRecord"
    }
    void "test update errors"() {
        when: "nonexistent future message id"
        Result res = service.update(-88L, [:])

        then: "not found"
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "futureMessageService.update.notFound"
    }
    void "test delete errors"() {
        when: "nonexistent future message id"
        Result res = service.delete(-88L)

        then: "not found"
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == NOT_FOUND
        res.payload.code == "futureMessageService.delete.notFound"
    }
}