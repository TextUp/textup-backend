package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.Constants
import org.textup.Utils
import org.textup.Staff

// documented as [teamAction] in CustomApiDocs.groovy

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@Validateable
class TeamAction extends BaseAction {

	Long id // id of the staff to modify

	static constraints = {
		id validator: { Long staffId ->
			if (!Utils.<Boolean>doWithoutFlush({ Staff.exists(staffId) })) {
				["doesNotExist"]
			}
		}
	}

	// Validation helpers
	// ------------------

	@Override
	Collection<String> getAllowedActions() {
		[Constants.TEAM_ACTION_ADD, Constants.TEAM_ACTION_REMOVE]
	}

	// Methods
	// -------

	Staff getStaff() {
		this.id ? Staff.get(this.id) : null
	}
}
