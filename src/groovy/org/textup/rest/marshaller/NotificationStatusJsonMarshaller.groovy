package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import groovy.util.logging.Log4j
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
@Log4j
class NotificationStatusJsonMarshaller extends JsonNamedMarshaller {
	static final Closure marshalClosure = { String namespace,
		SpringSecurityService springSecurityService, AuthService authService,
		LinkGenerator linkGenerator, NotificationStatus status1 ->

		Map json = [:]
		Staff s1 = status1.staff

		json.with {
			id = s1.id
			name = s1.name
			username = s1.username
			number = s1.phone.number.e164PhoneNumber
			canNotify = status1.canNotify
		}

		json
	}

	NotificationStatusJsonMarshaller() {
		super(NotificationStatus, marshalClosure)
	}
}

