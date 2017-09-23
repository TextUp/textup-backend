package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.util.CustomSpec
import org.textup.validator.ImageInfo

class ImageInfoJsonMarshallerIntegrationSpec extends CustomSpec {

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
        ImageInfo imageInfo = new ImageInfo(key:key, link:link)

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = jsonToObject(imageInfo as JSON) as Map
        }

        then:
        json.key == key
        json.link == link
    }
}
