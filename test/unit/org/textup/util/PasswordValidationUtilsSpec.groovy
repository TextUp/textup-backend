package org.textup.util

import grails.plugin.springsecurity.SpringSecurityService
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.validation.ValidationErrors
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class PasswordValidationUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    void "at least one upper case"() {
        expect:
        checkAtLeastOneUpperCase(null) == false
        checkAtLeastOneUpperCase("") == false

        checkAtLeastOneUpperCase("as") == false
        checkAtLeastOneUpperCase("a!@0dk3*()#!@?") == false

        checkAtLeastOneUpperCase("XrccPXLcyb") == true
        checkAtLeastOneUpperCase("a!@0dk3*()#!@?A") == true
    }

    void "at least one lower case"() {
        expect:
        checkAtLeastOneUpperCase(null) == false
        checkAtLeastOneUpperCase("") == false

        checkAtLeastOneUpperCase("AS") == false
        checkAtLeastOneUpperCase("1") == false
        checkAtLeastOneUpperCase("SDF!@#412") == false

        checkAtLeastOneUpperCase("XrccPXLcyb") == true
        checkAtLeastOneUpperCase("a") == true
        checkAtLeastOneUpperCase("SDF!@#412a") == true
        checkAtLeastOneUpperCase("a!@0dk3*()#!@?A") == true
    }

    void "at least one number"() {
        expect:
        checkAtLeastOneUpperCase(null) == false
        checkAtLeastOneUpperCase("") == false

        checkAtLeastOneUpperCase("AS") == false
        checkAtLeastOneUpperCase("one") == false
        checkAtLeastOneUpperCase("SDjk%#@?") == false
        checkAtLeastOneUpperCase("XrccPXLcyb") == false

        checkAtLeastOneUpperCase("SDF!@#412a") == true
        checkAtLeastOneUpperCase("a!@0dk3*()#!@?A") == true
        checkAtLeastOneUpperCase("1") == true
        checkAtLeastOneUpperCase("13") == true
    }

    void "at least one non-alphanumeric"() {
        expect:
        checkAtLeastOneUpperCase(null) == false
        checkAtLeastOneUpperCase("") == false

        checkAtLeastOneUpperCase("AS") == false
        checkAtLeastOneUpperCase("one") == false
        checkAtLeastOneUpperCase("XrccPXLcyb") == false
        checkAtLeastOneUpperCase("1") == false
        checkAtLeastOneUpperCase("13") == false

        checkAtLeastOneUpperCase("SDF!@#412a") == true
        checkAtLeastOneUpperCase("SDjk%#@?") == true
        checkAtLeastOneUpperCase("a!@0dk3*()#!@?A") == true
    }

    void "check minimum length"() {
        expect:
        checkPasswordLength(null,null) == false
        checkPasswordLength(null,3) == false
        checkPasswordLength("1jfkds",null) == false
        checkPasswordLength("",1) == false
        checkPasswordLength("",0) == true

        checkPasswordLength("1",1) == true
        checkPasswordLength("a!@0dk3*()#!@?A",5) == true
        checkPasswordLength("a!@0dk3*()#!@?A",10) == true
        checkPasswordLength("a!@0dk3*()#!@?A",15) == true


        checkPasswordLength("1",2) == false
        checkPasswordLength("a!@0dk3*()#!@?A",16) == false
    }
}
