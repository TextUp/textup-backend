package org.textup.rest

import grails.test.mixin.TestFor
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

// TODO

@TestFor(IncomingCallService)
class IncomingCallServiceSpec extends Specification {

    // // Helper methods
    // // --------------

    // void "test getting deliverable contacts"() {
    //     given: "blocked and non-blocked contacts for a single phone number within a TextUp phone"
    //     String numAsString = TestUtils.randPhoneNumberString()
    //     PhoneNumber pNum = new PhoneNumber(number: numAsString)
    //     assert pNum.validate()
    //     Contact c1 = p1.createContact([status: "blocked"], [numAsString]).payload
    //     Contact c2 = p1.createContact([:], [numAsString]).payload
    //     Contact c3 = p1.createContact([:], [numAsString]).payload
    //     [c1, c2, c3]*.save(flush: true, failOnError: true)

    //     when:
    //     Tuple<List<Contact>, List<Contact>> info = service.getDeliverableContacts(p1, pNum)

    //     then:
    //     info.first.size() == 3 // all matching contacts
    //     info.second.size() == 2 // not blocked contacts
    // }

    // void "test storing and updating status for a single contact"() {
    //     given:
    //     String numAsString = TestUtils.randPhoneNumberString()
    //     Contact c1 = p1.createContact([:], [numAsString]).payload
    //     SharedContact sc1 = p1.share(c1, p2, SharePermission.DELEGATE).payload
    //     [c1, sc1]*.save(flush: true, failOnError: true)

    //     and: "contact is NOT blocked"
    //     assert c1.status != ContactStatus.BLOCKED

    //     when: "contact with a shared contact that is NOT blocked"
    //     Result<Void> res = service.storeAndUpdateStatusForContact({ c2 -> }, c1)

    //     then:
    //     c1.status == ContactStatus.UNREAD
    //     sc1.status == ContactStatus.UNREAD

    //     when: "contact with a shared contact that is blocked"
    //     c1.status = ContactStatus.ACTIVE
    //     sc1.status = ContactStatus.BLOCKED
    //     [c1, sc1]*.save(flush: true, failOnError: true)
    //     res = service.storeAndUpdateStatusForContact({ c2 -> }, c1)

    //     then: "respect the collaborator's decision to block their shared contact"
    //     c1.status == ContactStatus.UNREAD
    //     sc1.status == ContactStatus.BLOCKED
    // }

    // @DirtiesRuntime
    // void "test storing for number without existing contacts"() {
    //     given:
    //     service.socketService = [
    //         sendContacts: { List<Contact> contacts -> new ResultGroup() }
    //     ] as SocketService
    //     int cBaseline = Contact.count()
    //     int cnBaseline = ContactNumber.count()
    //     String numAsString = TestUtils.randPhoneNumberString()
    //     PhoneNumber pNum = new PhoneNumber(number: numAsString)
    //     assert pNum.validate()

    //     when: "no contacts found for phone number"
    //     Result<List<Contact>> res = service.storeForNumber(p1, pNum, { c2 -> })

    //     then: "new contact is created"
    //     res.status == ResultStatus.OK
    //     res.payload.size() == 1 // newly created contact
    //     Contact.count() == cBaseline + 1
    //     ContactNumber.count() == cnBaseline + 1
    // }

    // @DirtiesRuntime
    // void "test storing for number with existing contacts"() {
    //     given:
    //     service.socketService = [
    //         sendContacts: { List<Contact> contacts -> new ResultGroup() }
    //     ] as SocketService
    //     String numAsString = TestUtils.randPhoneNumberString()
    //     PhoneNumber pNum = new PhoneNumber(number: numAsString)
    //     assert pNum.validate()
    //     Contact c1 = p1.createContact([status: "blocked"], [numAsString]).payload
    //     Contact c2 = p1.createContact([:], [numAsString]).payload
    //     SharedContact sc1 = p1.share(c1, p2, SharePermission.DELEGATE).payload
    //     SharedContact sc2 = p1.share(c2, p2, SharePermission.DELEGATE).payload
    //     sc1.status = ContactStatus.ARCHIVED
    //     sc2.status = ContactStatus.BLOCKED
    //     [c1, c2, sc1, sc2]*.save(flush: true, failOnError: true)
    //     int cBaseline = Contact.count()
    //     int cnBaseline = ContactNumber.count()

