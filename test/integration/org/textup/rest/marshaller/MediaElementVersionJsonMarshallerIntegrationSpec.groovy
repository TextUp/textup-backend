package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class MediaElementVersionJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling"() {
        given:
        MediaElementVersion mVers1 = TestUtils.buildMediaElementVersion()

        when:
        Map json = TestUtils.objToJsonMap(mVers1)

        then:
        json.height == mVers1.heightInPixels
        json.link == mVers1.link.toString()
        json.type == mVers1.type.mimeType
        json.width == mVers1.widthInPixels
    }
}
