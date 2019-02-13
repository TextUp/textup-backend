package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class TupleSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation and getting properties"() {
        when: "via constructor"
        Tuple<Integer, String> tuple = new Tuple(88, "hi")

        then:
        tuple.first instanceof Integer
        tuple.second instanceof String

        when: "via static factory method"
        tuple = Tuple.create(88, "hi")

        then:
        tuple.first instanceof Integer
        tuple.second instanceof String
    }

    void "testing splitting"() {
        given:
        String val1 = TestUtils.randString()
        String val2 = TestUtils.randString()
        String val3 = TestUtils.randString()
        String val4 = TestUtils.randString()
        Tuple tup1 = Tuple.create(val1, val2)
        Tuple tup2 = Tuple.create(val3, val4)

        String retVal = TestUtils.randString()
        def arg1, arg2
        Closure action = { a1, a2 ->
            arg1 = a1
            arg2 = a2
            retVal
        }

        when: "single"
        def outcome1 = Tuple.split(tup1, action)

        then:
        outcome1 == retVal
        arg1 == tup1.first
        arg2 == tup1.second

        when: "multiple"
        Collection tuples = [tup2, tup1]
        def outcome2 = Tuple.split(tuples, action)

        then:
        outcome2 == retVal
        arg1 == tuples*.first
        arg2 == tuples*.second
    }

    void "test checking if both are present"() {
        given:
        String val1 = TestUtils.randString()
        String val2 = TestUtils.randString()

        when:
        Tuple tup1 = Tuple.create(null, null)

        then:
        tup1.checkBothPresent().status == ResultStatus.BAD_REQUEST

        when:
        tup1 = Tuple.create(val1, val2)

        then:
        tup1.checkBothPresent().status == ResultStatus.OK
    }
}
