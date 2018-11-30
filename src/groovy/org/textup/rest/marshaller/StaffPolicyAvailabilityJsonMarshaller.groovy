package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import org.textup.*
import org.textup.rest.*

@GrailsTypeChecked
@Log4j
class StaffPolicyAvailabilityJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { StaffPolicyAvailability sp1 ->

        [
            id: sp1.jointId,
            name: sp1.name,
            useStaffAvailability: sp1.useStaffAvailability,
            manualSchedule: sp1.manualSchedule,
            isAvailable: sp1.isAvailable,
            isAvailableNow: sp1.isAvailableNow,
            schedule: sp1.schedule
        ]
    }

    StaffPolicyAvailabilityJsonMarshaller() {
        super(StaffPolicyAvailability, marshalClosure)
    }
}
