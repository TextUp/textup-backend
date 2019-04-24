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

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test cleaning phone number"() {
        expect:
        StringUtils.cleanPhoneNumber(null) == null
        StringUtils.cleanPhoneNumber("1112223333") == "1112223333"
        StringUtils.cleanPhoneNumber("881asdaf@()#@!(@#)9301223") == "8819301223"
        StringUtils.cleanPhoneNumber("8 abc @# 88") == "888"
        StringUtils.cleanPhoneNumber("12223338888") == "2223338888" // strip US country code
    }

    void "test cleaning username"() {
        expect:
        StringUtils.cleanUsername(null) == null
        StringUtils.cleanUsername("   hiTHERE ") == "hithere"
        StringUtils.cleanUsername("hello!@#123") == "hello!@#123"
    }

    void "test case insensitive equals"() {
        expect:
        StringUtils.equalsIgnoreCase("HIII", "hiii") == true
        StringUtils.equalsIgnoreCase(null, null) == true
        StringUtils.equalsIgnoreCase("hIiI", "hiii") == true
        StringUtils.equalsIgnoreCase("h@@IiI123", "h@@iii123") == true

        StringUtils.equalsIgnoreCase("h@@IiI123  ", "  h@@iii123") == false
        StringUtils.equalsIgnoreCase("yes", "y  es") == false
        StringUtils.equalsIgnoreCase(null, "hiii") == false
        StringUtils.equalsIgnoreCase("  HIII", "hiii") == false
    }

    void "test formatting string as SQL query"() {
        expect:
        StringUtils.toQuery(["HELLO"]) == "%[HELLO]%"
        StringUtils.toQuery("yEsS") == "%yEsS%"
        StringUtils.toQuery(null) == ""
        StringUtils.toQuery("%hi") == "%hi%"
        StringUtils.toQuery("hi%") == "%hi%"
        StringUtils.toQuery("%hi%") == "%hi%"
    }

    void "test building initials"() {
        expect:
        StringUtils.buildInitials(null) == null
        StringUtils.buildInitials("") == ""
        StringUtils.buildInitials("Hello there") == "H.T."
        StringUtils.buildInitials("  Hello 626 123 392 okay!!") == "H.O."
        StringUtils.buildInitials("  Hello 626 123 3920 okay!!") == "${StringUtils.PHONE_NUMBER_INITIALS_PREFIX} 626"
        StringUtils.buildInitials("""    Hello 626 123
                yes
            all right
            @@okay!!
        """) == "H.Y.A.R.@."
    }

    void "test pluralize units in phrase"() {
        given:
        String word = TestUtils.randString()

        expect:
        StringUtils.pluralize(-88, word) == "-88 ${word}s"
        StringUtils.pluralize(-1, word) == "-1 ${word}s"
        StringUtils.pluralize(0, word) == "0 ${word}s"
        StringUtils.pluralize(1, word) == "1 ${word}"
        StringUtils.pluralize(88, word) == "88 ${word}s"
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
