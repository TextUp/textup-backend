package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import org.apache.commons.lang3.tuple.Pair
import org.joda.time.DateTime
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Shared

@TestFor(AnnouncementService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    IncomingSession, FeaturedAnnouncement, AnnouncementReceipt, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
@TestMixin(HibernateTestMixin)
class AnnouncementServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        super.setupData()
        service.resultFactory = TestHelpers.getResultFactory(grailsApplication)
        service.authService = [getLoggedInAndActive:{ s1 }] as AuthService
    	Phone.metaClass.sendAnnouncement = { String message,
        	DateTime expiresAt, Staff staff ->
        	new Result(status:ResultStatus.OK, payload:null)
    	}
    }

    def cleanup() {
        super.cleanupData()
    }

    // CRUD
    // ----

    void "test create"() {
    	when: "no phone"
    	Result<FeaturedAnnouncement> res = service.create(null, [:])

    	then:
    	res.success == false
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages[0] == "announcementService.create.noPhone"

    	when: "success, having mocked method on phone"
    	res = service.create(p1, [message: "hi!", expiresAt:DateTime.now().toDate()])

    	then:
    	res.success == true
        res.status == ResultStatus.CREATED
    }

    void "test update"() {
    	given: "baselines and existing announcement"
    	FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
    		message:"hello there bud!", expiresAt:DateTime.now().plusDays(2))
    	announce.save(flush:true, failOnError:true)
    	int aBaseline = FeaturedAnnouncement.count()

    	when: "nonexistent id"
    	Result<FeaturedAnnouncement> res = service.update(-88L, [:])

    	then:
    	res.success == false
    	res.status == ResultStatus.NOT_FOUND
    	res.errorMessages[0] == "announcementService.update.notFound"

    	when: "invalid expires at"
    	res = service.update(announce.id, [expiresAt:"invalid"])

    	then:
    	res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages[0].contains("nullable")

    	when: "valid"
    	DateTime newExpires = DateTime.now().plusMinutes(30)
    	res = service.update(announce.id, [expiresAt:newExpires.toDate()])

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload instanceof FeaturedAnnouncement
    	res.payload.expiresAt == newExpires
    }

    // Outgoing
    // --------

    void "test starting text announcement"() {
        given: "baselines"
        int tBaseline = RecordText.count()
        int rBaseline = RecordItemReceipt.count()
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()
        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString:"1238470293")
        assert IncomingSession.findByPhoneAndNumberAsString(p1,
            session.numberAsString) == null
        session.save(flush:true, failOnError:true)
        // mock twimlbuilder to return a list of strings, as expected
        service.twimlBuilder = [translate:{ code, params=[:] ->
            new Result(status:ResultStatus.OK, payload:[params.message])
        }] as TwimlBuilder
        service.textService = [send:{ BasePhoneNumber fromNum, List<? extends BasePhoneNumber> toNums,
            String message ->

            new Result(status: ResultStatus.OK,
                payload: new TempRecordReceipt(apiId:"apiId", contactNumberAsString:toNums[0].number))
        }] as TextService
        service.socketService = [
            sendContacts: { List<Contact> contacts -> new ResultGroup()}
        ] as SocketService

        when: "for session with no contacts"
        String message = "hello"
        String ident = "kiki bai"
        Map<String, Result<TempRecordReceipt>> resMap = service.sendTextAnnouncement(p1, message, ident,
            [session], s1)

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
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()
        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString:"1238470294")
        assert IncomingSession.findByPhoneAndNumberAsString(p1,
            session.numberAsString) == null
        session.save(flush:true, failOnError:true)
        service.callService = [start:{ PhoneNumber fromNum, PhoneNumber toNum, Map afterPickup ->
            new Result(status: ResultStatus.OK,
                payload: new TempRecordReceipt(apiId:"apiId", contactNumberAsString:toNum.number))
        }] as CallService
        service.socketService = [
            sendContacts: { List<Contact> contacts -> new ResultGroup()}
        ] as SocketService

        when:
        String message = "hello"
        String ident = "kiki bai"
        Map<String, Result<TempRecordReceipt>> resMap = service.startCallAnnouncement(p1, message, ident,
            [session], s1)

        then:
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        Contact.count() == cBaseline + 1
        ContactNumber.count() == nBaseline + 1
        resMap[session.numberAsString].success == true
        resMap[session.numberAsString].payload instanceof TempRecordReceipt
        resMap[session.numberAsString].payload.contactNumberAsString == session.numberAsString
    }

    // Incoming
    // --------

    @FreshRuntime
    void "test handling incoming for text announcements"() {
        given:
        boolean didCallFallback = false
        Closure<Result<Closure>> fallbackAction = {
            didCallFallback = true
            new Result()
        }
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"5557778888")
        session.save(flush:true, failOnError:true)
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:"Hello!", expiresAt:DateTime.now().plusDays(2))
        announce.save(flush:true, failOnError:true)
        service.twimlBuilder = [
            build: { code, params = [:] -> new Result(status:ResultStatus.OK, payload:{ -> code }) }
        ] as TwimlBuilder

        when: "message is not a valid keyword"
        IncomingText text = new IncomingText(apiId:"apiId")
        Result<Closure> res = service.handleAnnouncementText(p1, text, session, fallbackAction)

        then: "relay text"
        true == didCallFallback
        res.success == true

        when: "have announcements and see announcements"
        didCallFallback = false
        text.message = Constants.TEXT_SEE_ANNOUNCEMENTS
        assert text.validate()
        res = service.handleAnnouncementText(p1, text, session, fallbackAction)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Pair
        res.payload.left instanceof Closure
        res.payload.left.call() == TextResponse.SEE_ANNOUNCEMENTS
        res.payload.right == []
        false == didCallFallback

        when: "duplicate receipts are not added for same announcement and session"
        int arBaseline = AnnouncementReceipt.count()
        res = service.handleAnnouncementText(p1, text, session, fallbackAction)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Pair
        res.payload.left instanceof Closure
        res.payload.left.call() == TextResponse.SEE_ANNOUNCEMENTS
        res.payload.right == []
        AnnouncementReceipt.count() == arBaseline
        false == didCallFallback

        when: "have announcements, is NOT subscribed, toggle subscription"
        session.isSubscribedToText = false
        session.save(flush: true, failOnError: true)
        text.message = Constants.TEXT_TOGGLE_SUBSCRIBE
        assert text.validate()
        res = service.handleAnnouncementText(p1, text, session, fallbackAction)

        then:
        session.isSubscribedToText == true
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Pair
        res.payload.left instanceof Closure
        res.payload.left.call() == TextResponse.SUBSCRIBED
        res.payload.right == []
        false == didCallFallback

        when: "have announcementsm, is subscribed, toggle subscription"
        session.isSubscribedToText = true
        session.save(flush: true, failOnError: true)
        text.message = Constants.TEXT_TOGGLE_SUBSCRIBE
        assert text.validate()
        res = service.handleAnnouncementText(p1, text, session, fallbackAction)

        then:
        session.isSubscribedToText == false
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Pair
        res.payload.left instanceof Closure
        res.payload.left.call() == TextResponse.UNSUBSCRIBED
        res.payload.right == []
        false == didCallFallback
    }

    void "test try building text instructions"() {
        given:
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"1112223333")
        session.save(flush:true, failOnError:true)
        service.twimlBuilder = [
            translate: { code, params = [:] -> new Result(status:ResultStatus.OK, payload:[code]) }
        ] as TwimlBuilder

        when: "no announcements"
        assert p1.countAnnouncements() == 0
        Result<List<String>> res = service.tryBuildTextInstructions(p1, session)

        then:
        res.status == ResultStatus.OK
        res.payload == []

        when: "has announcements, but should NOT send instructions"
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:"Hello!", expiresAt:DateTime.now().plusDays(2))
        announce.save(flush:true, failOnError:true)
        assert p1.countAnnouncements() > 0
        session.lastSentInstructions = DateTime.now().plusDays(2)
        assert session.shouldSendInstructions == false
        res = service.tryBuildTextInstructions(p1, session)

        then:
        res.status == ResultStatus.OK
        res.payload == []

        when: "has announcement and should send instructions"
        session.isSubscribedToText = true
        session.lastSentInstructions = DateTime.now().minusDays(2)
        assert session.shouldSendInstructions == true
        res = service.tryBuildTextInstructions(p1, session)

        then:
        res.status == ResultStatus.OK
        res.payload == [TextResponse.INSTRUCTIONS_SUBSCRIBED]
    }

    @FreshRuntime
    void "test handling incoming for call announcements"() {
        given: "no announcements and session"
        boolean didCallFallback = false
        Closure<Result<Closure>> fallbackAction = {
            didCallFallback = true
            new Result()
        }
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"1112223333")
        session.save(flush:true, failOnError:true)
        int aBaseline = AnnouncementReceipt.count()
        service.twimlBuilder = [
            build: { code, params = [:] -> new Result(status:ResultStatus.OK, payload:code) }
        ] as TwimlBuilder

        when: "no digits or announcements"
        assert p1.countAnnouncements() == 0
        Result res = service.handleAnnouncementCall(p1, null, session, fallbackAction)

        then: "fallback"
        true == didCallFallback
        res.success == true

        when: "no digits, has announcements"
        didCallFallback = false
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:"Hello!", expiresAt:DateTime.now().plusDays(2))
        announce.save(flush:true, failOnError:true)
        assert p1.countAnnouncements() > 0
        res = service.handleAnnouncementCall(p1, null, session, fallbackAction)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.ANNOUNCEMENT_GREETING
        AnnouncementReceipt.count() == aBaseline
        false == didCallFallback

        when: "digits, hear announcements"
        res = service.handleAnnouncementCall(p1,
            Constants.CALL_HEAR_ANNOUNCEMENTS, session, fallbackAction)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.HEAR_ANNOUNCEMENTS
        AnnouncementReceipt.count() == aBaseline + 1
        false == didCallFallback

        when: "digits, is NOT subscriber, toggle subscribe"
        session.isSubscribedToCall = false
        session.save(flush:true, failOnError:true)
        res = service.handleAnnouncementCall(p1, Constants.CALL_TOGGLE_SUBSCRIBE, session, fallbackAction)

        then:
        session.isSubscribedToCall == true
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.SUBSCRIBED
        false == didCallFallback

        when: "digits, is subscriber, toggle subscribe"
        session.isSubscribedToCall = true
        session.save(flush:true, failOnError:true)
        res = service.handleAnnouncementCall(p1, Constants.CALL_TOGGLE_SUBSCRIBE, session, fallbackAction)

        then:
        session.isSubscribedToCall == false
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.UNSUBSCRIBED
        false == didCallFallback

        when: "digits, no matching valid"
        res = service.handleAnnouncementCall(p1, "blah", session, fallbackAction)

        then: "fallback"
        res.success == true
        true == didCallFallback
    }
}
