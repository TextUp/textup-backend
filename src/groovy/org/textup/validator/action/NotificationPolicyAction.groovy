package org.textup.validator.action

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.Constants
import org.textup.Helpers
import org.textup.Staff
import org.textup.type.NotificationLevel

// documented as [notificationAction] in CustomApiDocs.groovy

@GrailsCompileStatic
@EqualsAndHashCode(callSuper=true)
@Validateable
class NotificationPolicyAction extends BaseAction {

	Long id // id of staff member to customize notifications for
	String level // required for default, level to change the default to

	static constraints = {
		id validator: { Long staffId ->
			if (staffId && !Helpers.<Boolean>doWithoutFlush({ Staff.exists(staffId) })) {
				["doesNotExist"]
			}
		}
		level nullable:true, blank:true, validator: { String level, NotificationPolicyAction obj ->
			if (obj.matches(Constants.NOTIFICATION_ACTION_DEFAULT)) {
				if (!level) {
					return ["requiredForChangingDefault"]
				}
				Collection<String> options = NotificationLevel.values().collect { it.toString() }
				if (!Helpers.inListIgnoreCase(level, options)) {
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
		Helpers.<NotificationLevel>convertEnum(NotificationLevel, this.level)
	}
}