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
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class JodaUtilsSpec extends Specification {

    void "test getting timezone from zone id"() {
        expect:
        JodaUtils.getZoneFromId(null) == DateTimeZone.UTC
        JodaUtils.getZoneFromId("invalid id") == DateTimeZone.UTC
        JodaUtils.getZoneFromId("America/New_York") == DateTimeZone.forID("America/New_York")
    }

    void "test converting to UTC date time"() {
        given:
        DateTimeZone notUTCZone = DateTimeZone.forID("America/New_York")

        expect:
        JodaUtils.toDateTimeWithZone(null) == null
        JodaUtils.toDateTimeWithZone("not a valid datetime input") == null
        JodaUtils.toDateTimeWithZone(DateTime.now(notUTCZone)).zone == DateTimeZone.UTC
    }

    void "test string --> date time --> specified timezone"() {
        given:
        String tzId = "America/New_York"
        DateTimeZone notUTCZone = DateTimeZone.forID(tzId)

        expect:
        JodaUtils.toDateTimeWithZone(null) == null
        JodaUtils.toDateTimeWithZone(null, null) == null
        JodaUtils.toDateTimeWithZone("invalid input") == null
        JodaUtils.toDateTimeWithZone(DateTime.now(DateTimeZone.UTC), tzId).zone == notUTCZone
    }

    void "test converting local time to date time"() {
        given:
        DateTimeZone notUTCZone = DateTimeZone.forID("America/New_York")
        int hourNum = 11
        LocalTime lTime = new LocalTime(hourNum, 0)

        expect: "both default to UTC if no zone specified so should be same"
        JodaUtils.toUTCDateTimeTodayThenZone(lTime).toString() ==
            JodaUtils.toZoneDateTimeTodayThenUTC(lTime).toString()

        and:
        // UTC date time -> zone date time = subtract 5 (Eastern time is behind UTC by five hours)
        JodaUtils.toUTCDateTimeTodayThenZone(lTime, notUTCZone).toString()
            .contains("${hourNum - 5}:00:00")
        // zone date time -> UTC date time = add 5 to remove -5 offset
        // (Eastern time is behind UTC by five hours)
        JodaUtils.toZoneDateTimeTodayThenUTC(lTime, notUTCZone).toString()
            .contains("${hourNum + 5}:00:00")
    }

    void "test determining start of the month"() {
        given:
        DateTime dt = DateTime.now()

        when:
        DateTime newDt = JodaUtils.atStartOfMonth(null)

        then:
        newDt == null

        when:
        newDt = JodaUtils.atStartOfMonth(dt)

        then:
        dt.millis != newDt.millis
        dt.year == newDt.year
        dt.monthOfYear == newDt.monthOfYear
        newDt.dayOfMonth == 1
        newDt.hourOfDay == 0
        newDt.minuteOfDay == 0
        newDt.secondOfMinute == 0
        newDt.millisOfSecond == 0
    }

    void "test determining end of the month"() {
        given:
        DateTime dt = DateTime.now()

        when:
        DateTime newDt = JodaUtils.atEndOfMonth(null)

        then:
        newDt == null

        when:
        newDt = JodaUtils.atEndOfMonth(dt)

        then:
        dt.millis != newDt.millis
        dt.year == newDt.year
        dt.monthOfYear == newDt.monthOfYear
        newDt.dayOfMonth > 0
        newDt.hourOfDay > 0
        newDt.minuteOfDay > 0
        newDt.secondOfMinute > 0
        newDt.millisOfSecond > 0
    }
}
