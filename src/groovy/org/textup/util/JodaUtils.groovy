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
class JodaUtils {

    static final DateTimeFormatter CURRENT_TIME_FORMAT = DateTimeFormat.forPattern("MMM dd, y h:mm a")
    static final DateTimeFormatter DISPLAYED_MONTH_FORMAT = DateTimeFormat.forPattern("MMM yyyy")
    static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormat.forPattern("MMM-dd-yyyy")
    static final DateTimeFormatter QUERY_MONTH_FORMAT = DateTimeFormat.forPattern("yyyy-MM")

    static DateTime utcNow() { DateTime.now(DateTimeZone.UTC) }

    static DateTimeZone getZoneFromId(String id) {
        try {
            id ? DateTimeZone.forID(id) : DateTimeZone.UTC
        }
        catch (e) {
            log.debug("getZoneFromId: ${e.message}")
            return DateTimeZone.UTC
        }
    }

    static DateTime toDateTimeWithZone(Object val, Object zone = "UTC") {
        try {
            if (!val) {
                return null
            }
            String stringVal = TypeUtils.to(String, val),
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
            log.debug("toDateTimeWithZone: $e")
            null
        }
    }

    static DateTime toUTCDateTimeTodayThenZone(LocalTime lt, DateTimeZone zone = DateTimeZone.UTC) {
        lt.toDateTimeToday(DateTimeZone.UTC).withZone(zone)
    }

    static DateTime toZoneDateTimeTodayThenUTC(LocalTime lt, DateTimeZone zone = DateTimeZone.UTC) {
        lt.toDateTimeToday(zone).withZone(DateTimeZone.UTC)
    }

    static DateTime atStartOfMonth(DateTime dt) {
        if (dt) {
            dt
                .dayOfMonth().withMinimumValue()
                .millisOfDay().withMinimumValue()
        }
        else { dt }
    }

    // standard MySQL DATETIME type only stores at the seconds fidelity so if we use the largest
    // milliseconds this will be rounded up to the next month, not something that we want IF
    // we do store this end of month value in the database
    static DateTime atEndOfMonth(DateTime dt) {
        if (dt) {
            dt
                .dayOfMonth().withMaximumValue()
                .millisOfDay().withMaximumValue()
                .millisOfSecond().withMinimumValue()
        }
        else { dt }
    }
}
