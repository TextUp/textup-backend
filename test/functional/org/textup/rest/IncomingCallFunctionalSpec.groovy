package org.textup.rest

import grails.plugins.rest.client.RestResponse
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import static org.springframework.http.HttpStatus.*

class IncomingCallFunctionalSpec extends RestSpec {

    String requestUrl = "${baseUrl}/v1/public/records"

    def setup() {
        setupData()
        remote.exec({
            // ensure that callbackService validates all requests
            ctx.callbackService.metaClass.validate = { HttpServletRequest request,
                GrailsParameterMap params ->
                ctx.resultFactory.success()
            }
            ctx.phoneService.metaClass.moveVoicemail = { String callId, String recordingId,
                String voicemailUrl ->
                ctx.resultFactory.success()
            }
            ctx.phoneService.metaClass.storeVoicemail = { String callId, int voicemailDuration ->
                ctx.resultFactory.success().toGroup()
            }
            return
        })
    }

    def cleanup() {
    	cleanupData()
    }

    void "test incoming call when none available goes to voicemail"() {
        given: "no one is available"
        String sid = "iAmAValidSid"
        String fromNum = "+16262027548"
        String phoneNum = remote.exec({ un, fNum ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
            assert s1.personalPhoneNumber.e164PhoneNumber != fNum
            return s1.phone.number.e164PhoneNumber
        }.curry(loggedInUsername, fromNum))

        when: "receive incoming call for unavailable"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("CallSid", sid)
        form.add("From", fromNum)
        form.add("To", phoneNum)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "get sent to voicemail"
        response.status == OK.value()
        response.xml != null
        response.xml.Record.@action.toString().contains("handle=")
        // want to end call after recording the voicemail because we wait for the
        // status callback to tell us that the voicemail is doing processing. If we tried to
        // retrieve the recording right when the recording is finished, it might not be done
        // processing yet and we'll encounter an error
        response.xml.Record.@action.toString().contains(CallResponse.END_CALL.toString())
        response.xml.Record.@recordingStatusCallback.toString().contains("handle=")
        // we wait until the recording is done being processed by waiting for the recording status
        // callback to return with the VOICEMAIL_DONE status
        response.xml.Record.@recordingStatusCallback.toString().contains(CallResponse.VOICEMAIL_DONE.toString())

        when: "send no-op request from action hook of Record verb"
        // actionHandle should be a no-op that will just end the call
        String actionHandle = response.xml.Record.@action.toString().split("handle=")[1]
        // statusHandle should be when we actually retrieve the processed voicemail  `
        String statusHandle = response.xml.Record.@recordingStatusCallback
            .toString()
            .split("handle=")[1]
            .split("&")[0] // to get rid of other parameters

        response = rest.post("${requestUrl}?handle=${actionHandle}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "returns a Hangup verb"
        response.status == OK.value()
        response.xml != null
        response.xml.text() == ""
        response.body == "<Response><Hangup/></Response>"

        when: "storing voicemail when recording has completed processing"
        form.set("RecordingDuration", "1234")
        form.set("RecordingUrl", "http://www.example.com")
        form.set("RecordingSid", "I am a recording sid")
        response = rest.post("${requestUrl}?handle=${statusHandle}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "no response"
        response.status == OK.value()
        response.xml != null
        response.xml.text() == ""
        response.body == "<Response></Response>"
    }

    void "test incoming call with some available attempts to connect"() {
        given: "some available"
        String sid = "iAmAValidSid"
        String fromNum = "+16262027548"
        Map data = remote.exec({ un, fNum ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = true
            s1.save(flush:true, failOnError:true)
            assert s1.personalPhoneNumber.e164PhoneNumber != fNum
            return [
                to: s1.phone.number.e164PhoneNumber,
                personal: s1.personalPhoneNumber.e164PhoneNumber
            ]
        }.curry(loggedInUsername, fromNum))
        String toNum = data.to,
            personalNum = data.personal

        when: "receive incoming call"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("CallSid", sid)
        form.add("From", fromNum)
        form.add("To", toNum)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "connect incoming, check if voicemail if none answer, and screen if one answers"
        response.status == OK.value()
        response.xml != null
        // incoming calls show on the TextUp user's phone as coming from the TextUp phone
        response.xml.Dial.@callerId.toString() == toNum
        // for the client, calls continues ringing even if redirected internally
        response.xml.Dial.@answerOnBridge.toString() == "true"
        response.xml.Dial.@action.toString().contains("handle=${CallResponse.CHECK_IF_VOICEMAIL.toString()}")
        response.xml.Dial.Number[0].@statusCallback.toString().contains("handle=${Constants.CALLBACK_STATUS}")
        response.xml.Dial.Number[0].@statusCallback.toString().contains(Constants.CALLBACK_CHILD_CALL_NUMBER_KEY)
        response.xml.Dial.Number[0].@url.toString().contains("handle=${CallResponse.SCREEN_INCOMING.toString()}")
        response.xml.Dial.Number[0].@url.toString().contains("originalFrom=")
        response.xml.Dial.Number[0].@url.toString().contains(URLEncoder.encode(fromNum, "UTF-8"))
        response.xml.Dial.Number[0].text() == personalNum

        when: "one of the numbers picks up"
        String voicemailUrl = response.xml.Dial.@action.toString()
        String screenUrl = response.xml.Dial.Number[0].@url.toString()
        // to do screening, a child call begins betwen the TextUp phone and personal phone
        form.set("From", toNum)
        form.set("To", personalNum)
        response = rest.post(screenUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "call continues ringing while TextUp user screens the call"
        response.status == OK.value()
        response.xml != null
        // press any digit to be redirected away from the Hangup verb at the end of this TwiML
        response.xml.Gather.@numDigits.toString() == "1"
        response.body.contains("Hangup")
        // after any digit is pressed, this TwiML redirects to a no-op. We don't actually
        // need to do anything with the Gathered keypress. The whole point was to avoid the
        // Hangup verb at the end. If we don't Hangup in the Number verb's url in the original
        // CONNECT_INCOMING request, then the Number verb will continue to connect the call
        response.xml.Gather.@action.toString().contains("handle=${CallResponse.DO_NOTHING}")

        when: "no user input on screen do caller is redirected to voicemail @action in Dial verb"
        // When handling voicemail, the from/to numbers are restored to their original config
        form.set("From", fromNum)
        form.set("To", toNum)
        // voicemail only started when the call has NOT connected
        form.set("DialCallStatus", ReceiptStatus.PENDING.statuses[0])

        response = rest.post(voicemailUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml != null
        response.xml.Record.@action.toString().contains("handle=${CallResponse.END_CALL}")
        response.xml.Record.@recordingStatusCallback.toString().contains("handle=${CallResponse.VOICEMAIL_DONE}")
        // first Say verb contains the phone's away message which is guaranteed to
        // have the default emergency message
        response.xml.Say[0].text().contains(Constants.AWAY_EMERGENCY_MESSAGE)
    }

    void "test call self"() {
        given: "staff's personal phone number"
        String sid = "iAmAValidSid"
        Map data = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            return [
                from:s1.personalPhoneNumber.e164PhoneNumber,
                to:s1.phone.number.e164PhoneNumber
            ]
        }.curry(loggedInUsername))
        String fromNum = data.from,
            toNum = data.to

        when: "call from self"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("CallSid", sid)
        form.add("From", fromNum)
        form.add("To", toNum)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml != null
        response.xml.Redirect.text() == requestUrl

        when: "invalid entry"
        String invalidNums = "1234"
        form.set("Digits", invalidNums)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml != null
        response.xml.Redirect.text() == requestUrl

        when: "valid entry"
        String validNums = "2678887452"
        form.set("Digits", validNums)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml != null
        response.xml.Dial.Number[0].@statusCallback.toString().contains(Constants.CALLBACK_STATUS)
        response.xml.Dial.Number[0].@statusCallback.toString().contains(Constants.CALLBACK_CHILD_CALL_NUMBER_KEY)
        response.xml.Dial.Number[0].text() == validNums
    }

    void "test calling with announcements"() {
        given: "phone with announcements, not a subscriber"
        String sid = "iAmAValidSid"
        Map data = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = s1.phone
            // add announcement
            FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
                message:"hello!", expiresAt:DateTime.now().plusDays(2))
            announce.save(flush:true, failOnError:true)
            // ensure not subscriber
            String fromNum = "6262027548"
            IncomingSession sess = IncomingSession.findByPhoneAndNumberAsString(p1, fromNum)
            if (sess) {
                sess.isSubscribedToCall = false
                sess.save(flush:true, failOnError:true)
            }
            return [
                message: announce.message,
                to: p1.number.e164PhoneNumber,
                from: fromNum,
                personal: s1.personalPhoneNumber.e164PhoneNumber
            ]
        }.curry(loggedInUsername))
        String message = data.message,
            toNum = data.to,
            fromNum = data.from,
            personalNum = data.personal

