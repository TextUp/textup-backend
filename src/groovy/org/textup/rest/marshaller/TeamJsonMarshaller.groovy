package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
class TeamJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        LinkGenerator linkGenerator, Team t1 ->

        Map json = [:]
        json.with {
            id = t1.id
            name = t1.name
            org = t1.org.id // MUST BE id or else you have circular reference in json
            hexColor = t1.hexColor
            phone = t1.phone
            hasInactivePhone = t1.hasInactivePhone
            location = t1.location
            numMembers = t1.activeMembers.size()
        }
        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"team", action:"show", id:t1.id, absolute:false)]
        json
    }

    TeamJsonMarshaller() {
        super(Team, marshalClosure)
    }
}
