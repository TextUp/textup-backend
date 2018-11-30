package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.type.NotificationLevel
import org.textup.util.*

// documented as [notificationAction] in CustomApiDocs.groovy

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@Validateable
class NotificationPolicyAction extends BaseAction {

	Long id // id of staff member to customize notifications for
	String level // required for default, level to change the default to

	static constraints = {
		id validator: { Long staffId ->
			if (staffId && !Utils.<Boolean>doWithoutFlush({ Staff.exists(staffId) })) {
				["doesNotExist"]
			}
		}
		level nullable:true, blank:true, validator: { String level, NotificationPolicyAction obj ->
			if (obj.matches(Constants.NOTIFICATION_ACTION_DEFAULT)) {
				if (!level) {
					return ["requiredForChangingDefault"]
				}
				Collection<String> options = NotificationLevel.values().collect { it.toString() }
				if (!CollectionUtils.inListIgnoreCase(level, options)) {
					return ["invalid", options]
				}
			}
		}
	}

	// Validation helpers
	// ------------------

	@Override
	Collection<String> getAllowedActions() {
		[Constants.NOTIFICATION_ACTION_DEFAULT, Constants.NOTIFICATION_ACTION_ENABLE,
			Constants.NOTIFICATION_ACTION_DISABLE]
	}

	// Methods
	// -------

	NotificationLevel getLevelAsEnum() {
		TypeConversionUtils.convertEnum(NotificationLevel, this.level)
	}
}