    //     when: "some contacts found for contact"
    //     Result<List<Contact>> res = service.storeForNumber(p1, pNum, { thisContact -> })

    //     then: "no new contact is created"
    //     res.status == ResultStatus.OK
    //     res.payload.size() == 1
    //     Contact.count() == cBaseline
    //     ContactNumber.count() == cnBaseline
    //     c1.status == ContactStatus.BLOCKED
    //     sc1.status == ContactStatus.ARCHIVED
    //     c2.status == ContactStatus.UNREAD
    //     sc2.status == ContactStatus.BLOCKED
    // }

    // // Calls
    // // -----

    // void "test storing incoming call"() {
    //     given:
    //     IncomingSession session = new IncomingSession(phone:p1, numberAsString: TestUtils.randPhoneNumberString())
    //     assert session.save(flush: true, failOnError: true)
    //     RecordCall rCall1
    //     Closure<Void> storeCall = { rCall1 = it }
    //     int cBaseline = RecordCall.count()
    //     int rptBaseline = RecordItemReceipt.count()

    //     when:
    //     service.storeIncomingCall("uid", session, storeCall, c1)
    //     RecordCall.withSession { it.flush() }

    //     then: "new call created + closure called"
    //     rCall1 instanceof RecordCall
    //     RecordCall.count() == cBaseline + 1
    //     RecordItemReceipt.count() == rptBaseline + 1
    // }

    // @DirtiesRuntime
    // void "test after storing incoming call"() {
    //     given:
    //     Boolean hasNotifications
    //     service.metaClass.handleNotificationsForIncomingCall = { Phone p1, IncomingSession sess1,
    //         List<BasicNotification> notifs ->

    //         hasNotifications = true
    //         new Result()
    //     }
    //     service.metaClass.handleAwayForIncomingCall = { Phone p1, IncomingSession sess1,
    //         List<RecordCall> rCalls ->

    //         hasNotifications = false
    //         new Result()
    //     }

    //     when: "all contacts are blocked"
    //     Result<Closure> res = service.afterStoreForCall(null, null, null, [])

    //     then:
    //     hasNotifications == null
    //     res.status == ResultStatus.OK
    //     TestUtils.buildXml(res.payload) == TestUtils.buildXml {
    //         Response { Reject(reason:"rejected") }
    //     }

    //     when: "no notifications built"
    //     service.notificationService = [
    //         build: { p1, notBlockedContacts -> [] }
    //     ] as NotificationService
    //     service.afterStoreForCall(null, null, null, [c1])

    //     then:
    //     hasNotifications == false

    //     when: "some notifications built"
    //     service.notificationService = [
    //         build: { p1, notBlockedContacts -> [new BasicNotification()] }
    //     ] as NotificationService
    //     service.afterStoreForCall(null, null, null, [c1])

    //     then:
    //     hasNotifications == true
    // }

    // void "test handling notifications for incoming call"() {
    //     given:
    //     IncomingSession sess1 = new IncomingSession(phone:p1, numberAsString: TestUtils.randPhoneNumberString())
    //     assert sess1.save(flush: true, failOnError: true)
    //     List<BasicNotification> notifs = []
    //     notifs << new BasicNotification(staff: new Staff(personalPhoneAsString: TestUtils.randPhoneNumberString()))
    //     notifs << new BasicNotification(staff: new Staff(personalPhoneAsString: TestUtils.randPhoneNumberString()))

    //     when:
    //     Result<Closure> res = service.handleNotificationsForIncomingCall(p1, sess1, notifs)
    //     Map numParams = [handle:CallResponse.SCREEN_INCOMING, originalFrom: sess1.number.e164PhoneNumber]

    //     String firstNum = notifs[0].staff.personalPhoneNumber.e164PhoneNumber,
    //         secondNum = notifs[1].staff.personalPhoneNumber.e164PhoneNumber

