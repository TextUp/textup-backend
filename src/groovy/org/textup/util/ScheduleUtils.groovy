package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.joda.time.*
import org.joda.time.format.*
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class ScheduleUtils {

    static final String DEFAULT_TIMEZONE = "UTC"
    static final List<String> DAYS_OF_WEEK =
        Collections.unmodifiableList(["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"])
    static final String RANGE_DELIMITER = ";"
    static final String TIME_DELIMITER = ","
    static final String REST_DELIMITER = ":"
    static final String TIME_FORMAT = "HHmm"

    static int getDaysBetween(DateTime dt1, DateTime dt2) {
        if (!dt1 || !dt2) {
            return 0
        }
        Days.daysBetween(dt1.toLocalDate(), dt2.toLocalDate()).getDays()
    }

    static Interval buildIntervalFromStrings(DateTimeFormatter dtf, DateTimeZone zone, List<String> times,
        int addDays) {

        DateTime start = DateTimeUtils
            .toUTCDateTimeTodayThenZone(dtf.parseLocalTime(times[0]), zone)
            .plusDays(addDays)
        DateTime end = DateTimeUtils
            .toUTCDateTimeTodayThenZone(dtf.parseLocalTime(times[1]), zone)
            .plusDays(addDays)
        new Interval(start, end)
    }

    static boolean validateIntervalsString(String str) {
        List<String> strInts = str.tokenize(RANGE_DELIMITER)
        DateTimeFormatter dtf = DateTimeFormat.forPattern(TIME_FORMAT).withZoneUTC()
        List<LocalTime> times = []
        try {
            for (rangeString in strInts) {
                List<String> rTimes = rangeString.tokenize(TIME_DELIMITER)
                if (rTimes.size() != 2) {
                    return false
                }
                LocalTime start = dtf.parseLocalTime(rTimes[0]),
                    end = dtf.parseLocalTime(rTimes[1])
                if (start.isAfter(end) || !times.isEmpty() && times.last().isAfter(start)) {
                    return false
                }
                (times << start) << end
            }
            return true
        }
        catch (e) {
            return false
        }
    }

    static Result<List<Interval>> parseIntervalStringsToUTCIntervals(List<String> intStrings,
        int addDays, DateTimeZone zone=null) {

        List<Interval> result = []
        DateTimeFormatter dtf = DateTimeFormat.forPattern(TIME_FORMAT).withZoneUTC()
        DateTime today = DateTimeUtils.now(),
            zoneToday = DateTime.now(zone)
        // order of the arguments in this call is very important
        // we put the zone first so that the result number is the modifier that we
        // can use to scale the addDays amount by.
        int daysBetween = ScheduleUtils.getDaysBetween(zoneToday, today)

        for (str in intStrings) {
            try {
                List<String> times = str.tokenize(REST_DELIMITER)
                if (times.size() == 2) {
                    DateTime start = DateTimeUtils.toZoneDateTimeTodayThenUTC(dtf.parseLocalTime(times[0]), zone),
                        end = DateTimeUtils.toZoneDateTimeTodayThenUTC(dtf.parseLocalTime(times[1]), zone)
                    //add days until we've reached the desired offset from today
                    //we use the start date as reference and increment the end date
                    //accordingly to preserve the range
                    //NO NEED to take into account the # days between start/end date and today here
                    //because if the start/end dates are actually a day ahead or behind because
                    //of timezone differences, the helper to parse the strings into DateTime objects
                    //already takes that into account. All we need to do is boost by the correct
                    //number of days here scaled by the number of days between UTC today and timezone today
                    (addDays + daysBetween).times {
                        start = start.plusDays(1)
                        end = end.plusDays(1)
                    }
                    result << new Interval(start, end)
                }
                else {
                    return IOCUtils.resultFactory.failWithCodeAndStatus("weeklySchedule.invalidRestTimeFormat",
                        ResultStatus.UNPROCESSABLE_ENTITY, [str])
                }
            }
            catch (e) {
                log.debug("WeeklyScheduleSpec.parseIntervalStrings: ${e.message}")
                return IOCUtils.resultFactory.failWithCodeAndStatus("weeklySchedule.invalidRestTimeFormat",
                    ResultStatus.UNPROCESSABLE_ENTITY, [str])
            }
        }
        IOCUtils.resultFactory.success(result)
    }
}
