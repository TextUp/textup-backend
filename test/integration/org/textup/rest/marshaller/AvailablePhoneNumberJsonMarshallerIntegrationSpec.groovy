package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.util.*
import org.textup.validator.AvailablePhoneNumber

class AvailablePhoneNumberJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
        setupIntegrationData()
    }

    def cleanup() {
        cleanupIntegrationData()
    }

    void "test marshalling image info"() {
        given: "info"
        String key = UUID.randomUUID().toString(),
            link = "https://www.test.com/${key}"
        AvailablePhoneNumber sidNum = new AvailablePhoneNumber(),
            regionNum = new AvailablePhoneNumber()
        sidNum.with {
            phoneNumber = "1112223333"
            sid = "i am sid"
        }
        regionNum.with {
            phoneNumber = "1112223333"
            region = "CA, USA"
        }
        assert [regionNum, sidNum]*.validate()

        when: "a number with a sid"
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(sidNum as JSON)
        }

        then:
        json.phoneNumber == sidNum.e164PhoneNumber
        json[sidNum.infoType] == sidNum.info

        when: "a number with a region"
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(regionNum as JSON)
        }

        then:
        json.phoneNumber == regionNum.e164PhoneNumber
        json[regionNum.infoType] == regionNum.info
    }
}
