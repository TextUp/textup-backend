package org.textup.util

import com.pusher.rest.data.Result as PusherResult
import com.pusher.rest.Pusher
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.web.ControllerUnitTestMixin
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
@TestFor(SocketService)
class SocketServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
        IOCUtils.metaClass."static".getQuartzScheduler = { -> TestUtils.mockScheduler() }
    }

    void "test authenticating pusher connection"() {
        given:
        String str1 = TestUtils.randString()
        String socketId = TestUtils.randString()
        Map data = [(TestUtils.randString()): TestUtils.randString()]
        Staff s1 = TestUtils.buildStaff()

        service.pusherService = GroovyMock(Pusher)
        MockedMethod tryGetAnyAuthUser = MockedMethod.create(AuthUtils, "tryGetAnyAuthUser") {
            Result.createSuccess(s1)
        }

        when:
        Result res = service.authenticate(null, null)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = service.authenticate(str1, socketId)

        then:
        res.status == ResultStatus.FORBIDDEN

        when:
        res = service.authenticate(SocketUtils.channelToUserName(s1.username), socketId)

        then:
        1 * service.pusherService.authenticate(socketId, SocketUtils.channelToUserName(s1.username)) >>
            DataFormatUtils.toJsonString(data)
        res.status == ResultStatus.OK
        res.payload == data

        cleanup:
        tryGetAnyAuthUser?.restore()
    }

    void "test trying to send data to specific staff member"() {
        given:
        String str1 = TestUtils.randString()
        String str2 = TestUtils.randString()
        String dataString = DataFormatUtils.toJsonString(occupied: true)
        Staff s1 = TestUtils.buildStaff()

        PusherResult mockResult1 = GroovyMock()
        PusherResult mockResult2 = GroovyMock()
        service.pusherService = GroovyMock(Pusher)

        when:
        Result res = service.trySendDataToStaff(str1, s1, str2)

        then:
        1 * service.pusherService.get({ it.contains(SocketUtils.channelName(s1)) }) >> mockResult1
        mockResult1.status >> PusherResult.Status.SERVER_ERROR
        mockResult1.httpStatus >> ResultStatus.REQUESTED_RANGE_NOT_SATISFIABLE.intStatus
        res.status == ResultStatus.REQUESTED_RANGE_NOT_SATISFIABLE

        when:
        res = service.trySendDataToStaff(str1, s1, str2)

        then:
        1 * service.pusherService.get({ it.contains(SocketUtils.channelName(s1)) }) >> mockResult1
        mockResult1.status >> PusherResult.Status.SUCCESS
        mockResult1.message >> dataString
        1 * service.pusherService.trigger(SocketUtils.channelName(s1), str1, str2) >> mockResult2
        mockResult2.status >> PusherResult.Status.SERVER_ERROR
        mockResult2.httpStatus >> ResultStatus.REQUESTED_RANGE_NOT_SATISFIABLE.intStatus
        res.status == ResultStatus.REQUESTED_RANGE_NOT_SATISFIABLE

        when:
        res = service.trySendDataToStaff(str1, s1, str2)

        then:
        1 * service.pusherService.get({ it.contains(SocketUtils.channelName(s1)) }) >> mockResult1
        mockResult1.status >> PusherResult.Status.SUCCESS
        mockResult1.message >> dataString
        1 * service.pusherService.trigger(SocketUtils.channelName(s1), str1, str2) >> mockResult2
        mockResult2.status >> PusherResult.Status.SUCCESS
        res.status == ResultStatus.NO_CONTENT
    }

    void "test collating send payload"() {
        given:
        String str1 = TestUtils.randString()
        Collection staffs = [TestUtils.buildStaff(), TestUtils.buildStaff()]
        Collection toSend = []
        int numBatches = 3
        (SocketService.PAYLOAD_BATCH_SIZE * 3).times { toSend << it }

        MockedMethod trySendDataToStaff = MockedMethod.create(service, "trySendDataToStaff") {
            Result.createError([], ResultStatus.FORBIDDEN)
        }

        when:
        Result res = service.trySend(str1, staffs, toSend)

        then:
        trySendDataToStaff.hasBeenCalled
        res.status == ResultStatus.FORBIDDEN

        when:
        trySendDataToStaff = MockedMethod.create(trySendDataToStaff) { Result.void() }
        res = service.trySend(str1, staffs, toSend)

        then:
        trySendDataToStaff.callCount == numBatches * staffs.size()
        trySendDataToStaff.callArgs.every {  it[0] == str1 }
        trySendDataToStaff.callArgs.every {  it[1] in staffs }
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        trySendDataToStaff?.restore()
    }

    void "test sending specific types of items"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        Phone p1 = TestUtils.buildActiveStaffPhone(s1)
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        FutureMessage fMsg1 = TestUtils.buildFutureMessage(ipr1.record)

        MockedMethod trySend = MockedMethod.create(service, "trySend") { Result.void() }
        MockedMethod refreshTrigger = MockedMethod.create(fMsg1, "refreshTrigger")

        when:
        service.sendItems([rItem1])

        then:
        trySend.latestArgs == [SocketService.EVENT_RECORD_ITEMS, [s1], [rItem1]]

        when:
        service.sendIndividualWrappers([ipr1.toWrapper()])

        then:
        trySend.latestArgs == [SocketService.EVENT_CONTACTS, [s1], [ipr1.toWrapper()]]

        when:
        refreshTrigger.hasBeenCalled
        service.sendFutureMessages([fMsg1])

        then:
        trySend.latestArgs == [SocketService.EVENT_FUTURE_MESSAGES, [s1], [fMsg1]]

        when:
        service.sendPhone(p1)

        then:
        trySend.latestArgs == [SocketService.EVENT_PHONES, p1.owner.buildAllStaff(), [p1]]

        cleanup:
        trySend?.restore()
        refreshTrigger?.restore()
    }
}
