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
        LinkGenerator linkGenerator, Contactable c ->
        
        Map json = [:]
        def thisId
        if (c.instanceOf(Contact)) { 
            Contact c1 = c as Contact 
            thisId = c1.id 
            ContactableJsonMarshaller.addContactFields(c1, json)
            json.sharedWith = SharedContact.nonexpiredForContact(c1).list().collect {
                Map scResult = [:]
                scResult.dateCreated = it.dateCreated
                scResult.permission = it.permission
                scResult.sharedWithId = it.sharedWith.ownerId
                scResult.sharedWith = Staff.get(it.sharedWith.ownerId)?.name
                scResult
            }
        }
        else if (c.instanceOf(SharedContact)) { 
            SharedContact sc = c as SharedContact
            thisId = sc.contact.id
            ContactableJsonMarshaller.addContactFields(sc.contact, json)
            json.permission = sc.permission
            json.sharedBy = Staff.get(sc.sharedBy.ownerId)?.name
            json.startedSharing = sc.dateCreated
        }
        else {
            log.error("ContactableJsonMarshaller: passed in Contactable $c is not an instance of either Contact or SharedContact")
        }

        json.links = [:]
        json.links << [self:linkGenerator.link(namespace:namespace, resource:"contact", action:"show", id:thisId, absolute:false)]
        json
    }

    static void addContactFields(Contact c1, Map jsonToAddTo) {
        jsonToAddTo.id = c1.id
        jsonToAddTo.lastRecordActivity = c1.lastRecordActivity
        if (c1.name) jsonToAddTo.name = c1.name 
        if (c1.note) jsonToAddTo.note = c1.note 
        if (c1.status) jsonToAddTo.status = c1.status
        List<ContactNumber> nums = c1.numbers 
        if (nums) {
            jsonToAddTo.numbers = nums.collect {
                [number:it.number, preference:it.preference]
            }
        }
        def request = WebUtils.retrieveGrailsWebRequest().currentRequest
        if (request.tagId != null) {
            TagMembership tm = TagMembership.forContactAndTagId(c1, request.tagId).list()[0]
            if (tm) jsonToAddTo.subscribed = tm.subscribed 
        }
    }

    ContactableJsonMarshaller() {
        super(Contactable, marshalClosure)
    }
}