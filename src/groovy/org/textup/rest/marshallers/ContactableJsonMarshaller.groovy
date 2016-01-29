package org.textup.rest.marshallers

import grails.plugin.springsecurity.SpringSecurityService
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.codehaus.groovy.grails.web.util.WebUtils
import org.textup.*
import org.textup.rest.*

@Log4j
class ContactableJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, Contactable c1 ->

        Map json = [:]
        // add general Contactable fields
        json.id = c1.contactId
        json.lastRecordActivity = c1.lastRecordActivity
        if (c1.name) json.name = c1.name
        if (c1.note) json.note = c1.note
        if (c1.status) json.status = c1.status
        json.numbers = c1.numbers.collect { ContactNumber num ->
            [
                id:num.id,
                number:num.prettyPhoneNumber,
                preference:num.preference,
                contactId:c1.contactId
            ]
        }
        // add fields specific to Contacts or SharedContacts
        if (c1.instanceOf(Contact)) {
            json.tags = c1.tags
            json.sharedWith = c1.sharedContacts.collect { SharedContact sc ->
                [
                    id:sc.id,
                    dateCreated:sc.dateCreated,
                    permission:sc.permission,
                    sharedWith:sc.sharedWith.name
                ]
            }
        }
        else if (c1.instanceOf(SharedContact)) {
            SharedContact sc = c1 as SharedContact
            json.permission = sc.permission
            json.startedSharing = sc.dateCreated
            json.sharedWith = sc.sharedWith.name
            json.sharedWithId = sc.sharedWith.id
        }
        else {
            log.error("ContactableJsonMarshaller: passed in Contactable $c1 is \
                not an instance of either Contact or SharedContact")
        }
        // add links
        json.links = [:]
        json.links << [self:linkGenerator.link(namespace:namespace,
            resource:"contact", action:"show", id:c1.contactId, absolute:false)]
        json
    }

    ContactableJsonMarshaller() {
        super(Contactable, marshalClosure)
    }
}
