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
