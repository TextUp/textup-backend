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

class AnnouncementFunctionalSpec extends RestSpec {

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

    void "test starting announcement with text subscribers"() {
        given:
        String authToken = getAuthToken()

        when: "create some incoming sessions subscribed to text"

        then:

        when: "list subscribers for text"

        then:

        when: "create announcement"

        then:

        when: "one of the text receipients unsubscribe"

        then:

    }

    void "test starting announcement with call subscribers"() {
        given:
        String authToken = getAuthToken()

        when: "create some incoming sessions subscribed to call"

        then:

        when: "list subscribers for call"

        then:

        when: "create announcement"

        then:

        when: "one of the call receipients unsubscribe"

        then:
    }
}
