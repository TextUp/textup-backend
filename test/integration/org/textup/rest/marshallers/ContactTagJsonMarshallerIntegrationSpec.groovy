package org.textup.rest.marshallers

import org.textup.util.CustomSpec
import grails.converters.JSON

class ContactTagJsonMarshallerIntegrationSpec extends CustomSpec {

	def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshalling tag"() {
    	when:
    	Map json
    	JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
    		json = jsonToObject(tag1 as JSON) as Map
    	}

    	then:
    	json.id == tag1.id
    	json.name == tag1.name
    	json.hexColor == tag1.hexColor
    	json.lastRecordActivity == tag1.record.lastRecordActivity.toString()
    }
}
