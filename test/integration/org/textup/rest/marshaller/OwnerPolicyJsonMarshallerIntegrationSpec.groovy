package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class OwnerPolicyJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling owner policy"() {
        given:
        OwnerPolicy op1 = TestUtils.buildOwnerPolicy()

        when:
        Map json = TestUtils.objToJsonMap(op1)

        then:
        json.frequency == op1.frequency.toString()
        json.level == op1.level.toString()
        json.method == op1.method.toString()
        json.name == op1.staff.name
        json.schedule instanceof Map
        json.schedule.id != null
        json.shouldSendPreviewLink == op1.shouldSendPreviewLink
        json.staffId == op1.staff.id
    }

    void "test marshalling default owner policy"() {
        given:
        Staff s1 = TestUtils.buildStaff()
        ReadOnlyOwnerPolicy rop1 = DefaultOwnerPolicy.create(s1)

        when:
        Map json = TestUtils.objToJsonMap(rop1)

        then:
        json.frequency == rop1.frequency.toString()
        json.level == rop1.level.toString()
        json.method == rop1.method.toString()
        json.name == s1.name
        json.schedule instanceof Map
        json.schedule.id == null
        json.shouldSendPreviewLink == rop1.shouldSendPreviewLink
        json.staffId == s1.id
    }
}
