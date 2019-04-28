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

        AuthUtils.tryGetActiveAuthUser().thenEnd { Staff authUser ->
            Long orgId = p1.owner.buildOrganization()?.id
            Collection<Staff> allStaffs = p1.owner.buildAllStaff()
            // only show owner policy info if the logged-in user is actually an owner
            if (Organizations.isAdminAt(orgId, authUser) || allStaffs.contains(authUser)) {
                Collection<ReadOnlyOwnerPolicy> allPolicies = allStaffs.collect { Staff s1 ->
                    OwnerPolicies.findReadOnlyOrDefaultForOwnerAndStaff(p1.owner, s1)
                }
                json.with {
                    allowSharingWithOtherTeams = p1.owner.allowSharingWithOtherTeams
                    tags                       = GroupPhoneRecords.buildForPhoneIdAndOptions(p1.id).list()
                    policies                   = allPolicies
                }
            }
        }

        json
    }

    PhoneJsonMarshaller() {
        super(Phone, marshalClosure)
    }
}
