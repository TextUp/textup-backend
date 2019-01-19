package org.textup.rest.marshaller

import grails.compiler.GrailsTypeChecked
import org.textup.*
import org.textup.rest.*
import org.textup.validator.*

@GrailsTypeChecked
class NotificationJsonMarshaller extends JsonNamedMarshaller {

	static final Closure marshalClosure = { Notification notif ->

        // TODO RequestUtils.OWNER_POLICY_ID

        // // TODO efficiently fetch
        // // - whether the phone belongs to a team or an individual
        // // - id of the PHONE OWNER (not the phone)
        // // - name of the PHONE OWNER (not the phone)
        // // - phone number of the TextUp phone
        // final Phone phone

        // // TODO need to find a way to efficiently fetch when redeeming LATER ON
        // // - record owner name
        // // - if the record owner is a contact or a tag
        // // - id of the RECORD OWNER (not the record) to enable opening up full details
        // // - item details (contents, note contents, media, etc.)
        // // - whether the message is outgoing or incoming
        // private final Map<PhoneRecordWrapper, NotificationDetail> wrapperToDetails = [:]

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

	NotificationJsonMarshaller() {
		super(Notification, marshalClosure)
	}
}
