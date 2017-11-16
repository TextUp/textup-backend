package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.ToString
import org.textup.*
import org.textup.type.PhoneOwnershipType

// See [notification] in CustomApiDocs.groovy for documentation

@GrailsCompileStatic
@Validateable
@ToString
class Notification extends BasicNotification {

	Long tokenId // id of associated token
	String contents
	Boolean outgoing

	// must have either contact or tag, can set indirectly via record
	Contact contact
	ContactTag tag

 	static constraints = {
		tokenId nullable:false
		contents nullable:false
		outgoing nullable:false
		contact nullable:true, validator:{ Contact c1, Notification notif ->
			if (!c1 && !notif.tag) { ['noContactOrTag'] }
		}
		tag nullable:true, validator:{ ContactTag tag1, Notification notif ->
			if (!tag1 && !notif.contact) { ['noContactOrTag'] }
		}
	}

	// Property access
	// ---------------

	@Override
	void setRecord(Record rec) {
		super.setRecord(rec)
		ContactTag ct1 = ContactTag.findByRecord(rec)
		if (ct1) { this.tag = ct1 }
		else {
			Contact c1 = Contact.findByRecord(rec)
			if (c1) { this.contact = c1 }
		}
	}
	String getOtherId() {
		this.contact?.id ?: this.tag?.name
	}
	String getOtherType() {
		this.contact ? "contact" : "tag"
	}
	String getOtherName() {
		this.contact?.getNameOrNumber() ?: this.tag?.name
	}

	// Methods
	// -------

	// override toString handler to avoid leaking message contents when this Notification
	// is logged
	@Override
	String toString() {
		"Notification: tokenId: $tokenId, contact: $contact, tag: $tag"
	}
}
