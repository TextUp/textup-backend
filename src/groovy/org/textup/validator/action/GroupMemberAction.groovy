package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.util.*

// documented as [tagAction] in CustomApiDocs.groovy

@GrailsTypeChecked
@EqualsAndHashCode(callSuper = true)
@Validateable
class GroupMemberAction extends BaseAction {

	static final String ADD = "add"
	static final String REMOVE = "remove"

	Long id

	static constraints = {
		id validator: { Long val ->
			if (!Utils.<Boolean>doWithoutFlush { PhoneRecord.exists(val) }) {
				["doesNotExist"]
			}
		}
	}

	PhoneRecord buildPhoneRecord() { id ? PhoneRecord.get(id) : null }

	// Properties
	// ----------

	@Override
	Collection<String> getAllowedActions() { [ADD, REMOVE] }
}
