package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Validateable
class MergeGroupItem implements CanValidate {

	PhoneNumber number
	Collection<Long> mergeIds = [] // initialize to empty collection to set off minSize constraint

	static constraints = {
		number cascadeValidation: true
	    mergeIds minSize: 1, validator: { Collection<Long> val ->
			Collection<Long> foundIds = Utils.<Collection<Long>>doWithoutFlush {
				IndividualPhoneRecords
					.buildForIds(mergeIds)
					.build(CriteriaUtils.returnsId())
					.list() as Collection<Long>
			}
			if (val.size() != foundIds.size()) {
				HashSet<Long> existingIds = new HashSet<>(val)
				return ["someDoNotExist", existingIds.removeAll(foundIds)]
			}
	    }
	}

	static MergeGroupItem create(PhoneNumber number, Collection<Long> mergeIds) {
		new MergeGroupItem(number: number, mergeIds: mergeIds)
	}

	// Methods
	// -------

	Collection<IndividualPhoneRecord> buildMergeWith() {
		CollectionUtils.ensureNoNull(AsyncUtils.getAllIds(IndividualPhoneRecord, mergeIds))
	}
}
