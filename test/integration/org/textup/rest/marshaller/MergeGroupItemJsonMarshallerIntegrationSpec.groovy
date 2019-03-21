package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class MergeGroupItemJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        MergeGroupItem mItem1 = MergeGroupItem.create(pNum1, [ipr1.id])

        when:
        Map json = TestUtils.objToJsonMap(mItem1)

        then:
        json.mergeBy == mItem1.number.prettyPhoneNumber
        json.mergeWith instanceof Collection
        json.mergeWith.size() == 1
        json.mergeWith[0].id == ipr1.toInfo().id
    }
}
