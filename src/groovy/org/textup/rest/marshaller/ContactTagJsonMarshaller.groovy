package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.type.PhoneRecordStatus

@GrailsTypeChecked
class ContactTagJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        LinkGenerator linkGenerator, ContactTag ct ->

        Map json = [:]
        json.with {
            id = ct.id
            name = ct.name
            hexColor = ct.hexColor
            lastRecordActivity = ct.record.lastRecordActivity
            numMembers = ct.getMembersByStatus([PhoneRecordStatus.ACTIVE, PhoneRecordStatus.UNREAD]).size()
            futureMessages = ct.record.getFutureMessages()
            notificationStatuses = ct.getNotificationStatuses()
            language = ct.language?.toString()
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
