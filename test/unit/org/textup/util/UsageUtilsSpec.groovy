package org.textup.util

import grails.test.runtime.DirtiesRuntime
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import grails.util.Holders
import java.util.concurrent.*
import org.apache.http.client.methods.*
import org.apache.http.HttpResponse
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.joda.time.DateTime
import org.textup.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class UsageUtilsSpec extends Specification {

    void "test getting table name for various phone ownership types"() {
        expect:
        UsageUtils.getTableName(null) == ""
        UsageUtils.getTableName(PhoneOwnershipType.INDIVIDUAL) == "staff"
        UsageUtils.getTableName(PhoneOwnershipType.GROUP) == "team"
    }

    void "test DateTime obj -> timestamp"() {
        given:
        DateTime dt = DateTime.now()

        expect:
        UsageUtils.dateTimeToTimestamp(null) == ""
        UsageUtils.dateTimeToTimestamp(dt) == DateTimeUtils.CURRENT_TIME_FORMAT.print(dt)
    }

    void "test DateTime obj -> month string"() {
        given:
        DateTime dt = DateTime.now()

        expect:
        UsageUtils.dateTimeToMonthString(null) == ""
        UsageUtils.dateTimeToMonthString(dt) == DateTimeUtils.DISPLAYED_MONTH_FORMAT.print(dt)
    }

    void "test month string -> DateTime obj"() {
        when:
        DateTime dt = UsageUtils.monthStringToDateTime("Nov 2018")

        then:
        dt.monthOfYear == 11
        dt.year == 2018

        expect:
        UsageUtils.monthStringToDateTime(null) == null
        UsageUtils.monthStringToDateTime("invalid string input") == null
    }

    void "test query month -> month string"() {
        expect:
        UsageUtils.queryMonthToMonthString(null) == ""
        UsageUtils.queryMonthToMonthString("invalid string input") == ""
        UsageUtils.queryMonthToMonthString("2018-11") == "Nov 2018"
    }

    @DirtiesRuntime
    void "test getting available month strings"() {
        given:
        RecordItem mockItem = Mock()

        when:
        RecordItem.metaClass."static".first = { String propName -> null }
        List<String> monthStrings = UsageUtils.getAvailableMonthStrings()

        then:
        monthStrings.size() == 1

        when:
        RecordItem.metaClass."static".first = { String propName -> mockItem }
        monthStrings = UsageUtils.getAvailableMonthStrings()

        then:
        1 * mockItem.whenCreated >> DateTime.now().minusMonths(8)
        monthStrings.size() == 8 + 1 // 8 prior months + 1 "now" month
    }

    @DirtiesRuntime
    void "test get available month string index"() {
        given:
        DateTime now = DateTime.now()
        int numMonthsLastIndex = 8
        RecordItem itemStub = Stub() { getWhenCreated() >> now.minusMonths(numMonthsLastIndex) }
        RecordItem.metaClass."static".first = { String propName -> itemStub }

        expect: "invalid + out of range inputs return -1"
        UsageUtils.getAvailableMonthStringIndex(null) == -1
        UsageUtils.getAvailableMonthStringIndex(now.plusYears(8)) == -1

        and:
        UsageUtils.getAvailableMonthStringIndex(now) == numMonthsLastIndex
        UsageUtils.getAvailableMonthStringIndex(now.minusMonths(numMonthsLastIndex)) == 0
        UsageUtils.getAvailableMonthStringIndex(now.minusMonths(numMonthsLastIndex).plusMonths(1)) == 1
    }

    void "test associating activity record with activity owners + has no side effects"() {
        given:
        List<UsageService.HasActivity> owners1 = []
        List<UsageService.ActivityRecord> activityList = []
        int numOwners = 8
        numOwners.times { BigInteger ownerId ->
            owners1 << new UsageService.HasActivity(id: ownerId)
            activityList << new UsageService.ActivityRecord(ownerId: ownerId)
        }

        when:
        List<UsageService.HasActivity> owners2 = UsageUtils.associateActivity(owners1, activityList)

        then: "returns copied owners -- no side effects"
        owners2 != owners1
        owners1.size() == numOwners
        owners1.every { it.activity.ownerId == null }
        owners2.size() == numOwners
        owners2.every { it.activity.ownerId == it.id }
    }

    @DirtiesRuntime
    void "test ensuring all months are present chronlogically within a list of activity records + has no side effects"() {
        given:
        DateTime now = DateTime.now()
        RecordItem mockItem = Stub() { getWhenCreated() >> now.minusMonths(2) }
        RecordItem.metaClass."static".first = { String propName -> mockItem }

        UsageService.ActivityRecord actNow = new UsageService.ActivityRecord(monthString: DateTimeUtils.QUERY_MONTH_FORMAT.print(now)),
            actNowMinusOne = new UsageService.ActivityRecord(monthString: DateTimeUtils.QUERY_MONTH_FORMAT.print(now.minusMonths(1))),
            actNowMinusTwo = new UsageService.ActivityRecord(monthString: DateTimeUtils.QUERY_MONTH_FORMAT.print(now.minusMonths(2))),
            actNowMinusEight = new UsageService.ActivityRecord(monthString: DateTimeUtils.QUERY_MONTH_FORMAT.print(now.minusMonths(8))),
            actNowPlusOne = new UsageService.ActivityRecord(monthString: DateTimeUtils.QUERY_MONTH_FORMAT.print(now.plusMonths(1)))

        when:
        List<UsageService.ActivityRecord> activities = UsageUtils.ensureMonths(null)

        then:
        activities.size() == 3
        activities[0].monthString == actNowMinusTwo.monthString
        activities[1].monthString == actNowMinusOne.monthString
        activities[2].monthString == actNow.monthString

        when:
        activities = UsageUtils.ensureMonths([])

        then:
        activities.size() == 3
        activities[0].monthString == actNowMinusTwo.monthString
        activities[1].monthString == actNowMinusOne.monthString
        activities[2].monthString == actNow.monthString

        when: "input is not sorted from oldest to newest"
        actNowMinusOne.numActivePhones = 888
        actNow.numActivePhones = 8
        List<UsageService.ActivityRecord> outOfOrderActivities =
            [actNowPlusOne, actNow, actNowMinusOne, actNowMinusEight]
        activities = UsageUtils.ensureMonths(outOfOrderActivities)

        then: "input is sorted + months that fall outside of the available months are kept"
        activities != outOfOrderActivities
        activities.size() == 5
        activities[0].monthString == actNowMinusEight.monthString
        activities[1].monthString == actNowMinusTwo.monthString
        activities[2].monthString == actNowMinusOne.monthString
        activities[3].monthString == actNow.monthString
        activities[4].monthString == actNowPlusOne.monthString

        and: "all of the original objs are preserved, not lost in the reshuffling"
        activities[1].numActivePhones == 0 // default value since we didn't pass in actNowMinusTwo
        activities[2].numActivePhones == actNowMinusOne.numActivePhones
        activities[3].numActivePhones == actNow.numActivePhones
    }
}
