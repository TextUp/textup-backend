package org.textup.rest

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import groovy.xml.MarkupBuilder
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.types.CallResponse
import org.textup.types.TextResponse
import org.textup.util.CustomSpec
import spock.lang.Ignore
import spock.lang.Shared
import static org.springframework.http.HttpStatus.*

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole])
@TestMixin(HibernateTestMixin)
class TwimlBuilderSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}

    def setup() {
    	setupData()
    }

    def cleanup() {
    	cleanupData()
    }

    protected LinkGenerator mockLinkGenerator() {
        [link: { Map m ->
            (m.params ?: [:]).toString()
        }] as LinkGenerator
    }
    protected String buildXml(Closure data) {
        StringWriter writer = new StringWriter()
        MarkupBuilder xmlBuilder = new MarkupBuilder(writer)
        xmlBuilder(data)
        writer.toString()
    }

    void "test not found responses"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "not found for call"
        Result<Closure> res = builder.notFoundForCall()

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Say("twimlBuilder.notFound")
                Hangup()
            }
        })

        when: "not found for text"
        res = builder.notFoundForText()

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { Message("twimlBuilder.notFound") }
        })
    }

    void "test error responses"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "error for call"
        Result<Closure> res = builder.errorForCall()

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Say("twimlBuilder.error")
                Hangup()
            }
        })

        when: "error for text"
        res = builder.errorForText()

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { Message("twimlBuilder.error") }
        })
    }

    void "test no response"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when:
        Result<Closure> res = builder.noResponse()

        then:
        res.success == true
        buildXml(res.payload) == buildXml({ Response { } })
    }

    // Texts
    // -----

    void "test texts"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "instructions unsubscribed"
        Result<Closure> res = builder.build(TextResponse.INSTRUCTIONS_UNSUBSCRIBED)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { Message("twimlBuilder.text.instructionsUnsubscribed") }
        })

        when: "instructions subscribed"
        res = builder.build(TextResponse.INSTRUCTIONS_SUBSCRIBED)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { Message("twimlBuilder.text.instructionsSubscribed") }
        })

        when: "announcements, no params"
        res = builder.build(TextResponse.SEE_ANNOUNCEMENTS)

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "announcements, invalid params"
        res = builder.build(TextResponse.SEE_ANNOUNCEMENTS, [announcements:"kiki"])

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "announcements empty list"
        res = builder.build(TextResponse.SEE_ANNOUNCEMENTS, [announcements:[]])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { Message("twimlBuilder.noAnnouncements") }
        })

        when: "announcements, valid"
        List<FeaturedAnnouncement> announces = [[
            whenCreated: DateTime.now(),
            owner: p1,
            message: "hello1"
        ] as FeaturedAnnouncement, [
            whenCreated: DateTime.now(),
            owner: p1,
            message: "hello2"
        ] as FeaturedAnnouncement]
        res = builder.build(TextResponse.SEE_ANNOUNCEMENTS, [announcements:announces])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Message("twimlBuilder.announcement")
                Message("twimlBuilder.announcement")
            }
        })

        when: "subscribed"
        res = builder.build(TextResponse.SUBSCRIBED)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { Message("twimlBuilder.text.subscribed") }
        })

        when: "unsubscribed"
        res = builder.build(TextResponse.UNSUBSCRIBED)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { Message("twimlBuilder.text.unsubscribed") }
        })
    }

    void "test building text announcement"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "invalid"
        Result res = builder.build(TextResponse.ANNOUNCEMENT, [:])

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "valid"
        Map info = [identifier:"kiki", message:"hello!"]
        res = builder.build(TextResponse.ANNOUNCEMENT, info)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Message("${info.identifier}: ${info.message}. twimlBuilder.text.announcementUnsubscribe")
            }
        })
    }

    void "test building text strings"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when:
        String msg1 = "hello"
        String msg2 = "there"
        Result<Closure> res = builder.buildTexts([msg2, msg1])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Message(msg2)
                Message(msg1)
            }
        })
    }

    // Calls
    // -----

    void "test calls without parameters"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "self greeting"
        Result<Closure> res = builder.build(CallResponse.SELF_GREETING)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Gather(numDigits:11) {
                    Say("twimlBuilder.call.selfGreeting")
                }
                Redirect("[:]")
            }
        })

        when: "unsubscribed"
        res = builder.build(CallResponse.UNSUBSCRIBED)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Say("twimlBuilder.call.unsubscribed")
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        })

        when: "subscribed"
        res = builder.build(CallResponse.SUBSCRIBED)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Say("twimlBuilder.call.subscribed")
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        })
    }

    void "test calls for self with parameters"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "self connecting invalid"
        Result<Closure> res = builder.build(CallResponse.SELF_CONNECTING)

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "self connecting valid"
        String num = "1112223333"
        res = builder.build(CallResponse.SELF_CONNECTING,
            [numAsString:num])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Say("twimlBuilder.call.selfConnecting")
                Dial { Number(num) }
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        })

        when: "self invalid digits invalid"
        res = builder.build(CallResponse.SELF_INVALID_DIGITS)

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "self invalid digits valid"
        res = builder.build(CallResponse.SELF_INVALID_DIGITS,
            [digits:"123"])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Say("twimlBuilder.call.selfInvalidDigits")
                Redirect("[:]")
            }
        })
    }

    void "test calls connect incoming with parameters"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "voicemail invalid"
        Result<Closure> res = builder.build(CallResponse.VOICEMAIL)

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "voicemail valid"
        Map linkParams =  [you:"got this!"]
        res = builder.build(CallResponse.VOICEMAIL, [linkParams:linkParams])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Say("twimlBuilder.call.voicemail")
                Record(action:linkParams.toString(), maxLength:160)
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        })

        when: "connect incoming invalid"
        res = builder.build(CallResponse.CONNECT_INCOMING)

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "connect incoming valid"
        String num = "1112223333"
        res = builder.build(CallResponse.CONNECT_INCOMING,
            [nameOrNumber:"kiki", numsToCall:[num], linkParams:linkParams])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Say("twimlBuilder.call.connectIncoming")
                Dial(timeout:"15") {
                    Number(num)
                }
                Redirect(linkParams.toString())
            }
        })
    }

    void "test calls bridge with parameters"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "confirm bridge invalid"
        Result<Closure> res = builder.build(CallResponse.CONFIRM_BRIDGE)

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "confirm bridge valid"
        Map linkParams =  [you:"got this!"]
        res = builder.build(CallResponse.CONFIRM_BRIDGE,
            [contact:c1, linkParams:linkParams])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Gather(action:linkParams.toString(), numDigits:1) {
                    Say("twimlBuilder.call.confirmBridge")
                }
                Say("twimlBuilder.call.noConfirmBridge")
                Hangup()
            }
        })

        when: "finish bridge invalid"
        res = builder.build(CallResponse.FINISH_BRIDGE)

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "finish bridge valid"
        res = builder.build(CallResponse.FINISH_BRIDGE,
            [contact:c1])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Say("twimlBuilder.call.finishBridge")
                c1.numbers?.each { ContactNumber num ->
                    Say("twimlBuilder.call.bridgeNumber")
                    Dial(num.e164PhoneNumber)
                }
                Pause(length:"5")
                Say("twimlBuilder.call.finishBridgeDone")
                Hangup()
            }
        })
    }

    void "test calls announcements with parameters"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "announcement greeting invalid"
        Result<Closure> res = builder.build(CallResponse.ANNOUNCEMENT_GREETING)

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "announcement greeting valid subscribed"
        res = builder.build(CallResponse.ANNOUNCEMENT_GREETING,
            [name:"kiki", isSubscribed: true])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Gather(numDigits:1) {
                    Say("twimlBuilder.call.announcementGreetingWelcome")
                    Say("twimlBuilder.call.announcementUnsubscribe")
                    Say("twimlBuilder.call.connectToStaff")
                }
                Redirect("[:]")
            }
        })

        when: "announcement greeting valid not subscribed"
        res = builder.build(CallResponse.ANNOUNCEMENT_GREETING,
            [name:"kiki", isSubscribed: false])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Gather(numDigits:1) {
                    Say("twimlBuilder.call.announcementGreetingWelcome")
                    Say("twimlBuilder.call.announcementSubscribe")
                    Say("twimlBuilder.call.connectToStaff")
                }
                Redirect("[:]")
            }
        })

        when: "hear announcements invalid"
        res = builder.build(CallResponse.HEAR_ANNOUNCEMENTS)

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "hear announcements valid subscribed"
        List<FeaturedAnnouncement> announces = [[
            whenCreated: DateTime.now(),
            owner: p1,
            message: "hello1"
        ] as FeaturedAnnouncement, [
            whenCreated: DateTime.now(),
            owner: p1,
            message: "hello2"
        ] as FeaturedAnnouncement]
        res = builder.build(CallResponse.HEAR_ANNOUNCEMENTS,
            [announcements:announces, isSubscribed:true])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Gather(numDigits:1) {
                    Say("twimlBuilder.announcement")
                    Say("twimlBuilder.announcement")
                    Say("twimlBuilder.call.announcementUnsubscribe")
                    Say("twimlBuilder.call.connectToStaff")
                }
                Redirect("[:]")
            }
        })

        when: "hear announcements valid not subscribed"
        res = builder.build(CallResponse.HEAR_ANNOUNCEMENTS,
            [announcements:announces, isSubscribed:false])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Gather(numDigits:1) {
                    Say("twimlBuilder.announcement")
                    Say("twimlBuilder.announcement")
                    Say("twimlBuilder.call.announcementSubscribe")
                    Say("twimlBuilder.call.connectToStaff")
                }
                Redirect("[:]")
            }
        })

        when: "announcement and digits invalid"
        res = builder.build(CallResponse.ANNOUNCEMENT_AND_DIGITS)

        then:
        res.success == false
        res.payload.status == BAD_REQUEST
        res.payload.code == "twimlBuilder.invalidCode"

        when: "announcement and digits valid"
        Map params = [identifier:"kiki", message:"hello"]
        res = builder.build(CallResponse.ANNOUNCEMENT_AND_DIGITS, params)

        String bodyString = buildXml({
            Say("twimlBuilder.call.announcementIntro")
            Gather(numDigits:1) {
                Say("twimlBuilder.announcement")
                Pause(length:"1")
                Say("twimlBuilder.call.announcementUnsubscribe")
            }
        }).replaceAll(/<call>|<\/call>/, "").replaceAll(/\s+/, "")

        then:
        res.success == true
        buildXml(res.payload).replaceAll(/\s+/, "").contains(bodyString)
        buildXml(res.payload).contains(params.identifier)
        buildXml(res.payload).contains(params.message)
        buildXml(res.payload).contains(CallResponse.ANNOUNCEMENT_AND_DIGITS.toString())
    }
}
