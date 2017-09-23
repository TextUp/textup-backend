package org.textup.validator.action

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.Constants
import org.textup.Contact
import org.textup.Helpers

// documented as [mergeAction] in CustomApiDocs.groovy

@GrailsCompileStatic
@EqualsAndHashCode(callSuper=true)
@Validateable
class MergeAction extends BaseAction {

	Object mergeIds
	Long nameId
	Long noteId

	private List<Long> ids = []
	private List<Contact> contacts = []

	static constraints = {
		mergeIds validator:{ Object val, MergeAction obj ->
			Collection<?> idObjs = Helpers.to(Collection, val)
			if (!idObjs) {
				return ["emptyOrNotACollection"]
			}
			List<Long> castIds = Helpers.allTo(Long, idObjs)
			if (castIds.size() != obj.ids.size()) {
				return ["notAllNumbers"]
			}
			if (obj.ids.size() != obj.contacts.size()) {
				return ["someDoNotExist", obj.ids - obj.contacts*.id]
			}
		}
		nameId nullable:true, validator:{ Long id, MergeAction obj ->
			if (obj.matches(Constants.MERGE_ACTION_RECONCILE) && !id) {
				return ["requiredForReconciliation"]
			}
			if (id && !obj.ids.contains(id)) {
				return ["notInIdsList"]
			}
		}
		noteId nullable:true, validator:{ Long id, MergeAction obj ->
			if (obj.matches(Constants.MERGE_ACTION_RECONCILE) && !id) {
				return ["requiredForReconciliation"]
			}
			if (id && !obj.ids.contains(id)) {
				return ["notInIdsList"]
			}
		}
	}

	// Validation helpers
	// ------------------

	@Override
	Collection<String> getAllowedActions() {
		[Constants.MERGE_ACTION_DEFAULT, Constants.MERGE_ACTION_RECONCILE]
	}

	// Property access
	// ---------------

	void setMergeIds(Object mIds) {
		this.mergeIds = mIds
		Collection<?> idObjs = Helpers.to(Collection, mIds)
		if (idObjs) {
			List<Long> thisIds = []
			Helpers.allTo(Long, idObjs)
				.each { Long id -> if (id) { thisIds << id } }
			this.ids = thisIds
			this.contacts = []
			Contact
				.getAll(thisIds as Iterable<Serializable>)
				.each { Contact c1 -> if (c1) { this.contacts << c1 } }
		}
	}

	List<Contact> getContacts() {
		this.contacts
	}
	String getName() {
		if (!this.nameId) {
			return null
		}
		this.contacts.find({ Contact c1 -> c1.id == this.nameId })?.name
	}
	String getNote() {
		if (!this.noteId) {
			return null
		}
		this.contacts.find({ Contact c1 -> c1.id == this.noteId })?.note
	}
}