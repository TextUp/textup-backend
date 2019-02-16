package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class NotificationDetailJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        NotificationDetail nd1 = NotificationDetail.tryCreate(ipr1.toWrapper()).payload
        NotificationDetail nd2 = NotificationDetail.tryCreate(gpr1.toWrapper()).payload

        when:
        Map json = TestUtils.objToJsonMap(nd1)

        then:
        json.items instanceof Collection
        json.items.isEmpty()
        json.name == ipr1.name
        json.tag == null
        json.contact == ipr1.id

        when:
        json = TestUtils.objToJsonMap(nd2)

        then:
        json.items instanceof Collection
        json.items.isEmpty()
        json.name == gpr1.name
        json.tag == gpr1.id
        json.contact == null
    }
}
