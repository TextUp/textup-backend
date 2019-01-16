package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*
import org.textup.util.*

@GrailsTypeChecked
@Validateable
class MergeGroup implements Validateable {

	Long targetId
	Collection<MergeGroupItem> possibleMerges = []

	static constraints = {
		targetId validator: { Long id ->
			if (!Utils.<Boolean>doWithoutFlush { IndividualPhoneRecord.exists(id) }) {
				["doesNotExist"]
			}
		}
		possibleMerges cascadeValidation: true, minSize: 1,
			validator: { Collection<MergeGroupItem> val, MergeGroup obj ->
				if (val) {
					Collection<Long> allIds = CollectionUtils.mergeUnique(*val*.mergeids)
					// check for no self merge
					if (allIds.contains(obj.targetId)) {
						return ["cannotMergeWithSelf", obj.targetId]
					}
					// check for no overlapping ids in suggested merges
					Map<Long, Collection<Long>> invalidIds = MapUtils
						.<Long, Long>buildManyObjectsMap(allIds, { it })
						.findAll { it.value.size() > 1 }
					if (invalidIds) {
						return ["overlappingIds", invalidIds.keySet()]
					}
				}
			}
	}

	static Result<MergeGroup> tryCreate(Long tId, Collection<MergeGroupItem> possibleMerges) {
		MergeGroup mGroup = new MergeGroup(targetId: tId, possibleMerges: possibleMerges)
		DomainUtils.tryValidate(mGroup, ResultStatus.CREATED)
	}

	// Methods
	// -------

	IndividualPhoneRecord buildTarget() { IndividualPhoneRecord.get(targetId) }

	MergeGroup add(PhoneNumber pNum, Collection<Long> mergeIds) {
		Collection<Long> itemIds = mergeIds.findAll { Long cId -> cId != targetId }
		possibleMerges << new MergeGroupItem(number: pNum, mergeIds: itemIds)
		this
	}
}
