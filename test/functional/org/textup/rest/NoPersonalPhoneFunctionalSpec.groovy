package org.textup.rest

import grails.plugins.rest.client.RestResponse
import java.util.UUID
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.Trigger
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.jobs.FutureMessageJob
import org.textup.types.CallResponse
import org.textup.types.FutureMessageType
import org.textup.types.ReceiptStatus
import org.textup.util.*
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

class NoPersonalPhoneFunctionalSpec extends RestSpec {

	String targetPhoneNumber
	String prevPersonalNumber

	def setup() {
		setupData()
		(targetPhoneNumber, prevPersonalNumber) = remote.exec({ un ->
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
            // return TextUp phone number of the logged-in
            Staff s1 = Staff.findByUsername(un)
            String prevPersonalNumber = s1.personalPhoneAsString
            s1.personalPhoneAsString = ""
            s1.save(flush:true, failOnError:true)
            return [s1.phone.numberAsString, prevPersonalNumber]
        }.curry(loggedInUsername))
	}

	def cleanup() {
		cleanupData()
        remote.exec({ un, prevNum ->
            Staff s1 = Staff.findByUsername(un)
            s1.personalPhoneAsString = prevNum
            s1.save(flush:true, failOnError:true)
            return
        }.curry(loggedInUsername, prevPersonalNumber))
	}

	// Calls
	// -----

	void "test incoming call"() {
		given:
		String sid = "iAmAValidSid"
        String fromNum = "+16262027548"
        String requestUrl = "${baseUrl}/v1/public/records"

		when:
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("CallSid", sid)
        form.add("From", fromNum)
        form.add("To", targetPhoneNumber)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

		then: "call sent to voicemail"
		response.status == OK.value()
        response.xml != null
        response.xml.Record.@action.toString() == "$requestUrl?handle=${CallResponse.VOICEMAIL}"
	}

	void "test incoming self call"() {
		given:
		String sid = "iAmAValidSid"
        String fromNum = prevPersonalNumber
        String requestUrl = "${baseUrl}/v1/public/records"

		when:
		MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("CallSid", sid)
        form.add("From", fromNum)
        form.add("To", targetPhoneNumber)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

		then: "treating as normal incoming call, sent to voicemail"
		response.status == OK.value()
        response.xml != null
        response.xml.Record.@action.toString() == "$requestUrl?handle=${CallResponse.VOICEMAIL}"
	}

	void "test attempting to start an outgoing bridge call"() {
		given:
        String authToken = getAuthToken()
        long contactId = remote.exec({ un ->
        	Staff s1 = Staff.findByUsername(un)
        	return s1.phone.contacts[0].id
    	}.curry(loggedInUsername))

		when:
		RestResponse response = rest.post("${baseUrl}/v1/records") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                record {
                    callContact = contactId
                }
            }
        }

		then: "returns error with unprocessable status"
		response.status == UNPROCESSABLE_ENTITY.value()
		response.json.errors instanceof List
		response.json.errors.size() == 1
	}

	// Text
	// ----

	void "test incoming text"() {
		given:
		String sid = "iAmAValidSid"
        String fromNum = "+16262027548"
        String requestUrl = "${baseUrl}/v1/public/records"

		when:
		MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("MessageSid", sid)
        form.add("From", fromNum)
        form.add("To", targetPhoneNumber)
        form.add("Body", "hi!")
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

		then: "respond with away message as if unavailable"
		response.status == OK.value()
        response.xml.Message.size() == 1
	}

	// for outgoing text test see OutgoingTextFunctionalSpec
}