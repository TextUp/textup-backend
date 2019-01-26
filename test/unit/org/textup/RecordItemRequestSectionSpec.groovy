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
class RecordItemRequestSectionSpec extends Specification {

    void "test default values"() {
        when:
        RecordItemRequestSection rSection = new RecordItemRequestSection()

        then:
        rSection.phoneName == ""
        rSection.phoneNumber == ""
        rSection.contactNames == []
        rSection.tagNames == []
        rSection.sharedContactNames == []
        rSection.recordItems == []
    }
}
