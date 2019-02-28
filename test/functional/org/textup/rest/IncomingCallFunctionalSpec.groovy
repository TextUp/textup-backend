package org.textup.rest

import grails.plugins.rest.client.RestResponse
import grails.test.mixin.*
import grails.test.mixin.support.*
import javax.servlet.http.HttpServletRequest
import org.joda.time.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.cache.*
import org.textup.media.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin) // enables local use of validator classes
class IncomingCallFunctionalSpec extends FunctionalSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    String requestUrl = "${baseUrl}/v1/public/records"

    def setup() {
        doSetup()
        remote.exec({ mockedMethodsKey ->
            app.config[mockedMethodsKey] << MockedMethod.create(MediaPostProcessor, "process") {
                UploadItem uItem1 = TestUtils.buildUploadItem(MediaType.AUDIO_MP3)
                UploadItem uItem2 = TestUtils.buildUploadItem(MediaType.AUDIO_WEBM_OPUS)
                ctx.resultFactory.success(uItem1, [uItem2])
            }
            return
        }.curry(MOCKED_METHODS_CONFIG_KEY))
    }

    def cleanup() {
    	doCleanup()
    }

    void "test incoming call when none available goes to voicemail"() {
        given: "no one is available"
        String sid = TestUtils.randString()
        String fromNum = TestUtils.randPhoneNumberString()
        String phoneNum = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)
                .logFail("IncomingCallFunctionalSpec")
                .thenEnd { op1 ->
                    op1.schedule.manual = true
                    op1.schedule.manualIsAvailable = false
                }
            return p1.number.e164PhoneNumber
        }.curry(loggedInUsername))

        when: "receive incoming call for unavailable"
        MultiValueMap form = new LinkedMultiValueMap()
        form.add(TwilioUtils.ID_CALL, sid)
        form.add(TwilioUtils.FROM, fromNum)
        form.add(TwilioUtils.TO, phoneNum)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "get sent to voicemail"
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Record.@action.toString().contains("${CallbackUtils.PARAM_HANDLE}=")
        // want to end call after recording the voicemail because we wait for the
        // status callback to tell us that the voicemail is doing processing. If we tried to
        // retrieve the recording right when the recording is finished, it might not be done
        // processing yet and we'll encounter an error
        response.xml.Record.@action.toString().contains(CallResponse.END_CALL.toString())
        response.xml.Record.@recordingStatusCallback.toString().contains("${CallbackUtils.PARAM_HANDLE}=")
        // we wait until the recording is done being processed by waiting for the recording status
        // callback to return with the VOICEMAIL_DONE status
        response.xml.Record.@recordingStatusCallback.toString().contains(CallResponse.VOICEMAIL_DONE.toString())

        when: "send no-op request from action hook of Record verb"
        // actionHandle should be a no-op that will just end the call
        String actionHandle = response.xml.Record.@action.toString().split("${CallbackUtils.PARAM_HANDLE}=")[1]
        // statusHandle should be when we actually retrieve the processed voicemail  `
        String statusHandle = response.xml.Record.@recordingStatusCallback
            .toString()
            .split("${CallbackUtils.PARAM_HANDLE}=")[1]
            .split("&")[0] // to get rid of other parameters

        response = rest.post("${requestUrl}?${CallbackUtils.PARAM_HANDLE}=${actionHandle}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "returns a Hangup verb"
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.text() == ""
        response.body == "<Response><Hangup/></Response>"

        when: "storing voicemail when recording has completed processing"
        form.set(TwilioUtils.RECORDING_DURATION, "1234")
        form.set(TwilioUtils.RECORDING_URL, "http://www.example.com")
        form.set(TwilioUtils.ID_RECORDING, TestUtils.randString())
        form.set(TwilioUtils.ID_ACCOUNT, TestUtils.randString())
        response = rest.post("${requestUrl}?${CallbackUtils.PARAM_HANDLE}=${statusHandle}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "no response"
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.text() == ""
        response.body == "<Response></Response>"
    }

    void "test incoming call with some available attempts to connect"() {
        given: "some available"
        String sid = TestUtils.randString()
        String fromNum = TestUtils.randPhoneNumberString()
        def (String toNum, String personalNum) = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)
                .logFail("IncomingCallFunctionalSpec")
                .thenEnd { op1 ->
                    op1.schedule.manual = true
                    op1.schedule.manualIsAvailable = true
                }
            return [p1.number.e164PhoneNumber, s1.personalNumber.e164PhoneNumber]
        }.curry(loggedInUsername))

        when: "receive incoming call"
        MultiValueMap form = new LinkedMultiValueMap()
        form.add(TwilioUtils.ID_CALL, sid)
        form.add(TwilioUtils.FROM, fromNum)
        form.add(TwilioUtils.TO, toNum)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "connect incoming, check if voicemail if none answer, and screen if one answers"
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        // incoming calls show on the TextUp user's phone as coming from the TextUp phone
        response.xml.Dial.@callerId.toString() == toNum
        // for the client, calls continues ringing even if redirected internally
        response.xml.Dial.@answerOnBridge.toString() == "true"
        response.xml.Dial.@action.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.CHECK_IF_VOICEMAIL.toString()}")
        response.xml.Dial.Number[0].@statusCallback.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallbackUtils.STATUS}")
        response.xml.Dial.Number[0].@statusCallback.toString().contains(CallbackUtils.PARAM_CHILD_CALL_NUMBER)
        response.xml.Dial.Number[0].@url.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.SCREEN_INCOMING.toString()}")
        response.xml.Dial.Number[0].@url.toString().contains("${CallTwiml.SCREEN_INCOMING_FROM}=")
        response.xml.Dial.Number[0].@url.toString().contains(URLEncoder.encode(fromNum, Constants.DEFAULT_CHAR_ENCODING))
        response.xml.Dial.Number[0].text() == personalNum

        when: "one of the numbers picks up"
        String voicemailUrl = response.xml.Dial.@action.toString()
        String screenUrl = response.xml.Dial.Number[0].@url.toString()
        // to do screening, a child call begins betwen the TextUp phone and personal phone
        form.set(TwilioUtils.FROM, toNum)
        form.set(TwilioUtils.TO, personalNum)
        response = rest.post(screenUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "call continues ringing while TextUp user screens the call"
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        // press any digit to be redirected away from the Hangup verb at the end of this TwiML
        response.xml.Gather.@numDigits.toString() == "1"
        response.body.contains("Hangup")
        // after any digit is pressed, this TwiML redirects to a no-op. We don't actually
        // need to do anything with the Gathered keypress. The whole point was to avoid the
        // Hangup verb at the end. If we don't Hangup in the Number verb's url in the original
        // CONNECT_INCOMING request, then the Number verb will continue to connect the call
        response.xml.Gather.@action.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.DO_NOTHING}")

        when: "no user input on screen do caller is redirected to voicemail @action in Dial verb"
        // When handling voicemail, the from/to numbers are restored to their original config
        form.set(TwilioUtils.FROM, fromNum)
        form.set(TwilioUtils.TO, toNum)
        // voicemail only started when the call has NOT connected
        form.set(TwilioUtils.STATUS_DIALED_CALL, ReceiptStatus.PENDING.statuses[0])

        response = rest.post(voicemailUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Record.@action.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.END_CALL}")
        response.xml.Record.@recordingStatusCallback.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.VOICEMAIL_DONE}")
        // first Say verb contains the phone's away message which is guaranteed to
        // have the default emergency message
        response.xml.Say[0].text().contains(Constants.DEFAULT_AWAY_MESSAGE_SUFFIX)
    }

    void "test call self"() {
        given: "staff's personal phone number"
        String sid = TestUtils.randString()
        def (String fromNum, String toNum) = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            return [s1.personalNumber.e164PhoneNumber, p1.number.e164PhoneNumber]
        }.curry(loggedInUsername))

        when: "call from self"
        MultiValueMap form = new LinkedMultiValueMap()
        form.add(TwilioUtils.ID_CALL, sid)
        form.add(TwilioUtils.FROM, fromNum)
        form.add(TwilioUtils.TO, toNum)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Gather.Say.@loop.toString().isInteger()

        when: "invalid entry"
        String invalidNums = "1234"
        form.set(TwilioUtils.DIGITS, invalidNums)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Redirect.text() == requestUrl

        when: "valid entry"
        String validNums = TestUtils.randPhoneNumberString()
        form.set(TwilioUtils.DIGITS, validNums)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Dial.Number[0].@statusCallback.toString().contains(CallbackUtils.STATUS)
        response.xml.Dial.Number[0].@statusCallback.toString().contains(CallbackUtils.PARAM_CHILD_CALL_NUMBER)
        response.xml.Dial.Number[0].text() == validNums
    }

    void "test calling with announcements"() {
        given: "phone with announcements, not a subscriber"
        String sid = TestUtils.randString()
        def (String message, String toNum, String fromNum, String personalNum) = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            FeaturedAnnouncement fa1 = TestUtils.buildAnnouncement(p1)
            IncomingSession is1 = TestUtils.buildSession(p1)
            if (is1) {
                is1.isSubscribedToCall = false
                is1.save(flush: true, failOnError: true)
            }
            return [fa1.message,
                p1.number.e164PhoneNumber,
                is1.number.e164PhoneNumber,
                s1.personalNumber.e164PhoneNumber]
        }.curry(loggedInUsername))

        when: "incoming call"
        MultiValueMap form = new LinkedMultiValueMap()
        form.add(TwilioUtils.ID_CALL, sid)
        form.add(TwilioUtils.FROM, fromNum)
        form.add(TwilioUtils.TO, toNum)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "announcement greeting"
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Gather.Say.size() == 3
        response.xml.Redirect.text() == requestUrl

        when: "toggle when unsubscribed"
        form.set(TwilioUtils.DIGITS, CallTwiml.DIGITS_TOGGLE_SUBSCRIBE)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "becomes subscribed"
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Say.text().contains("stop") == false

        when: "toggle when subscribed"
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "becomes unsubscribed"
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Say.text().contains("stop")

        when: "hear announcements"
        form.set(TwilioUtils.DIGITS, CallTwiml.DIGITS_HEAR_ANNOUNCEMENTS)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Gather.Say.any { it.toString().contains(message) }

        when: "any key to connect to staff, no staff available"
        remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)
                .logFail("IncomingCallFunctionalSpec")
                .thenEnd { op1 ->
                    op1.schedule.manual = true
                    op1.schedule.manualIsAvailable = false
                }
            return
        }.curry(loggedInUsername))
        form.set(TwilioUtils.DIGITS, "connect me please!")
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "voicemail"
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Dial.size() == 0
        response.xml.Record.@action.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.END_CALL}")
        response.xml.Record.@recordingStatusCallback.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.VOICEMAIL_DONE}")
        // first Say verb contains the phone's away message which is guaranteed to
        // have the default emergency message
        response.xml.Say[0].text().contains(Constants.DEFAULT_AWAY_MESSAGE_SUFFIX)

        when: "any key to connect to staff, some staff available"
        remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, s1.id)
                .logFail("IncomingCallFunctionalSpec")
                .thenEnd { op1 ->
                    op1.schedule.manual = true
                    op1.schedule.manualIsAvailable = true
                }
            return
        }.curry(loggedInUsername))
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "connect incoming"
        response.status == ResultStatus.OK.intStatus
        response.xml != null
        // incoming calls show on the TextUp user's phone as coming from the TextUp phone
        response.xml.Dial.@callerId.toString() == toNum
        // for the client, calls continues ringing even if redirected internally
        response.xml.Dial.@answerOnBridge.toString() == "true"
        response.xml.Dial.@action.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.CHECK_IF_VOICEMAIL.toString()}")
        response.xml.Dial.Number[0].@statusCallback.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallbackUtils.STATUS}")
        response.xml.Dial.Number[0].@statusCallback.toString().contains(CallbackUtils.PARAM_CHILD_CALL_NUMBER)
        response.xml.Dial.Number[0].@url.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.SCREEN_INCOMING.toString()}")
        response.xml.Dial.Number[0].@url.toString().contains("${CallTwiml.SCREEN_INCOMING_FROM}=")
        response.xml.Dial.Number[0].@url.toString().contains(URLEncoder.encode(fromNum, Constants.DEFAULT_CHAR_ENCODING))
        response.xml.Dial.Number[0].text() == personalNum
    }
}
