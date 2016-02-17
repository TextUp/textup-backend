package org.textup.rest.marshallers

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.types.PhoneOwnershipType

@GrailsCompileStatic
class AnnouncementJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, FeaturedAnnouncement announce ->

        Map json = [:]
        json.with {
            id = announce.id
            isExpired = announce.isExpired
            expiresAt = announce.expiresAt
            message = announce.message
            whenCreated = announce.whenCreated
            numReceipts = announce.numReceipts
            numCallReceipts = announce.numCallReceipts
            numTextReceipts = announce.numTextReceipts
        }
        if (announce.owner.owner.type == PhoneOwnershipType.INDIVIDUAL) {
            json.staff = announce.owner.owner.ownerId
        }
        else {
            json.team = announce.owner.owner.ownerId
        }

        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"announcement", action:"show", id:announce.id, absolute:false)]
        json
    }

    AnnouncementJsonMarshaller() {
        super(FeaturedAnnouncement, marshalClosure)
    }
}
