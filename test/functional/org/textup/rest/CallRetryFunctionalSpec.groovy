package org.textup.rest

import grails.plugins.rest.client.RestResponse
import java.util.UUID
import javax.servlet.http.HttpServletRequest
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.textup.*
import org.textup.types.FutureMessageType
import org.textup.util.*
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import static org.springframework.http.HttpStatus.*

class CallRetryFunctionalSpec extends RestSpec {

    String _firstApiId
    String _retryApiId
    List<String> _numbers = ["1112223333", "2223338888"]

    def setup() {
        _firstApiId = UUID.randomUUID().toString()
        _retryApiId = UUID.randomUUID().toString()

        setupData()
        remote.exec({ nums, apiId1, apiId2 ->
            // ensure that callbackService validates all requests
            ctx.callbackService.metaClass.validate = { HttpServletRequest request,
                GrailsParameterMap params ->
                ctx.resultFactory.success()
            }
            ctx.callService.metaClass.doCall = { PhoneNumber fromNum, BasePhoneNumber toNum,
                String afterLink, String callback ->

                println "CALLSEVICE DO CALL for fromNum: $fromNum, toNum: $toNum"

                String toNumAsString = toNum.number
                String apiId = (toNumAsString == nums[0]) ? apiId1 : apiId2
                ctx.resultFactory.success(new TempRecordReceipt(apiId:apiId,
                    receivedByAsString:toNumAsString))
            }
            return
        }.curry(_numbers, _firstApiId, _retryApiId))
    }

    def cleanup() {
    	cleanupData()
    }

    void "test retry call on failure of initial call"() {
        given:
        String authToken = getAuthToken()
        Long cId = remote.exec({ un, nums ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = s1.phone
            Contact contact = p1.createContact([:], nums).payload
            contact.save(flush:true, failOnError:true)
            return contact.id
        }.curry(loggedInUsername, _numbers))

        when: "creating a future message for a contact with two numbers"
        String msg = "hi"
        FutureMessageType fType = FutureMessageType.CALL
        RestResponse response = rest.post("${baseUrl}/v1/future-messages?contactId=${cId}") {
            contentType("application/json")
            header("Authorization", "Bearer $authToken")
            json {
                "future-message" {
                    message = msg
                    type = fType.toString().toLowerCase()
                }
            }
        }

        println "response.json: ${response.json}"

        then:
        response.status == CREATED.value()
        response.json["future-message"].message == msg
        response.json["future-message"].type == fType.toString()

        when: "first call made fails in the status callback"
        MultiValueMap<String,String> form = new LinkedMultiValueMap<>()
        form.add("CallSid", _apiId1)
        form.add("From", fromNum)
        form.add("To", phoneNum)
        form.add("CallStatus", Constants.FAILED_STATUSES[0])
        form.add("remaining", Helpers.toJsonString(_numbers[1..-1]))
        response = rest.post("${baseUrl}/v1/public/records?handle=${Constants.CALLBACK_STATUS}") {
            contentType("application/x-www-form-urlencoded")
            body(form)
        }

        println "response.xml: ${response.xml}"

        then: "status is stored but second number is tried"
        response.status == OK.value()
        response.xml != null
    }
}
