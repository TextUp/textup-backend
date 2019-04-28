package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.Sortable
import org.joda.time.DateTime
import org.textup.util.*

@EqualsAndHashCode
@GrailsTypeChecked
@Sortable(includes = ["monthObj"])
class ActivityRecord {

    Number ownerId
    String monthString
    DateTime monthObj
    Number numActivePhones = new Integer(0)

    Number numNotificationTexts = new Integer(0)

    Number numOutgoingTexts = new Integer(0)
    Number numOutgoingSegments = new Integer(0)
    Number numIncomingTexts = new Integer(0)
    Number numIncomingSegments = new Integer(0)

    Number numVoicemailMinutes = new Integer(0)
    Number numBillableVoicemailMinutes = new Integer(0)

    Number numOutgoingCalls = new Integer(0)
    Number numOutgoingMinutes = new Integer(0)
    Number numOutgoingBillableMinutes = new Integer(0)
    Number numIncomingCalls = new Integer(0)
    Number numIncomingMinutes = new Integer(0)
    Number numIncomingBillableMinutes = new Integer(0)

    void setMonthString(String queryMonth) {
        monthString = UsageUtils.queryMonthToMonthString(queryMonth)
        monthObj = UsageUtils.monthStringToDateTime(monthString)
    }
    void setMonthStringDirectly(String val) {
        monthString = val
    }

    BigDecimal getCost() {
        textCost + callCost
    }

    BigDecimal getNumTexts() { numOutgoingTexts + numIncomingTexts }
    BigDecimal getNumSegments() { numOutgoingSegments + numIncomingSegments }
    BigDecimal getTextCost() {
        (numSegments + numNotificationTexts) * UsageUtils.UNIT_COST_TEXT
    }

    BigDecimal getNumCalls() { numOutgoingCalls + numIncomingCalls }
    BigDecimal getNumMinutes() { numOutgoingMinutes + numIncomingMinutes }
    BigDecimal getNumBillableMinutes() { numOutgoingBillableMinutes + numIncomingBillableMinutes }

    BigDecimal getCallCost() {
        (numBillableMinutes + numBillableVoicemailMinutes) * UsageUtils.UNIT_COST_CALL
    }
}
