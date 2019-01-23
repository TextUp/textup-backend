package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.validation.ConstrainedProperty
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.type.StaffStatus

@GrailsTypeChecked
class OrganizationJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { Organization org1 ->
        Map<String, ConstrainedProperty> constraints = Organization.constraints as Map

        Map json = [:]
        json.with {
            id       = org1.id
            links    = MarshallerUtils.buildLinks(RestUtils.RESOURCE_ORGANIZATION, org1.id)
            location = org1.location
            name     = org1.name
        }
        AuthUtils.tryGetAuthUser().then { Staff authUser ->
            // only show this private information if the logged-in user is
            // (1) active and (2) a member of this organization
            if (authUser.org.id == org1.id) {
                json.with {
                    awayMessageSuffix          = org1.awayMessageSuffix
                    awayMessageSuffixMaxLength = constraints.awayMessageSuffix.size.to
                    numAdmins                  = Staffs.buildForOrgIdAndOptions([org.id], null, [StaffStatus.ADMIN]).count()
                    status                     = org1.status.toString()
                    teams                      = Teams.buildForOrgIds([org.id]).list()
                    timeout                    = org1.timeout
                    timeoutMax                 = constraints.timeout.max
                    timeoutMin                 = constraints.timeout.min
                }
            }
        }
        json
    }

    OrganizationJsonMarshaller() {
        super(Organization, marshalClosure)
    }
}
