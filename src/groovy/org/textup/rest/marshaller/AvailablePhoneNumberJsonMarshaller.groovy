package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.validator.AvailablePhoneNumber

@GrailsCompileStatic
class AvailablePhoneNumberJsonMarshaller extends JsonNamedMarshaller {

    static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, AvailablePhoneNumber aNum ->

        Map json = [phoneNumber:aNum.e164PhoneNumber]
        json[aNum.infoType] = aNum.info
        json
    }

    AvailablePhoneNumberJsonMarshaller() {
        super(AvailablePhoneNumber, marshalClosure)
    }
}
