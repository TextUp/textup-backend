package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.validator.RedeemedNotification

@GrailsTypeChecked
class RedeemedNotificationJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { RedeemedNotification notif ->

        Map json = [:]
        // TODO restore
   //      json.with {
   //      	id = notif.tokenId
   //      	ownerType = notif.ownerType
			// ownerId = notif.ownerId
			// ownerName = notif.owner.buildName()
			// ownerNumber = notif.owner.phone.number.e164PhoneNumber

			// otherType = notif.otherType
			// otherId = notif.otherId
			// otherName = notif.otherName
			// contents = notif.contents
			// outgoing = notif.outgoing
   //      }
    	json
	}

	RedeemedNotificationJsonMarshaller() {
		super(RedeemedNotification, marshalClosure)
	}
}
