package org.textup.rest.marshallers

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.codehaus.groovy.grails.web.util.WebUtils
import org.joda.time.DateTime
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
class PhoneJsonMarshaller extends JsonNamedMarshaller {
    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, Phone p1 ->

        Map json = [:]
        json.with {
            id = p1.id
            number = p1.number.e164PhoneNumber
            awayMessage = p1.awayMessage
            tags = p1.getTags() ?: []
        }
        json
    }

    PhoneJsonMarshaller() {
        super(Phone, marshalClosure)
    }
}