package org.textup.util

import grails.compiler.GrailsTypeChecked
import org.joda.time.*
import org.joda.time.format.*
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class LocalIntervalUtils {

    static List<LocalInterval> cleanLocalIntervals(List<LocalInterval> intervals,
        Integer minutesThreshold = null) {

        List<LocalInterval> sorted = intervals.sort(),
            cleaned = sorted.isEmpty() ? new ArrayList<LocalInterval>() : [sorted[0]]
        int sortedLen = sorted.size()
        boolean mergedPrevious = true
        for(int i = 1; i < sortedLen; i++) {
            LocalInterval int1 = sorted[i - 1],
                int2 = sorted[i]
            if (int1 != int2) {
                if (int1.abuts(int2) || int1.overlaps(int2) ||
                    (minutesThreshold && int1.withinMinutesOf(int2, minutesThreshold))) {

                    LocalInterval startingInt = mergedPrevious ? cleaned.last() : int1
                    if (cleaned.size() == 1) {
                        cleaned = []
                    }
                    else { cleaned = cleaned[0..-2] } //pop last

                    if (startingInt.end > int2.end) {
                        cleaned << new LocalInterval(start: startingInt.start, end: startingInt.end)
                    }
                    else {
                        cleaned << new LocalInterval(start: startingInt.start, end: int2.end)
                    }
                    mergedPrevious = true
                }
                else {
                    cleaned << int2
                    mergedPrevious = false
                }
            }
        }
        cleaned
    }

    static String dehydrateLocalIntervals(List<LocalInterval> intervals) {
        List<String> intStrings = []
        DateTimeFormatter dtf = DateTimeFormat.forPattern(ScheduleUtils.TIME_FORMAT).withZoneUTC()
        intervals.each { LocalInterval i ->
            intStrings <<
                "${dtf.print(i.start)}${ScheduleUtils.TIME_DELIMITER}${dtf.print(i.end)}".toString()
        }
        intStrings.join(ScheduleUtils.RANGE_DELIMITER)
    }

    static List<Interval> rehydrateAsIntervals(DateTime dt, Closure<String> getValueForDayOfWeek,
        boolean stitchEndOfDay = true) {

        DateTimeFormatter dtf = DateTimeFormat.forPattern(ScheduleUtils.TIME_FORMAT).withZoneUTC()
        Interval endOfDayInterval = null //stitch intervals that cross between days
        List<Interval> intervals = []
        String intervalsString = getValueForDayOfWeek(dt)
        intervalsString.tokenize(ScheduleUtils.RANGE_DELIMITER).each { String rangeString ->
            List<String> times = rangeString.tokenize(ScheduleUtils.TIME_DELIMITER)
            if (times.size() == 2) {
                DateTime start = dtf
                    .parseLocalTime(times[0])
                    .toDateTime(dt)
                    .withZoneRetainFields(DateTimeZone.UTC)
                DateTime end = dtf
                    .parseLocalTime(times[1])
                    .toDateTime(dt)
                    .withZoneRetainFields(DateTimeZone.UTC)
                Interval interval = new Interval(start, end)
                //if end of day interval, don't add it to intervals list yet
                if (isEndOfDay(end)) {
                    endOfDayInterval = interval
                }
                else { intervals << interval }
            }
            else {
                log.error("WeeklySchedule.rehydrateAsIntervals: \
                    for intervals $intervalsString, invalid range: $rangeString")
            }
        }
        if (stitchEndOfDay && endOfDayInterval) {
            List<Interval> tomorrowIntervals = rehydrateAsIntervals(dt.plusDays(1), getValueForDayOfWeek, false)
            Interval startOfDayInterval = tomorrowIntervals.find { isStartOfDay(it.start) }
            if (startOfDayInterval) {
                endOfDayInterval = new Interval(endOfDayInterval.start, startOfDayInterval.end)
            }
        }
        if (endOfDayInterval) {
            intervals << endOfDayInterval
        }
        intervals
    }

    // iterate each interval, and bin them into the appropriate day of the week,
    // assuming that sunday corresponds to day, monday to tomorrow and so forth.
    // For wraparound purposes, yesterday corresponds to saturday
    static Map<String, List<LocalInterval>> fromIntervalsToLocalIntervalsMap(List<Interval> intervals) {
        List<String> daysOfWeek = ScheduleUtils.DAYS_OF_WEEK
        Map<String,List<LocalInterval>> localIntervals = daysOfWeek.collectEntries { [(it):[]] }
        DateTime today = JodaUtils.now()
        intervals.each { Interval interval ->
            int startDayOfWeek = getDayOfWeekIndex(ScheduleUtils.getDaysBetween(today, interval.start)),
                endDayOfWeek = getDayOfWeekIndex(ScheduleUtils.getDaysBetween(today, interval.end))
            String startDay = daysOfWeek[startDayOfWeek],
                endDay = daysOfWeek[endDayOfWeek]
            //if interval does not span two days
            if (startDayOfWeek == endDayOfWeek) {
                localIntervals[startDay] << new LocalInterval(interval)
            }
            else { //interval spans two days, break interval into two local intervals
                localIntervals[startDay] <<
                    new LocalInterval(interval.start.toLocalTime(), new LocalTime(23, 59))
                localIntervals[endDay] <<
                    new LocalInterval(new LocalTime(00, 00), interval.end.toLocalTime())
            }
        }
        localIntervals
    }

    // Helpers
    // -------

    //0 corresponds to sunday, 6 to saturday
    protected static int getDayOfWeekIndex(int num) {
        Math.abs(num % 7)
    }

    protected static boolean isEndOfDay(DateTime dt) {
        dt.plusMinutes(2).dayOfWeek != dt.dayOfWeek
    }

    protected static boolean isStartOfDay(DateTime dt) {
        dt.minusMinutes(2).dayOfWeek != dt.dayOfWeek
    }
}
