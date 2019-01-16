package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.joda.time.*
import org.joda.time.format.*
import org.textup.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class DateTimeUtils {

    static DateTime now() { DateTimeUtils.now() }

    // Printing DateTimes
    // ------------------

    static final DateTimeFormatter CURRENT_TIME_FORMAT = DateTimeFormat.forPattern("MMM dd, y h:mm a")
    static final DateTimeFormatter DISPLAYED_MONTH_FORMAT = DateTimeFormat.forPattern("MMM yyyy")
    static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormat.forPattern("MMM-dd-yyyy")
    static final DateTimeFormatter QUERY_MONTH_FORMAT = DateTimeFormat.forPattern("yyyy-MM")

    static String printLocalInterval(LocalInterval localInt) {
        if (localInt) {
            String start1 = localInt.start.hourOfDay.toString().padLeft(2, "0"),
                start2 = localInt.start.minuteOfHour.toString().padLeft(2, "0"),
                end1 = localInt.end.hourOfDay.toString().padLeft(2, "0"),
                end2 = localInt.end.minuteOfHour.toString().padLeft(2, "0"),
                start = "${start1}${start2}",
                end = "${end1}${end2}"
            "${start}:${end}"
        }
        else { "" }
    }

    // Operations on DateTimes
    // -----------------------

    static DateTimeZone getZoneFromId(String id) {
        try {
            id ? DateTimeZone.forID(id) : DateTimeZone.UTC
        }
        catch (e) {
            log.debug("DateTimeUtils.getZoneFromId: ${e.message}")
            return DateTimeZone.UTC
        }
    }

    static DateTime toDateTimeWithZone(Object val, Object zone = "UTC") {
        try {
            if (!val) {
                return null
            }
            String stringVal = TypeConversionUtils.to(String, val),
                stringZone = zone as String
            DateTimeZone tz = getZoneFromId(stringZone)
            // must NOT use withZoneRetainFields because doing so results in this scenario:
            // The default system time might not be UTC time. Therefore, when we pass a UTC
            // string to the DateTime constructor, it converts the UTC fields to the fields
            // in the local time zone (that is the system default). Then, if we call
            // withZoneRetainFields on this DateTime object, we convert to the UTC time zone
            // using the LOCAL values, thereby losing the original time
            new DateTime(stringVal).withZone(tz)
        }
        catch (e) {
            log.debug("DateTimeUtils.toDateTimeWithZone: $e")
            null
        }
    }

    static DateTime toUTCDateTimeTodayThenZone(LocalTime lt, DateTimeZone zone = DateTimeZone.UTC) {
        lt.toDateTimeToday(DateTimeZone.UTC).withZone(zone)
    }

    static DateTime toZoneDateTimeTodayThenUTC(LocalTime lt, DateTimeZone zone = DateTimeZone.UTC) {
        lt.toDateTimeToday(zone).withZone(DateTimeZone.UTC)
    }
}
