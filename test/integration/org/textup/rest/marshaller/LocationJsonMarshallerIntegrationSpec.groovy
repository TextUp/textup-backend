package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class LocationJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling location"() {
        given:
        Location loc1 = TestUtils.buildLocation()

    	when:
        Map json = TestUtils.objToJsonMap(loc1)

        then:
        json.id == loc1.id
        json.address == loc1.address
        json.lat == loc1.lat
        json.lng == loc1.lng
    }
}
