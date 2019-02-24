package org.textup.util

import grails.util.Holders
import groovy.json.*
import java.util.concurrent.*
import org.apache.http.client.methods.*
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

class DataFormatUtilsIntegrationSpec extends Specification {

    void "test converting object to xml string"() {
        given:
        Map seedData = [hello: [1, 2, 3], goodbye: "hello"]

        expect:
        DataFormatUtils.toXmlString(seedData) ==
            '<?xml version="1.0" encoding="UTF-8"?><map><entry key="hello"><integer>1</integer><integer>2</integer><integer>3</integer></entry><entry key="goodbye">hello</entry></map>'
    }

    void "test converting object to json string uses custom marshallers when possible"() {
        when: "object does not have custom marshaller"
        Map seedData = [hello: [1, 2, 3], goodbye: "hello"]
        String jsonString = DataFormatUtils.toJsonString(seedData)

        then:
        jsonString == '{"hello":[1,2,3],"goodbye":"hello"}'

        when: "object DOES have custom marshaller"
        jsonString = DataFormatUtils.toJsonString(new MediaInfo())

        then: "custom marshaller is used"
        jsonString.contains('"images":')
        jsonString.contains('"audio":')
    }

    void "test converting json input to groovy object"() {
        when: "null input"
        Object resObj = DataFormatUtils.jsonToObject(null)

        then:
        resObj == null

        when: "invalid json string"
        resObj = DataFormatUtils.jsonToObject("i am not a valid json string]]")

        then:
        thrown JsonException

        when: "input is a json string"
        resObj = DataFormatUtils.jsonToObject('{"hello":"world"}')

        then:
        resObj == [hello: "world"]

        when: "input is an object"
        Map obj = [test: TestUtils.randString()]
        resObj = DataFormatUtils.jsonToObject(obj)

        then:
        resObj == obj
    }
}
