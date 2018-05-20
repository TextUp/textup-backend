package org.textup.rest

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.Staff
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class NotificationStatusSpec extends Specification {

    void "test constraints"() {
        when: "we have all null fields"
        NotificationStatus stat1 = new NotificationStatus()

        then: "invalid"
        stat1.validate() == false

        when: "we fill in null fields"
        stat1.staff = new Staff()
        stat1.canNotify = true
        stat1.isAvailableNow = true

        then: "valid"
        stat1.validate() == true
    }
}
