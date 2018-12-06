package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.validator.Notification

@GrailsTypeChecked
class NotificationJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { Notification notif ->

        Map json = [:]
        json.with {
        	id = notif.tokenId
        	ownerType = notif.ownerType
			ownerId = notif.ownerId
			ownerName = notif.owner.buildName()
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
