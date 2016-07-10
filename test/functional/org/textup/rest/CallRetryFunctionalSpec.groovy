package org.textup.rest

import grails.plugins.rest.client.RestResponse
import org.textup.util.*
import static org.springframework.http.HttpStatus.*
import org.textup.*
import org.textup.types.FutureMessageType

class CallRetryFunctionalSpec extends RestSpec {

    def setup() {
        setupData()
        remote.exec {
            // ensure that callbackService validates all requests
            ctx.callbackService.metaClass.validate = { HttpServletRequest request,
                GrailsParameterMap params ->
                ctx.resultFactory.success()
            }
            return
        }
    }

    def cleanup() {
    	cleanupData()
    }

    void "test retry call on failure of initial call"() {
        given:
        String authToken = getAuthToken()
        String sid = "iAmAValidSid"
        List<String> numbers = ["1112223333", "2223338888"]
        Long cId = remote.exec({ un, numbers ->
            Staff s1 = Staff.findByUsername(un)
            Phone p1 = s1.phone
            Contact contact = p1.createContact([:], numbers).payload
            contact.save(flush:true, failOnError:true)
            return contact.id
        }.curry(loggedInUsername, numbers))

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

        then:

        when: "first call made fails in the status callback"


        then: "status is stored but second number is tried"


    }
}
