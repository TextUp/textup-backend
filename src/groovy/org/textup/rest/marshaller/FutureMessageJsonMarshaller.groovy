package org.textup.rest.marshaller

import grails.compiler.GrailsCompileStatic
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.textup.*
import org.textup.rest.*
import org.textup.type.LogLevel

@GrailsCompileStatic
class FutureMessageJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { String namespace, GrailsApplication grailsApplication,
        LinkGenerator linkGenerator, ReadOnlyFutureMessage fMsg ->

        List<String> uploadErrors = Collections.emptyList()
        Helpers.<List<String>>tryGetFromRequest(Constants.REQUEST_UPLOAD_ERRORS)
            .logFail("FutureMessageJsonMarshaller: no available request", LogLevel.DEBUG)
            .then { List<String> errors -> uploadErrors = errors }

        Map json = [:]
        if (uploadErrors) {
            json.uploadErrors = uploadErrors
        }
        json.with {
        	id = fMsg.id
        	whenCreated = fMsg.whenCreated
			startDate = fMsg.startDate
			notifySelf = fMsg.notifySelf
			type = fMsg.type.toString()
			message = fMsg.message
            isDone = fMsg.isReallyDone
            isRepeating = fMsg.isRepeating
            language = fMsg.language?.toString()
            media = fMsg.readOnlyMedia
            if (fMsg.nextFireDate) nextFireDate = fMsg.nextFireDate
			if (fMsg.isRepeating) {
				if (fMsg.endDate) endDate = fMsg.endDate
				hasEndDate = !!fMsg.endDate
				// repeating specific to simple schedule
				if (fMsg instanceof ReadOnlySimpleFutureMessage) {
					repeatIntervalInDays = fMsg.repeatIntervalInDays
					if (fMsg.repeatCount) repeatCount = fMsg.repeatCount
					if (fMsg.timesTriggered) timesTriggered = fMsg.timesTriggered
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
		super(ReadOnlyFutureMessage, marshalClosure)
	}
}
