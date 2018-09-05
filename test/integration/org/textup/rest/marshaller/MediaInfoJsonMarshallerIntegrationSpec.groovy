package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.type.*
import org.textup.util.TestHelpers
import spock.lang.*

class MediaInfoJsonMarshallerIntegrationSpec extends Specification {

    def grailsApplication

    void "test marshalling media info"() {
        given:
        MediaInfo mInfo = new MediaInfo()
        mInfo.save(flush: true, failOnError: true)
        assert mInfo.id != null

        when: "empty"
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(mInfo as JSON)
        }

        then:
        json.id == mInfo.id
        json.images instanceof Collection
        json.images.isEmpty()
        json.uploadErrors == null

        when: "some media elements"
        List<MediaElement> elements = []
        int numElements = 4
        numElements.times {
            MediaElement e1 = TestHelpers.buildMediaElement(5)
            mInfo.addToMediaElements(e1)
            elements << e1
        }
        Collection<String> errorMessages = ["errors1", "errors2"]
        Helpers.trySetOnRequest(Constants.REQUEST_UPLOAD_ERRORS, errorMessages)

        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(mInfo as JSON)
        }

        then:
        json.id == mInfo.id
        json.images instanceof Collection
        json.images.size() == numElements
        elements.every { e1 -> json.images.find { it.uid == e1.uid } }
        json.uploadErrors instanceof List
        json.uploadErrors.size() == errorMessages.size()
        errorMessages.every { String msg -> json.uploadErrors.find { it.contains(msg) } }
    }
}
