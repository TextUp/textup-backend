package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.joda.time.*
import org.joda.time.format.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class DateTimeUtils {

    static final DateTimeFormatter currentTimeFormat = DateTimeFormat.forPattern("MMM dd, y h:mm a")
    static final DateTimeFormatter displayedMonthFormat = DateTimeFormat.forPattern("MMM yyyy")
    static final DateTimeFormatter fileTimestampFormat = DateTimeFormat.forPattern("MMM-dd-yyyy")
    static final DateTimeFormatter queryMonthFormat = DateTimeFormat.forPattern("yyyy-MM")

    // Printing DateTimes
    // ------------------

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
            return id ? DateTimeZone.forID(id) : DateTimeZone.UTC
        }
        catch (e) {
            log.debug("DateTimeUtils.getZoneFromId: ${e.message}")
            return DateTimeZone.UTC
        }
    }

    static DateTime toUTCDateTime(def val) {
        try {
            new DateTime(val, DateTimeZone.UTC)
        }
        catch (e) {
            log.debug("DateTimeUtils.toUTCDateTime: $e")
            null
        }
    }

    static DateTime toDateTimeWithZone(def time, def zone = null) {
        if (!time) return null
        new DateTime(TypeConversionUtils.to(String, time))
            // must NOT use withZoneRetainFields because doing so results in this scenario:
            // The default system time might not be UTC time. Therefore, when we pass a UTC
            // string to the DateTime constructor, it converts the UTC fields to the fields
            // in the local time zone (that is the system default). Then, if we call
            // withZoneRetainFields on this DateTime object, we convert to the UTC time zone
            // using the LOCAL values, thereby losing the original time
            .withZone(getZoneFromId(zone as String))
    }

    static DateTime toDateTimeTodayWithZone(LocalTime lt, DateTimeZone zone) {
        if (zone) {
            lt.toDateTimeToday(DateTimeZone.UTC).withZone(zone)
        }
        else { lt.toDateTimeToday(DateTimeZone.UTC) }
    }

    static DateTime toUTCDateTimeTodayFromZone(LocalTime lt, DateTimeZone zone) {
        if (zone) {
            lt.toDateTimeToday(zone).withZone(DateTimeZone.UTC)
        }
        else { lt.toDateTimeToday(DateTimeZone.UTC) }
    }

    static int getDaysBetween(DateTime dt1, DateTime dt2) {
        Days.daysBetween(dt1.toLocalDate(), dt2.toLocalDate()).getDays()
    }

    //0 corresponds to sunday, 6 to saturday
    static int getDayOfWeekIndex(int num) {
        Math.abs(num % 7)
    }
}
