package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.util.*

// documented as [tagAction] in CustomApiDocs.groovy

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@Validateable
class ContactTagAction extends BaseAction {

	Long id // id of contact to modify

	final Contact contact

	static constraints = {
		contact nullable: true
		id validator: { Long contactId ->
			if (!Utils.<Boolean>doWithoutFlush({ Contact.exists(contactId) })) {
				["doesNotExist"]
			}
		}
	}

	// Validation helpers
	// ------------------

	@Override
	Collection<String> getAllowedActions() {
		[Constants.TAG_ACTION_ADD, Constants.TAG_ACTION_REMOVE]
	}

	Contact getContact() {
		this.id ? Contact.get(this.id) : null
	}
}
