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
import org.textup.type.CallResponse
import org.textup.type.TextResponse
import org.textup.type.VoiceLanguage
import org.textup.type.VoiceType
import org.textup.util.CustomSpec
import spock.lang.Ignore
import spock.lang.Shared

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
	RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
	Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
	AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
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

    protected MessageSource mockMessageSource() {
        [getMessage: { String c, Object[] p, Locale l -> c }] as MessageSource
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

    void "test invalid number responses"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.resultFactory.messageSource = mockMessageSource()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "invalid number for call"
        Result<Closure> res = builder.invalidNumberForCall()

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Say("twimlBuilder.invalidNumber")
                Hangup()
            }
        })

        when: "invalid number for text"
        res = builder.invalidNumberForText()

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { Message("twimlBuilder.invalidNumber") }
        })
    }

    void "test not found responses"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.resultFactory.messageSource = mockMessageSource()
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
        builder.resultFactory.messageSource = mockMessageSource()
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
        builder.resultFactory.messageSource = mockMessageSource()
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
        builder.resultFactory.messageSource = mockMessageSource()
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
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "announcements, invalid params"
        res = builder.build(TextResponse.SEE_ANNOUNCEMENTS, [announcements:"kiki"])

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

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

        when: "blocked"
        res = builder.build(TextResponse.BLOCKED)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { Message("twimlBuilder.text.blocked") }
        })
    }

    void "test building text announcement"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.resultFactory.messageSource = mockMessageSource()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "invalid"
        Result res = builder.build(TextResponse.ANNOUNCEMENT, [:])

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

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
        builder.resultFactory.messageSource = mockMessageSource()
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

    void "test call utility responses"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.resultFactory.messageSource = mockMessageSource()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when:
        Result<Closure> res = builder.build(CallResponse.END_CALL)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { Hangup() }
        })

        when:
        res = builder.build(CallResponse.DO_NOTHING)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { }
        })

        when:
        res = builder.build(CallResponse.BLOCKED)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response { Reject(reason:"rejected") }
        })
    }

    void "test calls without parameters"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.resultFactory.messageSource = mockMessageSource()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "self greeting"
        Result<Closure> res = builder.build(CallResponse.SELF_GREETING)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Gather(numDigits:10) {
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

        when: "blocked"
        res = builder.build(CallResponse.BLOCKED)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Reject(reason:"rejected")
            }
        })
    }

    void "test calls for self with parameters"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.resultFactory.messageSource = mockMessageSource()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "self connecting invalid"
        Result<Closure> res = builder.build(CallResponse.SELF_CONNECTING)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "self connecting valid"
        String num = "1112223333"
        String displayNum = "2223338888"
        res = builder.build(CallResponse.SELF_CONNECTING,
            [displayedNumber:displayNum, numAsString:num])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Say("twimlBuilder.call.selfConnecting")
                Dial(callerId:displayNum) { Number(num) }
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        })

        when: "self invalid digits invalid"
        res = builder.build(CallResponse.SELF_INVALID_DIGITS)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

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
        builder.resultFactory.messageSource = mockMessageSource()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "voicemail invalid"
        Result<Closure> res = builder.build(CallResponse.CHECK_IF_VOICEMAIL)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "voicemail valid"
        Map linkParams =  [you:"got this!"]
        String awayMsg = "i am away"
        VoiceType voiceType = VoiceType.FEMALE
        res = builder.build(CallResponse.CHECK_IF_VOICEMAIL, [awayMessage:awayMsg,
            linkParams:linkParams, callbackParams:linkParams, voice:voiceType])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Pause(length:"1")
                Say(voice:voiceType.toTwimlValue(), awayMsg)
                Say("twimlBuilder.call.voicemailDirections")
                Record(action:linkParams.toString(), maxLength:160,
                    recordingStatusCallback:linkParams.toString())
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        })

        when: "voicemail done valid"
        res = builder.build(CallResponse.VOICEMAIL_DONE)

        then:
        res.success == true
        buildXml(res.payload) == buildXml({ Response {} })

        when: "connect incoming invalid"
        res = builder.build(CallResponse.CONNECT_INCOMING)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "connect incoming valid"
        String dispNum = "2223338888"
        String num = "1112223333"
        res = builder.build(CallResponse.CONNECT_INCOMING,
            [displayedNumber:dispNum, numsToCall:[num],
                linkParams:linkParams, screenParams:linkParams])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Dial(callerId:dispNum, timeout:"15", answerOnBridge:true,
                    action:linkParams.toString()) {
                    Number(url:linkParams.toString(), num)
                }
            }
        })

        when: "screen incoming invalid"
        res = builder.build(CallResponse.SCREEN_INCOMING)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "screen incoming valid"
        String callerId = "Joey Joe"
        res = builder.build(CallResponse.SCREEN_INCOMING,
            [callerId:callerId, linkParams:linkParams])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Gather(numDigits:"1", action:linkParams.toString()) {
                    Pause(length:"1")
                    Say("twimlBuilder.call.screenIncoming")
                    Say("twimlBuilder.call.screenIncoming")
                }
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        })
    }

    void "test calls bridge with parameters"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.resultFactory.messageSource = mockMessageSource()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "finish bridge invalid"
        Result res = builder.build(CallResponse.FINISH_BRIDGE)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "finish bridge for a contact without numbers"
        Result<Contact> contactRes = p1.createContact()
        assert contactRes.status == ResultStatus.CREATED
        Contact contact1 = contactRes.payload
        assert contact1.numbers == null

        res = builder.build(CallResponse.FINISH_BRIDGE,
            [contact:contact1])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Pause(length:"1")
                Say("twimlBuilder.call.bridgeNoNumbers")
                Hangup()
            }
        })

        when: "finish bridge for a contact with one number"
        ContactNumber cNum1 = contact1.mergeNumber("1112223333").payload
        contact1.save(flush:true, failOnError:true)
        res = builder.build(CallResponse.FINISH_BRIDGE,[contact:contact1])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Pause(length:"1")
                Say("twimlBuilder.call.bridgeNumberStart")
                Dial(timeout:"60", hangupOnStar:"true") {
                    Number(cNum1.e164PhoneNumber)
                }
                Say("twimlBuilder.call.bridgeNumberFinish")
                Pause(length:"5")
                Say("twimlBuilder.call.bridgeDone")
                Hangup()
            }
        })

        when: "finish bridge if contact has numbers specified"
        ContactNumber cNum2 = contact1.mergeNumber("2223338888").payload
        contact1.save(flush:true, failOnError:true)
        res = builder.build(CallResponse.FINISH_BRIDGE, [contact:contact1])

        then:
        res.success == true
        buildXml(res.payload) == buildXml({
            Response {
                Pause(length:"1")
                Say("twimlBuilder.call.bridgeNumberStart")
                Say("twimlBuilder.call.bridgeNumberSkip")
                Dial(timeout:"60", hangupOnStar:"true") {
                    Number(cNum1.e164PhoneNumber)
                }
                Say("twimlBuilder.call.bridgeNumberFinish")
                Say("twimlBuilder.call.bridgeNumberStart")
                Dial(timeout:"60", hangupOnStar:"true") {
                    Number(cNum2.e164PhoneNumber)
                }
                Say("twimlBuilder.call.bridgeNumberFinish")
                Pause(length:"5")
                Say("twimlBuilder.call.bridgeDone")
                Hangup()
            }
        })
    }

    void "test direct calls with parameters"() {
        given: "twiml builder"
        int numTimesCalled = 0
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.resultFactory.messageSource = mockMessageSource()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()
        builder.tokenService = [
            buildCallDirectMessageBody: { Closure<String> getMessage, Closure<String> getLink,
                String token = null, Integer repeatsSoFar = null ->
                numTimesCalled++
                null
            }
        ] as TokenService

        when: "direct message invalid"
        builder.build(CallResponse.DIRECT_MESSAGE)

        then:
        1 == numTimesCalled
    }

    void "test calls announcements with parameters"() {
        given: "twiml builder"
        TwimlBuilder builder = new TwimlBuilder()
        builder.resultFactory = getResultFactory()
        builder.resultFactory.messageSource = mockMessageSource()
        builder.messageSource = mockMessageSource()
        builder.linkGenerator = mockLinkGenerator()

        when: "announcement greeting invalid"
        Result<Closure> res = builder.build(CallResponse.ANNOUNCEMENT_GREETING)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

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
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

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
        // the resultfactory's messageSource regresses back to the StaticMessageSource
        // default by this method call instead of our mocked message source. Can't
        // figure out why so we use just add this errorCode to the StaticMessageSource
        // so that the built result will have the appropriate error messages
        addToMessageSource("twimlBuilder.invalidCode")
        res = builder.build(CallResponse.ANNOUNCEMENT_AND_DIGITS)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

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
