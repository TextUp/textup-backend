package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.*
import org.joda.time.format.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class ScheduleUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test calculating # days between two date times"() {
        given:
        DateTime now = DateTime.now()

        expect:
        ScheduleUtils.getDaysBetween(null, null) == 0
        ScheduleUtils.getDaysBetween(now, null) == 0
        ScheduleUtils.getDaysBetween(null, now) == 0
        ScheduleUtils.getDaysBetween(now, now) == 0
        ScheduleUtils.getDaysBetween(now.plusDays(3), now) == -3
        ScheduleUtils.getDaysBetween(now, now.plusDays(8)) == 8
    }

    void "test build interval from strings"() {
        given:
        DateTime start = DateTime.now().withHourOfDay(6) // avoid midnight errors
        DateTime end = start.plusHours(1)
        int daysToAdd = TestUtils.randIntegerUpTo(88, true)
        DateTimeFormatter dtf = JodaUtils.CURRENT_TIME_FORMAT

        when:
        Interval interval = ScheduleUtils.buildIntervalFromStrings(dtf, DateTimeZone.UTC,
            [dtf.print(start), dtf.print(end)], daysToAdd)

        then:
        Minutes.minutesBetween(interval.start, start.plusDays(daysToAdd)).get() < 1
        Minutes.minutesBetween(interval.end, end.plusDays(daysToAdd)).get() < 1
    }

    void "test validating interval strings"() {
        given:
        DateTime start = DateTime.now().withHourOfDay(6)
        DateTime end = start.plusHours(1)
        DateTimeFormatter dtf = DateTimeFormat.forPattern(ScheduleUtils.TIME_FORMAT).withZoneUTC()

        expect:
        ScheduleUtils.validateIntervalsString(null) == false
        ScheduleUtils.validateIntervalsString("") == false
        ScheduleUtils.validateIntervalsString(TestUtils.randString()) == false
        ScheduleUtils.validateIntervalsString(dtf.print(end)) == false
        ScheduleUtils.validateIntervalsString(ScheduleUtils.TIME_DELIMITER) == false
        ScheduleUtils
            .validateIntervalsString(dtf.print(end) + ScheduleUtils.TIME_DELIMITER + dtf.print(start)) == false

        and:
        ScheduleUtils
            .validateIntervalsString(dtf.print(start) + ScheduleUtils.TIME_DELIMITER + dtf.print(end)) == true
    }

    void "test parsing strings into intervals with UTC time zone"() {
        given:
        DateTime start = DateTime.now().withHourOfDay(6)
        DateTime end = start.plusHours(1)
        DateTimeFormatter dtf = DateTimeFormat.forPattern(ScheduleUtils.TIME_FORMAT).withZoneUTC()

        int daysToAdd = TestUtils.randIntegerUpTo(88, true)
        String str1 = dtf.print(start) + ScheduleUtils.REST_DELIMITER + dtf.print(end)
        String invalid1 = TestUtils.randString()

        when: "some invalid"
        Result res = ScheduleUtils.parseIntervalStringsToUTCIntervals([str1, invalid1], daysToAdd)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages == ["scheduleUtils.invalidRestTimeFormat"]

        when:
        res = ScheduleUtils.parseIntervalStringsToUTCIntervals([null, str1, null], daysToAdd)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof List
        res.payload.size() == 1 // nulls are ignored
        Minutes.minutesBetween(res.payload[0].start, start.plusDays(daysToAdd)).get() < 1
        Minutes.minutesBetween(res.payload[0].end, end.plusDays(daysToAdd)).get() < 1
    }
}
