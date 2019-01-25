package org.textup

import groovy.transform.EqualsAndHashCode
import grails.compiler.GrailsTypeChecked
import groovy.transform.AutoClone
import org.textup.util.*

@GrailsTypeChecked
class ActivityEntity {

    @AutoClone
    @EqualsAndHashCode
    static class HasActivity {
        Number id
        ActivityRecord activity = new ActivityRecord()

        BigDecimal getTotalCost() {
            activity.textCost + activity.callCost + UsageUtils.UNIT_COST_NUMBER
        }
    }

    @EqualsAndHashCode
    static class Organization extends ActivityEntity.HasActivity {
        String name
        Number totalNumPhones = new Integer(0)

        @Override
        BigDecimal getTotalCost() {
            activity.textCost + activity.callCost + (totalNumPhones * UsageUtils.UNIT_COST_NUMBER)
        }
    }

    @EqualsAndHashCode
    static class Staff extends ActivityEntity.HasActivity {
        String name
        String username
        String email
        Number phoneId

        String getNumbers() {
            UsageUtils.buildNumbersStringForMonth(phoneId, activity?.monthObj)
        }
    }

    @EqualsAndHashCode
    static class Team extends ActivityEntity.HasActivity {
        String name
        Number numStaff = new Integer(0)
        Number phoneId

        String getNumbers() {
            UsageUtils.buildNumbersStringForMonth(phoneId, activity?.monthObj)
        }
    }
}
