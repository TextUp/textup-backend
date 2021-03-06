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
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class UsageUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

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
        UsageUtils.dateTimeToTimestamp(dt) == JodaUtils.CURRENT_TIME_FORMAT.print(dt)
    }

    void "test DateTime obj -> month string"() {
        given:
        DateTime dt = DateTime.now()

        expect:
        UsageUtils.dateTimeToMonthString(null) == ""
        UsageUtils.dateTimeToMonthString(dt) == JodaUtils.DISPLAYED_MONTH_FORMAT.print(dt)
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

        then: "will return appropriate number of month strings by normalizing day-of-month and time-of-day"
        1 * mockItem.whenCreated >> DateTime.now().withDayOfMonth(8).minusMonths(8)
        monthStrings.size() == 8 + 1 // 8 prior months + 1 "now" month
    }

    void "testing building numbers for a certain phone and month"() {
        given:
        MockedMethod mustFindForId
        Phone p1 = GroovyMock() { asBoolean() >> true }
        Long pId = TestUtils.randIntegerUpTo(88)
        DateTime dt = DateTime.now()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        PhoneNumber pNum2 = TestUtils.randPhoneNumber()
        PhoneNumber pNum3 = TestUtils.randPhoneNumber()

        when: "null inputs"
        mustFindForId = MockedMethod.create(Phones, "mustFindForId") { Result.void() }
        String numbers = UsageUtils.buildNumbersStringForMonth(null, null)

        then:
        numbers == UsageUtils.NO_NUMBERS

        when: "valid but no numbers found"
        mustFindForId = MockedMethod.create(mustFindForId) { Result.createSuccess(p1) }
        numbers = UsageUtils.buildNumbersStringForMonth(pId, dt)

        then:
        p1.asType(Phone) >> p1
        1 * p1.buildNumbersForMonth(dt.monthOfYear, dt.year) >> []
        numbers == UsageUtils.NO_NUMBERS

        when:
        numbers = UsageUtils.buildNumbersStringForMonth(pId, dt)

        then:
        p1.asType(Phone) >> p1
        1 * p1.buildNumbersForMonth(dt.monthOfYear, dt.year) >> [pNum1, pNum2, pNum3]
        numbers != UsageUtils.NO_NUMBERS
        numbers == "${pNum1}, ${pNum2} and ${pNum3}"

        cleanup:
        mustFindForId?.restore()
    }

    @DirtiesRuntime
    void "test get available month string index"() {
        given:
        DateTime now = DateTime.now()
        int numMonthsLastIndex = 8
        RecordItem itemStub = GroovyStub() { getWhenCreated() >> now.minusMonths(numMonthsLastIndex) }
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
        List<ActivityEntity.HasActivity> owners1 = []
        List<ActivityRecord> activityList = []
        int numOwners = 8
        numOwners.times { BigInteger ownerId ->
            owners1 << new ActivityEntity.HasActivity(id: ownerId)
            activityList << new ActivityRecord(ownerId: ownerId)
        }
        DateTime dt = DateTime.now()
        String monthString = UsageUtils.dateTimeToMonthString(dt)
        DateTime monthObj = UsageUtils.monthStringToDateTime(monthString)

        when:
        List<ActivityEntity.HasActivity> owners2 = UsageUtils.associateActivityForMonth(dt, owners1, activityList)

        then: "returns copied owners -- no side effects"
        // `.is()` in Groovy is `==` in Java. See http://mrhaki.blogspot.com/2009/09/groovy-goodness-check-for-object.html
        owners2.is(owners1) == false

        owners1.size() == numOwners
        owners1.every { it.activity.ownerId == null }
        owners1.every { it.activity.monthString == null }
        owners1.every { it.activity.monthObj == null }

        owners2.size() == numOwners
        owners2.every { it.activity.ownerId == it.id }
        owners2.every { it.activity.monthString == monthString }
        owners2.every { it.activity.monthObj == monthObj }
    }

    @DirtiesRuntime
    void "test ensuring all months are present chronlogically within a list of activity records + has no side effects"() {
        given:
        DateTime now = DateTime.now()
        RecordItem mockItem = GroovyStub() { getWhenCreated() >> now.minusMonths(2) }
        RecordItem.metaClass."static".first = { String propName -> mockItem }

        ActivityRecord actNow = new ActivityRecord(monthString: JodaUtils.QUERY_MONTH_FORMAT.print(now)),
            actNowMinusOne = new ActivityRecord(monthString: JodaUtils.QUERY_MONTH_FORMAT.print(now.minusMonths(1))),
            actNowMinusTwo = new ActivityRecord(monthString: JodaUtils.QUERY_MONTH_FORMAT.print(now.minusMonths(2))),
            actNowMinusEight = new ActivityRecord(monthString: JodaUtils.QUERY_MONTH_FORMAT.print(now.minusMonths(8))),
            actNowPlusOne = new ActivityRecord(monthString: JodaUtils.QUERY_MONTH_FORMAT.print(now.plusMonths(1)))

        when:
        List<ActivityRecord> activities = UsageUtils.ensureMonths(null)

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
        List<ActivityRecord> outOfOrderActivities =
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
