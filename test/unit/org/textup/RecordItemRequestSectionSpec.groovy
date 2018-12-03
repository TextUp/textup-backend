package org.textup

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.test.*
import org.textup.util.*
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class RecordItemRequestSectionSpec extends Specification {

    void "test default values"() {
        when:
        RecordItemRequestSection rSection = new RecordItemRequestSection()

        then:
        rSection.phoneName == ""
        rSection.contactNames == []
        rSection.tagNames == []
        rSection.sharedContactNames == []
        rSection.recordItems == []
    }
}
