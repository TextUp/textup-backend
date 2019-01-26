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
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

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

    void "test building initials"() {
        expect:
        StringUtils.buildInitials(null) == null
        StringUtils.buildInitials("") == ""
        StringUtils.buildInitials("Hello there") == "H.T."
        StringUtils.buildInitials("  Hello 626 123 3920 okay!!") == "H.O."
        StringUtils.buildInitials("""    Hello 626 123
                yes
            all right
            3920 @@okay!!
        """) == "H.Y.A.R.@."
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
