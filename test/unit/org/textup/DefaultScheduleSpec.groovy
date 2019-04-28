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
class DefaultScheduleSpec extends Specification {

    void "test creation"() {
        when:
        DefaultSchedule dSched1 = DefaultSchedule.create()

        then:
        dSched1.isAvailableNow() == true
        dSched1.nextAvailable() == null
        dSched1.nextUnavailable() == null
        dSched1.id == null
        dSched1.manual == DefaultSchedule.DEFAULT_MANUAL
        dSched1.manualIsAvailable == DefaultSchedule.DEFAULT_MANUAL_IS_AVAILABLE

        when:
        Map localIntMap = dSched1.getAllAsLocalIntervals()

        then:
        ScheduleUtils.DAYS_OF_WEEK.every { localIntMap.containsKey(it) }
        localIntMap.values().every { it == [] }
    }
}
