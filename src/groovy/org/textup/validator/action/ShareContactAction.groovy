package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.type.SharePermission
import org.textup.util.*

// documented as [shareAction] in CustomApiDocs.groovy

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@Validateable
class ShareContactAction extends BaseAction {

	Long id // id of phone that we are sharing with
	String permission

	final SharePermission permissionAsEnum
	final Phone phone

	static constraints = {
		permissionAsEnum nullable: true
		phone nullable: true
		id validator: { Long phoneId, ShareContactAction ->
			if (!Utils.<Boolean>doWithoutFlush({ Phone.exists(phoneId) })) {
				["doesNotExist"]
			}
		}
		permission nullable:true, blank:true, validator: { String permission, ShareContactAction obj ->
			if (obj.matches(Constants.SHARE_ACTION_MERGE)) {
				Collection<String> options = SharePermission.values().collect { it.toString() }
				if (!CollectionUtils.inListIgnoreCase(permission, options)) {
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
		TypeConversionUtils.convertEnum(SharePermission, this.permission)
	}
	Phone getPhone() {
		this.id ? Phone.get(this.id) : null
	}
}
