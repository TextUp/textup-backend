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

@EqualsAndHashCode(callSuper = true)
@GrailsTypeChecked
@Validateable
class MergeIndividualAction extends BaseAction {

	static final String DEFAULT = "default"
	static final String RECONCILE = "reconcile"

	Object mergeIds
	Long nameId
	Long noteId

	final Collection<Long> toBeMergedIds

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
				if (!obj.toBeMergedIds?.contains(id)) {
					return ["notInIdsList"]
				}
			}
		}
		noteId nullable: true, validator: { Long id, MergeIndividualAction obj ->
			if (obj.matches(RECONCILE)) {
				if (!id) {
					return ["requiredForReconciliation"]
				}
				if (!obj.toBeMergedIds?.contains(id)) {
					return ["notInIdsList"]
				}
			}
		}
	}

	// Methods
	// -------

	String buildName() { IndividualPhoneRecords.mustFindActiveForId(nameId).payload?.name }

	String buildNote() { IndividualPhoneRecords.mustFindActiveForId(noteId).payload?.note }

	// Properties
	// ----------

	@Override
	Collection<String> getAllowedActions() { [DEFAULT, RECONCILE] }

	Collection<Long> getToBeMergedIds() {
		Collection<?> possibleIds = TypeUtils.to(Collection, mergeIds, [])
		TypeUtils.allTo(Long, possibleIds)
	}
}
