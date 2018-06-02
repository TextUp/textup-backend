package org.textup.rest

import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.mixin.TestMixin
import org.textup.*
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class StaffPolicyAvailabilitySpec {

    void "test creation of staff policy availability"() {
        given: "a staff, phone, and notification policy"
        Long staffScheduleId = 1,
            policyScheduleId = 2,
            staffId = 3
        Staff s1 = new Staff(name: UUID.randomUUID().toString(), manualSchedule: true,
            isAvailable: false, schedule: new Schedule())
        NotificationPolicy np1 = new NotificationPolicy(staffId: staffId, useStaffAvailability: false,
            manualSchedule: true, isAvailable: true, schedule: new Schedule())
        Phone phoneWithPolicy = new Phone(owner: new PhoneOwnership(policies: [np1])),
            phoneNoPolicy = new Phone(owner: new PhoneOwnership())
        StaffPolicyAvailability spa1

        // have to manually set the id's on domain objects; cannot use the map constructor
        s1.id = staffId
        s1.schedule.id = staffScheduleId
        np1.schedule.id = policyScheduleId

        when: "not all required info provided"
        spa1 = new StaffPolicyAvailability(null, s1, true)

        then: "no initialization happens"
        spa1.jointId == null
        spa1.name == null
        spa1.schedule == null
        spa1.useStaffAvailability == null
        spa1.manualSchedule == null
        spa1.isAvailable == null
        spa1.isAvailableNow == null

        when: "policy doesn't exist + yes include staff if no policy"
        spa1 = new StaffPolicyAvailability(phoneNoPolicy, s1, true)

        then:
        spa1.jointId instanceof String
        spa1.jointId.contains("phone-")
        spa1.jointId.contains("staff-")
        spa1.name == s1.name
        spa1.schedule instanceof Schedule
        spa1.schedule.id == staffScheduleId
        spa1.useStaffAvailability == true
        spa1.manualSchedule == true
        spa1.isAvailable == false
        spa1.isAvailableNow == false

        when: "policy doesn't exist + no include staff if no policy"
        spa1 = new StaffPolicyAvailability(phoneNoPolicy, s1, false)

        then:
        spa1.jointId instanceof String
        spa1.jointId.contains("phone-")
        spa1.jointId.contains("staff-")
        spa1.name == s1.name
        spa1.schedule == null
        spa1.useStaffAvailability == true
        spa1.manualSchedule == null
        spa1.isAvailable == null
        spa1.isAvailableNow == false

        when: "policy does exist + yes include staff if no policy"
        spa1 = new StaffPolicyAvailability(phoneWithPolicy, s1, true)

        then:
        spa1.jointId instanceof String
        spa1.jointId.contains("phone-")
        spa1.jointId.contains("staff-")
        spa1.name == s1.name
        spa1.schedule instanceof Schedule
        spa1.schedule.id == policyScheduleId
        spa1.useStaffAvailability == false
        spa1.manualSchedule == true
        spa1.isAvailable == true
        spa1.isAvailableNow == true

        when: "policy does exist + no include staff if no policy"
        spa1 = new StaffPolicyAvailability(phoneWithPolicy, s1, false)

        then:
        spa1.jointId instanceof String
        spa1.jointId.contains("phone-")
        spa1.jointId.contains("staff-")
        spa1.name == s1.name
        spa1.schedule instanceof Schedule
        spa1.schedule.id == policyScheduleId
        spa1.useStaffAvailability == false
        spa1.manualSchedule == true
        spa1.isAvailable == true
        spa1.isAvailableNow == true
    }
}
