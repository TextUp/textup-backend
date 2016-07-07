package org.textup.rest.marshallers

import grails.compiler.GrailsCompileStatic
import grails.plugin.springsecurity.SpringSecurityService
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*

@GrailsCompileStatic
class FutureMessageJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { String namespace,
        SpringSecurityService springSecurityService, AuthService authService,
        LinkGenerator linkGenerator, FutureMessage fMsg ->

        Map json = [:]
        json.with {
        	whenCreated = fMsg.whenCreated
			startDate = fMsg.startDate
			notifySelf = fMsg.notifySelf
			type = fMsg.type.toString()
			message = fMsg.message
            isDone = fMsg.isReallyDone
			if (fMsg.isRepeating) {
				repeatIntervalInDays = fMsg.repeatIntervalInDays
				if (fMsg.willEndOnDate) {
					endDate = fMsg.endDate
				}
				else { repeatCount = fMsg.repeatCount }

				if (fMsg.timesTriggered) timesTriggered = fMsg.timesTriggered
				if (fMsg.nextFireDate) nextFireDate = fMsg.nextFireDate
			}
        }
        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"futureMessage", action:"show", id:fMsg.id, absolute:false)]
        json
	}

	FutureMessageJsonMarshaller() {
		super(FutureMessage, marshalClosure)
	}
}
