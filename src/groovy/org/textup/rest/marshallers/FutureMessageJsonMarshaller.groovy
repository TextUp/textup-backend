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
            isRepeating = fMsg.isRepeating
            if (fMsg.nextFireDate) nextFireDate = fMsg.nextFireDate
			if (fMsg.isRepeating) {
				if (fMsg.endDate) endDate = fMsg.endDate
				hasEndDate = !!fMsg.endDate
				// repeating specific to simple schedule
				if (fMsg.instanceOf(SimpleFutureMessage)) {
					SimpleFutureMessage sMsg = fMsg as SimpleFutureMessage
					repeatIntervalInDays = sMsg.repeatIntervalInDays
					if (sMsg.repeatCount) repeatCount = sMsg.repeatCount
					if (sMsg.timesTriggered) timesTriggered = sMsg.timesTriggered
				}
			}
        }
        // find owner, may be a contact or a tag
        json.contact = Contact.findByRecord(fMsg.record)?.id
        if (!json.contact) {
        	json.tag = ContactTag.findByRecord(fMsg.record)?.id
        }
        json.links = [:] << [self:linkGenerator.link(namespace:namespace,
            resource:"futureMessage", action:"show", id:fMsg.id, absolute:false)]
        json
	}

	FutureMessageJsonMarshaller() {
		super(FutureMessage, marshalClosure)
	}
}
