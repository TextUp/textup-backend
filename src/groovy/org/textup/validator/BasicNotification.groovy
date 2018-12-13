package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.ToString
import org.textup.*
import org.textup.type.PhoneOwnershipType

// This is the barebones validator class created when first generating a token and sending out
// the notification message. The `Notification` subclass is the class with additional information
// recreated from the stored token data when the notification preview link is selected.

@GrailsTypeChecked
@Validateable
@ToString
class BasicNotification {

	PhoneOwnership owner
	Record record
	// optionally specify the staff member to send this notification to
	Staff staff
	// if available, record the recipient's name to let the outgoing notification text
	// have the name initials for greater context
	String otherName

	static constraints = {
		owner nullable: false
		record nullable: false
		staff nullable: true
		otherName nullable: true
	}

	// Property access
	// ---------------

	String getOwnerId() {
		if (ownerType == "staff") {
			Staff.get(owner?.ownerId)?.username ?: ''
		}
		else {
			Team.get(owner?.ownerId)?.name ?: ''
		}
	}
	String getOwnerType() {
		(owner?.type == PhoneOwnershipType.INDIVIDUAL) ? "staff" : "team"
	}
}
