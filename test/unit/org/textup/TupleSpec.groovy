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
