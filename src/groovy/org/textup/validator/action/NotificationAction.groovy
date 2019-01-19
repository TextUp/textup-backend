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
class NotificationAction extends BaseAction {

	static final String ENABLE = "enable"
	static final String DISABLE = "disable"

	Long id // id of staff member to customize notifications for

	static constraints = {
		id validator: { Long staffId ->
			if (staffId && !Utils.<Boolean>doWithoutFlush { Staff.exists(staffId) }) {
				["doesNotExist"]
			}
		}
	}

	// Properties
	// ----------

	@Override
	Collection<String> getAllowedActions() { [ENABLE, DISABLE] }
}
