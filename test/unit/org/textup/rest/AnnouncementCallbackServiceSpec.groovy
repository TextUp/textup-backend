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

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(AnnouncementCallbackService)
class AnnouncementCallbackServiceSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }

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

    // @FreshRuntime // TODO
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
