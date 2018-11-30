package org.textup.rest

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.*
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
class CallTwimlSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    void "test errors"() {
        when: "invalid number for call"
        Result<Closure> res = CallTwiml.invalid()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Say("twimlBuilder.invalidNumber")
                Hangup()
            }
        })

        when: "not found for call"
        res = CallTwiml.notFound()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Say("twimlBuilder.notFound")
                Hangup()
            }
        })

        when: "error for call"
        res = CallTwiml.error()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Say("twimlBuilder.error")
                Hangup()
            }
        })
    }

    void "test building child call status callback"() {
        when:
        String randString = TestUtils.randString()
        String link = CallTwiml.childCallStatus(randString)

        then:
        link.contains("handle")
        link.contains(Constants.CALLBACK_STATUS)
        link.contains(Constants.CALLBACK_CHILD_CALL_NUMBER_KEY)
        link.contains(randString)
    }

    void "test call utility responses"() {
        when:
        Result<Closure> res = CallTwiml.hangUp()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response { Hangup() }
        })

        when:
        res = CallTwiml.blocked()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response { Reject(reason:"rejected") }
        })
    }

    void "test calls without parameters"() {
        when: "self greeting"
        Result<Closure> res = CallTwiml.selfGreeting()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Gather(numDigits: 10) {
                    Say(loop: 20, "twimlBuilder.call.selfGreeting")
                }
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        })

        when: "unsubscribed"
        res = CallTwiml.unsubscribed()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Say("twimlBuilder.call.unsubscribed")
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        })

        when: "subscribed"
        res = CallTwiml.subscribed()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Say("twimlBuilder.call.subscribed")
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        })
    }

    void "test self connecting"() {
        when: "self connecting invalid"
        Result<Closure> res = CallTwiml.selfConnecting(null, null)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "self connecting valid"
        String num = "1112223333"
        String displayNum = "2223338888"
        res = CallTwiml.selfConnecting(displayNum, num)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Say("twimlBuilder.call.selfConnecting")
                Dial(callerId: displayNum) {
                    Number(statusCallback: CallTwiml.childCallStatus(num), num)
                }
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        })
    }

    void "test self invalid digits"() {
        when: "self invalid digits invalid"
        Result<Closure> res = CallTwiml.selfInvalid(null)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "self invalid digits valid"
        res = CallTwiml.selfInvalid("123")

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Say("twimlBuilder.call.selfInvalidDigits")
                Redirect("[:]")
            }
        })
    }

    void "test trying to starting recording voicemail message"() {
        when: "voicemail invalid"
        Result<Closure> res = CallTwiml.recordVoicemailMessage(null, null)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "voicemail -- robot reading away message"
        PhoneNumber fromNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        Map callbackParams = [handle: CallResponse.VOICEMAIL_DONE, From: fromNum.e164PhoneNumber,
            To: p1.number.e164PhoneNumber]
        Map actionParams = [handle: CallResponse.END_CALL]
        res = CallTwiml.recordVoicemailMessage(p1, fromNum)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Pause(length: 1)
                Say(voice: p1.voice.toTwimlValue(), p1.awayMessage)
                Say("twimlBuilder.call.voicemailDirections")
                Record(action: actionParams.toString(), maxLength: 160,
                    recordingStatusCallback:callbackParams.toString())
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        }

        when: "voicemail -- playing pre-recorded message"
        String greetingUrl = "http://www.example.com/${TestUtils.randString()}"
        p1.useVoicemailRecordingIfPresent = true
        p1.metaClass.getVoicemailGreetingUrl = { -> new URL(greetingUrl) }
        res = CallTwiml.recordVoicemailMessage(p1, fromNum)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Pause(length: 1)
                Play(greetingUrl)
                Say("twimlBuilder.call.voicemailDirections")
                Record(action: actionParams.toString(), maxLength: 160,
                    recordingStatusCallback:callbackParams.toString())
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        }

        when: "has voicemail but chooses to use away message"
        p1.useVoicemailRecordingIfPresent = false
        res = CallTwiml.recordVoicemailMessage(p1, fromNum)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Pause(length: 1)
                Say(voice: p1.voice.toTwimlValue(), p1.awayMessage)
                Say("twimlBuilder.call.voicemailDirections")
                Record(action: actionParams.toString(), maxLength: 160,
                    recordingStatusCallback:callbackParams.toString())
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        }
    }

    void "test connecting incoming calls"() {
        when: "connect incoming invalid"
        Result<Closure> res = CallTwiml.connectIncoming(null, null, null)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "connect incoming valid"
        PhoneNumber dispNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        PhoneNumber originalFrom = new PhoneNumber(number: TestUtils.randPhoneNumber())
        String num = TestUtils.randPhoneNumber()
        Map voicemailParams = [handle: CallResponse.CHECK_IF_VOICEMAIL]
        Map screenParams = CallTwiml.infoForScreenIncoming(originalFrom)
        res = CallTwiml.connectIncoming(dispNum, originalFrom, [num])

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Dial(callerId: dispNum.e164PhoneNumber, timeout: 15, answerOnBridge: true,
                    action: voicemailParams.toString()) {
                    Number(statusCallback: CallTwiml.childCallStatus(num),
                        url: screenParams.toString(), num)
                }
            }
        })
    }

    void "test screening incoming calls"() {
        when: "screen incoming invalid"
        Result<Closure> res = CallTwiml.screenIncoming(null)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "screen incoming valid"
        String callerId1 = "Joey Joe"
        String callerId2 = "Tommy Tom"
        Map finishScreenParams = [handle: CallResponse.DO_NOTHING]
        res = CallTwiml.screenIncoming([callerId1, callerId2])

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Gather(numDigits: 1, action: finishScreenParams.toString()) {
                    Pause(length: 1)
                    Say(loop: 2, "twimlBuilder.call.screenIncoming")
                }
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        }
    }

    void "test finishing bridge call"() {
        when: "finish bridge invalid"
        Result res = CallTwiml.finishBridge(null)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "finish bridge for a contact without numbers"
        Result<Contact> contactRes = p1.createContact()
        assert contactRes.status == ResultStatus.CREATED
        Contact contact1 = contactRes.payload
        assert contact1.numbers == null

        res = CallTwiml.finishBridge(contact1)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Pause(length: 1)
                Say("twimlBuilder.call.bridgeNoNumbers")
                Hangup()
            }
        })

        when: "finish bridge for a contact with one number"
        ContactNumber cNum1 = contact1.mergeNumber("1112223333").payload
        contact1.save(flush:true, failOnError:true)
        res = CallTwiml.finishBridge(contact1)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Pause(length: 1)
                Say("twimlBuilder.call.bridgeNumberStart")
                Dial(timeout: 60, hangupOnStar: true) {
                    Number(statusCallback: CallTwiml.childCallStatus(cNum1.e164PhoneNumber),
                        cNum1.e164PhoneNumber)
                }
                Say("twimlBuilder.call.bridgeNumberFinish")
                Pause(length: 5)
                Say("twimlBuilder.call.bridgeDone")
                Hangup()
            }
        })

        when: "finish bridge if contact has numbers specified"
        ContactNumber cNum2 = contact1.mergeNumber("2223338888").payload
        contact1.save(flush:true, failOnError:true)
        res = CallTwiml.finishBridge(contact1)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Pause(length: 1)
                Say("twimlBuilder.call.bridgeNumberStart")
                Say("twimlBuilder.call.bridgeNumberSkip")
                Dial(timeout: 60, hangupOnStar: true) {
                    Number(statusCallback: CallTwiml.childCallStatus(cNum1.e164PhoneNumber),
                        cNum1.e164PhoneNumber)
                }
                Say("twimlBuilder.call.bridgeNumberFinish")
                Say("twimlBuilder.call.bridgeNumberStart")
                Dial(timeout: 60, hangupOnStar: true) {
                    Number(statusCallback: CallTwiml.childCallStatus(cNum2.e164PhoneNumber),
                        cNum2.e164PhoneNumber)
                }
                Say("twimlBuilder.call.bridgeNumberFinish")
                Pause(length: 5)
                Say("twimlBuilder.call.bridgeDone")
                Hangup()
            }
        })
    }

    void "test direct calls with parameters"() {
        when: "direct message invalid"
        Result<Closure> res = CallTwiml.directMessage(null, null, null, null)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "valid without recording urls"
        String id = TestUtils.randString()
        String msg = TestUtils.randString()
        VoiceLanguage lang = VoiceLanguage.CHINESE
        res = CallTwiml.directMessage(id, msg, lang)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("twimlBuilder.call.messageIntro")
                Pause(length: 1)
                Constants.DIRECT_MESSAGE_MAX_REPEATS.times {
                    Say(language: lang.toTwimlValue(), msg)
                }
                Hangup()
            }
        }

        when: "valid with recording urls"
        List<URL> recordingUrls = [
            new URL("https://www.example.com/${TestUtils.randString()}"),
            new URL("https://www.example.com/${TestUtils.randString()}")
        ]
        res = CallTwiml.directMessage(id, msg, lang, recordingUrls)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("twimlBuilder.call.messageIntro")
                Pause(length: 1)
                Constants.DIRECT_MESSAGE_MAX_REPEATS.times {
                    Say(language: lang.toTwimlValue(), msg)
                    recordingUrls.each { Play(it.toString()) }
                }
                Hangup()
            }
        }
    }

    void "test calls announcements with parameters"() {
        when: "announcement greeting invalid"
        Result<Closure> res = CallTwiml.announcementGreeting(null, null)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "announcement greeting valid subscribed"
        res = CallTwiml.announcementGreeting("kiki", true)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Gather(numDigits: 1) {
                    Say("twimlBuilder.call.announcementGreetingWelcome")
                    Say("twimlBuilder.call.announcementUnsubscribe")
                    Say("twimlBuilder.call.connectToStaff")
                }
                Redirect("[:]")
            }
        }

        when: "announcement greeting valid not subscribed"
        res = CallTwiml.announcementGreeting("kiki", false)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Gather(numDigits: 1) {
                    Say("twimlBuilder.call.announcementGreetingWelcome")
                    Say("twimlBuilder.call.announcementSubscribe")
                    Say("twimlBuilder.call.connectToStaff")
                }
                Redirect("[:]")
            }
        })
    }

    void "test hearing announcements"() {
        when: "hear announcements invalid"
        Result<Closure> res = CallTwiml.hearAnnouncements(null, null)

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
        res = CallTwiml.hearAnnouncements(announces, true)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Gather(numDigits: 1) {
                    Say("twimlBuilder.announcement")
                    Say("twimlBuilder.announcement")
                    Say("twimlBuilder.call.announcementUnsubscribe")
                    Say("twimlBuilder.call.connectToStaff")
                }
                Redirect("[:]")
            }
        })

        when: "hear announcements valid not subscribed"
        res = CallTwiml.hearAnnouncements(announces, false)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml({
            Response {
                Gather(numDigits: 1) {
                    Say("twimlBuilder.announcement")
                    Say("twimlBuilder.announcement")
                    Say("twimlBuilder.call.announcementSubscribe")
                    Say("twimlBuilder.call.connectToStaff")
                }
                Redirect("[:]")
            }
        })
    }

    void "test announcement and digits"() {
        when: "announcement and digits invalid"
        Result<Closure> res = CallTwiml.announcementAndDigits(null, null)

        then:
        res.success == false
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "announcement and digits valid"
        String identifier = TestUtils.randString()
        String msg = TestUtils.randString()
        Map repeatParams = [handle: CallResponse.ANNOUNCEMENT_AND_DIGITS,
            identifier: identifier, message: msg]
        res = CallTwiml.announcementAndDigits(identifier, msg)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("twimlBuilder.call.announcementIntro")
                Gather(numDigits: 1) {
                    Say("twimlBuilder.announcement")
                    Pause(length: 1)
                    Say("twimlBuilder.call.announcementUnsubscribe")
                }
                Redirect(repeatParams.toString())
            }
        }
    }

    void "test recording voicemail greeting"() {
        when: "invalid"
        Result<Closure> res = CallTwiml.recordVoicemailGreeting(null, null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "valid"
        PhoneNumber phoneNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        PhoneNumber sessNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        Map processingParams = [handle: CallResponse.VOICEMAIL_GREETING_PROCESSING]
        Map doneParams = CallTwiml.infoForVoicemailGreetingFinishedProcessing(phoneNum, sessNum)
        res = CallTwiml.recordVoicemailGreeting(phoneNum, sessNum)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Pause(length: 1)
                Say("twimlBuilder.call.recordVoicemailGreeting")
                Record(action: processingParams.toString(), maxLength: 180,
                    recordingStatusCallback: doneParams.toString())
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        }
    }

    void "test processing voicemail greeting"() {
        when:
        Result<Closure> res = CallTwiml.processingVoicemailGreeting()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say(loop: 2, "twimlBuilder.call.processingVoicemailGreeting")
                Play(Constants.CALL_HOLD_MUSIC_URL)
                Say(loop: 2, "twimlBuilder.call.processingVoicemailGreeting")
                Play(loop: 0, Constants.CALL_HOLD_MUSIC_URL)
            }
        }
    }

    void "test playing voicemail greeting"() {
        when: "invalid"
        Result<Closure> res = CallTwiml.playVoicemailGreeting(null, null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twimlBuilder.invalidCode"

        when: "valid"
        PhoneNumber fromNum = new PhoneNumber(number: TestUtils.randPhoneNumber())
        URL greetingLink = new URL("http://www.example.com/${TestUtils.randString()}")
        Map recordParams = CallTwiml.infoForRecordVoicemailGreeting()
        res = CallTwiml.playVoicemailGreeting(fromNum, greetingLink)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Gather(numDigits: 1, action: recordParams.toString()) {
                    Say("twimlBuilder.call.finishedVoicemailGreeting")
                    Play(greetingLink.toString())
                    Say("twimlBuilder.call.finishedVoicemailGreeting")
                    Play(greetingLink.toString())
                }
                Say("twimlBuilder.call.goodbye")
                Hangup()
            }
        }
    }
}
