package org.textup.validator.action

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.Constants
import org.textup.Contact
import org.textup.Helpers

// documented as [tagAction] in CustomApiDocs.groovy

@GrailsCompileStatic
@EqualsAndHashCode(callSuper=true)
@Validateable
class ContactTagAction extends BaseAction {

	Long id // id of contact to modify

	static constraints = {
		id validator: { Long contactId ->
			if (!Helpers.<Boolean>doWithoutFlush({ Contact.exists(contactId) })) {
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