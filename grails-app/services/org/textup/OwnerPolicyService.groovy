package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.annotation.*
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OwnerPolicyService {

    ScheduleService scheduleService

    Result<OwnerPolicy> tryUpdate(OwnerPolicy op1, TypeMap body, String timezone) {
        trySetFields(op1, body)
            .then { scheduleService.tryUpdate(op1.schedule, body.typeMapNoNull("schedule"), timezone) }
            .then { DomainUtils.trySave(op1) }
    }

    // Helpers
    // -------

    protected Result<OwnerPolicy> trySetFields(OwnerPolicy op1, TypeMap body) {
        if (op1 && body) {
            op1.with {
                if (body.frequency) frequency = body.enum(NotificationFrequency, "frequency")
                if (body.level) level = body.enum(NotificationLevel, "level")
                if (body.method) method = body.enum(NotificationMethod, "method")
                if (body.boolean("shouldSendPreviewLink") != null) {
                    shouldSendPreviewLink = body.boolean("shouldSendPreviewLink")
                }
            }
        }
        DomainUtils.trySave(op1)
    }
}
