package org.textup.rest.marshallers

import org.textup.*
import org.textup.rest.*
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import grails.plugin.springsecurity.SpringSecurityService

class ContactTagJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace, 
        SpringSecurityService springSecurityService, AuthService authService, 
        LinkGenerator linkGenerator, ContactTag ct ->
        
        Map json = [:]
        json.with {
            id = ct.id
            name = ct.name
            hexColor = ct.hexColor
            if (ct.instanceOf(TeamContactTag)) {
                hasRecord = true
                lastRecordActivity = ct.lastRecordActivity
            }
        }
        
        json.links = [:]
        json.links << [self:linkGenerator.link(namespace:namespace, resource:"tag", action:"show", id:ct.id, absolute:false)]    
        json
    }

    ContactTagJsonMarshaller() {
        super(ContactTag, marshalClosure)
    }
}