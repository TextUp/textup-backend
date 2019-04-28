package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@TestMixin(GrailsUnitTestMixin)
class ActivityRecordSpec extends Specification {

    void "test activity record"() {
        given:
        String randVal = TestUtils.randString()
        DateTime dt = DateTime.now()
        String queryMonth = JodaUtils.QUERY_MONTH_FORMAT.print(dt)

        when: "empty"
        ActivityRecord a1 = new ActivityRecord()

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
        a1.monthString == JodaUtils.DISPLAYED_MONTH_FORMAT.print(dt)
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
            (a1.numOutgoingSegments + a1.numIncomingSegments + a1.numNotificationTexts) * UsageUtils.UNIT_COST_TEXT
        a1.numCalls == 13
        a1.numMinutes == 5
        a1.numBillableMinutes == 15
        a1.callCost ==
            (a1.numBillableVoicemailMinutes + a1.numOutgoingBillableMinutes + a1.numIncomingBillableMinutes) * UsageUtils.UNIT_COST_CALL
        a1.cost == a1.textCost + a1.callCost
    }

    void "sorting activity record"() {
        given:
        ActivityRecord a1 = new ActivityRecord(monthObj: DateTime.now())
        ActivityRecord a2 = new ActivityRecord(monthObj: DateTime.now().minusHours(3))
        ActivityRecord a3 = new ActivityRecord(monthObj: DateTime.now().plusHours(3))

        expect:
        [a1, a2, a3].sort() == [a2, a1, a3]
    }
}
