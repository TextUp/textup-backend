package org.textup.rest

import grails.plugins.rest.client.RestResponse
import grails.test.mixin.TestFor
import org.textup.util.*
import static org.springframework.http.HttpStatus.*

@TestFor(StaffController)
class StaffControllerSpec extends RestSpec {

	String requestUrl = "$baseUrl/v1/staff"

    def setup() {
    	super.setupData()
    }

    def cleanup() {
    	super.cleanupData()
    }

    void "test list"() {

    }

    void "test show"() {

    }

    void "test save"() {

    }

    void "test update"() {

    }

    void "test delete"() {
    	
    }
}
