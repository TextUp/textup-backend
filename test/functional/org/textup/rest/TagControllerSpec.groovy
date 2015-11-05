package org.textup.rest

import grails.plugins.rest.client.RestResponse
import grails.test.mixin.TestFor
import org.textup.util.*
import static org.springframework.http.HttpStatus.*

@TestFor(TagController)
class TagControllerSpec extends RestSpec {

	String requestUrl = "$baseUrl/v1/tags"

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
