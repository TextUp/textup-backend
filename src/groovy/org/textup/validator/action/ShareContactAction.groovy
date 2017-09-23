package org.textup.validator.action

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.Constants
import org.textup.Helpers
import org.textup.Phone
import org.textup.type.SharePermission

// documented as [shareAction] in CustomApiDocs.groovy

@GrailsCompileStatic
@EqualsAndHashCode(callSuper=true)
@Validateable
class ShareContactAction extends BaseAction {

	Long id // id of phone that we are sharing with
	String permission

	static constraints = {
		id validator: { Long phoneId, ShareContactAction ->
			if (!Helpers.<Boolean>doWithoutFlush({ Phone.exists(phoneId) })) {
				["doesNotExist"]
			}
		}
		permission nullable:true, blank:true, validator: { String permission, ShareContactAction obj ->
			if (obj.matches(Constants.SHARE_ACTION_MERGE)) {
				Collection<String> options = SharePermission.values().collect { it.toString() }
				if (!Helpers.inListIgnoreCase(permission, options)) {
					["invalid", options]
				}
			}
		}
	}

	// Validation helpers
	// ------------------

	@Override
	Collection<String> getAllowedActions() {
		[Constants.SHARE_ACTION_MERGE, Constants.SHARE_ACTION_STOP]
	}

	// Methods
	// -------

	SharePermission getPermissionAsEnum() {
		Helpers.<SharePermission>convertEnum(SharePermission, this.permission)
	}
	Phone getPhone() {
		this.id ? Phone.get(this.id) : null
	}
}