package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.util.Holders
import grails.validation.Validateable
import org.textup.*

@GrailsCompileStatic
@Validateable
class MergeGroup {

	Long targetContactId
	Collection<MergeGroupItem> possibleMerges = []

	static constraints = {
		targetContactId validator:{ Long id ->
			if (id && !Helpers.<Boolean>doWithoutFlush({ Contact.exists(id) })) {
				["doesNotExist"]
			}
		}
		possibleMerges minSize:1, validator:{ Collection<MergeGroupItem> val, MergeGroup obj ->
			// shortcircuit custom validation if no possible merges
			if (!val) { return }
			// check that all items contain unique ids
			HashSet<Long> alreadySeenIds = new HashSet<>([obj.targetContactId])
			HashSet<Long> allToBeMerged = new HashSet<Long>()
			for (MergeGroupItem i1 in val) {
				Collection<Long> cIds = i1.contactIds
				allToBeMerged.addAll(cIds)
				for (cId in cIds) {
					if (alreadySeenIds.contains(cId)) {
						return ["overlappingId", cId]
					}
					alreadySeenIds.add(cId)
				}
			}
			// batch check existence
			Collection<Contact> found = Helpers.<Collection<Contact>>doWithoutFlush({
				Contact
					.getAll(allToBeMerged as Iterable<Serializable>)
					.findAll { Contact c1 -> c1 != null }
			})
			if (found.size() != allToBeMerged.size()) {
				return ["someDoNotExist", allToBeMerged - found*.id]
			}
		}
	}

	// Validation
	// ----------

	boolean deepValidate() {
		boolean isAllSuccess = this.validate()
		Collection<String> errorMessages = []
		this.possibleMerges.each { MergeGroupItem i1 ->
			if (!i1.validate()) {
				isAllSuccess = false
				errorMessages += resultFactory.failWithValidationErrors(i1.errors).errorMessages
			}
		}
		if (errorMessages) {
			this.errors.rejectValue("possibleMerges", "mergeGroup.possibleMerges.invalidItems",
				errorMessages as Object[], "Invalid possible merges")
		}
		isAllSuccess
	}

	// Methods
	// -------

	MergeGroup add(PhoneNumber mergeGroupNumber, Collection<Long> contactIds) {
		Collection<Long> itemIds = new ArrayList<Long>(contactIds)
		itemIds.remove(this.targetContactId)
		possibleMerges << new MergeGroupItem(number:mergeGroupNumber, contactIds:itemIds)
		this
	}

	// Property access
	// ---------------

	Contact getTargetContact() {
		Contact.get(this.targetContactId)
	}

	// Helpers
	// -------

	protected ResultFactory getResultFactory() {
		Holders
			.applicationContext
			.getBean("resultFactory") as ResultFactory
	}
}