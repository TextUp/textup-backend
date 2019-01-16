package org.textup.util

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class ScheduleUtils {


    static Interval toIntervalToday(LocalInterval li1) {
        try {
            if (li1) {
                new Interval(li1?.start?.toDateTimeToday(DateTimeZone.UTC),
                    li1?.end?.toDateTimeToday(DateTimeZone.UTC))
            }
            else { null }
        }
        catch (e) { null }
    }



    // TODO remove??
    // static final String RANGE_DELIMITER = ";"
    // static final String TIME_DELIMITER = ","
    // static final String REST_DELIMITER = ":"
    // static final String TIME_FORMAT = "HHmm"

    static int getDaysBetween(DateTime dt1, DateTime dt2) {
        if (!dt1 || !dt2) {
            return 0
        }
        Days.daysBetween(dt1.toLocalDate(), dt2.toLocalDate()).getDays()
    }

    //0 corresponds to sunday, 6 to saturday
    static int getDayOfWeekIndex(int num) {
        Math.abs(num % 7)
    }

    static boolean isEndOfDay(DateTime dt) {
        dt.plusMinutes(2).dayOfWeek != dt.dayOfWeek
    }

    static boolean isStartOfDay(DateTime dt) {
        dt.minusMinutes(2).dayOfWeek != dt.dayOfWeek
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static List checkWraparoundHelper(List<String> daysOfWeek) {
        boolean lastDayAtEnd = false,
            firstDayAtBeginning = false
        String firstDayWrappedEnd
        for (wrapRange in this."${daysOfWeek[0]}".tokenize(_rangeDelimiter)) {
            if (wrapRange.tokenize(TIME_DELIMITER)[0] == "0000") {
                firstDayWrappedEnd = wrapRange.tokenize(TIME_DELIMITER)[1]
                firstDayAtBeginning = true
                break
            }
        }
        for (wrapRange in this."${daysOfWeek.last()}".tokenize(_rangeDelimiter)) {
            if (wrapRange.tokenize(TIME_DELIMITER)[1] == "2359") {
                lastDayAtEnd = true
                break
            }
        }
        boolean hasWraparound = lastDayAtEnd && firstDayAtBeginning
        [hasWraparound, firstDayWrappedEnd]
    }

    static Interval buildIntervalFromStrings(DateTimeFormatter dtf, DateTimeZone zone, List<String> times,
        int addDays) {

        DateTime start = DateTimeUtils.toUTCDateTimeTodayThenZone(dtf.parseLocalTime(times[0]), zone)
            .plusDays(addDays)
        DateTime end = DateTimeUtils.toUTCDateTimeTodayThenZone(dtf.parseLocalTime(times[1]), zone)
            .plusDays(addDays)
        new Interval(start, end)
    }

    // TODO should move to ValidationUtils or to LocalIntervl validator?
    static boolean validateIntervalsString(String str) {
        List<String> strInts = str.tokenize(_rangeDelimiter)
        DateTimeFormatter dtf = DateTimeFormat.forPattern(TIME_FORMAT).withZoneUTC()
        List<LocalTime> times = []
        try {
            for (rangeString in strInts) {
                List<String> rTimes = rangeString.tokenize(TIME_DELIMITER)
                if (rTimes.size() != 2) { return false }
                LocalTime start = dtf.parseLocalTime(rTimes[0]),
                    end = dtf.parseLocalTime(rTimes[1])
                if (start.isAfter(end) ||
                    !times.isEmpty() && times.last().isAfter(start)) { return false }
                (times << start) << end
            }
            return true
        }
        catch (e) {
            return false
        }
    }

    static String dehydrateLocalIntervals(List<LocalInterval> intervals) {
        List<String> intStrings = []
        DateTimeFormatter dtf = DateTimeFormat.forPattern(TIME_FORMAT).withZoneUTC()
        intervals.each { LocalInterval i ->
            intStrings << "${dtf.print(i.start)}${TIME_DELIMITER}${dtf.print(i.end)}".toString()
        }
        intStrings.join(_rangeDelimiter)
    }

    static List<LocalInterval> cleanLocalIntervals(List<LocalInterval> intervals,
        Integer minutesThreshold=null) {
        List<LocalInterval> sorted = intervals.sort(),
            cleaned = sorted.isEmpty() ? new ArrayList<LocalInterval>() : [sorted[0]]
        int sortedLen = sorted.size()
        boolean mergedPrevious = true
        for(int i = 1; i < sortedLen; i++) {
            LocalInterval int1 = sorted[i - 1], int2 = sorted[i]
            if (int1 != int2) {
                if (int1.abuts(int2) || int1.overlaps(int2) ||
                    (minutesThreshold && int1.withinMinutesOf(int2, minutesThreshold))) {

                    LocalInterval startingInt = mergedPrevious ? cleaned.last() : int1
                    cleaned = (cleaned.size() == 1) ?
                        new ArrayList<LocalInterval>() :
                        cleaned[0..-2] //pop last
                    if (startingInt.end > int2.end) {
                        cleaned << new LocalInterval(start:startingInt.start, end:startingInt.end)
                    }
                    else {
                        cleaned << new LocalInterval(start:startingInt.start, end:int2.end)
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
}