        when: "incoming call"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("CallSid", sid)
        form.add("From", fromNum)
        form.add("To", toNum)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "announcement greeting"
        response.status == OK.value()
        response.xml != null
        response.xml.Gather.Say.size() == 3
        response.xml.Redirect.text() == requestUrl

        when: "toggle when unsubscribed"
        form.set("Digits", Constants.CALL_TOGGLE_SUBSCRIBE)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "becomes subscribed"
        response.status == OK.value()
        response.xml != null
        response.xml.Say.text().contains("stop") == false

        when: "toggle when subscribed"
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "becomes unsubscribed"
        response.status == OK.value()
        response.xml != null
        response.xml.Say.text().contains("stop") == true

        when: "hear announcements"
        form.set("Digits", Constants.CALL_HEAR_ANNOUNCEMENTS)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml != null
        response.xml.Gather.Say.any { it.toString().contains(message) }

        when: "any key to connect to staff, no staff available"
        remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
            return
        }.curry(loggedInUsername))
        form.set("Digits", "connect me please!")
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "voicemail"
        response.status == OK.value()
        response.xml != null
        response.xml.Dial.size() == 0
        response.xml.Record.@action.toString().contains("handle=${CallResponse.END_CALL}")
        response.xml.Record.@recordingStatusCallback.toString().contains("handle=${CallResponse.VOICEMAIL_DONE}")
        // first Say verb contains the phone's away message which is guaranteed to
        // have the default emergency message
        response.xml.Say[0].text().contains(Constants.AWAY_EMERGENCY_MESSAGE)

        when: "any key to connect to staff, some staff available"
        remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = true
            s1.save(flush:true, failOnError:true)
            return
        }.curry(loggedInUsername))
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "connect incoming"
        response.status == OK.value()
        response.xml != null
        // incoming calls show on the TextUp user's phone as coming from the TextUp phone
        response.xml.Dial.@callerId.toString() == toNum
        // for the client, calls continues ringing even if redirected internally
        response.xml.Dial.@answerOnBridge.toString() == "true"
        response.xml.Dial.@action.toString().contains("handle=${CallResponse.CHECK_IF_VOICEMAIL.toString()}")
        response.xml.Dial.Number[0].@statusCallback.toString().contains("handle=${Constants.CALLBACK_STATUS}")
        response.xml.Dial.Number[0].@statusCallback.toString().contains(Constants.CALLBACK_CHILD_CALL_NUMBER_KEY)
        response.xml.Dial.Number[0].@url.toString().contains("handle=${CallResponse.SCREEN_INCOMING.toString()}")
        response.xml.Dial.Number[0].@url.toString().contains("originalFrom=")
        response.xml.Dial.Number[0].@url.toString().contains(URLEncoder.encode(fromNum, "UTF-8"))
        response.xml.Dial.Number[0].text() == personalNum
    }
}
