package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class GroupPhoneRecordJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling"() {
        given:
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        gpr1.members.addToPhoneRecords(ipr1)

        FutureMessage fMsg1 = TestUtils.buildFutureMessage(gpr1.record)

    	when:
    	Map json = TestUtils.objToJsonMap(gpr1)

    	then:
        json.futureMessages instanceof Collection
        json.futureMessages.size() == 1
        json.futureMessages[0].id == fMsg1.id
        json.hexColor == gpr1.hexColor
        json.id == gpr1.id
        json.language == gpr1.record.language.toString()
        json.lastRecordActivity == gpr1.record.lastRecordActivity.toString()
        json.name == gpr1.name
        json.numMembers == 1
        json.phone == gpr1.phone.id
    }
}