    //     then: "response includes unique set of all numbers to call"
    //     res.status == ResultStatus.OK
    //     // need to test presence of number elements separately because we pass in a set, which
    //     // does not guaranteee iteration order
    //     TestUtils.buildXml(res.payload).contains(TestUtils.buildXml {
    //         Number(statusCallback: CallTwiml.childCallStatus(firstNum),
    //             url: numParams.toString(), firstNum)
    //     })
    //     TestUtils.buildXml(res.payload).contains(TestUtils.buildXml {
    //         Number(statusCallback: CallTwiml.childCallStatus(secondNum),
    //             url:numParams.toString(), secondNum)
    //     })
    // }

    // @DirtiesRuntime
    // void "test handling away for incoming call"() {
    //     given:
    //     IncomingSession sess1 = new IncomingSession(phone:p1, numberAsString: TestUtils.randPhoneNumberString())
    //     assert sess1.save(flush: true, failOnError: true)
    //     List<RecordCall> rCalls = []
    //     10.times { rCalls << c1.record.storeOutgoingCall().payload }

    //     when:
    //     Result<Closure> res = service.handleAwayForIncomingCall(p1, sess1, rCalls)

    //     then: "all texts have away message flag set"
    //     rCalls.every { it.hasAwayMessage }
    //     res.status == ResultStatus.OK
    //     TestUtils.buildXml(res.payload).contains("twimlBuilder.call.voicemailDirections")
    // }

    // @DirtiesRuntime
    // void "test relaying incoming call overall"() {
    //     given:
    //     String numAsString = TestUtils.randPhoneNumberString()
    //     IncomingSession session = new IncomingSession(phone:p1, numberAsString: numAsString)
    //     assert session.save(flush: true, failOnError: true)
    //     Contact c1 = p1.createContact([:], [numAsString]).payload
    //     Contact c2 = p1.createContact([:], [numAsString]).payload
    //     [c1, c2]*.save(flush: true, failOnError: true)
    //     List<RecordCall> rCalls
    //     List<Contact> notBlockedContacts
    //     service.metaClass.afterStoreForCall = { Phone p1, IncomingSession sess1,
    //         List<RecordCall> callsList, List<Contact> contactsList ->
    //         rCalls = callsList
    //         notBlockedContacts = contactsList
    //         new Result()
    //     }
    //     service.socketService = GroovyMock(SocketService)

    //     when:
    //     service.relayCall(p1, "apiId", session)

    //     then: "all calls collected when storing are passed to the after handler"
    //     1 * service.socketService._
    //     notBlockedContacts == [c1, c2]
    //     rCalls.size() == 2
    //     rCalls.every { it.record == c1.record || it.record == c2.record }
    // }

    // @DirtiesRuntime
    // void "test receiving call"() {
    //     given:
    //     MockedMethod handleSelfCall = MockedMethod.create(service, "handleSelfCall") { new Result() }
    //     MockedMethod relayCall = MockedMethod.create(service, "relayCall") { new Result() }
    //     service.announcementService = GroovyMock(AnnouncementService)
    //     Phone p1 = GroovyMock(Phone)
    //     String pNum = TestUtils.randPhoneNumberString()
    //     IncomingSession sess1 = Stub(IncomingSession) { getNumberAsString() >> pNum }

    //     when: "self call"
    //     Result<Closure> res = service.receiveCall(p1, null, null, sess1)

    //     then:
    //     (1.._) * p1.owner >> Stub(PhoneOwnership) { buildAllStaff() >> [[personalPhoneAsString: pNum]] }
    //     0 * service.announcementService._
    //     handleSelfCall.callCount == 1
    //     relayCall.callCount == 0

    //     when: "announcement call with fallback to relaying call"
    //     res = service.receiveCall(p1, null, null, sess1)

    //     then:
    //     (1.._) * p1.owner >> Stub(PhoneOwnership) { buildAllStaff() >> [[personalPhoneAsString: "other"]] }
    //     (1.._) * p1.announcements >> [GroovyMock(FeaturedAnnouncement)]
    //     // check that the fallback closure passed in actually calls relayCall
    //     1 * service.announcementService.handleAnnouncementCall(*_) >> { args -> args[3].call(); null; }
    //     handleSelfCall.callCount == 1
    //     relayCall.callCount == 1

