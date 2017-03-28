package org.textup.validator

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@TestMixin(GrailsUnitTestMixin)
class ImageInfoSpec extends Specification {

    void "test constraints"() {
        when: "we have all empty fields"
        ImageInfo imageInfo = new ImageInfo()

        then:
        imageInfo.validate() == false
        imageInfo.errors.errorCount == 2

        when: "we have an invalid link"
        imageInfo.key = UUID.randomUUID().toString()
        imageInfo.link = "not really a link"

        then:
        imageInfo.validate() == false
        imageInfo.errors.errorCount == 1
        imageInfo.errors.getFieldErrorCount("link") == 1

        when: "we have all valid fields"
        imageInfo.link = "https://www.test.com"

        then:
        imageInfo.validate() == true
    }
}