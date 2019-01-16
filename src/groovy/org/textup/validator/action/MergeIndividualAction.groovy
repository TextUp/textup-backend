package org.textup.validator.action

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.util.*

// documented as [mergeAction] in CustomApiDocs.groovy

@GrailsTypeChecked
@EqualsAndHashCode(callSuper = true)
@Validateable
class MergeIndividualAction extends BaseAction {

	static final String DEFAULT = "default"
	static final String RECONCILE = "reconcile"

	Object mergeIds
	Long nameId
	Long noteId

	static constraints = {
		toBeMergedIds validator: { Collection<Long> val ->
			if (!val) {
				return ["emptyOrNotACollection"]
			}
		}
		nameId nullable: true, validator: { Long id, MergeIndividualAction obj ->
			if (obj.matches(RECONCILE)) {
				if (!id) {
					return ["requiredForReconciliation"]
				}
				if (!obj.ids.contains(id)) {
					return ["notInIdsList"]
				}
			}
		}
		noteId nullable: true, validator: { Long id, MergeIndividualAction obj ->
			if (obj.matches(RECONCILE)) {
				if (!id) {
					return ["requiredForReconciliation"]
				}
				if (!obj.ids.contains(id)) {
					return ["notInIdsList"]
				}
			}
		}
	}

	// Methods
	// -------

	String buildName() {
		nameId ? toBeMerged.find { IndividualPhoneRecord ipr1 -> ipr1.id == nameId }?.name : null
	}

	String buildNote() {
		noteId ? toBeMerged.find { IndividualPhoneRecord ipr1 -> ipr1.id == noteId }?.note : null
	}

	// Properties
	// ----------

	@Override
	Collection<String> getAllowedActions() { [DEFAULT, RECONCILE] }

	Collection<Long> getToBeMergedIds() {
		Collection<?> possibleIds = TypeConversionUtils.to(Collection, mergeIds, [])
		TypeConversionUtils.allTo(Long, possibleIds)
	}
}
