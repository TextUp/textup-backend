package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.type.StaffStatus

@GrailsTypeChecked
class OrganizationJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        LinkGenerator linkGenerator, Organization org ->

        Map json = [:]
        json.with {
            id = org.id
            name = org.name
            location = org.location
        }
        Staff s1 = authService.getLoggedInAndActive()
        // only show this private information if the logged-in user is (1) active and
        // (2) a member of this organization
        if (s1?.org && s1.org.id == org.id) {
            json.with {
                status = org.status.toString()
                timeout = org.timeout
                teams = org.getTeams()
                numAdmins = org.countPeople(statuses:[StaffStatus.ADMIN])
            }
        }
        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"organization", action:"show", id:org.id, absolute:false)]
        json
    }

    OrganizationJsonMarshaller() {
        super(Organization, marshalClosure)
    }
}
