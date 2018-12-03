package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import grails.util.Holders
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
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class StringUtilsSpec extends Specification {

    void "test to lower case string"() {
        expect:
        StringUtils.toLowerCaseString(["HELLO"]) == "[hello]"
        StringUtils.toLowerCaseString("yEsS") == "yess"
        StringUtils.toLowerCaseString(null) == "null"
    }

    void "test formatting string as SQL query"() {
        expect:
        StringUtils.toQuery(["HELLO"]) == "%[HELLO]%"
        StringUtils.toQuery("yEsS") == "%yEsS%"
        StringUtils.toQuery(null) == "%null%"
        StringUtils.toQuery("%hi") == "%hi%"
        StringUtils.toQuery("hi%") == "%hi%"
        StringUtils.toQuery("%hi%") == "%hi%"
    }

    void "test appending strings while guaranteeing a max resulting length"() {
        expect:
        StringUtils.appendGuaranteeLength("hello", null, 1) == "h"
        StringUtils.appendGuaranteeLength(null, "yes", 1) == null
        StringUtils.appendGuaranteeLength("hello", "yes", -1) == "hello"
        StringUtils.appendGuaranteeLength("hello", "yes", 1) == "h"
        StringUtils.appendGuaranteeLength("hello", "yes", 6) == "helyes"
        StringUtils.appendGuaranteeLength("hello", "yes", 7) == "hellyes"
        StringUtils.appendGuaranteeLength("hello", "yes", 8) == "helloyes"
        StringUtils.appendGuaranteeLength("hello", "yes", 10) == "helloyes"
    }

    void "test generating random alphanumeric strings"() {
        expect:
        10.times {
            assert StringUtils.randomAlphanumericString() != StringUtils.randomAlphanumericString()
        }
    }

    void "test generating alphabet"() {
        when:
        List<String> alphabet = StringUtils.buildAlphabet()

        then:
        StringUtils.ALPHABET == alphabet
        alphabet.size() == 26 + 10
        alphabet.every { it instanceof String }
    }
}
