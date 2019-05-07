package org.textup.rest

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.web.ControllerUnitTestMixin
import org.textup.*
import org.textup.cache.*
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
@TestFor(CallbackStatusService)
@TestMixin([HibernateTestMixin, ControllerUnitTestMixin])
class CallbackStatusServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test retrying parent call"() {
        given:
        String callId = TestUtils.randString()

        TypeMap params1 = TestUtils.randTypeMap()
        TypeMap params2 = TypeMap.create((TwilioUtils.FROM): TestUtils.randPhoneNumber(),
            (CallService.RETRY_REMAINING): [TestUtils.randPhoneNumber()],
            (TwilioUtils.ID_ACCOUNT): TestUtils.randString(),
            (CallService.RETRY_AFTER_PICKUP): [(TestUtils.randString()): null])

        service.callService = GroovyMock(CallService)

        when: "no remaining numbers"
        service.tryRetryParentCall(callId, params1)

        then:
        0 * service.callService._

        when: "has remaining numbers"
        service.tryRetryParentCall(callId, params2)

        then:
        1 * service.callService.retry(params2[TwilioUtils.FROM],
            params2[CallService.RETRY_REMAINING],
            callId,
            params2[CallService.RETRY_AFTER_PICKUP],
            params2[TwilioUtils.ID_ACCOUNT]) >> Result.void()
    }

    void "test sending items after a delay"() {
        given:
        RecordItem rItem1 = TestUtils.buildRecordItem()
        RecordItemReceiptCacheInfo rptInfo = RecordItemReceiptCacheInfo.create(null, rItem1, null, null)

        service.threadService = GroovyMock(ThreadService)
        service.socketService = GroovyMock(SocketService)

        when:
        service.sendAfterDelay(null)

        then:
        0 * service.threadService._

        when:
        service.sendAfterDelay([rptInfo])

        then:
        1 * service.threadService.delay(*_) >> { args -> args[2].call(); null; }
        1 * service.socketService.sendItems([rItem1])
    }

    void "test updating existing receipts"() {
        given:
        String apiId = TestUtils.randString()
        Long rptId = TestUtils.randIntegerUpTo(88)
        ReceiptStatus stat1 = ReceiptStatus.values()[0]
        ReceiptStatus stat2 = ReceiptStatus.values()[1]
        Integer duration1 = TestUtils.randIntegerUpTo(88)
        Integer duration2 = TestUtils.randIntegerUpTo(88)
        RecordItemReceiptCacheInfo rptInfo = RecordItemReceiptCacheInfo.create(rptId, null,
            stat1, duration1)

        service.receiptCache = GroovyMock(RecordItemReceiptCache)

        when: "no cache infos found"
        Result res = service.updateExistingReceipts(apiId, null)

        then:
        1 * service.receiptCache.findEveryReceiptInfoByApiId(apiId) >> []
        0 * service.receiptCache.updateReceipts(*_)
        res.status == ResultStatus.OK
        res.payload == []

        when: "no change"
        res = service.updateExistingReceipts(apiId, stat1, duration1)

        then:
        1 * service.receiptCache.findEveryReceiptInfoByApiId(apiId) >> [rptInfo]
        0 * service.receiptCache.updateReceipts(*_)
        res.status == ResultStatus.OK
        res.payload == [rptInfo]

        when: "update for status change"
        res = service.updateExistingReceipts(apiId, stat2)

        then:
        1 * service.receiptCache.findEveryReceiptInfoByApiId(apiId) >> [rptInfo]
        1 * service.receiptCache.updateReceipts(apiId, [rptId], stat2, null) >> [rptInfo]
        res.status == ResultStatus.OK
        res.payload == [rptInfo]

        when: "update for duration change"
        res = service.updateExistingReceipts(apiId, stat1, duration2)

        then:
        1 * service.receiptCache.findEveryReceiptInfoByApiId(apiId) >> [rptInfo]
        1 * service.receiptCache.updateReceipts(apiId, [rptId], stat1, duration2) >> [rptInfo]
        res.status == ResultStatus.OK
        res.payload == [rptInfo]
    }

    void "test creating new receipts"() {
        given:
        String apiId1 = TestUtils.randString()
        String apiId2 = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        ReceiptStatus stat1 = ReceiptStatus.values()[0]
        Integer duration1 = TestUtils.randIntegerUpTo(88)

        RecordItem rItem1 = TestUtils.buildRecordItem()
        RecordItemReceiptCacheInfo rptInfo = RecordItemReceiptCacheInfo.create(null, rItem1, null, null)

        int rptBaseline = RecordItemReceipt.count()

        service.receiptCache = GroovyMock(RecordItemReceiptCache)

        when:
        Result res = service.createNewReceipts(apiId1, apiId2, pNum1, stat1, duration1)

        then:
        1 * service.receiptCache.findEveryReceiptInfoByApiId(apiId1) >> [rptInfo]
        res.status == ResultStatus.OK
        res.payload == [rptInfo]
        RecordItemReceipt.count() == rptBaseline + 1
        rItem1.receipts.size() == 1
        rItem1.receipts[0].apiId == apiId2
        rItem1.receipts[0].status == stat1
        rItem1.receipts[0].numBillable == duration1
        rItem1.receipts[0].contactNumber == pNum1
    }

    void "test updating parent call"() {
        given:
        String callId = TestUtils.randString()
        Integer duration = TestUtils.randIntegerUpTo(88)
        TypeMap params = TestUtils.randTypeMap()

        RecordItemReceiptCacheInfo rptInfo = GroovyStub()
        MockedMethod updateExistingReceipts = MockedMethod.create(service, "updateExistingReceipts") {
            Result.createSuccess([rptInfo])
        }
        MockedMethod tryRetryParentCall = MockedMethod.create(service, "tryRetryParentCall")
        MockedMethod sendAfterDelay = MockedMethod.create(service, "sendAfterDelay")

        when:
        service.handleUpdateForParentCall(callId, ReceiptStatus.BUSY, duration, params)

        then:
        updateExistingReceipts.latestArgs == [callId, ReceiptStatus.BUSY, duration]
        tryRetryParentCall.notCalled
        sendAfterDelay.latestArgs == [[rptInfo]]

        when:
        service.handleUpdateForParentCall(callId, ReceiptStatus.FAILED, duration, params)

        then:
        updateExistingReceipts.latestArgs == [callId, ReceiptStatus.FAILED, duration]
        tryRetryParentCall.latestArgs == [callId, params]
        sendAfterDelay.latestArgs == [[rptInfo]]

        cleanup:
        updateExistingReceipts?.restore()
        tryRetryParentCall?.restore()
        sendAfterDelay?.restore()
    }

    void "test updating child call"() {
        given:
        String apiId1 = TestUtils.randString()
        String apiId2 = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        ReceiptStatus status = ReceiptStatus.values()[0]
        Integer duration = TestUtils.randIntegerUpTo(88)

        RecordItemReceiptCacheInfo rptInfo = GroovyStub()
        MockedMethod createNewReceipts = MockedMethod.create(service, "createNewReceipts") {
            Result.createSuccess([rptInfo])
        }
        MockedMethod sendAfterDelay = MockedMethod.create(service, "sendAfterDelay")

        when:
        service.handleUpdateForChildCall(apiId1, apiId2, pNum1, status, duration)

        then:
        createNewReceipts.latestArgs == [apiId1, apiId2, pNum1, status, duration]
        sendAfterDelay.latestArgs == [[rptInfo]]

        cleanup:
        createNewReceipts?.restore()
        sendAfterDelay?.restore()
    }

    void "test updating text"() {
        given:
        String apiId = TestUtils.randString()
        ReceiptStatus status = ReceiptStatus.values()[0]

        RecordItemReceiptCacheInfo rptInfo = GroovyStub()
        MockedMethod updateExistingReceipts = MockedMethod.create(service, "updateExistingReceipts") {
            Result.createSuccess([rptInfo])
        }
        MockedMethod sendAfterDelay = MockedMethod.create(service, "sendAfterDelay")

        when:
        service.handleUpdateForText(apiId, status)

        then:
        updateExistingReceipts.latestArgs == [apiId, status, null]
        sendAfterDelay.latestArgs == [[rptInfo]]

        cleanup:
        updateExistingReceipts?.restore()
        sendAfterDelay?.restore()
    }

    void "test processing overall + duration is allowed to be zero"() {
        given:
        ReceiptStatus stat1 = ReceiptStatus.values()[0]
        ReceiptStatus stat2 = ReceiptStatus.values()[1]
        ReceiptStatus stat3 = ReceiptStatus.values()[2]
        TypeMap params1 = TypeMap.create((TwilioUtils.ID_CALL): TestUtils.randString(),
            (TwilioUtils.STATUS_CALL): stat1.statuses[0],
            (TwilioUtils.CALL_DURATION): 0,
            (TwilioUtils.ID_PARENT_CALL): TestUtils.randString(),
            (CallbackUtils.PARAM_CHILD_CALL_NUMBER): TestUtils.randPhoneNumber())
        TypeMap params2 = TypeMap.create((TwilioUtils.ID_CALL): TestUtils.randString(),
            (TwilioUtils.STATUS_CALL): stat2.statuses[0],
            (TwilioUtils.CALL_DURATION): 0)
        TypeMap params3 = TypeMap.create((TwilioUtils.ID_TEXT): TestUtils.randString(),
            (TwilioUtils.STATUS_TEXT): stat3.statuses[0])

        MockedMethod handleUpdateForChildCall = MockedMethod.create(service, "handleUpdateForChildCall")
        MockedMethod handleUpdateForParentCall = MockedMethod.create(service, "handleUpdateForParentCall")
        MockedMethod handleUpdateForText = MockedMethod.create(service, "handleUpdateForText")

        when:
        service.process(params1)

        then:
        handleUpdateForChildCall.latestArgs == [params1[TwilioUtils.ID_PARENT_CALL],
            params1[TwilioUtils.ID_CALL],
            params1[CallbackUtils.PARAM_CHILD_CALL_NUMBER],
            stat1,
            params1[TwilioUtils.CALL_DURATION]]
        handleUpdateForParentCall.notCalled
        handleUpdateForText.notCalled

        when:
        service.process(params2)

        then:
        handleUpdateForChildCall.hasBeenCalled
        handleUpdateForParentCall.latestArgs == [params2[TwilioUtils.ID_CALL],
            stat2,
            params2[TwilioUtils.CALL_DURATION],
            params2]
        handleUpdateForText.notCalled

        when:
        service.process(params3)

        then:
        handleUpdateForChildCall.hasBeenCalled
        handleUpdateForParentCall.hasBeenCalled
        handleUpdateForText.latestArgs == [params3[TwilioUtils.ID_TEXT], stat3]

        cleanup:
        handleUpdateForChildCall?.restore()
        handleUpdateForParentCall?.restore()
        handleUpdateForText?.restore()
    }
}
