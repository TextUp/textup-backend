package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.codehaus.groovy.grails.web.util.WebUtils
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
@Log4j
class ContactableJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, Contactable c1 ->

        Map json = [:]
        // add general Contactable fields
        json.id = c1.contactId
        json.lastRecordActivity = c1.lastRecordActivity
        if (c1.name) {
            json.name = c1.name
        }
        if (c1.note) {
            json.note = c1.note
        }
        json.numbers = c1.sortedNumbers.collect { ContactNumber num -> [number:num.prettyPhoneNumber] }
        json.futureMessages = c1.getFutureMessages()
        json.notificationStatuses = c1.getNotificationStatuses()
        // add fields specific to Contacts or SharedContacts
        if (c1.instanceOf(Contact)) {
            Contact contact = c1 as Contact
            json.tags = contact.getTags()
            json.sessions = contact.getSessions()
            json.sharedWith = contact.sharedContacts.collect { SharedContact sc ->
                [
                    id:sc.id,
                    whenCreated:sc.whenCreated,
                    permission:sc.permission.toString(),
                    sharedWith:sc.sharedWith.id // use the PHONE's id
                ]
            }
            json.phone = contact.phone.id
            json.status = c1.status?.toString()
        }
        else if (c1.instanceOf(SharedContact)) {
            SharedContact sc = c1 as SharedContact
            json.permission = sc.permission.toString()
            json.startedSharing = sc.whenCreated
            json.sharedBy = sc.sharedBy.name
            json.sharedById = sc.sharedBy.id
            json.phone = sc.sharedWith.id
            json.status = sc.status?.toString()
        }
        else {
            log.error("ContactableJsonMarshaller: passed in Contactable $c1 is \
                not an instance of either Contact or SharedContact")
        }
        // add links
        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"contact", action:"show", id:c1.contactId, absolute:false)]
        json
    }

    ContactableJsonMarshaller() {
        super(Contactable, marshalClosure)
    }
}
