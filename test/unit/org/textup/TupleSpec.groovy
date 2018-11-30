package org.textup

import org.textup.test.*
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class TupleSpec extends Specification {

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
}
