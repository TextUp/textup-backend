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
@EqualsAndHashCode(callSuper=true)
@Validateable
class ShareAction extends BaseAction {

	static final String MERGE = "merge"
	static final String STOP = "stop"

	Long id // id of phone that we are sharing with
	String permission

	static constraints = {
		id validator: { Long phoneId, ShareAction ->
			if (!Utils.<Boolean>doWithoutFlush { Phone.exists(phoneId) }) {
				["doesNotExist"]
			}
		}
		permission nullable:true, blank:true, validator: { String permission, ShareAction obj ->
			if (obj.matches(MERGE)) {
				Collection<String> options = SharePermission.values().collect { it.toString() }
				if (!CollectionUtils.inListIgnoreCase(permission, options)) {
					["invalid", options]
				}
			}
		}
	}

	// Methods
	// -------

	SharePermission buildSharePermission() {
		TypeUtils.convertEnum(SharePermission, permission)
	}

	Phone buildPhone() { id ? Phone.get(id) : null }

	// Properties
	// ----------

	@Override
	Collection<String> getAllowedActions() { [MERGE, STOP] }
}
