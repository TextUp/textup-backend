package org.textup.rest.marshallers

import org.textup.util.CustomSpec
import grails.converters.JSON
import org.textup.*

class FutureMessageJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshalling future message"() {
        expect:
        1 == 2
    }
}
