package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class MergeGroupJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        MergeGroupItem mItem1 = MergeGroupItem.create(TestUtils.randPhoneNumber(), [ipr1.id])
        MergeGroup mGroup1 = MergeGroup.tryCreate(ipr2.id, [mItem1]).payload

        when:
        Map json = TestUtils.objToJsonMap(mGroup1)

        then:
        json.id == ipr2.id
        json.name == ipr2.name
        json.note == ipr2.note
        json.numbers instanceof Collection
        json.numbers.size() == ipr2.numbers.size()
        json.merges instanceof Collection
        json.merges.size() == mGroup1.possibleMerges.size()
        json.merges[0].mergeBy == mItem1.number.prettyPhoneNumber
    }
}
