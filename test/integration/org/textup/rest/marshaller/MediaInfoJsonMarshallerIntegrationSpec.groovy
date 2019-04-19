package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class MediaInfoJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling media info"() {
        given:
        MediaInfo mInfo = TestUtils.buildMediaInfo()
        List elements = []
        int numImages = 4
        numImages.times {
            MediaElement e1 = TestUtils.buildMediaElement(5)
            elements << e1
        }
        int numAudio = 2
        numAudio.times {
            MediaElement e1 = TestUtils.buildMediaElement()
            e1.sendVersion.type = MediaType.AUDIO_WEBM_OPUS
            elements << e1
        }
        Collection<String> errorMessages = [TestUtils.randString(), TestUtils.randString()]

        when: "empty"
        Map json = TestUtils.objToJsonMap(mInfo)

        then:
        json.id == mInfo.id
        json.images instanceof Collection
        json.images.isEmpty()
        json.audio instanceof Collection
        json.audio.isEmpty()
        json.uploadErrors == null

        when: "some media elements with upload errors"
        mInfo.tryAddAllElements(elements)
        RequestUtils.trySet(RequestUtils.UPLOAD_ERRORS, errorMessages)

        json = TestUtils.objToJsonMap(mInfo)

        then:
        json.id == mInfo.id

        json.images instanceof Collection
        json.images.size() == numImages
        json.images.every { elements.find { e1 -> it.uid == e1.uid } }

        json.audio instanceof Collection
        json.audio.size() == numAudio
        json.audio.every { elements.find { e1 -> it.uid == e1.uid } }

        json.uploadErrors instanceof List
        json.uploadErrors.size() == errorMessages.size()
        errorMessages.every { String msg -> json.uploadErrors.find { it.contains(msg) } }

        when:
        RequestUtils.trySet(RequestUtils.MOST_RECENT_MEDIA_ELEMENTS_ONLY, true)
        json = TestUtils.objToJsonMap(mInfo)

        then:
        json.id == mInfo.id
        json.images instanceof Collection
        json.images.size() == 1
        json.audio instanceof Collection
        json.audio.size() == 1
    }
}
