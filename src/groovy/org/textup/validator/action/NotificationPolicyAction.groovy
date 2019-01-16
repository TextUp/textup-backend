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

	static final String DEFAULT = "changedefault"
	static final String ENABLE = "enable"
	static final String DISABLE = "disable"

	Long id // id of staff member to customize notifications for
	String level // required for default, level to change the default to

	static constraints = {
		id validator: { Long staffId ->
			if (staffId && !Utils.<Boolean>doWithoutFlush { Staff.exists(staffId) }) {
				["doesNotExist"]
			}
		}
		level nullable:true, blank:true, validator: { String level, NotificationPolicyAction obj ->
			if (obj.matches(DEFAULT)) {
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

	// Methods
	// -------

	NotificationLevel buildNotificationLevel() {
		TypeConversionUtils.convertEnum(NotificationLevel, level)
	}

	// Properties
	// ----------

	@Override
	Collection<String> getAllowedActions() { [DEFAULT, ENABLE, DISABLE] }
}
