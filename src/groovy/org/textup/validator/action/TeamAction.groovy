package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.util.*

// documented as [teamAction] in CustomApiDocs.groovy

@GrailsTypeChecked
@EqualsAndHashCode(callSuper = true)
@Validateable
class TeamAction extends BaseAction {

	static final String ADD = "add"
	static final String REMOVE = "remove"

	Long id // id of the staff to modify

	static constraints = {
		id validator: { Long staffId ->
			if (!Utils.<Boolean>doWithoutFlush { Staff.exists(staffId) }) {
				["doesNotExist"]
			}
		}
	}

	// Methods
	// -------

	Staff buildStaff() { id ? Staff.get(id) : null }

	// Properties
	// ----------

	@Override
	Collection<String> getAllowedActions() { [ADD, REMOVE] }
}
