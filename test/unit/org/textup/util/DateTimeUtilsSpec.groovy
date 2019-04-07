package org.textup.util

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import grails.util.Holders
import java.util.concurrent.*
import org.apache.http.client.methods.*
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.*
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class DateTimeUtilsSpec extends Specification {

    void "test printing local interval"() {
        given:
        LocalInterval lInt = new LocalInterval(new LocalTime(5, 0), new LocalTime(7, 0))

        expect:
        DateTimeUtils.printLocalInterval(null) == ""
        DateTimeUtils.printLocalInterval(lInt) == "0500:0700"
    }

    void "test getting timezone from zone id"() {
        expect:
        DateTimeUtils.getZoneFromId(null) == DateTimeZone.UTC
        DateTimeUtils.getZoneFromId("invalid id") == DateTimeZone.UTC
        DateTimeUtils.getZoneFromId("America/New_York") == DateTimeZone.forID("America/New_York")
    }

    void "test converting to UTC date time"() {
        given:
        DateTimeZone notUTCZone = DateTimeZone.forID("America/New_York")

        expect:
        DateTimeUtils.toDateTimeWithZone(null) == null
        DateTimeUtils.toDateTimeWithZone("not a valid datetime input") == null
        DateTimeUtils.toDateTimeWithZone(DateTime.now(notUTCZone)).zone == DateTimeZone.UTC
    }

    void "test string --> date time --> specified timezone"() {
        given:
        String tzId = "America/New_York"
        DateTimeZone notUTCZone = DateTimeZone.forID(tzId)

        expect:
        DateTimeUtils.toDateTimeWithZone(null) == null
        DateTimeUtils.toDateTimeWithZone(null, null) == null
        DateTimeUtils.toDateTimeWithZone("invalid input") == null
        DateTimeUtils.toDateTimeWithZone(DateTime.now(DateTimeZone.UTC), tzId).zone == notUTCZone
    }

    void "test converting local time to date time"() {
        given:
        String tzId = "America/New_York"
        DateTimeZone notUTCZone = DateTimeZone.forID(tzId)
        int hourNum = 11
        int utcOffsetInHours = TestUtils.getOffsetInHours(tzId)
        LocalTime lTime = new LocalTime(hourNum, 0)

        expect: "both default to UTC if no zone specified so should be same"
        DateTimeUtils.toUTCDateTimeTodayThenZone(lTime).toString() ==
            DateTimeUtils.toZoneDateTimeTodayThenUTC(lTime).toString()

        and:
        // UTC date time -> zone date time = subtract 5 (Eastern time is behind UTC by five hours)
        DateTimeUtils.toUTCDateTimeTodayThenZone(lTime, notUTCZone).toString()
            .contains("${hourNum + utcOffsetInHours}:00:00")
        // zone date time -> UTC date time = add 5 to remove -5 offset
        // (Eastern time is behind UTC by five hours)
        DateTimeUtils.toZoneDateTimeTodayThenUTC(lTime, notUTCZone).toString()
            .contains("${hourNum - utcOffsetInHours}:00:00")
    }

    void "test calculating # days between two date times"() {
        given:
        DateTime now = DateTime.now()

        expect:
        DateTimeUtils.getDaysBetween(null, null) == 0
        DateTimeUtils.getDaysBetween(now, null) == 0
        DateTimeUtils.getDaysBetween(null, now) == 0
        DateTimeUtils.getDaysBetween(now, now) == 0
        DateTimeUtils.getDaysBetween(now.plusDays(3), now) == -3
        DateTimeUtils.getDaysBetween(now, now.plusDays(8)) == 8
    }

    void "test get day of week index"() {
        expect:
        DateTimeUtils.getDayOfWeekIndex(-1) == 1
        DateTimeUtils.getDayOfWeekIndex(0) == 0
        DateTimeUtils.getDayOfWeekIndex(5) == 5
        DateTimeUtils.getDayOfWeekIndex(7) == 0
        DateTimeUtils.getDayOfWeekIndex(8) == 1
    }
}
