package org.textup.rest.marshallers

import grails.converters.JSON
import org.textup.*
import org.textup.util.CustomSpec

class PhoneJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
        setupIntegrationData()
    }

    def cleanup() {
        cleanupIntegrationData()
    }

    void "test marshal phone"() {
        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(p1 as JSON) as Map
        }

        then:
        json.id == p1.id
        json.number == p1.number.e164PhoneNumber
        json.awayMessage == p1.awayMessage
        json.tags.size() == p1.tags.size()
        p1.tags.every { ContactTag ct1 ->
            json.tags.find { it.id == ct1.id }
        }
    }
}
