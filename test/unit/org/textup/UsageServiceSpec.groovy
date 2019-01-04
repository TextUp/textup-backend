package org.textup

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.joda.time.DateTime
import org.textup.test.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class UsageServiceSpec extends Specification {

    void "test activity record"() {
        given:
        String randVal = TestUtils.randString()
        DateTime dt = DateTime.now()
        String queryMonth = DateTimeUtils.QUERY_MONTH_FORMAT.print(dt)

        when: "empty"
        UsageService.ActivityRecord a1 = new UsageService.ActivityRecord()

        then:
        a1.ownerId == null
        a1.monthString == null
        a1.monthObj == null
        a1.numActivePhones == 0
        a1.numNotificationTexts == 0
        a1.numOutgoingTexts == 0
        a1.numOutgoingSegments == 0
        a1.numIncomingTexts == 0
        a1.numIncomingSegments == 0
        a1.numVoicemailMinutes == 0
        a1.numBillableVoicemailMinutes == 0
        a1.numOutgoingCalls == 0
        a1.numOutgoingMinutes == 0
        a1.numOutgoingBillableMinutes == 0
        a1.numIncomingCalls == 0
        a1.numIncomingMinutes == 0
        a1.numIncomingBillableMinutes == 0

        a1.cost == 0
        a1.numTexts == 0
        a1.numSegments == 0
        a1.textCost == 0
        a1.numCalls == 0
        a1.numMinutes == 0
        a1.numBillableMinutes == 0
        a1.callCost == 0

        when: "setting month string directly"
        a1.setMonthStringDirectly(randVal)

        then:
        a1.monthString == randVal
        a1.monthObj == null

        when: "using custom month string setter"
        a1.monthString = queryMonth

        then:
        a1.monthString == DateTimeUtils.DISPLAYED_MONTH_FORMAT.print(dt)
        a1.monthObj == dt.withTimeAtStartOfDay().withDayOfMonth(1)

        when: "set other properties"
        a1.with {
            numOutgoingTexts = 5
            numIncomingTexts = 5

            numOutgoingSegments = 2
            numIncomingSegments = 3

            numNotificationTexts = 3

            numOutgoingCalls = 8
            numIncomingCalls = 5

            numOutgoingMinutes = 3
            numIncomingMinutes = 2

            numOutgoingBillableMinutes = 6
            numIncomingBillableMinutes = 9

            numBillableVoicemailMinutes = 8
        }

        then:
        a1.numTexts == 10
        a1.numSegments == 5
        a1.textCost ==
            (a1.numOutgoingSegments + a1.numIncomingSegments + a1.numNotificationTexts) * Constants.UNIT_COST_TEXT
        a1.numCalls == 13
        a1.numMinutes == 5
        a1.numBillableMinutes == 15
        a1.callCost ==
            (a1.numBillableVoicemailMinutes + a1.numOutgoingBillableMinutes + a1.numIncomingBillableMinutes) * Constants.UNIT_COST_CALL
        a1.cost == a1.textCost + a1.callCost
    }

    void "sorting activity record"() {
        given:
        UsageService.ActivityRecord a1 = new UsageService.ActivityRecord(monthObj: DateTime.now())
        UsageService.ActivityRecord a2 = new UsageService.ActivityRecord(monthObj: DateTime.now().minusHours(3))
        UsageService.ActivityRecord a3 = new UsageService.ActivityRecord(monthObj: DateTime.now().plusHours(3))

        expect:
        [a1, a2, a3].sort() == [a2, a1, a3]
    }

    void "test has activity"() {
        when: "empty"
        UsageService.HasActivity hasAct1 = new UsageService.HasActivity()
        UsageService.ActivityRecord a1 = Mock()

        then:
        hasAct1.id == null
        hasAct1.activity instanceof UsageService.ActivityRecord
        hasAct1.totalCost == Constants.UNIT_COST_NUMBER

        when: "set properties"
        hasAct1.activity = a1
        BigDecimal totalCost = hasAct1.totalCost

        then:
        1 * a1.getTextCost() >> 8
        1 * a1.getCallCost() >> 5
        totalCost == 8  + 5 + Constants.UNIT_COST_NUMBER

        when: "cloning"
        hasAct1.id == 888
        UsageService.HasActivity hasAct2 = hasAct1.clone()

        then:
        hasAct2.id == hasAct1.id
    }

    void "test organization"() {
        when: "empty"
        UsageService.Organization org1 = new UsageService.Organization()
        UsageService.ActivityRecord a1 = Mock()

        then:
        org1.name == null
        org1.totalNumPhones == 0
        org1 instanceof UsageService.HasActivity
        org1.totalCost == 0

        when: "set properties"
        org1.totalNumPhones = 8
        org1.activity = a1
        BigDecimal totalCost = org1.totalCost

        then:
        1 * a1.getTextCost() >> 8
        1 * a1.getCallCost() >> 5
        totalCost == 8 + 5 + (8 * Constants.UNIT_COST_NUMBER)
    }

    void "test staff"() {
        when: "empty"
        UsageService.Staff s1 = new UsageService.Staff()

        then:
        s1.name == null
        s1.username == null
        s1.email == null
        s1.number == null
        s1 instanceof UsageService.HasActivity
        s1.phoneNumber instanceof PhoneNumber
        s1.phoneNumber.validate() == false

        when: "set properties"
        s1.number = TestUtils.randPhoneNumberString()

        then:
        s1.phoneNumber.validate() == true
    }

    void "test team"() {
        when: "empty"
        UsageService.Team t1 = new UsageService.Team()

        then:
        t1.name == null
        t1.numStaff == 0
        t1.number == null
        t1 instanceof UsageService.HasActivity
        t1.phoneNumber instanceof PhoneNumber
        t1.phoneNumber.validate() == false

        when: "set properties"
        t1.number = TestUtils.randPhoneNumberString()

        then:
        t1.phoneNumber.validate() == true
    }
}
