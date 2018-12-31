package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import java.util.concurrent.Future
import org.joda.time.DateTime
import org.quartz.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

// When writing Groovy code, use GroovyMock instead of Mock which is aware of Groovy features
// such as normalizing setProperty("name", value) to setName(value)
// see: https://stackoverflow.com/a/46572812

@Domain([CustomAccountDetails, Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, FutureMessage, SimpleFutureMessage, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
@TestFor(FutureMessageJobService)
class FutureMessageJobServiceSpec extends CustomSpec {

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

    // Job management
    // --------------

    void "test building trigger"() {
        given:
        service.authService = GroovyMock(AuthService)
        TriggerBuilder builder = TriggerBuilder.newTrigger()
        FutureMessage fMsg = GroovyMock()
        String keyName = TestUtils.randString()
        DateTime startDate = DateTime.now().plusDays(1)
        DateTime endDate = DateTime.now().plusDays(8)
        TriggerKey trigKey = TriggerKey.triggerKey(TestUtils.randString())

        when:
        Trigger trigger = service.buildTrigger(trigKey, builder, fMsg)

        then:
        (1.._) * fMsg.startDate >> startDate
        (1.._) * fMsg.endDate >> endDate
        (1.._) * fMsg.keyName >> keyName
        (1.._) * service.authService.loggedInAndActive >> s1
        trigger.startTime == startDate.toDate()
        trigger.endTime == endDate.toDate()
        trigger.jobDataMap.size() == 2
        trigger.jobDataMap[Constants.JOB_DATA_FUTURE_MESSAGE_KEY] == keyName
        trigger.jobDataMap[Constants.JOB_DATA_STAFF_ID] == s1.id
    }

    @DirtiesRuntime
    void "test scheduling"() {
        given:
        service.quartzScheduler = Mock(Scheduler)
        FutureMessage fMsg = GroovyMock()
        MockedMethod buildTrigger = TestUtils.mock(service, "buildTrigger")

        when: "error scheduling job"
        Result<Void> res = service.schedule(fMsg)

        then:
        1 * service.quartzScheduler.getTrigger(*_)
        buildTrigger.callCount == 1
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages[0] == "futureMessageService.schedule.unspecifiedError"

        when: "new job without trigger"
        res = service.schedule(fMsg)

        then:
        1 * service.quartzScheduler.getTrigger(*_) >> null
        1 * service.quartzScheduler.scheduleJob(*_) >> new Date()
        0 * service.quartzScheduler.rescheduleJob(*_)
        buildTrigger.callCount == 2
        res.status == ResultStatus.NO_CONTENT

        when: "job with existing trigger"
        res = service.schedule(fMsg)

        then:
        1 * service.quartzScheduler.getTrigger(*_) >> Mock(Trigger)
        0 * service.quartzScheduler.scheduleJob(*_)
        1 * service.quartzScheduler.rescheduleJob(*_) >> new Date()
        buildTrigger.callCount == 3
        res.status == ResultStatus.NO_CONTENT
    }

    void "test unscheduling"() {
        given:
        service.quartzScheduler = Mock(Scheduler)
        FutureMessage fMsg = GroovyMock()

        when: "gracefully catch exceptions"
        Result<Void> res = service.unschedule(fMsg)

        then:
        1 * service.quartzScheduler.unscheduleJob(*_) >> { throw new IllegalArgumentException() }
        res.status == ResultStatus.INTERNAL_SERVER_ERROR

        when:
        res = service.unschedule(fMsg)

        then:
        1 * service.quartzScheduler.unscheduleJob(*_) >> true
        res.status == ResultStatus.NO_CONTENT
    }

    @DirtiesRuntime
    void "test cancelling all"() {
        given:
        MockedMethod unschedule = TestUtils.mock(service, 'unschedule') { new Result() }
        // see https://github.com/spockframework/spock/issues/438
        FutureMessage fMsg = GroovyMock() { asBoolean() >> true }

        when:
        ResultGroup<FutureMessage> resGroup = service.cancelAll(null)

        then:
        resGroup.isEmpty
        unschedule.callCount == 0

        when:
        resGroup = service.cancelAll([fMsg])

        then:
        1 * fMsg.setIsDone(true)
        1 * fMsg.save() >> fMsg
        resGroup.successes.size() == 1
        resGroup.anyFailures == false
        unschedule.callCount == 1
    }

    // Job execution
    // -------------

    @DirtiesRuntime
    void "test execution errors"() {
        given:
        service.outgoingMessageService = GroovyMock(OutgoingMessageService)
        service.notificationService = GroovyMock(NotificationService)
        FutureMessage fMsg = GroovyMock() { asBoolean() >> true }
        OutgoingMessage msg1 = GroovyMock() { asBoolean() >> true }

        when: "nonexistent keyName"
        FutureMessage.metaClass."static".findByKeyName = { String key -> null }
        ResultGroup<RecordItem> resGroup = service.execute(null, null)

        then: "not found"
        0 * service.outgoingMessageService._
        0 * service.notificationService._
        resGroup.anySuccesses == false
        resGroup.failureStatus == ResultStatus.NOT_FOUND
        resGroup.failures.size() == 1
        resGroup.failures[0].errorMessages[0] == "futureMessageService.execute.messageNotFound"

        when: "invalid outgoing message"
        FutureMessage.metaClass."static".findByKeyName = { String key -> fMsg }
        resGroup = service.execute(null, null)

        then:
        0 * service.outgoingMessageService._
        0 * service.notificationService._
        1 * fMsg.tryGetOutgoingMessage() >> new Result(status: ResultStatus.UNPROCESSABLE_ENTITY)
        resGroup.anySuccesses == false
        resGroup.failureStatus == ResultStatus.UNPROCESSABLE_ENTITY

        when: "associated phone not found"
        resGroup = service.execute(null, null)

        then:
        0 * service.outgoingMessageService._
        0 * service.notificationService._
        1 * fMsg.tryGetOutgoingMessage() >> new Result(payload: msg1)
        1 * msg1.phones >> []
        resGroup.anySuccesses == false
        resGroup.failureStatus == ResultStatus.NOT_FOUND
        resGroup.failures.size() == 1
        resGroup.failures[0].errorMessages[0] == "futureMessageService.execute.phoneNotFound"
    }

    @DirtiesRuntime
    void "test execute"() {
        given: "overrides and baselines"
        FutureMessage fMsg = GroovyMock() { asBoolean() >> true }
        OutgoingMessage msg1 = GroovyMock() { asBoolean() >> true }
        RecordItem rItem = GroovyMock() { asBoolean() >> true }
        Future fut1 = Mock()
        service.outgoingMessageService = GroovyMock(OutgoingMessageService)
        service.notificationService = GroovyMock(NotificationService)
        service.threadService = GroovyMock(ThreadService)
        FutureMessage.metaClass."static".findByKeyName = { String key -> fMsg }
        Closure asyncAction

        when: "do not notify self"
        ResultGroup<RecordItem> resGroup = service.execute(null, s1.id)

        then:
        1 * fMsg.tryGetOutgoingMessage() >> new Result(payload: msg1)
        1 * msg1.phones >> [GroovyMock(Phone) { asBoolean() >> true }]
        1 * service.outgoingMessageService.processMessage(_, _, s1) >>
            Tuple.create(new Result(payload: rItem).toGroup(), fut1)
        1 * fMsg.notifySelf >> false
        0 * service.notificationService._
        1 * rItem.setWasScheduled(true)
        resGroup.anyFailures == false
        resGroup.successes.size() == 1
        resGroup.payload[0] == rItem

        when: "yes notify self"
        resGroup = service.execute(null, s1.id)

        then:
        1 * fMsg.tryGetOutgoingMessage() >> new Result(payload: msg1)
        1 * msg1.phones >> [GroovyMock(Phone) { asBoolean() >> true }]
        1 * service.outgoingMessageService.processMessage(_, _, s1) >>
            Tuple.create(new Result(payload: rItem).toGroup(), fut1)
        1 * fMsg.notifySelf >> true
        1 * rItem.setWasScheduled(true)
        1 * service.notificationService.build(*_) >> [GroovyMock(BasicNotification)]
        1 * msg1.contacts >> GroovyMock(Recipients)
        1 * msg1.tags >> GroovyMock(Recipients)
        1 * service.threadService.submit(*_) >> { Closure action -> asyncAction = action; null; }
        resGroup.anyFailures == false
        resGroup.successes.size() == 1
        resGroup.payload[0] == rItem

        when:
        RecordItem.metaClass."static".getAll = { Iterable<Serializable> ids -> [rItem] }
        asyncAction.call() // notify self closure

        then:
        1 * fut1.get()
        1 * service.notificationService.send(*_) >> new ResultGroup()
        1 * rItem.save() >> rItem // need to save so that re-fetched rItems persist
        1 * rItem.setNumNotified(1)
    }

    @DirtiesRuntime
    void "test mark done"() {
        given:
        service.socketService = GroovyMock(SocketService)
        FutureMessage fMsg = GroovyMock() { asBoolean() >> true }

        when: "passed in a nonexistent keyName"
        FutureMessage.metaClass."static".findByKeyName = { String key -> null }
        Result<FutureMessage> res = service.markDone(null)

        then: "not found"
        0 * service.socketService._
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "futureMessageService.markDone.messageNotFound"

        when: "passed in an existing keyName"
        FutureMessage.metaClass."static".findByKeyName = { String key -> fMsg }
        res = service.markDone(null)

        then:
        1 * service.socketService.sendFutureMessages(*_)
        1 * fMsg.setIsDone(true)
        1 * fMsg.save() >> fMsg
        res.status == ResultStatus.OK
    }
}
