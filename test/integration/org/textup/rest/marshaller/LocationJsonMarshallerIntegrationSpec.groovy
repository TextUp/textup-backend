package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.util.CustomSpec

class LocationJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
    	setupIntegrationData()
    }

    def cleanup() {
    	cleanupIntegrationData()
    }

    void "test marshalling location"() {
        given:
        Location loc = t1.location

    	when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(loc as JSON) as Map
        }

        then:
        json.id == loc.id
        json.address == loc.address
        json.lat == loc.lat
        json.lon == loc.lon
    }
}
