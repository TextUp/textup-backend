package org.textup.rest

import grails.plugins.rest.client.RestResponse
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.type.OrgStatus
import org.textup.type.StaffStatus
import org.textup.validator.PhoneNumber
import org.textup.validator.BasePhoneNumber
import org.textup.validator.TempRecordReceipt
import org.textup.util.*
import static org.springframework.http.HttpStatus.*

class IncomingTextFunctionalSpec extends RestSpec {

    String requestUrl = "${baseUrl}/v1/public/records"

    def setup() {
        setupData()
        remote.exec {
            // ensure that callbackService validates all requests
            ctx.callbackService.metaClass.validate = { HttpServletRequest request,
                GrailsParameterMap params ->
                ctx.resultFactory.success()
            }
            String apiId = "iamsosospecial!"
            ctx.textService.metaClass.send = { BasePhoneNumber fromNum,
                List<? extends BasePhoneNumber> toNums, String message ->
                assert toNums.isEmpty() == false
                TempRecordReceipt temp = new TempRecordReceipt(apiId:apiId)
                temp.contactNumber = toNums[0]
                assert temp.validate()
                ctx.resultFactory.success(temp)
            }
            return
        }
    }

    def cleanup() {
        cleanupData()
    }

    void "test incoming text without announcements"() {
        given: "phone without announcements, none available"
        String sid = "iAmAValidSid"
        String fromNum = "+13829182928"
        String toNum = remote.exec({ un, fNum ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
            //ensure that incoming session doesn't exist
            PhoneNumber fromPNum = new PhoneNumber(number:fNum)
            assert IncomingSession.countByPhoneAndNumberAsString(s1.phone, fromPNum.number) == 0
            return s1.phone.number.e164PhoneNumber
        }.curry(loggedInUsername, fromNum))

        when: "none available"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("MessageSid", sid)
        form.add("From", fromNum)
        form.add("To", toNum)
        form.add("Body", "hi!")
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "away message and marked as away, no instructions"
        response.status == OK.value()
        response.xml.Message.size() == 1

        when: "some available"
        remote.exec({ un, fNum ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = true
            s1.save(flush:true, failOnError:true)
            return
        }.curry(loggedInUsername, fromNum))
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "no response, no instructions"
        response.status == OK.value()
        response.xml.Message.size() == 0
        response.xml.text() == ""
    }

    void "test incoming text with announcements"() {
        given: "session not subscribed, none available, should send instructions"
        String sid = "iAmAValidSid"
        String fromNum = "+12348398298"
        Map data = remote.exec({ un, fNum ->
            Staff s1 = Staff.findByUsername(un)
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
            Phone p1 = s1.phone
            // add announcement
            FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
                message:"hello!", expiresAt:DateTime.now().plusDays(2))
            announce.save(flush:true, failOnError:true)
            //ensure that incoming session doesn't exist
            PhoneNumber fromPNum = new PhoneNumber(number:fNum)
            assert IncomingSession.countByPhoneAndNumberAsString(p1, fromPNum.number) == 0
            return [
                to:p1.number.e164PhoneNumber,
                announce:announce.message,
                away:p1.awayMessage
            ]
        }.curry(loggedInUsername, fromNum))
        String toNum = data.to,
            announce = data.announce,
            away = data.away

        when: "incoming text"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("MessageSid", sid)
        form.add("From", fromNum)
        form.add("To", toNum)
        form.add("Body", "hi!")
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "away message and marked as away, no instructions"
        response.status == OK.value()
        response.xml.Message.size() == 2
        response.xml.Message.any { it.toString() == away }
        response.xml.Message.any {
            it.toString().contains(Constants.TEXT_TOGGLE_SUBSCRIBE) &&
                it.toString().contains(Constants.TEXT_SEE_ANNOUNCEMENTS)
        }

        when: "incoming text again"
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then: "instructions only shown once per day"
        response.status == OK.value()
        response.xml.Message.size() == 1
        response.xml.Message[0].toString() == away

        when: "see announcements"
        form.set("Body", Constants.TEXT_SEE_ANNOUNCEMENTS)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(announce)

        when: "toggle subscription when unsubscribed"
        form.set("Body", Constants.TEXT_TOGGLE_SUBSCRIBE)
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(Constants.TEXT_TOGGLE_SUBSCRIBE)
        response.xml.Message[0].toString().contains("receive")

        when: "toggle subscription when subscribed"
        response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        then:
        response.status == OK.value()
        response.xml.Message.size() == 1
        response.xml.Message[0].toString().contains(Constants.TEXT_TOGGLE_SUBSCRIBE)
        response.xml.Message[0].toString().contains("stop receiving")
    }
}
