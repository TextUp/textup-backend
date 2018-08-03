package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import grails.validation.Validateable
import org.codehaus.groovy.grails.validation.exceptions.ConstraintException
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class CascadeValidationConstraintSpec extends Specification {

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
        when:
        InvalidParam invalidParam = new InvalidParam(address: ["hi", "there"])
        invalidParam.validate()

        then: "ConstraintException wrapping IllegalArgumentException"
        thrown ConstraintException
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
            address cascadeValidation: "not a boolean but needs to be one"
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
