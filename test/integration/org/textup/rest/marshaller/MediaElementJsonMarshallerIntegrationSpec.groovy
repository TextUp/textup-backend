package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class MediaElementJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling element"() {
        given:
        MediaElement el1 = TestUtils.buildMediaElement()

        when: "element with only send version"
        Map json = TestUtils.objToJsonMap(el1)

        then:
        json.uid == el1.uid
        json.whenCreated == el1.whenCreated.toString()
        json.versions.size() == 1

        when: "element with array of display versions"
        MediaElementVersion altVersion = TestUtils.buildMediaElementVersion()
        el1.addToAlternateVersions(altVersion)

        json = TestUtils.objToJsonMap(el1)

        then:
        json.uid == el1.uid
        json.whenCreated == el1.whenCreated.toString()
        json.versions.size() == 2
    }
}
