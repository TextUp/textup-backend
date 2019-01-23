package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.validation.ConstrainedProperty
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class PhoneJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { Phone p1 ->
        Map<String, ConstrainedProperty> constraints = Phone.constraints as Map

        Map json = [:]
        json.with {
            awayMessage                    = p1.awayMessage
            awayMessageMaxLength           = constraints.awayMessage.size.to
            id                             = p1.id
            isActive                       = p1.isActive()
            language                       = p1.language.toString()
            media                          = p1.media
            number                         = p1.number
            useVoicemailRecordingIfPresent = p1.useVoicemailRecordingIfPresent
            voice                          = p1.voice.toString()
        }

        AuthUtils.tryGetAuthId().then { Long authId ->
            Collection<Long> allStaffIds = p1.owner.buildAllStaff()*.id
            // only show owner policy info if the logged-in user is actually an owner
            if (allStaffIds.contains(authId)) {
                json.with {
                    allowSharingWithOtherTeams = p1.owner.allowSharingWithOtherTeams
                    owner                      = OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, authId).payload
                    tags                       = GroupPhoneRecords.buildForPhoneIdAndOptions([p1.id]).list()
                    (allStaffIds - authId).each { Long sId ->
                        others = OwnerPolicies.tryFindOrCreateForOwnerAndStaffId(p1.owner, sId).payload
                    }
                }
            }
        }

        json
    }

    PhoneJsonMarshaller() {
        super(Phone, marshalClosure)
    }
}
