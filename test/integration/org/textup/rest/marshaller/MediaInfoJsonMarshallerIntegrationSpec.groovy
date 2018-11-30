package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.test.*
import org.textup.*
import org.textup.type.*
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
            json = TestUtils.jsonToMap(mInfo as JSON)
        }

        then:
        json.id == mInfo.id
        json.images instanceof Collection
        json.images.isEmpty()
        json.uploadErrors == null

        when: "some media elements"
        List<MediaElement> elements = []
        int numImages = 4
        numImages.times {
            MediaElement e1 = TestUtils.buildMediaElement(5)
            mInfo.addToMediaElements(e1)
            elements << e1
        }
        int numAudio = 2
        numAudio.times {
            MediaElement e1 = TestUtils.buildMediaElement()
            e1.sendVersion.type = MediaType.AUDIO_WEBM_OPUS
            mInfo.addToMediaElements(e1)
            elements << e1
        }
        Collection<String> errorMessages = ["errors1", "errors2"]
        Utils.trySetOnRequest(Constants.REQUEST_UPLOAD_ERRORS, errorMessages)

        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestUtils.jsonToMap(mInfo as JSON)
        }

        then:
        json.id == mInfo.id
        json.images instanceof Collection
        json.images.size() == numImages
        json.audio instanceof Collection
        json.audio.size() == numAudio
        json.images.every { elements.find { e1 -> it.uid == e1.uid } }
        json.audio.every { elements.find { e1 -> it.uid == e1.uid } }
        json.uploadErrors instanceof List
        json.uploadErrors.size() == errorMessages.size()
        errorMessages.every { String msg -> json.uploadErrors.find { it.contains(msg) } }
    }
}
