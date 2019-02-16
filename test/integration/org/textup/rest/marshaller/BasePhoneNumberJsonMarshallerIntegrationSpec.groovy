package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class BasePhoneNumberJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling phone number"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        when:
        Map json = TestUtils.objToJsonMap(pNum1)

        then:
        json.e164Number == pNum1.e164PhoneNumber
        json.noFormatNumber == pNum1.number
        json.number == pNum1.prettyPhoneNumber
    }

    void "test marshalling `AvailablePhoneNumber`"() {
        given:
        AvailablePhoneNumber sidNum = AvailablePhoneNumber
            .tryCreateExisting(TestUtils.randPhoneNumberString(), TestUtils.randString())
            .payload
        AvailablePhoneNumber regionNum = AvailablePhoneNumber
            .tryCreateNew(TestUtils.randPhoneNumberString(), TestUtils.randString(), TestUtils.randString())
            .payload

        when: "a number with a sid"
        Map json = TestUtils.objToJsonMap(sidNum)

        then:
        json.e164Number == sidNum.e164PhoneNumber
        json.noFormatNumber == sidNum.number
        json.number == sidNum.prettyPhoneNumber
        json[sidNum.infoType] == sidNum.info

        when: "a number with a region"
        json = TestUtils.objToJsonMap(regionNum)

        then:
        json.e164Number == regionNum.e164PhoneNumber
        json.noFormatNumber == regionNum.number
        json.number == regionNum.prettyPhoneNumber
        json[regionNum.infoType] == regionNum.info
    }
}
