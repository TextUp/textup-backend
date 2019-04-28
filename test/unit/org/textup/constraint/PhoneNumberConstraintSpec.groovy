package org.textup.constraint

import org.textup.*
import org.textup.test.*
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import grails.validation.Validateable
import org.codehaus.groovy.grails.validation.exceptions.ConstraintException
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class PhoneNumberConstraintSpec extends Specification {

    static final String INVALID_PARAM_VALUE = "not a boolean"

    void "test constraint with invalid param value"() {
        given:
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()
        InvalidParam obj = new InvalidParam(numberAsString: TestUtils.randPhoneNumberString())

        when:
        obj.validate()

        then:
        thrown ConstraintException
        stdErr.toString().contains(INVALID_PARAM_VALUE)

        cleanup:
        TestUtils.restoreAllStreams()
    }

    void "test when property the constraint is applied to is an unsupported type"() {
        when:
        InvalidPropertyType obj = new InvalidPropertyType(numberAsNumber: 88l)

        then: "if property isn't a string then constraint does nothing"
        obj.validate()
        notThrown ConstraintException
    }

    void "test when not a phone number"() {
        when:
        NotPhoneNumber obj = new NotPhoneNumber(numberAsString: TestUtils.randString())

        then: "passing false to constraint makes it do nothing"
        obj.validate()
    }

    void "test when IS a phone number for a class that allows nullable and blank withOUT proper param"() {
        when: "null"
        HasPhoneNumberAllowEmptyFails obj = new HasPhoneNumberAllowEmptyFails(numberAsString: null)

        then: "tolerates nulls, if specified"
        obj.validate()

        when: "blank"
        obj = new HasPhoneNumberAllowEmptyFails(numberAsString: "")

        then: "empty string (blank) is still a specified string and this is not a phone number so should fail"
        obj.validate() == false

        when: "filled out invalid"
        obj = new HasPhoneNumberAllowEmptyFails(numberAsString: TestUtils.randString())

        then:
        obj.validate() == false

        when: "filled out valid"
        obj = new HasPhoneNumberAllowEmptyFails(numberAsString: TestUtils.randPhoneNumberString())

        then:
        obj.validate()
    }

    void "test when IS a phone number for a class that allows nullable and blank WITH proper param"() {
        when: "null"
        HasPhoneNumberAllowEmptySucceeds obj = new HasPhoneNumberAllowEmptySucceeds(numberAsString: null)

        then: "tolerates nulls, if specified"
        obj.validate()

        when: "blank"
        obj = new HasPhoneNumberAllowEmptySucceeds(numberAsString: "")

        then: "empty string (blank) is still a specified string and this is not a phone number so should fail"
        obj.validate()

        when: "filled out invalid"
        obj = new HasPhoneNumberAllowEmptySucceeds(numberAsString: TestUtils.randString())

        then:
        obj.validate() == false

        when: "filled out valid"
        obj = new HasPhoneNumberAllowEmptySucceeds(numberAsString: TestUtils.randPhoneNumberString())

        then:
        obj.validate()
    }

    void "test when IS a phone number for a class that forbids nullable and blank"() {
        when: "null"
        HasPhoneNumberForbidEmpty obj = new HasPhoneNumberForbidEmpty(numberAsString: null)

        then: "tolerates nulls, if specified"
        obj.validate() == false

        when: "blank"
        obj = new HasPhoneNumberForbidEmpty(numberAsString: "")

        then: "tolerates blanks, if specified"
        obj.validate() == false

        when: "filled out invalid"
        obj = new HasPhoneNumberForbidEmpty(numberAsString: TestUtils.randString())

        then:
        obj.validate() == false

        when: "filled out valid"
        obj = new HasPhoneNumberForbidEmpty(numberAsString: TestUtils.randPhoneNumberString())

        then:
        obj.validate()
    }

    @Validateable
    protected class InvalidParam {
        String numberAsString

        static constraints = {
            numberAsString phoneNumber: PhoneNumberConstraintSpec.INVALID_PARAM_VALUE
        }
    }

    @Validateable
    protected class InvalidPropertyType {
        Long numberAsNumber

        static constraints = {
            numberAsNumber phoneNumber: true
        }
    }

    @Validateable
    protected class NotPhoneNumber {
        String numberAsString

        static constraints = {
            numberAsString phoneNumber: false
        }
    }

    @Validateable
    protected class HasPhoneNumberAllowEmptyFails {
        String numberAsString

        static constraints = {
            numberAsString nullable: true, blank: true, phoneNumber: true
        }
    }

    @Validateable
    protected class HasPhoneNumberAllowEmptySucceeds {
        String numberAsString

        static constraints = {
            numberAsString nullable: true, blank: true, phoneNumber: PhoneNumberConstraint.PARAM_ALLOW_BLANK
        }
    }

    @Validateable
    protected class HasPhoneNumberForbidEmpty {
        String numberAsString

        static constraints = {
            numberAsString phoneNumber: true
        }
    }
}
