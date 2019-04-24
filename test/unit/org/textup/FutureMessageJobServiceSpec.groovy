package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import java.util.concurrent.*
import org.quartz.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

// When writing Groovy code, use GroovyMock instead of Mock which is aware of Groovy features
// such as normalizing setProperty("name", value) to setName(value)
// see: https://stackoverflow.com/a/46572812

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
@TestFor(FutureMessageJobService)
class FutureMessageJobServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    Scheduler scheduler

    def setup() {
        TestUtils.standardMockSetup()
        scheduler = GroovyMock(Scheduler)
        IOCUtils.metaClass."static".getQuartzScheduler = { -> scheduler }
    }

    void "test finishing notify self"() {
        given:
        int num1 = TestUtils.randIntegerUpTo(88, true)
        int num2 = TestUtils.randIntegerUpTo(88, true)

        RecordItem rItem1 = TestUtils.buildRecordItem()
        rItem1.record = null
        assert rItem1.validate() == false
        RecordItem rItem2 = TestUtils.buildRecordItem()

        DehydratedNotificationGroup dng1 = GroovyMock()
        NotificationGroup notifGroup1 = GroovyMock()
        Future fut1 = GroovyMock()
        service.notificationService = GroovyMock(NotificationService)

        when: "has some errors"
        Result res = service.finishNotifySelf(dng1, fut1)

        then:
        1 * fut1.get()
        1 * dng1.tryRehydrate() >> Result.createSuccess(notifGroup1)
        1 * notifGroup1.eachItem(_ as Closure) >> { args -> args[0].call(rItem1) }
        1 * notifGroup1.getNumNotifiedForItem(rItem1) >> num1
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "no errors"
        res = service.finishNotifySelf(dng1, fut1)

        then:
        1 * fut1.get()
        1 * dng1.tryRehydrate() >> Result.createSuccess(notifGroup1)
        1 * notifGroup1.eachItem(_ as Closure) >> { args -> args[0].call(rItem2) }
        1 * notifGroup1.getNumNotifiedForItem(rItem2) >> num2
        1 * service.notificationService.send(notifGroup1) >> Result.void()
        res.status == ResultStatus.NO_CONTENT
        rItem2.numNotified == num2
    }

    void "test starting notify self"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        RecordItem rItem2 = TestUtils.buildRecordItem(gpr1.record)

        Future fut1 = GroovyMock()
        NotificationGroup notifGroup1 = GroovyMock()
        DehydratedNotificationGroup dng1 = GroovyMock()
        service.threadService = GroovyMock(ThreadService)
        MockedMethod tryBuildNotificationGroup = MockedMethod.create(NotificationUtils, "tryBuildNotificationGroup") {
            Result.createSuccess(notifGroup1)
        }
        MockedMethod tryCreate = MockedMethod.create(DehydratedNotificationGroup, "tryCreate") {
            Result.createSuccess(dng1)
        }
        MockedMethod finishNotifySelf = MockedMethod.create(service, "finishNotifySelf") {
            Result.void()
        }

        when:
        service.startNotifySelf([rItem1, rItem2], fut1)

        then:
        tryBuildNotificationGroup.latestArgs == [[rItem1]] // group item removed
        1 * notifGroup1.canNotifyAnyAllFrequencies() >> true
        1 * service.threadService.submit(_ as Closure) >> { args -> args[0].call(); null; }
        tryCreate.latestArgs == [notifGroup1]
        finishNotifySelf.latestArgs == [dng1, fut1]

        cleanup:
        tryBuildNotificationGroup?.restore()
        tryCreate?.restore()
        finishNotifySelf?.restore()
    }

    void "test marking done"() {
        given:
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()

        service.socketService = GroovyMock(SocketService)

        when:
        Result res = service.markDone(fMsg1.keyName)

        then:
        1 * service.socketService.sendFutureMessages([fMsg1])
        res.status == ResultStatus.OK
        res.payload == fMsg1
        fMsg1.isDone
    }

    void "test executing"() {
        given:
        RecordItem rItem1 = TestUtils.buildRecordItem()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)
        FutureMessage fMsg1 = TestUtils.buildFutureMessage(ipr1.record)
        fMsg1.notifySelfOnSend = true
        Staff s1 = TestUtils.buildStaff()

        Future fut1 = GroovyMock()
        service.outgoingMessageService = GroovyMock(OutgoingMessageService)
        MockedMethod startNotifySelf = MockedMethod.create(service, "startNotifySelf")

        when:
        Result res = service.execute(fMsg1.keyName, s1.id)

        then:
        1 * service.outgoingMessageService.tryStart(fMsg1.type.toRecordItemType(),
            { it.language == fMsg1.language && !(spr1 in it.all) && ipr1 in it.all }, // only owned
            { it.text == fMsg1.message && !it.media && !it.location },
            Author.create(s1)) >> Result.createSuccess(Tuple.create([rItem1], fut1))
        startNotifySelf.latestArgs == [[rItem1], fut1]
        res.status == ResultStatus.NO_CONTENT
        rItem1.wasScheduled == true

        cleanup:
        startNotifySelf?.restore()
    }

    void "test trying to unschedule"() {
        given:
        String errMsg1 = TestUtils.randString()
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()

        when:
        Result res = service.tryUnschedule(fMsg1)

        then:
        1 * scheduler.unscheduleJob(fMsg1.triggerKey) >> true
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.tryUnschedule(fMsg1)

        then:
        1 * scheduler.unscheduleJob(fMsg1.triggerKey) >> {
            throw new IllegalArgumentException(errMsg1)
        }
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        errMsg1 in res.errorMessages
    }

    void "test cancelling all"() {
        given:
        FutureMessage fMsg1 = TestUtils.buildFutureMessage()

        when:
        ResultGroup resGroup = service.cancelAll([fMsg1])

        then:
        1 * scheduler.unscheduleJob(fMsg1.triggerKey) >> true
        resGroup.anyFailures == false
        resGroup.payload == [fMsg1]
        fMsg1.isDone
    }

    void "test trying to schedule"() {
        given:
        FutureMessage fMsg1 = GroovyMock()
        TriggerKey trigKey = GroovyMock()
        Trigger newTrigger = GroovyMock()
        Trigger oldTrigger = GroovyMock() { asBoolean() >> true }
        MockedMethod tryBuildTrigger = MockedMethod.create(QuartzUtils, "tryBuildTrigger") {
            Result.createSuccess(Tuple.create(newTrigger, null))
        }

        when:
        Result res = service.trySchedule(false, fMsg1)

        then:
        1 * fMsg1.shouldReschedule >> false
        tryBuildTrigger.notCalled
        res.status == ResultStatus.NO_CONTENT

        when: "only new trigger"
        res = service.trySchedule(true, fMsg1)

        then:
        tryBuildTrigger.latestArgs == [fMsg1]
        1 * scheduler.scheduleJob(newTrigger) >> new Date()
        res.status == ResultStatus.NO_CONTENT

        when: "both old and new triggers"
        tryBuildTrigger = MockedMethod.create(tryBuildTrigger) {
            Result.createSuccess(Tuple.create(newTrigger, oldTrigger))
        }
        res = service.trySchedule(true, fMsg1)

        then:
        tryBuildTrigger.latestArgs == [fMsg1]
        1 * fMsg1.triggerKey >> trigKey
        1 * scheduler.rescheduleJob(trigKey, newTrigger) >> new Date()
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.trySchedule(true, fMsg1)

        then:
        tryBuildTrigger.latestArgs == [fMsg1]
        1 * fMsg1.triggerKey >> trigKey
        1 * scheduler.rescheduleJob(trigKey, newTrigger) >> null
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
        res.errorMessages == ["futureMessageJobService.unspecifiedError"]

        cleanup:
        tryBuildTrigger?.restore()
    }
}
