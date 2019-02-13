package org.textup.rest

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
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
@TestMixin(HibernateTestMixin)
class CallbackStatusServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
        IOCUtils.metaClass."static".getMessageSource = { -> TestUtils.mockMessageSource() }
    }

    def cleanup() {
        cleanupData()
    }

    // Shared helpers
    // --------------

    void "test sending items through socket"() {
        given: "list of valid receipts all belonging to the same item"
        service.socketService = Mock(SocketService)
        service.threadService = Mock(ThreadService)
        List<RecordItemReceipt> validRpts = []
        8.times {
            RecordItemReceipt validRpt = TestUtils.buildReceipt()
            rText1.addToReceipts(validRpt)
            assert validRpt.validate()
            validRpts << validRpt
        }
        assert validRpts.size() > 0

        when: "no receipts"
        service.sendItemsThroughSocket(null)

        then:
        0 * service.threadService._
        0 * service.socketService._

        when:
        service.sendItemsThroughSocket(validRpts)

        then: "only one item passed to socket service"
        1 * service.threadService.delay(_ as Long, _ as TimeUnit, _ as Closure) >> { args ->
            args[2](); return null;
        }
        1 * service.socketService.sendItems(*_) >> { args ->
            assert args[0].size() == 1 // only 1 unique record item
            assert args[0][0] == rText1
            new Result().toGroup()
        }
    }

    void "test retrying parent call"() {
        given:
        String accountId = TestUtils.randString()
        service.callService = Mock(CallService)
        TypeMap params = TypeMap.create([:])

        when: "no remaining numbers"
        service.tryRetryParentCall(null, params)

        then:
        0 * service.callService._

        when: "has remaining numbers"
        params = TypeMap.create(remaining: [TestUtils.randPhoneNumberString()], AccountSid: accountId)
        service.tryRetryParentCall(null, params)

        then:
        1 * service.callService.retry(_, _, _, _, accountId) >> new Result()
    }

    void "test creating new receipts"() {
        given: "an existing parent id and valid child number"
        service.receiptCache = GroovyMock(RecordItemReceiptCache)
        String parentId = TestUtils.randString()
        String childId = TestUtils.randString()
        PhoneNumber childNumber = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        ReceiptStatus status = ReceiptStatus.BUSY
        Integer duration = TestUtils.randIntegerUpTo(100, true)

        RecordItemReceipt mockRpt = GroovyMock()
        RecordItem mockItem = GroovyMock() { asBoolean() >> true }

        when:
        Result<List<RecordItemReceipt>> res = service.createNewReceipts(parentId, childId,
            childNumber, status, duration)

        then:
        1 * service.receiptCache.findReceiptsByApiId(parentId) >> [mockRpt]
        (1.._) * mockRpt.getItem() >> mockItem
        1 * mockItem.addToReceipts(_ as RecordItemReceipt)
        1 * mockItem.merge() >> mockItem
        res.status == ResultStatus.OK
        res.payload.size() == 1
        res.payload[0].status == status
        res.payload[0].apiId == childId
        res.payload[0].contactNumberAsString == childNumber.number
    }

    void "test updating existing receipts"() {
        given:
        service.receiptCache = GroovyMock(RecordItemReceiptCache)
        String apiId = TestUtils.randString()
        ReceiptStatus status1 = ReceiptStatus.BUSY
        ReceiptStatus oldStatus = ReceiptStatus.SUCCESS
        Integer duration1 = TestUtils.randIntegerUpTo(100, true)
        Integer oldDuration = TestUtils.randIntegerUpTo(100, true)

        RecordItemReceipt mockRpt1 = GroovyMock()
        RecordItemReceipt mockRpt2 = GroovyMock()
        MockedMethod shouldUpdateStatus = MockedMethod.create(TwilioUtils, "shouldUpdateStatus") { true }
        MockedMethod shouldUpdateDuration = MockedMethod.create(TwilioUtils, "shouldUpdateDuration") { true }

        when: "no receipts found"
        Result<List<RecordItemReceipt>> res = service.updateExistingReceipts(apiId, status1, duration1)

        then:
        1 * service.receiptCache.findReceiptsByApiId(apiId) >> []
        shouldUpdateStatus.callCount == 0
        shouldUpdateDuration.callCount == 0
        res.status == ResultStatus.OK
        res.payload instanceof List
        res.payload.isEmpty()

        when: "some receipts found"
        res = service.updateExistingReceipts(apiId, status1, duration1)

        then:
        1 * service.receiptCache.findReceiptsByApiId(apiId) >> [mockRpt1]
        1 * mockRpt1.getStatus() >> oldStatus
        1 * mockRpt1.getNumBillable() >> oldDuration
        shouldUpdateStatus.callCount == 1 || shouldUpdateDuration.callCount == 1
        1 * service.receiptCache.updateReceipts([mockRpt1], status1, duration1) >> [mockRpt2]
        res.status == ResultStatus.OK
        res.payload instanceof List
        res.payload.isEmpty() == false
        res.payload == [mockRpt2]

        cleanup:
        shouldUpdateStatus.restore()
        shouldUpdateDuration.restore()
    }

    // Three types of entities to update
    // ---------------------------------

    void "test updating text"() {
        given:
        RecordItemReceipt mockRpt = GroovyMock()
        String textId = TestUtils.randString()
        ReceiptStatus status = ReceiptStatus.SUCCESS

        MockedMethod updateExistingReceipts = MockedMethod.create(service, "updateExistingReceipts") {
            new Result(payload: [mockRpt])
        }
        MockedMethod sendItemsThroughSocket = MockedMethod.create(service, "sendItemsThroughSocket")

        when:
        Result<Void> res = service.handleUpdateForText(textId, status)

        then:
        updateExistingReceipts.callCount == 1
        updateExistingReceipts.allArgs[0] == [textId, status, null]
        sendItemsThroughSocket.callCount == 1
        sendItemsThroughSocket.allArgs[0] == [[mockRpt]]
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        updateExistingReceipts.restore()
        sendItemsThroughSocket.restore()
    }

    void "test updating child call"() {
        given:
        String parentId = TestUtils.randString()
        String childId = TestUtils.randString()
        PhoneNumber childNumber = new PhoneNumber(number: TestUtils.randPhoneNumberString())
        ReceiptStatus status = ReceiptStatus.SUCCESS
        Integer duration = TestUtils.randIntegerUpTo(100, true)

        RecordItemReceipt mockRpt = GroovyMock()
        MockedMethod createNewReceipts = MockedMethod.create(service, "createNewReceipts") {
            new Result(payload: [mockRpt])
        }
        MockedMethod sendItemsThroughSocket = MockedMethod.create(service, "sendItemsThroughSocket")

        when:
        Result<Void> res = service
            .handleUpdateForChildCall(parentId, childId, childNumber, status, duration)

        then:
        createNewReceipts.callCount == 1
        createNewReceipts.allArgs[0] == [parentId, childId, childNumber, status, duration]
        sendItemsThroughSocket.callCount == 1
        sendItemsThroughSocket.allArgs[0] == [[mockRpt]]
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        createNewReceipts.restore()
        sendItemsThroughSocket.restore()
    }

    void "test updating parent call"() {
        given:
        String callId = TestUtils.randString()
        Integer duration = TestUtils.randIntegerUpTo(100, true)
        TypeMap params = TypeMap.create([:])

        RecordItemReceipt mockRpt = GroovyMock()
        MockedMethod updateExistingReceipts = MockedMethod.create(service, "updateExistingReceipts") {
            new Result(payload: [mockRpt])
        }
        MockedMethod tryRetryParentCall = MockedMethod.create(service, "tryRetryParentCall")
        MockedMethod sendItemsThroughSocket = MockedMethod.create(service, "sendItemsThroughSocket")

        when: "not failed"
        Result<Void> res = service
            .handleUpdateForParentCall(callId, ReceiptStatus.SUCCESS, duration, params)

        then:
        updateExistingReceipts.callCount == 1
        updateExistingReceipts.allArgs[0] == [callId, ReceiptStatus.SUCCESS, duration]
        tryRetryParentCall.callCount == 0
        sendItemsThroughSocket.callCount == 1
        sendItemsThroughSocket.allArgs[0] == [[mockRpt]]
        res.status == ResultStatus.NO_CONTENT

        when: "is failed"
        res = service.handleUpdateForParentCall(callId, ReceiptStatus.FAILED, duration, params)

        then:
        updateExistingReceipts.callCount == 2
        updateExistingReceipts.allArgs[1] == [callId, ReceiptStatus.FAILED, duration]
        tryRetryParentCall.callCount == 1
        tryRetryParentCall.allArgs[0] == [callId, params]
        sendItemsThroughSocket.callCount == 2
        sendItemsThroughSocket.allArgs[1] == [[mockRpt]]
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        updateExistingReceipts.restore()
        tryRetryParentCall.restore()
        sendItemsThroughSocket.restore()
    }

    // Overall
    // -------

    @DirtiesRuntime
    void "test selecting update method from passed-in parameters"() {
        given:
        MockedMethod handleUpdateForText = MockedMethod.create(service, "handleUpdateForText")
            { new Result() }
        MockedMethod handleUpdateForChildCall = MockedMethod.create(service, "handleUpdateForChildCall")
            { new Result() }
        MockedMethod handleUpdateForParentCall = MockedMethod.create(service, "handleUpdateForParentCall")
            { new Result() }

        when: "empty input"
        TypeMap params = TypeMap.create([:])
        service.process(params)

        then: "no method called"
        0 == handleUpdateForText.callCount
        0 == handleUpdateForChildCall.callCount
        0 == handleUpdateForParentCall.callCount

        when: "call id but invalid status and duration"
        params = TypeMap.create([
            CallSid: "hi",
            CallStatus: "no",
            CallDuration: "no"
        ])
        service.process(params)

        then: "no method called"
        0 == handleUpdateForText.callCount
        0 == handleUpdateForChildCall.callCount
        0 == handleUpdateForParentCall.callCount

        when: "parent call id, call id, valid status, valid duration, invalid child number"
        params = TypeMap.create([
            CallSid: "hi",
            CallStatus: ReceiptStatus.PENDING.statuses[0],
            CallDuration: "88",
            ParentCallSid: "yes",
            (Constants.CALLBACK_CHILD_CALL_NUMBER_KEY): "not a valid phone number"
        ])
        service.process(params)

        then: "no method called"
        0 == handleUpdateForText.callCount
        0 == handleUpdateForChildCall.callCount
        0 == handleUpdateForParentCall.callCount

        when: "text id but invalid status"
        params = TypeMap.create([
            MessageSid: "hi",
            MessageStatus: "no"
        ])
        service.process(params)

        then: "no method called"
        0 == handleUpdateForText.callCount
        0 == handleUpdateForChildCall.callCount
        0 == handleUpdateForParentCall.callCount

        when: "call id, valid status, valid duration"
        params = TypeMap.create([
            CallSid: "hi",
            CallStatus: ReceiptStatus.PENDING.statuses[0],
            CallDuration: "88",
        ])
        service.process(params)

        then: "parent call"
        0 == handleUpdateForText.callCount
        0 == handleUpdateForChildCall.callCount
        1 == handleUpdateForParentCall.callCount

        when: "parent call id, call id, valid status, valid duration, valid child number"
        params = TypeMap.create([
            CallSid: "hi",
            CallStatus: ReceiptStatus.PENDING.statuses[0],
            CallDuration: "88",
            ParentCallSid: "yes",
            (Constants.CALLBACK_CHILD_CALL_NUMBER_KEY): TestUtils.randPhoneNumberString()
        ])
        service.process(params)

        then: "child call"
        0 == handleUpdateForText.callCount
        1 == handleUpdateForChildCall.callCount
        1 == handleUpdateForParentCall.callCount

        when: "text id, valid status"
        params = TypeMap.create([
            MessageSid: "hi",
            MessageStatus: ReceiptStatus.PENDING.statuses[0]
        ])
        service.process(params)

        then: "text message"
        1 == handleUpdateForText.callCount
        1 == handleUpdateForChildCall.callCount
        1 == handleUpdateForParentCall.callCount
    }
}
