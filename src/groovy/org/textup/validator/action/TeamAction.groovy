package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.util.*

// documented as [teamAction] in CustomApiDocs.groovy

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@Validateable
class TeamAction extends BaseAction {

	static final String ADD = "add"
	static final String REMOVE = "remove"

	Long id // id of the staff to modify

	final Staff staff

	static constraints = {
		staff nullable: true
		id validator: { Long staffId ->
			if (!Utils.<Boolean>doWithoutFlush({ Staff.exists(staffId) })) {
				["doesNotExist"]
			}
		}
	}

	// Validation helpers
	// ------------------

	@Override
	Collection<String> getAllowedActions() { [TeamAction.ADD, TeamAction.REMOVE] }

	// Methods
	// -------

	Staff getStaff() {
		id ? Staff.get(id) : null
	}
}
