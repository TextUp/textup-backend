package org.textup

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.*
import grails.validation.ValidationErrors
import org.joda.time.DateTime
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Shared

@TestFor(OutgoingAnnouncementService)
@Domain([CustomAccountDetails, Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    IncomingSession, FeaturedAnnouncement, AnnouncementReceipt, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class OutgoingAnnouncementServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    void "test starting text announcement"() {
        given: "baselines"
        int tBaseline = RecordText.count()
        int rBaseline = RecordItemReceipt.count()
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()
        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString: TestUtils.randPhoneNumberString())
        assert IncomingSession.findByPhoneAndNumberAsString(p1,
            session.numberAsString) == null
        session.save(flush:true, failOnError:true)

        service.textService = GroovyStub(TextService) {
            send(*_) >> { args ->
                new Result(payload: new TempRecordReceipt(apiId: TestUtils.randString(),
                    contactNumberAsString: args[1][0].number))
            }
        }
        service.socketService = GroovyStub(SocketService) { sendContacts(*_) >> new ResultGroup() }

        when: "for session with no contacts"
        String message = "hello"
        String ident = "kiki bai"
        Map<String, Result<TempRecordReceipt>> resMap = service.sendTextAnnouncement(p1, message, ident,
            [session], s1)
        Record.withSession { it.flush() }

        then:
        RecordText.count() == tBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        Contact.count() == cBaseline + 1
        ContactNumber.count() == nBaseline + 1
        resMap[session.numberAsString].success == true
        resMap[session.numberAsString].payload instanceof TempRecordReceipt
        resMap[session.numberAsString].payload.contactNumberAsString == session.numberAsString

        when: "for session with multiple contacts"
        resMap = service.sendTextAnnouncement(p1, message, ident, [session], s1)
        Record.withSession { it.flush() }

        then:
        RecordText.count() == tBaseline + 2
        RecordItemReceipt.count() == rBaseline + 2
        Contact.count() == cBaseline + 1
        ContactNumber.count() == nBaseline + 1
        resMap[session.numberAsString].success == true
        resMap[session.numberAsString].payload instanceof TempRecordReceipt
        resMap[session.numberAsString].payload.contactNumberAsString == session.numberAsString
    }

    void "test starting call announcement"() {
        given: "baselines"
        String message = TestUtils.randString()
        String ident = TestUtils.randString()

        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()
        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString: TestUtils.randPhoneNumberString())
        assert IncomingSession.findByPhoneAndNumberAsString(p1,
            session.numberAsString) == null
        session.save(flush:true, failOnError:true)

        service.callService = GroovyStub(CallService) {
            start(*_) >> { args ->
                new Result(payload: new TempRecordReceipt(apiId: TestUtils.randString(),
                    contactNumberAsString: args[1][0].number))
            }
        }
        service.socketService = GroovyStub(SocketService) { sendContacts(*_) >> new ResultGroup() }

        when:
        Map<String, Result<TempRecordReceipt>> resMap = service.startCallAnnouncement(p1, message, ident,
            [session], s1)
        RecordItem.withSession { it.flush() }

        then:
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        Contact.count() == cBaseline + 1
        ContactNumber.count() == nBaseline + 1
        resMap[session.numberAsString].success == true
        resMap[session.numberAsString].payload instanceof TempRecordReceipt
        resMap[session.numberAsString].payload.contactNumberAsString == session.numberAsString
    }

    // Sending overall
    // ---------------

    void "test announcement error conditions"() {
        when: "expires in the past"
        Result<FeaturedAnnouncement> res = service.send(null, null, null, null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
    }

    @DirtiesRuntime
    void "test announcement none reached"() {
        given: "phone and incoming sessions, some coinciding with contacts"
        MockedMethod sendTextAnnouncement = TestUtils.mock(service, "sendTextAnnouncement") { [:] }
        MockedMethod startCallAnnouncement = TestUtils.mock(service, "startCallAnnouncement") { [:] }

        when: "none reached with no subscribers"
        Result<FeaturedAnnouncement> res = service.send(p1, "hello", DateTime.now().plusDays(1), s1)

        then:
        sendTextAnnouncement.callCount == 1
        startCallAnnouncement.callCount == 1
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload instanceof FeaturedAnnouncement

        when: "none reached with some subscribers"
        // add a subscriber
        String subNum = TestUtils.randPhoneNumberString()
        IncomingSession sess = new IncomingSession(phone:p1, numberAsString:subNum,
            isSubscribedToText:true, isSubscribedToCall:true)
        sess.save(flush:true, failOnError:true)
        // another announcement
        res = service.send(p1, "hello", DateTime.now().plusDays(1), s1)

        then:
        sendTextAnnouncement.callCount == 2
        startCallAnnouncement.callCount == 2
        res.success == false
        res.payload == null
        res.status == ResultStatus.INTERNAL_SERVER_ERROR
    }

    @DirtiesRuntime
    void "test announcement success"() {
        given: "phone and incoming sessions, some coinciding with contacts"
        // subscriber
        String subNum = TestUtils.randPhoneNumberString()
        IncomingSession sess = new IncomingSession(phone:p1, numberAsString:subNum,
            isSubscribedToText:true, isSubscribedToCall:true)
        sess.save(flush:true, failOnError:true)
        // mock helper methods
        MockedMethod sendTextAnnouncement = TestUtils.mock(service, "sendTextAnnouncement")
            { [(subNum): new Result(status:ResultStatus.OK)] }
        MockedMethod startCallAnnouncement = TestUtils.mock(service, "startCallAnnouncement")
            { [(subNum): new Result(status:ResultStatus.OK)] }
        // baselines
        int featBaseline = FeaturedAnnouncement.count(),
            aReceiptBaseline = AnnouncementReceipt.count()

        when: "valid and some subscribers successfully reached"
        Result<FeaturedAnnouncement> res = service.send(p1, "hello",
            DateTime.now().plusDays(1), s1)
        assert res.success
        FeaturedAnnouncement.withSession { it.flush() }

        then:
        sendTextAnnouncement.callCount == 1
        startCallAnnouncement.callCount == 1
        FeaturedAnnouncement.count() == featBaseline + 1
        AnnouncementReceipt.count() == aReceiptBaseline + 2
        res.status == ResultStatus.CREATED
        res.payload.instanceOf(FeaturedAnnouncement)
    }
}
