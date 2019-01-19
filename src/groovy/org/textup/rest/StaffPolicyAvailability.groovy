package org.textup.rest

import org.textup.*
import grails.compiler.GrailsTypeChecked

// TODO remove this class

@GrailsTypeChecked
class StaffPolicyAvailability {

    String jointId
    String name

    Boolean useStaffAvailability

    Boolean manualSchedule
    Boolean isAvailable

    Boolean isAvailableNow
    Schedule schedule

    StaffPolicyAvailability(Phone p1, Staff s1, boolean includeStaffIfNoPolicy) {
        if (!p1 || !s1) {
            return
        }

        jointId = buildJointId(p1, s1)
        name = s1.name

        OwnerPolicy np1 = p1.owner.findPolicyForStaff(s1.id)
        if (np1) {
            useStaffAvailability = np1.useStaffAvailability
            initAvailability(np1, true)
        }
        else { // default to staff availability if no policy
            useStaffAvailability = true
            initAvailability(s1, includeStaffIfNoPolicy)
        }
    }

    // TODO remove Schedulable
    protected void initAvailability(Schedulable sched1, boolean includeDetails) {
        isAvailableNow = sched1.isAvailableNow()

        if (includeDetails) {
            manualSchedule = sched1.manualSchedule
            isAvailable = sched1.isAvailable
            schedule = sched1.schedule
        }
    }
    protected String buildJointId(Phone p1, Staff s1) {
        "phone-${p1.id}-staff-${s1.id}"
    }
}
