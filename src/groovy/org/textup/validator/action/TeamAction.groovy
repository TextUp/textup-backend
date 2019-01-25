package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode(callSuper = true)
@GrailsTypeChecked
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
