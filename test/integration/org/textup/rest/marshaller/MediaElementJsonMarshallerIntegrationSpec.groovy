package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.type.*
import org.textup.util.TestHelpers
import spock.lang.*

class MediaElementJsonMarshallerIntegrationSpec extends Specification {

    def grailsApplication

    void "test marshalling element"() {
        given:
        MediaElementVersion sendVersion = new MediaElementVersion(type: MediaType.IMAGE_PNG,
            versionId: TestHelpers.randString(),
            sizeInBytes: 888,
            widthInPixels: 12345,
            heightInPixels: 34567)
        assert sendVersion.validate()
        MediaElement e1 = new MediaElement(sendVersion: sendVersion)
        assert e1.validate()

        when: "element with only send version"
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(e1 as JSON)
        }

        then:
        json.uid == e1.uid
        json.versions.size() == 1
        json.versions[0] instanceof Map
        json.versions[0].type == sendVersion.type.mimeType
        json.versions[0].link.contains(sendVersion.versionId)
        json.versions[0].width == sendVersion.widthInPixels
        json.versions[0].height == sendVersion.heightInPixels

        when: "element with array of display versions"
        MediaElementVersion altVersion = new MediaElementVersion(type: MediaType.IMAGE_JPEG,
            versionId: TestHelpers.randString(),
            sizeInBytes: 888,
            widthInPixels: 12345,
            heightInPixels: 34567)
        assert altVersion.validate()
        e1.addToAlternateVersions(altVersion)
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(e1 as JSON)
        }

        then:
        json.uid == e1.uid
        json.versions.size() == 2
        json.versions.every { it instanceof Map }
        json.versions*.type.every { it in [sendVersion, altVersion]*.type*.mimeType }
        json.versions*.width.every { it in [sendVersion, altVersion]*.widthInPixels }
        json.versions*.height.every { it in [sendVersion, altVersion]*.heightInPixels }
        json.versions*.link.every { it.contains(sendVersion.versionId) || it.contains(altVersion.versionId) }
    }
}
