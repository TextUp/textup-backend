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
class LocalIntervalUtilsSpec extends Specification {

    void "test get day of week index"() {
        expect:
        LocalIntervalUtils.getDayOfWeekIndex(-8) == -1
        LocalIntervalUtils.getDayOfWeekIndex(-7) == 0
        LocalIntervalUtils.getDayOfWeekIndex(-1) == -1
        LocalIntervalUtils.getDayOfWeekIndex(0) == 0
        LocalIntervalUtils.getDayOfWeekIndex(5) == 5
        LocalIntervalUtils.getDayOfWeekIndex(7) == 0
        LocalIntervalUtils.getDayOfWeekIndex(8) == 1
    }

    void "test determining if at start or end of day"() {
        given:
        DateTime dayStart = DateTime.now()
            .hourOfDay().withMinimumValue()
            .minuteOfHour().withMinimumValue()
            .secondOfMinute().withMinimumValue()
        DateTime midDay = DateTime.now().withHourOfDay(6)
        DateTime dayEnd = DateTime.now()
            .hourOfDay().withMaximumValue()
            .minuteOfHour().withMaximumValue()
            .secondOfMinute().withMaximumValue()

        expect:
        LocalIntervalUtils.isEndOfDay(dayStart) == false
        LocalIntervalUtils.isEndOfDay(midDay) == false
        LocalIntervalUtils.isEndOfDay(dayEnd) == true

        and:
        LocalIntervalUtils.isStartOfDay(dayStart) == true
        LocalIntervalUtils.isStartOfDay(midDay) == false
        LocalIntervalUtils.isStartOfDay(dayEnd) == false
    }

    void "test dehydrating local intervals"() {
        given:
        DateTimeFormatter formatter = DateTimeFormat.forPattern(ScheduleUtils.TIME_FORMAT).withZoneUTC()
        LocalTime start = LocalTime.now()
        LocalTime end = start.plusMinutes(10)
        LocalInterval lInt1 = new LocalInterval(start, end)

        expect:
        LocalIntervalUtils.dehydrateLocalIntervals(null) == ""
        LocalIntervalUtils.dehydrateLocalIntervals([]) == ""
        LocalIntervalUtils.dehydrateLocalIntervals([null]) == ""
        LocalIntervalUtils.dehydrateLocalIntervals([lInt1]) ==
            formatter.print(start) + ScheduleUtils.TIME_DELIMITER + formatter.print(end)
        LocalIntervalUtils.dehydrateLocalIntervals([lInt1, null, lInt1]) ==
            formatter.print(start) + ScheduleUtils.TIME_DELIMITER + formatter.print(end) +
            ScheduleUtils.RANGE_DELIMITER +
            formatter.print(start) + ScheduleUtils.TIME_DELIMITER + formatter.print(end)
    }

    void "test cleaning local intervals"() {
        given:
        LocalTime start = LocalTime.now()
        LocalTime middle = start.plusMinutes(5)
        LocalTime end = start.plusMinutes(10)
        LocalInterval lInt1 = new LocalInterval(start, middle)
        LocalInterval lInt2 = new LocalInterval(middle, end)

        when: "has overlapping intervals"
        List cleaned = LocalIntervalUtils.cleanLocalIntervals([lInt1, lInt2])

        then: "merge into one"
        cleaned.size() == 1
        cleaned[0].start == start
        cleaned[0].end == end
    }

    void "test rehydrating local intervals"() {
        given:
        LocalTime time1 = LocalTime.now()
        LocalTime time2 = time1.plusMinutes(5)
        LocalTime time3 = time1.plusMinutes(10)
        LocalTime midnightBefore = LocalTime.now().millisOfDay().withMaximumValue()
        LocalTime midnightAfter = LocalTime.now().millisOfDay().withMinimumValue()
        LocalTime time4 = LocalTime.now()
        LocalInterval lInt1 = new LocalInterval(time1, time2)
        LocalInterval lInt2 = new LocalInterval(time3, midnightBefore)
        LocalInterval lInt3 = new LocalInterval(midnightAfter, time4)
        DateTime today = DateTime.now()
        Closure getValidData = { dt ->
            dt == today ?
                LocalIntervalUtils.dehydrateLocalIntervals([lInt1, lInt2]) :
                LocalIntervalUtils.dehydrateLocalIntervals([lInt3])
        }

        when: "some invalid"
        List intervals = LocalIntervalUtils.rehydrateAsIntervals(today) { "not valid data string" }

        then:
        intervals == []

        when: "all valid + need to stich interval with adjoining one from following day"
        intervals = LocalIntervalUtils.rehydrateAsIntervals(today, getValidData)

        then:
        intervals.size() == 2

        and: "need to do this because we drop seconds and millisecond info when storing"
        Minutes.minutesBetween(intervals[0].start.toLocalTime(), time1).get() < 1
        Minutes.minutesBetween(intervals[0].end.toLocalTime(), time2).get() < 1
        Minutes.minutesBetween(intervals[1].start.toLocalTime(), time3).get() < 1
        Minutes.minutesBetween(intervals[1].end.toLocalTime(), time4).get() < 1
    }

    void "test converting from list of intervals to map of local intervals"() {
        given:
        DateTime now = DateTime.now().withHourOfDay(6)
        DateTime tomorrow = now.plusDays(1)
        DateTime yesterday = now.minusDays(1)

        Interval todayInt = new Interval(now, now.plusHours(4))
        Interval tomorrowTwoDayInt = new Interval(tomorrow, tomorrow.plusDays(1))
        Interval yesterdayInt = new Interval(yesterday, yesterday.plusHours(1))

        when:
        Map lIntMap = LocalIntervalUtils
            .fromIntervalsToLocalIntervalsMap([todayInt, tomorrowTwoDayInt, yesterdayInt])

        then:
        lIntMap.size() == ScheduleUtils.DAYS_OF_WEEK.size()
        lIntMap.every { k, v -> v instanceof Collection }

        and: "on sunday (today)"
        lIntMap[ScheduleUtils.DAYS_OF_WEEK[0]].size() == 1
        lIntMap[ScheduleUtils.DAYS_OF_WEEK[0]][0].start == now.toLocalTime()
        lIntMap[ScheduleUtils.DAYS_OF_WEEK[0]][0].end == now.plusHours(4).toLocalTime()

        and: "on monday (tomorrow) and tuesday"
        lIntMap[ScheduleUtils.DAYS_OF_WEEK[1]].size() == 1
        lIntMap[ScheduleUtils.DAYS_OF_WEEK[1]][0].start == tomorrow.toLocalTime()
        lIntMap[ScheduleUtils.DAYS_OF_WEEK[1]][0].end == new LocalTime(23, 59)

        lIntMap[ScheduleUtils.DAYS_OF_WEEK[2]].size() == 1
        lIntMap[ScheduleUtils.DAYS_OF_WEEK[2]][0].start == new LocalTime(00, 00)
        lIntMap[ScheduleUtils.DAYS_OF_WEEK[2]][0].end == tomorrow.plusDays(1).toLocalTime()

        and: "on saturday (yesterday)"
        lIntMap[ScheduleUtils.DAYS_OF_WEEK[-1]].size() == 1
        lIntMap[ScheduleUtils.DAYS_OF_WEEK[-1]][0].start == yesterday.toLocalTime()
        lIntMap[ScheduleUtils.DAYS_OF_WEEK[-1]][0].end == yesterday.plusHours(1).toLocalTime()
    }
}
