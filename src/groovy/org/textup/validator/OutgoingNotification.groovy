package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*
import org.textup.type.*

@GrailsTypeChecked
@Validateable
class OutgoingNotification {

	// TODO efficiently fetch
	// - whether the phone belongs to a team or an individual
	// - id of the PHONE OWNER (not the phone)
	// - name of the PHONE OWNER (not the phone)
	// - phone number of the TextUp phone
	Phone phone

	// TODO need to find a way to efficiently fetch when redeeming LATER ON
	// - record owner name
	// - if the record owner is a contact or a tag
	// - id of the RECORD OWNER (not the record) to enable opening up full details
	// - item details (contents, note contents, media, etc.)
	// - whether the message is outgoing or incoming
	Collection<RecordItem> items = []

	// TODO
	int getNumOutgoing() {}
	int getNumIncoming() {}


	// TODO restore?
	// Record record
	// // optionally specify the staff member to send this notification to
	// Staff staff
	// // if available, record the recipient's name to let the outgoing notification text
	// // have the name initials for greater context
	// String otherName

	// static constraints = { // default nullable: false
	// 	otherName nullable: true, blank: true
	// }

	// // Property access
	// // ---------------

	// String getOwnerId() {
	// 	if (ownerType == "staff") {
	// 		Staff.get(owner?.ownerId)?.username ?: ""
	// 	}
	// 	else {
	// 		Team.get(owner?.ownerId)?.name ?: ""
	// 	}
	// }
	// String getOwnerType() {
	// 	(owner?.type == PhoneOwnershipType.INDIVIDUAL) ? "staff" : "team"
	// }
}
