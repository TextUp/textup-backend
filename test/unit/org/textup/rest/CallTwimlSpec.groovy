package org.textup.rest

import org.textup.test.*
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class CallTwimlSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    MockedMethod getWebhookLink
    MockedMethod getHandleLink
    String webhookUrl
    String handleUrl

    def setup() {
        TestUtils.standardMockSetup()
        webhookUrl = TestUtils.randLinkString()
        handleUrl = TestUtils.randLinkString()
        getWebhookLink = MockedMethod.create(IOCUtils, "getWebhookLink") { webhookUrl }
        getHandleLink = MockedMethod.create(IOCUtils, "getHandleLink") { handleUrl }
    }

    def cleanup() {
        getWebhookLink?.restore()
        getHandleLink?.restore()
    }

    void "test errors"() {
        when: "invalid number for call"
        Result res = CallTwiml.invalid()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("twiml.invalidNumber")
                Hangup()
            }
        }

        when: "not found for call"
        res = CallTwiml.notFound()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("twiml.notFound")
                Hangup()
            }
        }

        when: "error for call"
        res = CallTwiml.error()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("callTwiml.error")
                Hangup()
            }
        }
    }

    void "test call utility responses"() {
        when:
        Result res = CallTwiml.hangUp()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response { Hangup() }
        }

        when:
        res = CallTwiml.blocked()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response { Reject(reason: "rejected") }
        }
    }

    void "test building direct message info"() {
        given:
        String token = TestUtils.randString()

        when:
        Map info = CallTwiml.infoForDirectMessage(token)

        then:
        info[CallbackUtils.PARAM_HANDLE] == CallResponse.DIRECT_MESSAGE.toString()
        CallTwiml.extractDirectMessageToken(TypeMap.create(info)) == token
    }

    void "test direct calls"() {
        given:
        String id = TestUtils.randString()
        String msg = TestUtils.randString()
        VoiceLanguage lang = VoiceLanguage.CHINESE
        List recordingUrls = [TestUtils.randUrl(), TestUtils.randUrl()]

        when: "direct message invalid"
        Result res = CallTwiml.directMessage(null, null, null, null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "valid only message"
        res = CallTwiml.directMessage(id, lang, msg)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("callTwiml.messageIntro")
                Pause(length: 1)
                CallTwiml.DIRECT_MESSAGE_MAX_REPEATS.times {
                    Say(language: lang.toTwimlValue(), msg)
                }
                Hangup()
            }
        }

        when: "valid only recording urls"
        res = CallTwiml.directMessage(id, lang, null, recordingUrls)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("callTwiml.messageIntro")
                Pause(length: 1)
                CallTwiml.DIRECT_MESSAGE_MAX_REPEATS.times {
                    recordingUrls.each { Play(it.toString()) }
                }
                Hangup()
            }
        }

        when: "valid both message and recording urls"
        res = CallTwiml.directMessage(id, lang, msg, recordingUrls)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("callTwiml.messageIntro")
                Pause(length: 1)
                CallTwiml.DIRECT_MESSAGE_MAX_REPEATS.times {
                    Say(language: lang.toTwimlValue(), msg)
                    recordingUrls.each { Play(it.toString()) }
                }
                Hangup()
            }
        }
    }

    void "test self greeting"() {
        when:
        Result res = CallTwiml.selfGreeting()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Gather(numDigits: 10) {
                    Say(loop: 20, "callTwiml.selfGreeting")
                }
                Say("callTwiml.goodbye")
                Hangup()
            }
        }
    }

    void "test self connecting"() {
        when: "self connecting invalid"
        Result res = CallTwiml.selfConnecting(null, null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "self connecting valid"
        String num = "1112223333"
        String displayNum = "2223338888"
        res = CallTwiml.selfConnecting(displayNum, num)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("callTwiml.selfConnecting")
                Dial(callerId: displayNum) {
                    Number(statusCallback: CallTwiml.childCallStatus(num), num)
                }
                Say("callTwiml.goodbye")
                Hangup()
            }
        }
    }

    void "test self invalid digits"() {
        when: "self invalid digits invalid"
        Result res = CallTwiml.selfInvalid(null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "self invalid digits valid"
        res = CallTwiml.selfInvalid("123")

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("callTwiml.selfInvalidDigits")
                Redirect(webhookUrl)
            }
        }

        and:
        getWebhookLink.hasBeenCalled
        getWebhookLink.latestArgs == [null]
    }

    void "test connecting incoming calls"() {
        given:
        PhoneNumber dispNum = TestUtils.randPhoneNumber()
        PhoneNumber originalFrom = TestUtils.randPhoneNumber()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        when: "connect incoming invalid"
        Result res = CallTwiml.connectIncoming(null, null, null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "connect incoming valid"
        res = CallTwiml.connectIncoming(dispNum, originalFrom, [pNum1, pNum1, pNum1])

        then: "only unique numbers are called"
        res.status == ResultStatus.OK
        getHandleLink.latestArgs == [CallResponse.CHECK_IF_VOICEMAIL, null]
        getWebhookLink.latestArgs == [CallTwiml.infoForScreenIncoming(originalFrom)]
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Dial(callerId: dispNum.e164PhoneNumber, timeout: 15, answerOnBridge: true, action: handleUrl) {
                    Number(statusCallback: CallTwiml.childCallStatus(pNum1.e164PhoneNumber),
                        url: webhookUrl, pNum1.e164PhoneNumber)
                }
            }
        }
    }

    void "test building screen incoming info"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        when:
        Map info = CallTwiml.infoForScreenIncoming(pNum1)

        then:
        info[CallbackUtils.PARAM_HANDLE] == CallResponse.SCREEN_INCOMING.toString()
        CallTwiml.tryExtractScreenIncomingFrom(TypeMap.create(info)).payload == pNum1
    }

    void "test screening incoming calls"() {
        given:
        String callerId1 = TestUtils.randString()
        String callerId2 = TestUtils.randString()

        when: "screen incoming invalid"
        Result res = CallTwiml.screenIncoming(null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "screen incoming valid"
        res = CallTwiml.screenIncoming([callerId1, callerId2])

        then:
        res.status == ResultStatus.OK
        getHandleLink.latestArgs == [CallResponse.DO_NOTHING, null]
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Gather(numDigits: 1, action: handleUrl) {
                    Pause(length: 1)
                    Say(loop: 2, "callTwiml.screenIncoming")
                }
                Say("callTwiml.goodbye")
                Hangup()
            }
        }
    }

    void "test trying to starting recording voicemail message"() {
        given:
        PhoneNumber fromNum = TestUtils.randPhoneNumber()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        Map callbackParams = [(TwilioUtils.FROM): fromNum.e164PhoneNumber,
            (TwilioUtils.TO): pNum1.e164PhoneNumber]
        URL greetingUrl = TestUtils.randUrl()
        String awayMsg = TestUtils.randString()
        VoiceType voiceType = VoiceType.FEMALE
        Phone p1 = GroovyMock() {
            asBoolean() >> true
            getNumber() >> pNum1
            getVoice() >> voiceType
            buildAwayMessage() >> awayMsg
        }

        when: "voicemail invalid"
        Result res = CallTwiml.recordVoicemailMessage(null, null)

        then:
        getHandleLink.notCalled
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "voicemail -- robot reading away message"
        getHandleLink.reset()
        res = CallTwiml.recordVoicemailMessage(p1, fromNum)

        then:
        1 * p1.useVoicemailRecordingIfPresent >> true
        1 * p1.voicemailGreetingUrl >> null
        getHandleLink.callCount == 2
        getHandleLink.callArgs.find { it == [CallResponse.END_CALL, null] }
        getHandleLink.callArgs.find { it == [CallResponse.VOICEMAIL_DONE, callbackParams] }
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Pause(length: 1)
                Say(voice: voiceType.toTwimlValue(), awayMsg)
                Say("callTwiml.voicemailDirections")
                Record(action: handleUrl, maxLength: 160, recordingStatusCallback: handleUrl)
                Say("callTwiml.goodbye")
                Hangup()
            }
        }

        when: "voicemail -- playing pre-recorded message"
        getHandleLink.reset()
        res = CallTwiml.recordVoicemailMessage(p1, fromNum)

        then:
        1 * p1.useVoicemailRecordingIfPresent >> true
        1 * p1.voicemailGreetingUrl >> greetingUrl
        getHandleLink.callCount == 2
        getHandleLink.callArgs.find { it == [CallResponse.END_CALL, null] }
        getHandleLink.callArgs.find { it == [CallResponse.VOICEMAIL_DONE, callbackParams] }
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Pause(length: 1)
                Play(greetingUrl.toString())
                Say("callTwiml.voicemailDirections")
                Record(action: handleUrl, maxLength: 160, recordingStatusCallback: handleUrl)
                Say("callTwiml.goodbye")
                Hangup()
            }
        }

        when: "has voicemail but chooses to use away message"
        getHandleLink.reset()
        p1.useVoicemailRecordingIfPresent = false
        res = CallTwiml.recordVoicemailMessage(p1, fromNum)

        then:
        1 * p1.useVoicemailRecordingIfPresent >> false
        1 * p1.voicemailGreetingUrl >> greetingUrl
        getHandleLink.callCount == 2
        getHandleLink.callArgs.find { it == [CallResponse.END_CALL, null] }
        getHandleLink.callArgs.find { it == [CallResponse.VOICEMAIL_DONE, callbackParams] }
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Pause(length: 1)
                Say(voice: voiceType.toTwimlValue(), awayMsg)
                Say("callTwiml.voicemailDirections")
                Record(action: handleUrl, maxLength: 160, recordingStatusCallback: handleUrl)
                Say("callTwiml.goodbye")
                Hangup()
            }
        }
    }

    void "test finishing bridge call"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(null, false)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr3 = TestUtils.buildIndPhoneRecord()
        ipr3.mergeNumber(TestUtils.randPhoneNumber(), 8)

        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)
        PhoneRecord spr2 = TestUtils.buildSharedPhoneRecord(ipr2)
        PhoneRecord spr3 = TestUtils.buildSharedPhoneRecord(ipr3)


        when: "finish bridge invalid"
        Result res = CallTwiml.finishBridge(TypeMap.create(CallTwiml.infoForFinishBridge(gpr1.id)))

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "finish bridge for a contact without numbers"
        res = CallTwiml.finishBridge(TypeMap.create(CallTwiml.infoForFinishBridge(spr1.id)))

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Pause(length: 3)
                Say("callTwiml.bridgeNoNumbers")
                Hangup()
            }
        }

        when: "finish bridge for a contact with one number"
        res = CallTwiml.finishBridge(TypeMap.create(CallTwiml.infoForFinishBridge(spr2.id)))

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Pause(length: 3)
                Say("callTwiml.bridgeNumberStart")
                Dial(timeout: 60, hangupOnStar: true, callerId: ipr2.phone.number.e164PhoneNumber) {
                    Number(statusCallback: CallTwiml.childCallStatus(ipr2.sortedNumbers[0].e164PhoneNumber),
                        ipr2.sortedNumbers[0].e164PhoneNumber)
                }
                Say("callTwiml.bridgeNumberFinish")
                Pause(length: 5)
                Say("callTwiml.bridgeDone")
                Hangup()
            }
        }

        when: "finish bridge if contact has numbers specified"
        res = CallTwiml.finishBridge(TypeMap.create(CallTwiml.infoForFinishBridge(spr3.id)))

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Pause(length: 3)
                Say("callTwiml.bridgeNumberStart")
                Say("callTwiml.bridgeNumberSkip")
                Dial(timeout: 60, hangupOnStar: true, callerId: ipr3.phone.number.e164PhoneNumber) {
                    Number(statusCallback: CallTwiml.childCallStatus(ipr3.sortedNumbers[0].e164PhoneNumber),
                        ipr3.sortedNumbers[0].e164PhoneNumber)
                }
                Say("callTwiml.bridgeNumberFinish")
                Say("callTwiml.bridgeNumberStart")
                Dial(timeout: 60, hangupOnStar: true, callerId: ipr3.phone.number.e164PhoneNumber) {
                    Number(statusCallback: CallTwiml.childCallStatus(ipr3.sortedNumbers[1].e164PhoneNumber),
                        ipr3.sortedNumbers[1].e164PhoneNumber)
                }
                Say("callTwiml.bridgeNumberFinish")
                Pause(length: 5)
                Say("callTwiml.bridgeDone")
                Hangup()
            }
        }
    }

    void "test building info for recording voicemail greeting"() {
        expect:
        CallTwiml.infoForRecordVoicemailGreeting()[CallbackUtils.PARAM_HANDLE] ==
            CallResponse.VOICEMAIL_GREETING_RECORD.toString()
    }

    void "test recording voicemail greeting"() {
        given:
        PhoneNumber phoneNum = TestUtils.randPhoneNumber()
        PhoneNumber is1Num = TestUtils.randPhoneNumber()

        when: "invalid"
        Result res = CallTwiml.recordVoicemailGreeting(null, null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "valid"
        res = CallTwiml.recordVoicemailGreeting(phoneNum, is1Num)

        then:
        getHandleLink.latestArgs == [CallResponse.VOICEMAIL_GREETING_PROCESSING, null]
        getWebhookLink.latestArgs == [CallTwiml.infoForVoicemailGreetingFinishedProcessing(phoneNum, is1Num)]
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Pause(length: 1)
                Say("callTwiml.recordVoicemailGreeting")
                Record(action: handleUrl, maxLength: 180, recordingStatusCallback: webhookUrl)
                Say("callTwiml.goodbye")
                Hangup()
            }
        }
    }

    void "test processing voicemail greeting"() {
        when:
        Result res = CallTwiml.processingVoicemailGreeting()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say(loop: 2, "callTwiml.processingVoicemailGreeting")
                Play(CallTwiml.HOLD_MUSIC_URL)
                Say(loop: 2, "callTwiml.processingVoicemailGreeting")
                Play(loop: 0, CallTwiml.HOLD_MUSIC_URL)
            }
        }
    }

    void "test building info for finished processing voicemail greeting"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()

        when:
        Map info = CallTwiml.infoForVoicemailGreetingFinishedProcessing(pNum1, pNum2)

        then:
        info[CallbackUtils.PARAM_HANDLE] == CallResponse.VOICEMAIL_GREETING_PROCESSED.toString()
        PhoneNumber.create(info[TwilioUtils.FROM]) == pNum1
        PhoneNumber.create(info[TwilioUtils.TO]) == pNum2
    }

    void "test building info for playing voicemail greeting"() {
        expect:
        CallTwiml.infoForPlayVoicemailGreeting()[CallbackUtils.PARAM_HANDLE] ==
            CallResponse.VOICEMAIL_GREETING_PLAY.toString()
    }

    void "test playing voicemail greeting"() {
        given:
        PhoneNumber fromNum = TestUtils.randPhoneNumber()
        URL greetingLink = TestUtils.randUrl()

        when: "invalid"
        Result res = CallTwiml.playVoicemailGreeting(null, null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "valid"
        res = CallTwiml.playVoicemailGreeting(fromNum, greetingLink)

        then:
        getWebhookLink.latestArgs == [CallTwiml.infoForRecordVoicemailGreeting()]
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Gather(numDigits: 1, action: webhookUrl) {
                    Say("callTwiml.finishedVoicemailGreeting")
                    Play(greetingLink.toString())
                    Say("callTwiml.finishedVoicemailGreeting")
                    Play(greetingLink.toString())
                }
                Say("callTwiml.goodbye")
                Hangup()
            }
        }
    }

    void "test calls announcements with parameters"() {
        given:
        String name = TestUtils.randString()

        when: "announcement greeting invalid"
        Result res = CallTwiml.announcementGreeting(null, null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "announcement greeting valid subscribed"
        res = CallTwiml.announcementGreeting(name, true)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Gather(numDigits: 1) {
                    Say("callTwiml.announcementGreetingWelcome")
                    Say("callTwiml.announcementUnsubscribe")
                    Say("callTwiml.connectToStaff")
                }
                Redirect(webhookUrl)
            }
        }
        getWebhookLink.latestArgs == [null]

        when: "announcement greeting valid not subscribed"
        res = CallTwiml.announcementGreeting(name, false)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Gather(numDigits: 1) {
                    Say("callTwiml.announcementGreetingWelcome")
                    Say("callTwiml.announcementSubscribe")
                    Say("callTwiml.connectToStaff")
                }
                Redirect(webhookUrl)
            }
        }
        getWebhookLink.latestArgs == [null]
    }

    void "test hearing announcements"() {
        given:
        FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement()
        FeaturedAnnouncement fa2 = TestUtils.buildAnnouncement()

        when: "hear announcements invalid"
        Result res = CallTwiml.hearAnnouncements(null, null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "hear announcements valid subscribed"
        res = CallTwiml.hearAnnouncements([fa1, fa2], true)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Gather(numDigits: 1) {
                    Say("twilioUtils.announcement")
                    Say("twilioUtils.announcement")
                    Say("callTwiml.announcementUnsubscribe")
                    Say("callTwiml.connectToStaff")
                }
                Redirect(webhookUrl)
            }
        }
        getWebhookLink.latestArgs == [null]

        when: "hear announcements valid not subscribed"
        res = CallTwiml.hearAnnouncements([fa1, fa2], false)

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Gather(numDigits: 1) {
                    Say("twilioUtils.announcement")
                    Say("twilioUtils.announcement")
                    Say("callTwiml.announcementSubscribe")
                    Say("callTwiml.connectToStaff")
                }
                Redirect(webhookUrl)
            }
        }
        getWebhookLink.latestArgs == [null]
    }

    void "test announcement and digits"() {
        given:
        String ident1 = TestUtils.randString()
        String msg1 = TestUtils.randString()

        when: "announcement and digits invalid"
        Result res = CallTwiml.announcementAndDigits(null)

        then:
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages[0] == "twilioUtils.invalidCode"

        when: "announcement and digits valid"
        res = CallTwiml.announcementAndDigits(TypeMap.create(CallTwiml.infoForAnnouncementAndDigits(ident1, msg1)))

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("callTwiml.announcementIntro")
                Gather(numDigits: 1) {
                    Say("twilioUtils.announcement")
                    Pause(length: 1)
                    Say("callTwiml.announcementUnsubscribe")
                }
                Redirect(webhookUrl)
            }
        }
        getWebhookLink.latestArgs == [CallTwiml.infoForAnnouncementAndDigits(ident1, msg1)]
    }

    void "test unsubscribed"() {
        when:
        Result res = CallTwiml.unsubscribed()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("callTwiml.unsubscribed")
                Say("callTwiml.goodbye")
                Hangup()
            }
        }
    }

    void "test subscribed"() {
        when:
        Result res = CallTwiml.subscribed()

        then:
        res.status == ResultStatus.OK
        TestUtils.buildXml(res.payload) == TestUtils.buildXml {
            Response {
                Say("callTwiml.subscribed")
                Say("callTwiml.goodbye")
                Hangup()
            }
        }
    }

    void "test building child call status callback"() {
        when:
        String randString = TestUtils.randString()
        String link = CallTwiml.childCallStatus(randString)

        then:
        getHandleLink.latestArgs == [CallbackUtils.STATUS,
            [(CallbackUtils.PARAM_CHILD_CALL_NUMBER): randString]]
        link == handleUrl
    }
}
