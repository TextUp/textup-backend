package org.textup.rest.marshaller

import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

class ScheduleJsonMarshallerIntegrationSpec extends Specification {

    void "test marshalling schedule"() {
        given:
        String tzId = "Europe/Stockholm"
        String offsetString = TestUtils.getDateTimeOffsetString(tzId)

        Collection beforeIntStrings = ["0130:0231", "0230:0330", "0400:0430"]
        Collection afterIntStrings = ["0130:0330", "0400:0430"]

        Schedule sched1 = Schedule.tryCreate().payload
        sched1.manual = true
        sched1.updateWithIntervalStrings(monday: beforeIntStrings)
        Schedule.withSession { it.flush() }

        when:
        Map json = TestUtils.objToJsonMap(sched1)

    	then:
    	json.id == sched1.id
        json.isAvailableNow == sched1.isAvailableNow()
        json.manual == sched1.manual
        json.manualIsAvailable == sched1.manualIsAvailable
        json.nextUnavailable == null
        json.nextAvailable == null
        json.monday == afterIntStrings
        json.timezone == null

        when:
        sched1.manual = false
        RequestUtils.trySet(RequestUtils.TIMEZONE, 123)
        json = TestUtils.objToJsonMap(sched1)

        then:
        json.id == sched1.id
        json.isAvailableNow == sched1.isAvailableNow()
        json.manual == sched1.manual
        json.manualIsAvailable == sched1.manualIsAvailable
        json.nextUnavailable.contains("Z")
        json.nextAvailable.contains("Z")
        json.monday == afterIntStrings
        json.timezone == "UTC"

        when:
        RequestUtils.trySet(RequestUtils.TIMEZONE, tzId)

        json = TestUtils.objToJsonMap(sched1)

        then:
        json.id == sched1.id
        json.isAvailableNow == sched1.isAvailableNow()
        json.manual == sched1.manual
        json.manualIsAvailable == sched1.manualIsAvailable
        json.nextUnavailable.contains(offsetString)
        json.nextAvailable.contains(offsetString)
        json.monday != afterIntStrings // now offset based on timezone
        json.timezone == tzId
    }

    void "test marshalling default schedule"() {
        given:
        ReadOnlySchedule sched1 = DefaultSchedule.create()

        when:
        Map json = TestUtils.objToJsonMap(sched1)

        then:
        json.id == null
        json.isAvailableNow == sched1.isAvailableNow()
        json.manual == sched1.manual
        json.manualIsAvailable == sched1.manualIsAvailable
        json.nextUnavailable == null
        json.nextAvailable == null
        ScheduleUtils.DAYS_OF_WEEK.every { json[it] == [] }
        json.timezone == null
    }
}
