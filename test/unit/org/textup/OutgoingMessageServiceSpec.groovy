package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import grails.test.runtime.FreshRuntime
import java.util.concurrent.atomic.AtomicInteger
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
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

    // Text
    // ----

    void "test sending outgoing message asynchronously"() {
        given:
        AtomicInteger timesCalled = new AtomicInteger()
        service.tokenService = Mock(TokenService)
        service.mediaService = Mock(MediaService)
        OutgoingMessage msg1 = TestHelpers.buildOutgoingMessage("hello")
        List<Contactable> recipients = []
        50.times { recipients << p1.createContact([:], [TestHelpers.randPhoneNumber()]).payload }

        when:
        Result<Map<Contactable, Result<List<TempRecordReceipt>>>> res = service.sendForContactables(
            p1, recipients, msg1, null)

        then: "send to all recipients"
        recipients.size() * service.mediaService.sendWithMedia(*_) >>
            { timesCalled.getAndIncrement(); new Result(); }
        timesCalled.get() == recipients.size()
        res.status == ResultStatus.OK
        res.payload.size() == recipients.size()
    }

    void "test storing for a single contactable"() {
        given:
        OutgoingMessage msg1 = TestHelpers.buildOutgoingMessage()
        List<TempRecordReceipt> receipts = []
        10.times { receipts << TestHelpers.buildTempReceipt() }
        Closure<Void> doWhenAddingReceipt = { arg1, arg2 -> }
        int rBaseline = RecordText.count()
        int cBaseline = RecordCall.count()
        int rptBaseline = RecordItemReceipt.count()

        when: "cannot get record"
        sc1.stopSharing()
        sc1.save(flush: true, failOnError: true)
        Result<RecordItem> res = service.storeForSingleContactable(msg1, null, s1.toAuthor(),
            doWhenAddingReceipt, sc1, receipts)

        then: "short circuit"
        res.status == ResultStatus.FORBIDDEN
        RecordText.count() == rBaseline
        RecordCall.count() == cBaseline
        RecordItemReceipt.count() == rptBaseline

        when: "message is text"
        msg1.type = RecordItemType.TEXT
        res = service.storeForSingleContactable(msg1, null, s1.toAuthor(), doWhenAddingReceipt,
            c1, receipts)
        RecordText.withSession { it.flush() }

        then:
        res.status == ResultStatus.CREATED
        RecordText.count() == rBaseline + 1
        RecordCall.count() == cBaseline
        RecordItemReceipt.count() == rptBaseline + receipts.size()

        when: "message is call"
        msg1.type = RecordItemType.CALL
        res = service.storeForSingleContactable(msg1, null, s1.toAuthor(), doWhenAddingReceipt,
            c1, receipts)
        RecordText.withSession { it.flush() }

        then:
        res.status == ResultStatus.CREATED
        RecordText.count() == rBaseline + 1
        RecordCall.count() == cBaseline + 1
        RecordItemReceipt.count() == rptBaseline + receipts.size() + receipts.size()
    }

    @DirtiesRuntime
    void "test storing for many contactables"() {
        given:
        OutgoingMessage msg1 = TestHelpers.buildOutgoingMessage()
        Closure<Void> doWhenAddingReceipt = { arg1, arg2 -> }
        Map<Contactable, Result<List<TempRecordReceipt>>> contactableToReceiptResults = [:]
        5.times {
            String num = TestHelpers.randPhoneNumber()
            contactableToReceiptResults[p1.createContact([:], [num]).payload] = new Result(payload: [])
        }
        int numTimesCalled = 0
        service.metaClass.storeForSingleContactable = { OutgoingMessage msg2, MediaInfo mInfo,
            Author author1, Closure<Void> doWhenAddingReceipt2, Contactable c2,
            List<TempRecordReceipt> receipts ->
            numTimesCalled++; new Result();
        }

        when:
        ResultGroup<RecordItem> resGroup = service.storeForContactables(msg1, null, s1.toAuthor(),
            doWhenAddingReceipt, contactableToReceiptResults)

        then:
        resGroup.successes.size() == contactableToReceiptResults.size()
        contactableToReceiptResults.size() == numTimesCalled
    }

    void "test storing for a single tag"() {
        given:
        OutgoingMessage msg1 = TestHelpers.buildOutgoingMessage()
        int numTimesCalled = 0
        Closure<List<TempRecordReceipt>> getReceiptsFromContactId = { Long cId ->
            numTimesCalled++; [TestHelpers.buildTempReceipt()];
        }
        5.times { tag1.addToMembers(p1.createContact([:], [TestHelpers.randPhoneNumber()]).payload) }
        assert tag1.members.size() > 1
        int rBaseline = RecordText.count()
        int cBaseline = RecordCall.count()
        int rptBaseline = RecordItemReceipt.count()

        when: "message is text"
        msg1.type = RecordItemType.TEXT
        Result<RecordItem> res = service.storeForSingleTag(msg1, null, s1.toAuthor(),
            getReceiptsFromContactId, tag1)
        RecordText.withSession { it.flush() }

        then:
        tag1.members.size() == numTimesCalled
        res.status == ResultStatus.CREATED
        RecordText.count() == rBaseline + 1
        RecordCall.count() == cBaseline
        RecordItemReceipt.count() == rptBaseline + tag1.members.size()

        when: "message is call"
        msg1.type = RecordItemType.CALL
        res = service.storeForSingleTag(msg1, null, s1.toAuthor(), getReceiptsFromContactId, tag1)
        RecordText.withSession { it.flush() }

        then:
        tag1.members.size() * 2 == numTimesCalled
        res.status == ResultStatus.CREATED
        RecordText.count() == rBaseline + 1
        RecordCall.count() == cBaseline + 1
        RecordItemReceipt.count() == rptBaseline + tag1.members.size() + tag1.members.size()
    }

    @DirtiesRuntime
    void "test storing for tags"() {
        given:
        OutgoingMessage msg1 = TestHelpers.buildOutgoingMessage()
        15.times { msg1.tags.recipients << p1.createTag(name: UUID.randomUUID().toString()).payload }
        Closure<List<TempRecordReceipt>> getReceiptsFromContactId = { Long cId -> [] }
        ResultGroup<RecordItem> resGroup1 = new ResultGroup<>()
        int numTimesCalled = 0
        service.metaClass.storeForSingleTag = { OutgoingMessage msg2, MediaInfo mInfo, Author author1,
            Closure<List<TempRecordReceipt>> getReceiptsFromContactId2, ContactTag ct2 ->
            numTimesCalled++; new Result();
        }

        when: "result group has no successes"
        assert resGroup1.anySuccesses == false
        ResultGroup<RecordItem> resGroup2 = service.storeForTags(msg1, null, s1.toAuthor(),
            getReceiptsFromContactId, resGroup1)

        then: "short circuit"
        0 == numTimesCalled
        resGroup2.anySuccesses == false

        when: "result group has some successes"
        resGroup1 << new Result()
        assert resGroup1.anySuccesses == true
        resGroup2 = service.storeForTags(msg1, null, s1.toAuthor(), getReceiptsFromContactId, resGroup1)

        then:
        msg1.tags.recipients.size() == numTimesCalled
    }

    @Unroll
    void "test sending message overall for #type"() {
        given: "baselines"
        service.tokenService = Mock(TokenService)
        service.mediaService = Mock(MediaService)
        OutgoingMessage msg = TestHelpers.buildOutgoingMessage()
        msg.type = type
        msg.contacts.recipients = [c1, c1_1, c1_2]
        msg.sharedContacts.recipients = [sc2]
        msg.tags.recipients = [tag1, tag1_1]
        int iBaseline = RecordItem.count()
        int rBaseline = RecordItemReceipt.count()

        when: "we send to contacts, shared contacts and tags"
        ResultGroup<RecordItem> resGroup = service.sendMessage(p1, msg, null, s1)
        RecordItem.withSession { it.flush() }

        HashSet<Contact> uniqueContacts = new HashSet<>(msg.contacts.recipients)
        msg.tags.recipients.each { if (it.members) { uniqueContacts.addAll(it.members) } }
        int totalTagMembers = msg.tags.recipients.collect { it.members.size() ?: 0 }.sum()

        then: "no duplication, also stored in tags"
        1 * service.tokenService.tryBuildAndPersistCallToken(*_)
        // mediaService method mock MUST be a function instead of just a value
        // so that we can generate temp receipts with unique apiIds. We can't add
        // receipts with duplicate apiIds so then our counts below will fail
        msg.toRecipients().size() * service.mediaService.sendWithMedia(*_) >> {
            new Result(payload: [TestHelpers.buildTempReceipt()])
        }
        !resGroup.isEmpty && resGroup.failures.isEmpty() == true // all successes
        // one result for each contact
        // one result for each shared contact
        // one result for each tag
        resGroup.successes.size() == uniqueContacts.size() +
            msg.sharedContacts.recipients.size() + msg.tags.recipients.size()
        resGroup.successes.each { it.payload instanceof RecordItem }
        // one msg for each contact
        // one msg for each shared contact
        // one msg for each tag
        RecordItem.count() == iBaseline + uniqueContacts.size() +
            msg.sharedContacts.recipients.size() + msg.tags.recipients.size()
        // one receipt for each contact
        // one receipt for each shared contact
        // one receipt for each member of each tag
        RecordItemReceipt.count() == rBaseline + uniqueContacts.size() +
            msg.sharedContacts.recipients.size() + totalTagMembers
        // make sure that `storeMessageInTag` is storing appropriate record item type
        // with appropriate contents
        if (type == RecordItemType.TEXT) {
            msg.tags.recipients.each { ContactTag ct1 ->
                RecordItem latestItem = ct1.record.getItems([max:1])[0]
                // if an outgoing text, latest item is a text with contents set to message
                assert latestItem.instanceOf(RecordText)
                assert latestItem.contents == msg.message
            }
        }
        else {
            msg.tags.recipients.each { ContactTag ct1 ->
                RecordItem latestItem = ct1.record.getItems([max:1])[0]
                // if an outgoing call, latest item is a call with noteContents set to message
                assert latestItem.instanceOf(RecordCall)
                assert latestItem.noteContents == msg.message
            }
        }

        where:
        type                | _
        RecordItemType.TEXT | _
        RecordItemType.CALL | _
    }
}
