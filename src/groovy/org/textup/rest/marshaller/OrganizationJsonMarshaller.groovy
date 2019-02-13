package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.validation.ConstrainedProperty
import org.textup.*
import org.textup.rest.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
class OrganizationJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { ReadOnlyOrganization rorg1 ->
        Map<String, ConstrainedProperty> constraints = Organization.constraints as Map

        Map json = [:]
        json.with {
            id       = rorg1.id
            links    = MarshallerUtils.buildLinks(RestUtils.RESOURCE_ORGANIZATION, rorg1.id)
            location = rorg1.readOnlyLocation
            name     = rorg1.name
        }
        AuthUtils.tryGetActiveAuthUser().thenEnd { Staff authUser ->
            // only show this private information if the logged-in user is
            // (1) active and (2) a member of this organization
            if (authUser.org.id == rorg1.id) {
                json.with {
                    awayMessageSuffix          = rorg1.awayMessageSuffix
                    awayMessageSuffixMaxLength = constraints.awayMessageSuffix.size.to
                    numAdmins                  = Staffs.buildForOrgIdAndOptions(rorg1.id, null, [StaffStatus.ADMIN]).count()
                    status                     = rorg1.status.toString()
                    teams                      = Teams.buildActiveForOrgIds([rorg1.id]).list()
                    timeout                    = rorg1.timeout
                    timeoutMax                 = constraints.timeout.max
                    timeoutMin                 = constraints.timeout.min
                }
            }
        }
        json
    }

    OrganizationJsonMarshaller() {
        super(ReadOnlyOrganization, marshalClosure)
    }
}
