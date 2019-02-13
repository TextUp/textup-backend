package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true)
@Validateable
class MergeGroup implements CanValidate {

	final Long targetId
	final Collection<MergeGroupItem> possibleMerges

	static constraints = {
		targetId validator: { Long id ->
			if (id && !Utils.<Boolean>doWithoutFlush { IndividualPhoneRecord.exists(id) }) {
				["doesNotExist"]
			}
		}
		possibleMerges cascadeValidation: true, minSize: 1,
			validator: { Collection<MergeGroupItem> val, MergeGroup obj ->
				if (val) {

					println "val*.mergeIds: ${val*.mergeIds}"
					println "\t obj.targetId: ${obj.targetId}"

					Collection<Long> uniqueMergeIds = CollectionUtils.mergeUnique(val*.mergeIds)
					// check for no self merge
					if (uniqueMergeIds.contains(obj.targetId)) {
						return ["cannotMergeWithSelf", obj.targetId]
					}
					// check for no overlapping ids in suggested merges
					Collection<Long> allIds = [val*.mergeIds].flatten() as Collection<Long>
					Map<Long, Collection<Long>> invalidIds = MapUtils
						.<Long, Long>buildManyNonUniqueObjectsMap(allIds) { Long id -> id }
						.findAll { Long k, Collection<Long> v -> v.size() > 1 }
					if (invalidIds) {
						return ["overlappingIds", invalidIds.keySet()]
					}
				}
			}
	}

	static Result<MergeGroup> tryCreate(Long tId, Collection<MergeGroupItem> merges) {
		Collection<MergeGroupItem> possibleMerges = merges ?: new ArrayList<MergeGroupItem>()
		MergeGroup mGroup = new MergeGroup(tId, Collections.unmodifiableCollection(possibleMerges))
		DomainUtils.tryValidate(mGroup, ResultStatus.CREATED)
	}

	// Methods
	// -------

	IndividualPhoneRecord buildTarget() { targetId ? IndividualPhoneRecord.get(targetId) : null }
}
