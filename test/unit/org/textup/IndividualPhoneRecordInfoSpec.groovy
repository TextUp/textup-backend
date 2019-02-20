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
class IndividualPhoneRecordInfoSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        given:
        Long id = TestUtils.randIntegerUpTo(88)
        String name = TestUtils.randString()
        String note = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()

        when:
        IndividualPhoneRecordInfo iprInfo = IndividualPhoneRecordInfo.create(id, name, note, [pNum1, pNum2])

        then:
        iprInfo.id == id
        iprInfo.name == name
        iprInfo.note == note
        pNum1 in iprInfo.numbers
        pNum2 in iprInfo.numbers
    }

    void "test collection cannot be modified"() {
        given:
        IndividualPhoneRecordInfo iprInfo = IndividualPhoneRecordInfo.create(null, null, null, [])

        when:
        iprInfo.numbers << TestUtils.randPhoneNumber()

        then:
        thrown UnsupportedOperationException
    }
}
