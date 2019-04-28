package org.textup.rest.marshaller

import grails.converters.JSON
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
        JSON.use(MarshallerUtils.MARSHALLER_DEFAULT) {
            DataFormatUtils.jsonToObject(new JSON(pNum1).toString())
        }

        // Recent updates have made standalone string values also valid JSON. Grails's implementation
        // expects all marshallers to return an object or an array, but centralizing
        // the json conversion of of phone numbers in this way works in practice but not in testing
        // Therefore, we just catch the exception and make sure that the returned value is
        // the string value we expect.
        // see: https://stackoverflow.com/a/7487892
        then:
        RuntimeException exception = thrown()
        exception.cause instanceof org.codehaus.groovy.grails.web.converters.exceptions.ConverterException
        exception.message.contains(pNum1.toString())
    }

    void "test marshalling `ContactNumber`"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        ContactNumber cNum1 = new ContactNumber(preference: 0, number: pNum1.number)

        when:
        Map json = TestUtils.objToJsonMap(cNum1)

        then:
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
        json.number == sidNum.prettyPhoneNumber
        json[sidNum.infoType] == sidNum.info

        when: "a number with a region"
        json = TestUtils.objToJsonMap(regionNum)

        then:
        json.number == regionNum.prettyPhoneNumber
        json[regionNum.infoType] == regionNum.info
    }
}
