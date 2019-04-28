package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class ValidationUtilsSpec extends Specification {

    void "test validating lock code"() {
        expect:
        ValidationUtils.isValidLockCode(null) == false
        ValidationUtils.isValidLockCode("") == false
        ValidationUtils.isValidLockCode("absd") == false
        ValidationUtils.isValidLockCode("123324") == false
        ValidationUtils.isValidLockCode("  8888") == false
        ValidationUtils.isValidLockCode("8888") == true
    }

    void "test validating valid characters for Pusher"() {
        expect:
        ValidationUtils.isValidForPusher(null) == false
        ValidationUtils.isValidLockCode("") == false
        ValidationUtils.isValidForPusher("   abc!!!") == false
        ValidationUtils.isValidForPusher("   abc") == false
        ValidationUtils.isValidForPusher("abc-123@@=") == true
    }

    void "test validating hex color code"() {
        expect:
        ValidationUtils.isValidHexCode(null) == false
        ValidationUtils.isValidLockCode("") == false
        ValidationUtils.isValidHexCode("asf") == false
        ValidationUtils.isValidHexCode("#1293") == false
        ValidationUtils.isValidHexCode("#asf") == false
        ValidationUtils.isValidHexCode("#abf") == true
        ValidationUtils.isValidHexCode("#ABf") == true
        ValidationUtils.isValidHexCode("#A88") == true
        ValidationUtils.isValidHexCode("#888") == true
        ValidationUtils.isValidHexCode("#88ABC8") == true
    }
}
