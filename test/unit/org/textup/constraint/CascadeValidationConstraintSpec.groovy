package org.textup.constraint

import org.textup.*
import org.textup.test.*
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import grails.validation.Validateable
import org.codehaus.groovy.grails.validation.exceptions.ConstraintException
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class CascadeValidationConstraintSpec extends Specification {

    static final String INVALID_PARAM_VALUE = "not a boolean but needs to be one"

    void "test error when cascading property is not validateable"() {
        when: "a single association"
        InvalidSingle invalidSingle = new InvalidSingle(address: "hi")

        then: "`cascadeValidation: true` ignored because `supports` returns false"
        invalidSingle.validate()

        when: "a collection"
        InvalidList invalidList = new InvalidList(address: ["hi", "there"])
        invalidList.validate()

        then:
        thrown NoSuchMethodException
    }

    void "test error when cascading constraint parameter is not a boolean"() {
        given:
        ByteArrayOutputStream stdErr = TestUtils.captureAllStreamsReturnStdErr()

        when:
        InvalidParam invalidParam = new InvalidParam(address: ["hi", "there"])
        invalidParam.validate()

        then: "ConstraintException wrapping IllegalArgumentException"
        thrown ConstraintException
        stdErr.toString().contains(INVALID_PARAM_VALUE)

        cleanup:
        TestUtils.restoreAllStreams()
    }

    void "test skipping if constraint parameter value is set to false"() {
        when:
        ValidateableChild vChild = new ValidateableChild()
        NotValidateableParent notVParent = new NotValidateableParent(child: vChild)

        then:
        vChild.validate() == false
        notVParent.validate() == true
    }

    void "test validating for a single validateable association"() {
        given:
        ValidateableParent vParent = new ValidateableParent()

        when: "association is null"
        vParent.child = null

        then: "cascading is skipped"
        vParent.validate() == true

        when: "child is invalid"
        vParent.child = new ValidateableChild()

        then: "parent is invalid"
        vParent.validate() == false
        vParent.errors.getFieldErrorCount("child.name") == 1

        when: "child is made valid"
        vParent.child.name = "Ting Ting"

        then: "parent is valid too"
        vParent.validate() == true
    }

    void "test validating for a collection of validateable objects"() {
        given:
        ValidateableParent vParent = new ValidateableParent()

        when: "association is null"
        vParent.childList = null

        then: "cascading is skipped"
        vParent.validate() == true

        when: "add collection of two items, both invalid"
        vParent.childList = [new ValidateableChild(), new ValidateableChild()]

        then: "parent is invalid"
        vParent.validate() == false
        vParent.errors.getFieldErrorCount("childList.0.name") == 1
        vParent.errors.getFieldErrorCount("childList.1.name") == 1

        when: "second item is made valid"
        vParent.childList[1].name = "Kiki"

        then: "first item still makes parent invalid"
        vParent.validate() == false
        vParent.errors.getFieldErrorCount("childList.0.name") == 1
        vParent.errors.getFieldErrorCount("childList.1.name") == 0

        when: "first item is made valid"
        vParent.childList[0].name = "Ting Ting"

        then: "parent becomes valid too"
        vParent.validate() == true
    }

    // Test support classes
    // --------------------

    @Validateable
    protected class InvalidSingle {
        String address

        static constraints = {
            address cascadeValidation: true
        }
    }

    @Validateable
    protected class InvalidList {
        List<String> address

        static constraints = {
            address cascadeValidation: true
        }
    }

    @Validateable
    protected class InvalidParam {
        List<String> address

        static constraints = {
            address cascadeValidation: CascadeValidationConstraintSpec.INVALID_PARAM_VALUE
        }
    }

    @Validateable
    protected class NotValidateableParent {
        ValidateableChild child

        static constraints = { // default nullable: false
            child cascadeValidation: false
        }
    }

    @Validateable
    protected class ValidateableParent {
        ValidateableChild child
        List<ValidateableChild> childList

        static constraints = { // default nullable: false
            child cascadeValidation: true, nullable: true
            childList cascadeValidation: true, nullable: true
        }
    }

    @Validateable
    protected class ValidateableChild {
        String name

        static constraints = { // default nullable: false
            name blank: false
        }
    }
}
