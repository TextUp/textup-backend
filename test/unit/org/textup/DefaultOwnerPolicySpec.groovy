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
class DefaultOwnerPolicySpec extends Specification {

    void "test if should ensure that all staff members have owner policy"() {
        expect:
        NotificationFrequency.values().every {
            DefaultOwnerPolicy.shouldEnsureAll(it) == (it == DefaultOwnerPolicy.DEFAULT_FREQUENCY)
        }
    }

    void "test creation"() {
        given:
        Staff mockStaff = GroovyMock()

        when:
        List defaults = DefaultOwnerPolicy.createAll(null)

        then:
        defaults == []

        when:
        defaults = DefaultOwnerPolicy.createAll([mockStaff])

        then:
        defaults.size() == 1
        defaults[0].canNotifyForAny(null) == true
        defaults[0].isAllowed(null) == true
        defaults[0].shouldSendPreviewLink == DefaultOwnerPolicy.DEFAULT_SEND_PREVIEW_LINK
        defaults[0].frequency == DefaultOwnerPolicy.DEFAULT_FREQUENCY
        defaults[0].level == DefaultOwnerPolicy.DEFAULT_LEVEL
        defaults[0].method == DefaultOwnerPolicy.DEFAULT_METHOD
        defaults[0].readOnlySchedule instanceof DefaultSchedule
        defaults[0].readOnlyStaff == mockStaff
    }
}
