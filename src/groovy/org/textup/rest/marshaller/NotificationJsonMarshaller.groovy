package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.validator.Notification

@GrailsCompileStatic
class NotificationJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, Notification notif ->

        Map json = [:]
        json.with {
        	id = notif.tokenId
        	ownerType = notif.ownerType
			ownerId = notif.ownerId
			ownerName = notif.owner.name
			ownerNumber = notif.owner.phone.number.e164PhoneNumber
			otherType = notif.otherType
			otherId = notif.otherId
			otherName = notif.otherName
			contents = notif.contents
			outgoing = notif.outgoing
        }
    	json
	}

	NotificationJsonMarshaller() {
		super(Notification, marshalClosure)
	}
}
