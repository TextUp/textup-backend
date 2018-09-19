package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import grails.test.runtime.FreshRuntime
import org.apache.commons.lang3.tuple.Pair
import org.textup.rest.TwimlBuilder
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
@TestFor(IncomingMessageService)
class IncomingMessageServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
        twimlBuilder(TwimlBuilder)
    }

    def setup() {
        setupData()
        service.twimlBuilder = TestHelpers.getTwimlBuilder(grailsApplication)
        service.resultFactory = TestHelpers.getResultFactory(grailsApplication)
        service.messageSource = TestHelpers.mockMessageSource()
    }

    def cleanup() {
        cleanupData()
    }

    // Helper methods
    // --------------

    void "test getting deliverable contacts"() {
        given: "blocked and non-blocked contacts for a single phone number within a TextUp phone"
        String numAsString = TestHelpers.randPhoneNumber()
        PhoneNumber pNum = new PhoneNumber(number: numAsString)
        assert pNum.validate()
        Contact c1 = p1.createContact([status: "blocked"], [numAsString]).payload
        Contact c2 = p1.createContact([:], [numAsString]).payload
        Contact c3 = p1.createContact([:], [numAsString]).payload
        [c1, c2, c3]*.save(flush: true, failOnError: true)

        when:
        Pair<List<Contact>, List<Contact>> pair = service.getDeliverableContacts(p1, pNum)

        then:
        pair.left.size() == 3 // left is all matching contacts
        pair.right.size() == 2 // right is not blocked contacts
    }

    void "test storing and updating status for a single contact"() {
        given:
        String numAsString = TestHelpers.randPhoneNumber()
        Contact c1 = p1.createContact([:], [numAsString]).payload
        SharedContact sc1 = p1.share(c1, p2, SharePermission.DELEGATE).payload
        [c1, sc1]*.save(flush: true, failOnError: true)

        and: "contact is NOT blocked"
        assert c1.status != ContactStatus.BLOCKED

        when: "contact with a shared contact that is NOT blocked"
        Result<Void> res = service.storeAndUpdateStatusForContact({ c2 -> }, c1)

        then:
        c1.status == ContactStatus.UNREAD
        sc1.status == ContactStatus.UNREAD

        when: "contact with a shared contact that is blocked"
        c1.status = ContactStatus.ACTIVE
        sc1.status = ContactStatus.BLOCKED
        [c1, sc1]*.save(flush: true, failOnError: true)
        res = service.storeAndUpdateStatusForContact({ c2 -> }, c1)

        then: "respect the collaborator's decision to block their shared contact"
        c1.status == ContactStatus.UNREAD
        sc1.status == ContactStatus.BLOCKED
    }

    @DirtiesRuntime
    void "test storing for number without existing contacts"() {
        given:
        service.socketService = [
            sendContacts: { List<Contact> contacts -> new ResultGroup() }
        ] as SocketService
        int cBaseline = Contact.count()
        int cnBaseline = ContactNumber.count()
        String numAsString = TestHelpers.randPhoneNumber()
        PhoneNumber pNum = new PhoneNumber(number: numAsString)
        assert pNum.validate()

        when: "no contacts found for phone number"
        Result<List<Contact>> res = service.storeForNumber(p1, pNum, { c2 -> })

        then: "new contact is created"
        res.status == ResultStatus.OK
        res.payload.size() == 1 // newly created contact
        Contact.count() == cBaseline + 1
        ContactNumber.count() == cnBaseline + 1
    }

    @DirtiesRuntime
    void "test storing for number with existing contacts"() {
        given:
        service.socketService = [
            sendContacts: { List<Contact> contacts -> new ResultGroup() }
        ] as SocketService
        String numAsString = TestHelpers.randPhoneNumber()
        PhoneNumber pNum = new PhoneNumber(number: numAsString)
        assert pNum.validate()
        Contact c1 = p1.createContact([status: "blocked"], [numAsString]).payload
        Contact c2 = p1.createContact([:], [numAsString]).payload
        SharedContact sc1 = p1.share(c1, p2, SharePermission.DELEGATE).payload
        SharedContact sc2 = p1.share(c2, p2, SharePermission.DELEGATE).payload
        sc1.status = ContactStatus.ARCHIVED
        sc2.status = ContactStatus.BLOCKED
        [c1, c2, sc1, sc2]*.save(flush: true, failOnError: true)
        int cBaseline = Contact.count()
        int cnBaseline = ContactNumber.count()

        when: "some contacts found for contact"
        Result<List<Contact>> res = service.storeForNumber(p1, pNum, { thisContact -> })

        then: "no new contact is created"
        res.status == ResultStatus.OK
        res.payload.size() == 1
        Contact.count() == cBaseline
        ContactNumber.count() == cnBaseline
        c1.status == ContactStatus.BLOCKED
        sc1.status == ContactStatus.ARCHIVED
        c2.status == ContactStatus.UNREAD
        sc2.status == ContactStatus.BLOCKED
    }

    // Texts
    // -----

    void "test storing incoming text"() {
        given:
        IncomingText text = new IncomingText(apiId: "testing", message: "hello", numSegments: 88)
        assert text.validate()
        IncomingSession session = new IncomingSession(phone:p1, numberAsString: "1112223333")
        assert session.save(flush: true, failOnError: true)
        RecordText rText1
        Closure<Void> storeText = { rText1 = it }
        int tBaseline = RecordText.count()
        int rptBaseline = RecordItemReceipt.count()

        when:
        assert service.storeIncomingText(text, session, null, storeText, c1) == null
        RecordText.withSession { it.flush() }

        then: "new text created + closure called"
        rText1 instanceof RecordText
        RecordText.count() == tBaseline + 1
        RecordItemReceipt.count() == rptBaseline + 1
    }

    @DirtiesRuntime
    void "test after storing incoming text"() {
        given:
        Boolean hasNotifications
        service.metaClass.handleNotificationsForIncomingText = { Phone p1, IncomingText text,
            IncomingSession sess1, List<RecordText> rTexts, List<BasicNotification> notifs ->
            hasNotifications = true; new Result();
        }
        service.metaClass.handleAwayForIncomingText = { Phone p1, IncomingSession sess1,
            List<RecordText> rTexts ->
            hasNotifications = false; new Result();
        }
        service.notificationService = Mock(NotificationService)

        when: "all contacts are blocked"
        Result<Pair> res = service.afterStoreForText(null, null, null, [rText1], [])

        then:
        hasNotifications == null
        res.status == ResultStatus.OK
        res.payload instanceof Pair
        res.payload.left instanceof Closure
        res.payload.right == [rText1]
        TestHelpers.buildXml(res.payload.left) == TestHelpers.buildXml {
            Response { Message("twimlBuilder.text.blocked") }
        }

        when: "no notifications built"
        service.afterStoreForText(null, null, null, null, [c1])

        then:
        1 * service.notificationService.build(*_) >> []
        hasNotifications == false

        when: "some notifications built"
        service.afterStoreForText(null, null, null, null, [c1])

        then:
        1 * service.notificationService.build(*_) >> [new BasicNotification()]
        hasNotifications == true
    }

    void "test building response to incoming text"() {
        given:
        String randMsg1 = UUID.randomUUID().toString()
        String randMsg2 = UUID.randomUUID().toString()
        service.announcementService = Mock(AnnouncementService)
        service.socketService = Mock(SocketService)

        when:
        Result<Pair> res = service.buildIncomingTextResponse(null, null, [rText1], [randMsg2])

        then: "moved calling socket service over to callbackService to allow uploads to finish first"
        1 * service.announcementService.tryBuildTextInstructions(*_) >> new Result(payload:[randMsg1])
        0 * service.socketService.sendItems(*_)
        res.status == ResultStatus.OK
        res.payload instanceof Pair
        res.payload.left instanceof Closure
        res.payload.right == [rText1]
        TestHelpers.buildXml(res.payload.left) == TestHelpers.buildXml {
            Response {
                Message(randMsg2)
                Message(randMsg1)
            }
        }
    }

    @FreshRuntime
    void "test handling notifications for incoming text"() {
        given:
        int numTimesNotifiedStaff = 0
        service.metaClass.buildIncomingTextResponse = { Phone p1, IncomingSession sess1,
            List<RecordText> rTexts, List<String> responses = [] ->
            new Result()
        }
        service.tokenService = Mock(TokenService)
        IncomingText text = new IncomingText(message: "hi")
        List<BasicNotification> notifs = []
        10.times { notifs << new BasicNotification() }

        when:
        rText1.numNotified = 0
        service.handleNotificationsForIncomingText(null, text, null, [rText1], notifs)

        then: "tokenService called for all notifications"
        notifs.size() * service.tokenService.notifyStaff(*_) >> new Result()
        rText1.numNotified == notifs.size()
    }

    @DirtiesRuntime
    void "test handling away for incoming text"() {
        given:
        List<String> additionalMsgs
        service.metaClass.buildIncomingTextResponse = { Phone p1, IncomingSession sess1,
            List<RecordText> rTexts, List<String> responses = [] ->

            additionalMsgs = responses; new Result();
        }
        List<RecordText> rTexts = [rText1, rText2, rTeText1, rTeText2, otherRText2, otherRTeText2]

        when:
        service.handleAwayForIncomingText(p1, null, rTexts)

        then: "all texts have away message flag set"
        [p1.awayMessage] == additionalMsgs
        rTexts.every { it.hasAwayMessage }
    }

    @DirtiesRuntime
    void "test relaying incoming text overall"() {
        given:
        String numAsString = TestHelpers.randPhoneNumber()
        IncomingText text = new IncomingText(apiId: "testing", message: "hello", numSegments: 88)
        assert text.validate()
        IncomingSession sess1 = new IncomingSession(phone:p1, numberAsString: numAsString)
        assert sess1.save(flush: true, failOnError: true)
        Contact c1 = p1.createContact([:], [numAsString]).payload
        Contact c2 = p1.createContact([:], [numAsString]).payload
        [c1, c2]*.save(flush: true, failOnError: true)
        List<RecordText> rTexts
        List<Contact> notBlockedContacts
        service.metaClass.afterStoreForText = { Phone p2, IncomingText text2, IncomingSession sess2,
            List<RecordText> tList, List<Contact> cList ->
            rTexts = tList
            notBlockedContacts = cList
            new Result()
        }
        service.socketService = Mock(SocketService)

        when:
        service.relayText(p1, text, sess1, null)

        then: "all texts collected when storing are passed to the after handler"
        1 * service.socketService._
        notBlockedContacts == [c1, c2]
        rTexts.size() == 2
        rTexts.every { it.record == c1.record || it.record == c2.record }
    }

    // Calls
    // -----

    void "test storing incoming call"() {
        given:
        IncomingSession session = new IncomingSession(phone:p1, numberAsString: TestHelpers.randPhoneNumber())
        assert session.save(flush: true, failOnError: true)
        RecordCall rCall1
        Closure<Void> storeCall = { rCall1 = it }
        int cBaseline = RecordCall.count()
        int rptBaseline = RecordItemReceipt.count()

        when:
        service.storeIncomingCall("uid", session, storeCall, c1)
        RecordCall.withSession { it.flush() }

        then: "new call created + closure called"
        rCall1 instanceof RecordCall
        RecordCall.count() == cBaseline + 1
        RecordItemReceipt.count() == rptBaseline + 1
    }

    @DirtiesRuntime
    void "test after storing incoming call"() {
        given:
        Boolean hasNotifications
        service.metaClass.handleNotificationsForIncomingCall = { Phone p1, IncomingSession sess1,
            List<BasicNotification> notifs ->

            hasNotifications = true
            new Result()
        }
        service.metaClass.handleAwayForIncomingCall = { Phone p1, IncomingSession sess1,
            List<RecordCall> rCalls ->

            hasNotifications = false
            new Result()
        }

        when: "all contacts are blocked"
        Result<Closure> res = service.afterStoreForCall(null, null, null, [])

        then:
        hasNotifications == null
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml {
            Response { Reject(reason:"rejected") }
        }

        when: "no notifications built"
        service.notificationService = [
            build: { p1, notBlockedContacts -> [] }
        ] as NotificationService
        service.afterStoreForCall(null, null, null, [c1])

        then:
        hasNotifications == false

        when: "some notifications built"
        service.notificationService = [
            build: { p1, notBlockedContacts -> [new BasicNotification()] }
        ] as NotificationService
        service.afterStoreForCall(null, null, null, [c1])

        then:
        hasNotifications == true
    }

    void "test handling notifications for incoming call"() {
        given:
        IncomingSession sess1 = new IncomingSession(phone:p1, numberAsString: TestHelpers.randPhoneNumber())
        assert sess1.save(flush: true, failOnError: true)
        List<BasicNotification> notifs = []
        notifs << new BasicNotification(staff: new Staff(personalPhoneAsString: TestHelpers.randPhoneNumber()))
        notifs << new BasicNotification(staff: new Staff(personalPhoneAsString: TestHelpers.randPhoneNumber()))

        when:
        Result<Closure> res = service.handleNotificationsForIncomingCall(p1, sess1, notifs)
        Map numParams = [handle:CallResponse.SCREEN_INCOMING, originalFrom: sess1.number.e164PhoneNumber]

        then: "response includes unique set of all numbers to call"
        res.status == ResultStatus.OK
        // need to test presence of number elements separately because we pass in a set, which
        // does not guaranteee iteration order
        TestHelpers.buildXml(res.payload).contains(TestHelpers.buildXml {
            Number(url:numParams.toString(), notifs[0].staff.personalPhoneNumber.e164PhoneNumber)
        })
        TestHelpers.buildXml(res.payload).contains(TestHelpers.buildXml {
            Number(url:numParams.toString(), notifs[1].staff.personalPhoneNumber.e164PhoneNumber)
        })
    }

    @DirtiesRuntime
    void "test handling away for incoming call"() {
        given:
        IncomingSession sess1 = new IncomingSession(phone:p1, numberAsString: TestHelpers.randPhoneNumber())
        assert sess1.save(flush: true, failOnError: true)
        List<RecordCall> rCalls = []
        10.times { rCalls << c1.record.storeOutgoingCall().payload }
        BasePhoneNumber fromNum
        BasePhoneNumber toNum
        ReceiptStatus status
        p1.metaClass.tryStartVoicemail = { BasePhoneNumber f, BasePhoneNumber t, ReceiptStatus s ->
            fromNum = f; toNum = t; status = s; new Result();
        }

        when:
        service.handleAwayForIncomingCall(p1, sess1, rCalls)

        then: "all texts have away message flag set"
        rCalls.every { it.hasAwayMessage }
        fromNum.number == sess1.numberAsString
        toNum.number == p1.numberAsString
        status == ReceiptStatus.PENDING
    }

    @DirtiesRuntime
    void "test relaying incoming call overall"() {
        given:
        String numAsString = TestHelpers.randPhoneNumber()
        IncomingSession session = new IncomingSession(phone:p1, numberAsString: numAsString)
        assert session.save(flush: true, failOnError: true)
        Contact c1 = p1.createContact([:], [numAsString]).payload
        Contact c2 = p1.createContact([:], [numAsString]).payload
        [c1, c2]*.save(flush: true, failOnError: true)
        List<RecordCall> rCalls
        List<Contact> notBlockedContacts
        service.metaClass.afterStoreForCall = { Phone p1, IncomingSession sess1,
            List<RecordCall> callsList, List<Contact> contactsList ->
            rCalls = callsList
            notBlockedContacts = contactsList
            new Result()
        }
        service.socketService = Mock(SocketService)

        when:
        service.relayCall(p1, "apiId", session)

        then: "all calls collected when storing are passed to the after handler"
        1 * service.socketService._
        notBlockedContacts == [c1, c2]
        rCalls.size() == 2
        rCalls.every { it.record == c1.record || it.record == c2.record }
    }

    // Other call handlers
    // -------------------

    void "test screening incoming call"() {
        given:
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:c1.numbers[0].number)
        session.save(flush:true, failOnError:true)

        when:
        Result<Closure> res = service.screenIncomingCall(p1, session)

        then:
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml {
            Response {
                Gather(numDigits:"1", action:[handle:CallResponse.DO_NOTHING].toString()) {
                    Pause(length:"1")
                    Say("twimlBuilder.call.screenIncoming")
                    Say("twimlBuilder.call.screenIncoming")
                }
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        }
    }

    void "test storing outgoing call"() {
        given:
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        TempRecordReceipt rpt = TestHelpers.buildTempReceipt()

        when:
        assert service.storeOutgoingCall(s1, rpt, c1) == null
        RecordCall.withSession { it.flush() }

        then: "new call created + receipt added"
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
    }

    @DirtiesRuntime
    void "handling self call"() {
        given: "session with no corresponding contact"
        int cBaseline = Contact.count()
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        service.socketService = Mock(SocketService)

        when: "missing digits"
        Result<Closure> res = service.handleSelfCall(p1, "apiId", null, s1)

        then: "self greeting"
        0 * service.socketService._
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload).contains(TestHelpers.buildXml {
            Gather(numDigits:10) { Say("twimlBuilder.call.selfGreeting") }
        })

        when: "digits are an invalid phone number"
        res = service.handleSelfCall(p1, "apiId", "invalidNumber", s1)

        then:
        0 * service.socketService._
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload).contains(TestHelpers.buildXml {
            Say("twimlBuilder.call.selfInvalidDigits")
        })

        when: "valid phone number for missing apiId"
        res = service.handleSelfCall(p1, null, TestHelpers.randPhoneNumber(), s1)

        then:
        0 * service.socketService._
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload).contains(TestHelpers.buildXml {
            Say("twimlBuilder.error")
        })

        when: "valid phone number and valid apiId"
        String numAsString = TestHelpers.randPhoneNumber()
        res = service.handleSelfCall(p1, "apiId", numAsString, s1)

        then:
        1 * service.socketService._
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        res.status == ResultStatus.OK
        TestHelpers.buildXml(res.payload) == TestHelpers.buildXml {
            Response {
                Say("twimlBuilder.call.selfConnecting")
                Dial(callerId:p1.number.e164PhoneNumber) { Number(numAsString) }
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        }
    }
}
