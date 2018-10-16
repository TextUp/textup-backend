package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import grails.test.runtime.FreshRuntime
import java.util.concurrent.Future
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion, Token])
@TestMixin(HibernateTestMixin)
@TestFor(OutgoingMessageService)
class OutgoingMessageServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    // Call
    // ----

    void "test direct message call"() {
        given:
        service.tokenService = Mock(TokenService)
        Token tok1 = new Token(type: TokenType.CALL_DIRECT_MESSAGE,
            data: [message: "hi", identifier: "hi", language: null])
        tok1.save(flush: true, failOnError: true)

        when: "fail to find token"
        Result<Closure> res = service.directMessageCall(null)

        then:
        1 * service.tokenService.findDirectMessage(*_) >> new Result(payload: tok1)
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload).contains("twimlBuilder.call.messageIntro")

        when: "successfully finds token"
        res = service.directMessageCall(null)

        then:
        1 * service.tokenService.findDirectMessage(*_) >> new Result(status: ResultStatus.BAD_REQUEST)
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload).contains("twimlBuilder.error")
    }

    void "test handling after bridge call"() {
        given:
        TempRecordReceipt rpt = TestHelpers.buildTempReceipt()
        int cBaseline = RecordCall.count()
        int rptBaseline = RecordItemReceipt.count()

        when:
        Result<RecordCall> res = service.afterBridgeCall(c1, s1, rpt)
        RecordCall.withSession { it.flush() }

        then:
        RecordCall.count() == cBaseline + 1
        RecordItemReceipt.count() == rptBaseline + 1
        res.status == ResultStatus.CREATED
        res.payload instanceof RecordCall
        res.payload.outgoing == true
    }

    void "test starting bridge call errors"() {
        given:
        Phone p1 = Mock(Phone)
        Staff s1 = Mock(Staff)

        when: "phone is not active"
        Result<RecordCall> res = service.startBridgeCall(p1, null, s1)

        then:
        1 * p1.isActive >> false
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "phone.isInactive"

        when: "staff does not have personal phone"
        res = service.startBridgeCall(p1, null, s1)

        then:
        _ * p1.isActive >> true
        1 * s1.personalPhoneAsString >> null
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "outgoingMessageService.startBridgeCall.noPersonalNumber"
    }

    void "test starting bridge call"() {
        given: "baselines"
        TempRecordReceipt rpt = TestHelpers.buildTempReceipt()
        service.callService = Mock(CallService)
        int cBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()

        when:
        Result<RecordCall> res = service.startBridgeCall(p1, c1, s1)
        RecordCall.withSession { it.flush() }

        then:
        1 * service.callService.start(*_) >> new Result(payload: rpt)
        res.status == ResultStatus.CREATED
        res.payload instanceof RecordCall
        res.payload.receipts[0].apiId == rpt.apiId
        res.payload.receipts[0].contactNumberAsString == rpt.contactNumberAsString
        RecordCall.count() == cBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
    }

    void "test finishing bridge call"() {
        given:
        TypeConvertingMap params = new TypeConvertingMap(contactId: c1.id)

        when:
        Result<Closure> res = service.finishBridgeCall(params)

        then:
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload).contains("twimlBuilder.call.bridgeNumberStart")
    }

    // Text
    // ----

    void "test building outgoing messages"() {
        given:
        OutgoingMessage msg1 = Mock()
        WithRecord recordOwner1 = Mock()
        Record rec1 = Mock()

        when: "is outgoing call"
        ResultGroup<? extends RecordItem> resGroup = service.buildMessages(msg1, null)

        then:
        1 * msg1.toRecordOwners() >> [recordOwner1]
        1 * recordOwner1.tryGetRecord() >> new Result(payload: rec1)
        1 * msg1.isText >> false
        0 * rec1.storeOutgoingText(*_)
        1 * rec1.storeOutgoingCall(*_) >> new Result()
        resGroup.successes.size() == 1
        resGroup.anyFailures == false

        when: "is outgoing text"
        resGroup = service.buildMessages(msg1, null)

        then:
        1 * msg1.toRecordOwners() >> [recordOwner1]
        1 * recordOwner1.tryGetRecord() >> new Result(payload: rec1)
        1 * msg1.isText >> true
        1 * rec1.storeOutgoingText(*_) >> new Result()
        0 * rec1.storeOutgoingCall(*_)
        resGroup.successes.size() == 1
        resGroup.anyFailures == false
    }

    void "test try storing receipts"() {
        given:
        WithRecord owner = Mock()
        Record rec1 = Mock()
        RecordItem item1 = Mock()
        Long recId = 88

        when: "no items for a given record id"
        Map<Long, List<RecordItem>> recordIdToItems = [:]
        Result<Void> res = service.tryStoreReceipts(recordIdToItems, owner, [])

        then:
        1 * owner.tryGetRecord() >> new Result(payload: rec1)
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "outgoingMessageService.tryStoreReceipts.notFound"

        when: "all records match up with items"
        recordIdToItems = [(recId):[item1]]
        res = service.tryStoreReceipts(recordIdToItems, owner, [])

        then:
        1 * owner.tryGetRecord() >> new Result(payload: rec1)
        1 * rec1.id >> recId
        1 * item1.addAllReceipts(*_)
        res.status == ResultStatus.NO_CONTENT
    }

    @DirtiesRuntime
    void "test sending outgoing messages and storing receipts"() {
        given:
        OutgoingMessage msg1 = Mock()
        Contactable cont1 = Mock()
        ContactTag ct1 = Mock()
        service.tokenService = Mock(TokenService)
        service.outgoingMediaService = Mock(OutgoingMediaService)
        MockedMethod tryStoreReceipts = TestHelpers.mock(service, "tryStoreReceipts") { new Result() }
        Long cId = 88

        when:
        ResultGroup<?> resGroup = service.sendAndStore(null, null, msg1)

        then:
        1 * msg1.getContactIdToTags() >> [(cId):[ct1, ct1]]
        1 * service.tokenService.tryBuildAndPersistCallToken(*_)
        1 * msg1.toRecipients() >> [cont1]
        1 * cont1.contactId >> cId
        1 * service.outgoingMediaService.send(*_) >> new Result()
        tryStoreReceipts.callCount == 3 // once for contactable + twice for two associated tags
        resGroup.successes.size() == 3
        resGroup.anyFailures == false
    }

    @DirtiesRuntime
    void "test finishing processing outgoing messages overall"() {
        given:
        OutgoingMessage msg1 = Mock()
        Future<Result<MediaInfo>> mediaFuture = Mock()
        MediaInfo mInfo = Mock()
        MockedMethod sendAndStore = TestHelpers.mock(service, "sendAndStore") { new ResultGroup() }

        when: "no media"
        ResultGroup<?> resGroup = service.finishProcessingMessages(null, null, msg1)

        then:
        0 * mediaFuture._
        sendAndStore.callCount == 1

        when: "has media but future does NOT resolve to appropriate result"
        resGroup = service.finishProcessingMessages(null, null, msg1, mediaFuture)

        then:
        1 * mediaFuture.get() >> null
        sendAndStore.callCount == 1
        resGroup.anySuccesses == false
        resGroup.failures.size() == 1
        resGroup.failures[0].status == ResultStatus.INTERNAL_SERVER_ERROR
        resGroup.failures[0].errorMessages[0] == "outgoingMediaService.finishProcessingMessages.futureMissingPayload"

        when: "has media and future resolves to appropriate result"
        resGroup = service.finishProcessingMessages(null, null, msg1, mediaFuture)

        then:
        1 * mediaFuture.get() >> new Result(payload: mInfo)
        1 * msg1.setMedia(mInfo)
        sendAndStore.callCount == 2
    }

    @DirtiesRuntime
    void "test processing outgoing messages overall"() {
        given:
        Phone phone = Mock()
        Staff staff = Stub()
        RecordItem i1 = Mock()
        Future fut1 = Mock(Future)
        service.threadService = Mock(ThreadService)
        MockedMethod finishProcessingMessages = TestHelpers.mock(service, "finishProcessingMessages")
            { new ResultGroup() }
        Record rec1 = Mock()
        Long recordId = 88

        when: "phone is not active"
        Tuple<ResultGroup<RecordItem>, Future<?>> tuple = service.processMessage(phone, null, staff, null)

        then:
        1 * phone.isActive >> false
        0 * service.threadService._
        finishProcessingMessages.callCount == 0
        tuple.first.failures.size() == 1
        tuple.first.failures[0].status == ResultStatus.NOT_FOUND
        tuple.first.failures[0].errorMessages[0] == "phone.isInactive"
        tuple.second instanceof Future // no-op future

        when: "building messages yields some errors"
        MockedMethod buildMessages = TestHelpers.mock(service, "buildMessages")
            { new Result(status: ResultStatus.LOCKED).toGroup() }
        tuple = service.processMessage(phone, null, staff, null)

        then:
        1 * phone.isActive >> true
        0 * service.threadService._
        buildMessages.callCount == 1
        finishProcessingMessages.callCount == 0
        tuple.first.failures.size() == 1
        tuple.first.failures[0].status == ResultStatus.LOCKED
        tuple.second instanceof Future // no-op future

        when: "building messages is successful"
        buildMessages.restore()
        buildMessages = TestHelpers.mock(service, "buildMessages")
            { new Result(payload: i1).toGroup() }
        tuple = service.processMessage(phone, null, staff, null)

        then:
        1 * phone.isActive >> true
        1 * service.threadService.submit(*_) >> { args -> args[0].call(); fut1; }
        1 * i1.record >> rec1
        1 * rec1.id >> recordId
        buildMessages.callCount == 1
        finishProcessingMessages.callCount == 1
        // build map of record id to record items
        finishProcessingMessages.callArguments[0][0][recordId].contains(i1)
        tuple.first.anyFailures == false
        tuple.first.successes.size() == 1
        tuple.second == fut1
    }
}
