package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.ToString
import org.textup.*
import org.textup.type.PhoneOwnershipType

@GrailsTypeChecked
@Validateable
@ToString
class BasicNotification {

	PhoneOwnership owner
	Record record
	// optionally specify the staff member to send this notification to
	Staff staff

	static constraints = {
		owner nullable:false
		record nullable:false
		staff nullable:true
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
