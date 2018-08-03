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
        MediaElementVersion mVers = new MediaElementVersion(mediaVersion: MediaVersion.SEND,
            key: UUID.randomUUID().toString(),
            sizeInBytes: 888)
        assert mVers.validate()
        MediaElement e1 = new MediaElement(type: MediaType.IMAGE, sendVersion: mVers)

        when: "element with only send version"
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(e1 as JSON)
        }

        then:
        json.uid == e1.uid
        json[MediaVersion.LARGE.displayName] instanceof Map
        json[MediaVersion.LARGE.displayName].link == mVers.getLink().toString()
        json[MediaVersion.LARGE.displayName].width == mVers.inherentWidth
        json[MediaVersion.MEDIUM.displayName] == null
        json[MediaVersion.SMALL.displayName] == null

        when: "element with array of display versions"
        MediaElementVersion dVers = new MediaElementVersion(mediaVersion: MediaVersion.MEDIUM,
            key: UUID.randomUUID().toString(),
            sizeInBytes: 888)
        assert dVers.validate()
        e1.addToDisplayVersions(dVers)
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(e1 as JSON)
        }

        then:
        json.uid == e1.uid
        json[MediaVersion.LARGE.displayName] == null
        json[MediaVersion.MEDIUM.displayName] instanceof Map
        json[MediaVersion.MEDIUM.displayName].link == dVers.getLink().toString()
        json[MediaVersion.MEDIUM.displayName].width == dVers.inherentWidth
        json[MediaVersion.SMALL.displayName] == null
    }
}
