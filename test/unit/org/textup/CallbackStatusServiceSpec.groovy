package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@TestFor(CallbackStatusService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    RecordNote, RecordNoteRevision, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class CallbackStatusServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
        twimlBuilder(TwimlBuilder)
    }

    def setup() {
        setupData()
        Helpers.metaClass.'static'.getMessageSource = { -> TestHelpers.mockMessageSource() }
        service.resultFactory = TestHelpers.getResultFactory(grailsApplication)
    }

    def cleanup() {
        cleanupData()
    }

    // Shared helpers
    // --------------

    void "test updating receipt status and receipt duration for call"() {
        given: "lists of valid and invalid receipts"
        List<RecordItemReceipt> validRpts = [], invalidRpts = []
        8.times {
            RecordItemReceipt validRpt = TestHelpers.buildReceipt(ReceiptStatus.PENDING),
                invalidRpt = TestHelpers.buildReceipt(ReceiptStatus.PENDING)
            rText1.addToReceipts(validRpt)
            assert validRpt.validate()
            assert !invalidRpt.validate()
            validRpts << validRpt
            invalidRpts << invalidRpt
        }
        assert validRpts.size() > 0
        assert invalidRpts.size() > 0
        Integer duration = 88

        when: "no receipts"
        Result<List<RecordItemReceipt>> statusRes = service.updateStatusForReceipts(null, ReceiptStatus.BUSY)
        Result<List<RecordItemReceipt>> durationRes = service.updateDurationForCall(null, duration)

        then:
        statusRes.status == ResultStatus.OK
        statusRes.payload == null
        durationRes.status == ResultStatus.OK
        durationRes.payload == null

        when: "valid receipts"
        statusRes = service.updateStatusForReceipts(validRpts, ReceiptStatus.BUSY)
        durationRes = service.updateDurationForCall(validRpts, duration)

        then:
        statusRes.status == ResultStatus.OK
        statusRes.payload.size() == validRpts.size()
        statusRes.payload.every { it.status == ReceiptStatus.BUSY }
        durationRes.status == ResultStatus.OK
        durationRes.payload.size() == validRpts.size()
        durationRes.payload.every { it.numBillable == duration }

        when: "invalid receipts"
        statusRes = service.updateStatusForReceipts(invalidRpts, ReceiptStatus.BUSY)
        durationRes = service.updateDurationForCall(invalidRpts, duration)

        then:
        statusRes.status == ResultStatus.UNPROCESSABLE_ENTITY
        durationRes.status == ResultStatus.UNPROCESSABLE_ENTITY
    }

    void "test sending items through socket"() {
        given: "list of valid receipts all belonging to the same item"
        service.socketService = Mock(SocketService)
        service.threadService = Mock(ThreadService)
        List<RecordItemReceipt> validRpts = []
        8.times {
            RecordItemReceipt validRpt = TestHelpers.buildReceipt()
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
        1 * service.threadService.submit(_ as Long, _ as TimeUnit, _ as Closure) >> { args ->
            args[2](); return null;
        }
        1 * service.socketService.sendItems(*_) >> { args ->
            assert args[0].size() == 1 // only 1 unique record item
            assert args[0][0] == rText1
            new Result().toGroup()
        }
    }

    void "test creating new receipt with status"() {
        given: "an existing parent id and valid child number"
        RecordItemReceipt validRpt = TestHelpers.buildReceipt(ReceiptStatus.PENDING)
        rText1.addToReceipts(validRpt)
        [validRpt, rText1]*.save(flush: true, failOnError: true)
        PhoneNumber childNumber = new PhoneNumber(number: TestHelpers.randPhoneNumber())
        assert childNumber.validate()
        String childId = TestHelpers.randString()
        ReceiptStatus status = ReceiptStatus.BUSY
        int rptBaseline = RecordItemReceipt.count()

        when:
        Result<List<RecordItemReceipt>> res = service.createNewReceiptsWithStatus(validRpt.apiId,
            childId, childNumber, status)

        then:
        RecordItemReceipt.count() == rptBaseline + 1
        res.status == ResultStatus.OK
        res.payload.size() == 1
        res.payload[0].status == status
        res.payload[0].apiId == childId
        res.payload[0].contactNumberAsString == childNumber.number
    }

    void "test finding and updating existing receipts with status"() {
        given: "an existing api id"
        RecordItemReceipt validRpt = TestHelpers.buildReceipt(ReceiptStatus.PENDING)
        rText1.addToReceipts(validRpt)
        [validRpt, rText1]*.save(flush: true, failOnError: true)
        int rptBaseline = RecordItemReceipt.count()
        ReceiptStatus status = ReceiptStatus.BUSY

        when:
        Result<List<RecordItemReceipt>> res = service.updateExistingReceiptsWithStatus(
            validRpt.apiId, status)

        then:
        RecordItemReceipt.count() == rptBaseline
        res.status == ResultStatus.OK
        res.payload.size() == 1
        res.payload[0].id == validRpt.id
        res.payload[0].status == status
    }

    void "test retrying parent call"() {
        given:
        service.callService = Mock(CallService)
        HttpServletRequest mockRequest = Mock(HttpServletRequest)
        GrailsParameterMap params = new GrailsParameterMap([:], mockRequest)


        when: "no remaining numbers"
        service.tryRetryParentCall(null, params)

        then:
        0 * service.callService._

        when: "has remaining numbers"
        params = new GrailsParameterMap([remaining:[TestHelpers.randPhoneNumber()]], mockRequest)
        service.tryRetryParentCall(null, params)

        then:
        1 * service.callService.retry(*_) >> new Result()
    }

    // Three types of entities to update
    // // ---------------------------------

    void "test handling update for text, child calls, and parent calls"() {
        given:
        service.threadService = Mock(ThreadService)
        RecordItemReceipt validRpt = TestHelpers.buildReceipt(ReceiptStatus.PENDING)
        rText1.addToReceipts(validRpt)
        [validRpt, rText1]*.save(flush: true, failOnError: true)
        int rptBaseline = RecordItemReceipt.count()
        ReceiptStatus status = ReceiptStatus.BUSY

        when: "handling update for text"
        Result<Void> res = service.handleUpdateForText(validRpt.apiId, status)

        then:
        1 * service.threadService.submit(*_)
        res.status == ResultStatus.NO_CONTENT
        RecordItemReceipt.get(validRpt.id).status == status
        RecordItemReceipt.count() == rptBaseline
    }

    void "test handling update for child calls"() {
        given:
        service.threadService = Mock(ThreadService)
        RecordItemReceipt validRpt = TestHelpers.buildReceipt(ReceiptStatus.PENDING)
        rText1.addToReceipts(validRpt)
        [validRpt, rText1]*.save(flush: true, failOnError: true)
        int rptBaseline = RecordItemReceipt.count()
        ReceiptStatus status = ReceiptStatus.BUSY
        String childId = TestHelpers.randString()
        Integer duration = 88
        PhoneNumber childNumber = new PhoneNumber(number: TestHelpers.randPhoneNumber())
        assert childNumber.validate()
        int originalNumReceipts = rText1.receipts.size()

        when:
        Result<Void> res = service.handleUpdateForChildCall(validRpt.apiId, childId, childNumber,
            status, duration)

        then:
        1 * service.threadService.submit(*_)
        res.status == ResultStatus.NO_CONTENT
        RecordItemReceipt.count() == rptBaseline + 1
        rText1.receipts.size() == originalNumReceipts + 1
        // parent receipt is not updated
        RecordItemReceipt.get(validRpt.id).status != status
        RecordItemReceipt.get(validRpt.id).numBillable != duration
        // newly-created child receipt is updated
        RecordItemReceipt.last().id in rText1.receipts*.id
        RecordItemReceipt.last().status == status
        RecordItemReceipt.last().numBillable== duration
    }

    void "test handling update for parent calls"() {
        given:
        service.threadService = Mock(ThreadService)
        service.callService = Mock(CallService)
        RecordItemReceipt validRpt = TestHelpers.buildReceipt(ReceiptStatus.PENDING)
        rText1.addToReceipts(validRpt)
        [validRpt, rText1]*.save(flush: true, failOnError: true)
        int rptBaseline = RecordItemReceipt.count()
        ReceiptStatus status = ReceiptStatus.BUSY
        Integer duration = 88
        HttpServletRequest mockRequest = Mock(HttpServletRequest)
        GrailsParameterMap params = new GrailsParameterMap([
            remaining:[TestHelpers.randPhoneNumber()]
        ], mockRequest)

        when: "call status is not failed"
        Result<Void> res = service.handleUpdateForParentCall(validRpt.apiId, status,
            duration, params)

        then:
        1 * service.threadService.submit(*_)
        0 * service.callService._
        res.status == ResultStatus.NO_CONTENT
        RecordItemReceipt.count() == rptBaseline
        RecordItemReceipt.get(validRpt.id).status == status
        RecordItemReceipt.get(validRpt.id).numBillable == duration

        when: "call status is failed"
        status = ReceiptStatus.FAILED
        res = service.handleUpdateForParentCall(validRpt.apiId, status, duration, params)

        then:
        1 * service.threadService.submit(*_)
        1 * service.callService.retry(*_) >> new Result()
        res.status == ResultStatus.NO_CONTENT
        RecordItemReceipt.count() == rptBaseline
    }

    // Overall
    // -------

    @DirtiesRuntime
    void "test selecting update method from passed-in parameters"() {
        given:
        int numText = 0
        int numChildCall = 0
        int numParentCall = 0
        service.metaClass.handleUpdateForText = { String textId, ReceiptStatus status ->
            numText++; new Result();
        }
        service.metaClass.handleUpdateForChildCall = { String parentId, String childId,
            PhoneNumber childNumber, ReceiptStatus status, Integer duration ->
            numChildCall++; new Result();
        }
        service.metaClass.handleUpdateForParentCall = { String callId, ReceiptStatus status,
            Integer duration, GrailsParameterMap params ->
            numParentCall++; new Result();
        }
        HttpServletRequest mockRequest = Mock(HttpServletRequest)

        when: "empty input"
        GrailsParameterMap params = new GrailsParameterMap([:], mockRequest)
        service.process(params)

        then: "no method called"
        0 == numText
        0 == numChildCall
        0 == numParentCall

        when: "call id but invalid status and duration"
        params = new GrailsParameterMap([
            CallSid: "hi",
            CallStatus: "no",
            CallDuration: "no"
        ], mockRequest)
        service.process(params)

        then: "no method called"
        0 == numText
        0 == numChildCall
        0 == numParentCall

        when: "parent call id, call id, valid status, valid duration, invalid child number"
        params = new GrailsParameterMap([
            CallSid: "hi",
            CallStatus: ReceiptStatus.PENDING.statuses[0],
            CallDuration: "88",
            ParentCallSid: "yes",
            (Constants.CALLBACK_CHILD_CALL_NUMBER_KEY): "not a valid phone number"
        ], mockRequest)
        service.process(params)

        then: "no method called"
        0 == numText
        0 == numChildCall
        0 == numParentCall

        when: "text id but invalid status"
        params = new GrailsParameterMap([
            MessageSid: "hi",
            MessageStatus: "no"
        ], mockRequest)
        service.process(params)

        then: "no method called"
        0 == numText
        0 == numChildCall
        0 == numParentCall

        when: "call id, valid status, valid duration"
        params = new GrailsParameterMap([
            CallSid: "hi",
            CallStatus: ReceiptStatus.PENDING.statuses[0],
            CallDuration: "88",
        ], mockRequest)
        service.process(params)

        then: "parent call"
        0 == numText
        0 == numChildCall
        1 == numParentCall

        when: "parent call id, call id, valid status, valid duration, valid child number"
        params = new GrailsParameterMap([
            CallSid: "hi",
            CallStatus: ReceiptStatus.PENDING.statuses[0],
            CallDuration: "88",
            ParentCallSid: "yes",
            (Constants.CALLBACK_CHILD_CALL_NUMBER_KEY): TestHelpers.randPhoneNumber()
        ], mockRequest)
        service.process(params)

        then: "child call"
        0 == numText
        1 == numChildCall
        1 == numParentCall

        when: "text id, valid status"
        params = new GrailsParameterMap([
            MessageSid: "hi",
            MessageStatus: ReceiptStatus.PENDING.statuses[0]
        ], mockRequest)
        service.process(params)

        then: "text message"
        1 == numText
        1 == numChildCall
        1 == numParentCall
    }
}