    //     when: "relaying call"
    //     res = service.receiveCall(p1, null, null, sess1)

    //     then:
    //     (1.._) * p1.owner >> Stub(PhoneOwnership) { buildAllStaff() >> [[personalPhoneAsString: "other"]] }
    //     (1.._) * p1.announcements >> []
    //     0 * service.announcementService._
    //     handleSelfCall.callCount == 1
    //     relayCall.callCount == 2
    // }

    // // Other call handlers
    // // -------------------

    // void "test screening incoming call"() {
    //     given:
    //     IncomingSession session = new IncomingSession(phone:p1, numberAsString:c1.numbers[0].number)
    //     session.save(flush:true, failOnError:true)

    //     when:
    //     Result<Closure> res = service.screenIncomingCall(p1, session)

    //     then:
    //     res.status == ResultStatus.OK
    //     TestUtils.buildXml(res.payload).contains("twimlBuilder.call.screenIncoming")
    // }

    // void "test storing outgoing call"() {
    //     given:
    //     int iBaseline = RecordCall.count()
    //     int rBaseline = RecordItemReceipt.count()
    //     TempRecordReceipt rpt = TestUtils.buildTempReceipt()

    //     when:
    //     assert service.storeOutgoingCall(s1, rpt, c1) == null
    //     RecordCall.withSession { it.flush() }

    //     then: "new call created + receipt added"
    //     RecordCall.count() == iBaseline + 1
    //     RecordItemReceipt.count() == rBaseline + 1
    // }

    // @DirtiesRuntime
    // void "handling self call"() {
    //     given: "session with no corresponding contact"
    //     int cBaseline = Contact.count()
    //     int iBaseline = RecordCall.count()
    //     int rBaseline = RecordItemReceipt.count()
    //     service.socketService = GroovyMock(SocketService)

    //     when: "missing digits"
    //     Result<Closure> res = service.handleSelfCall(p1, "apiId", null, s1)

    //     then: "self greeting"
    //     0 * service.socketService._
    //     Contact.count() == cBaseline
    //     RecordCall.count() == iBaseline
    //     RecordItemReceipt.count() == rBaseline
    //     res.status == ResultStatus.OK
    //     TestUtils.buildXml(res.payload).contains("twimlBuilder.call.selfGreeting")

    //     when: "digits are an invalid phone number"
    //     res = service.handleSelfCall(p1, "apiId", "invalidNumber", s1)

    //     then:
    //     0 * service.socketService._
    //     Contact.count() == cBaseline
    //     RecordCall.count() == iBaseline
    //     RecordItemReceipt.count() == rBaseline
    //     res.status == ResultStatus.OK
    //     TestUtils.buildXml(res.payload).contains("twimlBuilder.call.selfInvalidDigits")

    //     when: "valid phone number for missing apiId"
    //     res = service.handleSelfCall(p1, null, TestUtils.randPhoneNumberString(), s1)

    //     then:
    //     0 * service.socketService._
    //     Contact.count() == cBaseline
    //     RecordCall.count() == iBaseline
    //     RecordItemReceipt.count() == rBaseline
    //     res.status == ResultStatus.OK
    //     TestUtils.buildXml(res.payload).contains("twimlBuilder.error")

    //     when: "valid phone number and valid apiId"
    //     String numAsString = TestUtils.randPhoneNumberString()
    //     res = service.handleSelfCall(p1, "apiId", numAsString, s1)
    //     Phone.withSession { it.flush() }

    //     then:
    //     1 * service.socketService._
    //     Contact.count() == cBaseline + 1
    //     RecordCall.count() == iBaseline + 1
    //     RecordItemReceipt.count() == rBaseline + 1
    //     res.status == ResultStatus.OK
    //     TestUtils.buildXml(res.payload).contains("twimlBuilder.call.selfConnecting")
    // }
}
