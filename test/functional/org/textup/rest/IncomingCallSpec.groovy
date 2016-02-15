package org.textup.rest

import grails.plugins.rest.client.RestResponse
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.types.OrgStatus
import org.textup.types.CallResponse
import org.textup.types.StaffStatus
import org.textup.util.*
import static org.springframework.http.HttpStatus.*

class IncomingCallSpec extends RestSpec {

    String requestUrl = "${baseUrl}/v1/public/records"

    def setup() {
        setupData()
        remote.exec {
            // ensure that callbackService validates all requests
            ctx.callbackService.metaClass.validate = { HttpServletRequest request,
                GrailsParameterMap params ->
                ctx.resultFactory.success()
            }
            ctx.phoneService.metaClass.moveVoicemail = { String apiId ->
                ctx.resultFactory.success()
            }
            ctx.phoneService.metaClass.storeVoicemail = { String apiId, int dur ->
                ctx.resultFactory.success()
            }
            return
        }
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

        when: "resend request without voicemail duration"
        String handle = response.xml.Record.@action.toString().split("handle=")[1]
        RestResponse response2 = rest.post("${requestUrl}?handle=${handle}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "repeat voicemail message"
        response2.status == OK.value()
        response2.xml != null
        response.xml == response2.xml

        when: "store voicemail"
        form.set("RecordingDuration", "1234")
        response = rest.post("${requestUrl}?handle=${handle}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "no response"
        response.status == OK.value()
        response.xml != null
        response.xml.text() == ""
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

        then: "get sent to voicemail"
        response.status == OK.value()
        response.xml != null
        response.xml.Redirect.text() == "$requestUrl?handle=${CallResponse.VOICEMAIL}"
        response.xml.Dial.Number[0] == personalNum

        when: "gets redirected to voicemail"
        String voicemailUrl = response.xml.Redirect.text()
        response = rest.post(voicemailUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml != null
        response.xml.Record.@action.toString().contains("handle=")
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
                from: fromNum
            ]
        }.curry(loggedInUsername))
        String message = data.message,
            toNum = data.to,
            fromNum = data.from

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
        response.xml.Record.@action.toString().contains("handle=")

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
        response.xml.Dial.size() != 0
        response.xml.Record.size() == 0
        response.xml.Redirect.text() == "$requestUrl?handle=${CallResponse.VOICEMAIL}"
    }
}
