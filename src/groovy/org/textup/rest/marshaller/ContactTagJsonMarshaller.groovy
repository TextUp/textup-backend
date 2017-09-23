package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.type.ContactStatus

@GrailsCompileStatic
class ContactTagJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, ContactTag ct ->

        Map json = [:]
        json.with {
            id = ct.id
            name = ct.name
            hexColor = ct.hexColor
            lastRecordActivity = ct.record.lastRecordActivity
            numMembers = ct.getMembersByStatus([ContactStatus.ACTIVE, ContactStatus.UNREAD]).size()
            futureMessages = ct.record.getFutureMessages()
            notificationStatuses = ct.getNotificationStatuses()
            phone = ct.phone.id
        }
        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"tag", action:"show", id:ct.id, absolute:false)]
        json
    }

    ContactTagJsonMarshaller() {
        super(ContactTag, marshalClosure)
    }
}
