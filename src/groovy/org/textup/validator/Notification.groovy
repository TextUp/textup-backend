package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.ToString
import org.textup.*
import org.textup.types.PhoneOwnershipType

// See [notification] in CustomApiDocs.groovy for documentation

@GrailsCompileStatic
@Validateable
@ToString
class Notification {

	PhoneOwnership owner
	String contents
	Boolean outgoing
	Long tokenId // id of associated token

	// must have either contact or tag
	Contact contact
	ContactTag tag

	static constraints = {
		tokenId nullable:false
		owner nullable:false
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

	void setRecord(Record rec) {
		ContactTag ct1 = ContactTag.findByRecord(rec)
		if (ct1) { this.tag = ct1 }
		else {
			Contact c1 = Contact.findByRecord(rec)
			if (c1) { this.contact = c1 }
		}
	}

	String getOwnerId() {
		if (ownerType == "staff") {
			Staff.get(owner?.ownerId)?.username ?: ''
		}
		else {
			Team.get(owner?.ownerId)?.name ?: ''
		}
	}
	String getOtherId() {
		this.contact?.id ?: this.tag?.name
	}

	String getOwnerType() {
		(owner?.type == PhoneOwnershipType.INDIVIDUAL) ? "staff" : "team"
	}
	String getOtherType() {
		this.contact ? "contact" : "tag"
	}

	String getOtherName() {
		this.contact?.getNameOrNumber() ?: this.tag?.name
	}
}
