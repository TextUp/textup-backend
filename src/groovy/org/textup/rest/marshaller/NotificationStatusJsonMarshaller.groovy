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

		// Omit phone number to contact. Notification statuses are always nested in either
		// a ContactTag or a Contactable. Therefore, the phone number to send the notification
		// to is obvious and we can save a db call here and omit the number.
		json.with {
			id = s1.id
			name = s1.name
			username = s1.username
			canNotify = status1.canNotify
		}

		json
	}

	NotificationStatusJsonMarshaller() {
		super(NotificationStatus, marshalClosure)
	}
}

