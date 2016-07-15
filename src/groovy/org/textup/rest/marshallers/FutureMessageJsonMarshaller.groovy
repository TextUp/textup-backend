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
        	id = fMsg.id
        	whenCreated = fMsg.whenCreated
			startDate = fMsg.startDate
			notifySelf = fMsg.notifySelf
			type = fMsg.type.toString()
			message = fMsg.message
            isDone = fMsg.isReallyDone
			if (fMsg.isRepeating) {
				if (fMsg.endDate) endDate = fMsg.endDate
				if (fMsg.nextFireDate) nextFireDate = fMsg.nextFireDate
				// repeating specific to simple schedule
				if (fMsg.instanceOf(SimpleFutureMessage)) {
					SimpleFutureMessage sMsg = fMsg as SimpleFutureMessage
					repeatIntervalInDays = sMsg.repeatIntervalInDays
					if (sMsg.repeatCount) repeatCount = sMsg.repeatCount
					if (sMsg.timesTriggered) timesTriggered = sMsg.timesTriggered
				}
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
