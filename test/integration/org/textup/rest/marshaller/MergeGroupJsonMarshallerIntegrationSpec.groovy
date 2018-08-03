package org.textup.rest.marshaller

import grails.converters.JSON
import org.textup.*
import org.textup.util.*
import org.textup.validator.MergeGroup
import org.textup.validator.MergeGroupItem

class MergeGroupJsonMarshallerIntegrationSpec extends CustomSpec {

    def grailsApplication

    def setup() {
        setupIntegrationData()
    }

    def cleanup() {
        cleanupIntegrationData()
    }

    void "test marshal merge group"() {
        given:
        MergeGroupItem mItem1 = new MergeGroupItem(contactIds:[c1_1.id], numberAsString:"1112223333")
        MergeGroupItem mItem2 = new MergeGroupItem(contactIds:[c1_2.id], numberAsString:"1112223333")
        MergeGroup mGroup = new MergeGroup(targetContactId:c1.id, possibleMerges:[mItem1, mItem2])
        assert mGroup.deepValidate() == true

        when:
        Map json
        JSON.use(grailsApplication.config.textup.rest.defaultLabel) {
            json = TestHelpers.jsonToMap(mGroup as JSON)
        }

        then:
        json.id == c1.id
        json.name == c1.name
        json.note == c1.note
        json.numbers.every { c1.numbers.find { ContactNumber num -> num.prettyPhoneNumber == it.number } }
        [mItem1, mItem2].every { MergeGroupItem item1 ->
            json.merges.find { merge1 ->
                merge1.mergeBy == item1.getNumber().prettyPhoneNumber &&
                    merge1.mergeWith.every { mergeWithMap ->
                        item1.getMergeWith().find { Contact c3 -> c3.id == mergeWithMap.id }
                    }
            }
        }
        // assert json.merges format
        json.merges.every { merge1 ->
            merge1.mergeBy instanceof String && merge1.mergeWith instanceof Collection &&
                merge1.mergeWith.every { it.id instanceof Number && it.numbers instanceof Collection }
        }
    }
}
