package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.util.CustomSpec

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
        json.futureMessages instanceof List
        json.futureMessages.size() == (tag1.record.futureMessages ?
            tag1.record.futureMessages.size() : 0)
        tag1.record.futureMessages?.every { FutureMessage fMsg ->
            json.futureMessages.any { it.id == fMsg.id }
        }
    }
}
