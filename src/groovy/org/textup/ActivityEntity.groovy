package org.textup

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class ActivityEntity {

    @AutoClone
    static class HasActivity {
        Number id
        ActivityRecord activity = new ActivityRecord()

        BigDecimal getTotalCost() {
            activity.textCost + activity.callCost + UsageUtils.UNIT_COST_NUMBER
        }
    }

    static class Organization extends ActivityEntity.HasActivity {
        String name
        Number totalNumPhones = new Integer(0)

        @Override
        BigDecimal getTotalCost() {
            activity.textCost + activity.callCost + (totalNumPhones * UsageUtils.UNIT_COST_NUMBER)
        }
    }

    static class Staff extends ActivityEntity.HasActivity {
        String name
        String username
        String email
        String number

        PhoneNumber getPhoneNumber() { PhoneNumber.create(number) }
    }

    static class Team extends ActivityEntity.HasActivity {
        String name
        Number numStaff = new Integer(0)
        String number

        PhoneNumber getPhoneNumber() { PhoneNumber.create(number) }
    }
}
