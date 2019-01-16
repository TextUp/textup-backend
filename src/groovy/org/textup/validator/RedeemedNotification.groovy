package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.ToString
import org.textup.*
import org.textup.type.*
import org.textup.util.*

// See [redeemedNotification] in CustomApiDocs.groovy for documentation

@GrailsTypeChecked
@Validateable
// TODO fix names of properties?
@ToString(includes = ["tokenId", "contact", "tag"]) // avoid leaking message contents
class RedeemedNotification implements WithId, Validateable {

	PhoneOwnership owner

	final List<NotificationRecordDetails> details

	RedeemedNotification(Collection<Long> itemIds) {
		details = tryBuildDetails(itemIds)
	}

	// TODO
	protected List<NotificationRecordDetails> tryBuildDetails(Collection<Long> itemIds) {
		List<NotificationRecordDetails> details = []



		Collections.unmodifiableList(details)
	}

	Long getId() {
		// TODO
	}


	// TODO restore?
	// Long tokenId // id of associated token
	// String contents
	// Boolean outgoing

	// // must have either contact or tag, can set indirectly via record
	// Contact contact
	// ContactTag tag

 // 	static constraints = { // default nullable: false
	// 	contact nullable:true, validator:{ Contact c1, RedeemedNotification notif ->
	// 		if (!c1 && !notif.tag) { ['noContactOrTag'] }
	// 	}
	// 	tag nullable:true, validator:{ ContactTag tag1, RedeemedNotification notif ->
	// 		if (!tag1 && !notif.contact) { ['noContactOrTag'] }
	// 	}
	// }

	// // Property access
	// // ---------------

	// @Override
	// void setRecord(Record rec) {
	// 	super.setRecord(rec)
	// 	ContactTag ct1 = ContactTag.findByRecord(rec)
	// 	if (ct1) { this.tag = ct1 }
	// 	else {
	// 		Contact c1 = Contact.findByRecord(rec)
	// 		if (c1) { this.contact = c1 }
	// 	}
	// }
	// String getOtherId() {
	// 	this.contact?.id ?: this.tag?.name
	// }
	// String getOtherType() {
	// 	this.contact ? "contact" : "tag"
	// }
	// @Override
	// String getOtherName() {
	// 	this.contact?.getNameOrNumber() ?: this.tag?.name
	// }
	// // alias tokenId to id so that the `getId` method in BaseController can find an ID when
	// // generating a link to return with the JSON payload
	// Long getId() {
	// 	this.tokenId
	// }
}
