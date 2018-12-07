package org.textup

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import org.joda.time.DateTime
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
        setupData()
        service.resultFactory = TestUtils.getResultFactory(grailsApplication)
    }

    def cleanup() {
        cleanupData()
    }

    // CRUD
    // ----

    void "test create"() {
        given:
        service.outgoingAnnouncementService = GroovyMock(OutgoingAnnouncementService)
        service.authService = GroovyMock(AuthService)

    	when: "no phone"
        Map params = [:]
    	Result<FeaturedAnnouncement> res = service.create(null, params)

    	then:
        0 * service.outgoingAnnouncementService._
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages[0] == "announcementService.create.noPhone"

        when: "phone is inactive"
        p1.numberAsString = null
        res = service.create(p1, params)

        then:
        0 * service.outgoingAnnouncementService._
        res.status == ResultStatus.NOT_FOUND
        res.errorMessages[0] == "phone.isInactive"

        when: "expiration time is missing or in the past"
        p1.numberAsString = TestUtils.randPhoneNumber()
        params.expiresAt = DateTime.now().minusDays(1).toString()
        res = service.create(p1, params)

        then:
        0 * service.outgoingAnnouncementService._
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "announcementService.create.expiresInPast"

        when: "success, having mocked method on phone"
        params.expiresAt = DateTime.now().plusDays(1).toString()
        res = service.create(p1, params)

        then:
        1 * service.outgoingAnnouncementService.send(*_) >> new Result()
        1 * service.authService.loggedInAndActive >> p1.owner.buildAllStaff()[0]
        res.status == ResultStatus.OK

        when: "currently logged-in user is not an owner for the passed-in phone"
        res = service.create(p1, params)

        then:
        0 * service.outgoingAnnouncementService._
        1 * service.authService.loggedInAndActive >> null
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages[0] == "phone.notOwner"
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
        FeaturedAnnouncement.count() == aBaseline

    	when: "invalid expires at"
    	res = service.update(announce.id, [expiresAt:"invalid"])

    	then:
    	res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages[0].contains("nullable")
        FeaturedAnnouncement.count() == aBaseline

    	when: "valid"
    	DateTime newExpires = DateTime.now().plusMinutes(30)
    	res = service.update(announce.id, [expiresAt: newExpires.toString()])

    	then:
    	res.success == true
        res.status == ResultStatus.OK
    	res.payload instanceof FeaturedAnnouncement
    	res.payload.expiresAt == newExpires
        FeaturedAnnouncement.count() == aBaseline
    }

    // Incoming
    // --------

    void "test see announcement via text"() {
        given:
        Phone owner = GroovyMock(Phone)
        FeaturedAnnouncement fa1 = GroovyMock(FeaturedAnnouncement) {
            getWhenCreated() >> DateTime.now()
            getOwner() >> owner
        }
        IncomingSession sess1 = GroovyMock(IncomingSession)

        when:
        Result<Closure> res = service.textSeeAnnouncements([fa1], sess1)

        then:
        1 * fa1.addToReceipts(RecordItemType.TEXT, sess1) >> new ResultGroup()
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twimlBuilder.announcement")
    }

    void "test change announcement subscription for texts"() {
        given:
        IncomingSession sess1 = GroovyMock(IncomingSession)

        when: "is unsubscribed"
        Result<Closure> res = service.textToggleSubscribe(sess1)

        then: "subscribe"
        1 * sess1.isSubscribedToText >> false
        1 * sess1.setIsSubscribedToText(true)
        TestUtils.buildXml(res.payload).contains("twimlBuilder.text.subscribed")

        when: "is subscribed"
        res = service.textToggleSubscribe(sess1)

        then: "unsubscribe"
        1 * sess1.isSubscribedToText >> true
        1 * sess1.setIsSubscribedToText(false)
        TestUtils.buildXml(res.payload).contains("twimlBuilder.text.unsubscribed")
    }

    void "test try building text instructions"() {
        given:
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"1112223333")
        session.save(flush:true, failOnError:true)

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
        res.payload == ["twimlBuilder.text.instructionsSubscribed"]

        when: "has announcement and should send instructions"
        session.isSubscribedToText = false
        session.lastSentInstructions = DateTime.now().minusDays(2)
        assert session.shouldSendInstructions == true
        res = service.tryBuildTextInstructions(p1, session)

        then:
        res.status == ResultStatus.OK
        res.payload == ["twimlBuilder.text.instructionsUnsubscribed"]
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
        TestUtils.buildXml(res.payload).contains("twimlBuilder.call.announcementGreetingWelcome")
        AnnouncementReceipt.count() == aBaseline
        false == didCallFallback

        when: "digits, hear announcements"
        res = service.handleAnnouncementCall(p1,
            Constants.CALL_HEAR_ANNOUNCEMENTS, session, fallbackAction)

        then:
        res.success == true
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twimlBuilder.announcement")
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
        TestUtils.buildXml(res.payload).contains("twimlBuilder.call.subscribed")
        false == didCallFallback

        when: "digits, is subscriber, toggle subscribe"
        session.isSubscribedToCall = true
        session.save(flush:true, failOnError:true)
        res = service.handleAnnouncementCall(p1, Constants.CALL_TOGGLE_SUBSCRIBE, session, fallbackAction)

        then:
        session.isSubscribedToCall == false
        res.success == true
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twimlBuilder.call.unsubscribed")
        false == didCallFallback

        when: "digits, no matching valid"
        res = service.handleAnnouncementCall(p1, "blah", session, fallbackAction)

        then: "fallback"
        res.success == true
        true == didCallFallback
    }

    void "test complete call announcement"() {
        given:
        IncomingSession session = GroovyMock(IncomingSession)

        when: "unsubscribe"
        String digits = Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE
        Result<Closure> res = service.completeCallAnnouncement(digits, null, null, session)

        then:
        1 * session.setIsSubscribedToCall(false)
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twimlBuilder.call.unsubscribed")

        when: "digits do not match unsubscribe"
        digits = TestUtils.randString()
        res = service.completeCallAnnouncement(digits, "hi", "hi", session)

        then:
        0 * session._
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload).contains("twimlBuilder.call.announcementIntro")
    }
}
