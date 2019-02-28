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
class NoPersonalPhoneFunctionalSpec extends FunctionalSpec {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

	String toNum
	String prevPersonalNumber
	String requestUrl = "${baseUrl}/v1/public/records"

	def setup() {
		doSetup()
		(toNum, prevPersonalNumber) = remote.exec({ un ->
            Staff s1 = Staff.findByUsername(un)
            String prevPersonalNumber = s1.personalNumberAsString
            s1.personalNumberAsString = null
            s1.save(flush: true, failOnError: true)
            Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
            return [p1.numberAsString, prevPersonalNumber]
        }.curry(loggedInUsername))
	}

	def cleanup() {
		doCleanup()
	}

	// Calls
	// -----

	void "test incoming call"() {
		given:
		String sid = TestUtils.randString()
        String fromNum = TestUtils.randPhoneNumberString()

		when:
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add(TwilioUtils.ID_CALL, sid)
        form.add(TwilioUtils.FROM, fromNum)
        form.add(TwilioUtils.TO, toNum)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

		then: "call sent to voicemail"
		response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Record.@action.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.END_CALL}")
        response.xml.Record.@recordingStatusCallback.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.VOICEMAIL_DONE}")
        // first Say verb contains the phone's away message which is guaranteed to
        // have the default emergency message
        response.xml.Say[0].text().contains(Constants.DEFAULT_AWAY_MESSAGE_SUFFIX)
	}

	void "test incoming self call"() {
		given:
		String sid = TestUtils.randString()
        String fromNum = prevPersonalNumber

		when:
		MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add(TwilioUtils.ID_CALL, sid)
        form.add(TwilioUtils.FROM, fromNum)
        form.add(TwilioUtils.TO, toNum)
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

		then: "treating as normal incoming call, sent to voicemail"
		response.status == ResultStatus.OK.intStatus
        response.xml != null
        response.xml.Record.@action.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.END_CALL}")
        response.xml.Record.@recordingStatusCallback.toString().contains("${CallbackUtils.PARAM_HANDLE}=${CallResponse.VOICEMAIL_DONE}")
        // first Say verb contains the phone's away message which is guaranteed to
        // have the default emergency message
        response.xml.Say[0].text().contains(Constants.DEFAULT_AWAY_MESSAGE_SUFFIX)
	}

	void "test attempting to start an outgoing bridge call"() {
		given:
        String authToken = getAuthToken()
        long iprId = remote.exec({ un ->
        	Staff s1 = Staff.findByUsername(un)
        	Phone p1 = IOCUtils.phoneCache.findPhone(s1.id, PhoneOwnershipType.INDIVIDUAL)
        	return TestUtils.buildIndPhoneRecord(p1).id
    	}.curry(loggedInUsername))

		when:
		RestResponse response = rest.post("${baseUrl}/v1/records") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                record {
                	type = RecordItemType.CALL.toString()
                    ids = [iprId]
                }
            }
        }

		then: "returns error with unprocessable status"
		response.status == ResultStatus.UNPROCESSABLE_ENTITY.intStatus
		response.json.errors instanceof List
		response.json.errors.size() == 1
	}

	// Text
	// ----

	void "test incoming text when no personal phone associated"() {
		given:
		String sid = TestUtils.randString()
        String fromNum = TestUtils.randPhoneNumberString()

		when:
		MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add(TwilioUtils.ID_TEXT, sid)
        form.add(TwilioUtils.FROM, fromNum)
        form.add(TwilioUtils.TO, toNum)
        form.add(TwilioUtils.BODY, "hi!")
        form.add(TwilioUtils.NUM_SEGMENTS, "8")
        RestResponse response = rest.post(requestUrl) {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

		then: "still attempts to notify and silenty fails so no away message returned"
		response.status == ResultStatus.OK.intStatus
		response.text == "<Response></Response>"
	}

	// for outgoing text test see OutgoingTextFunctionalSpec
}
