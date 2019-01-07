package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

// For some reason, TypeCheckingMode.SKIP argument passsed to @GrailsTypeChecked doesn't
// cause type checking to be skipped here

@Transactional
class DuplicateService {

    Result<List<MergeGroup>> findDuplicates(Collection<Long> contactIds) {
    	if (contactIds) {
            List<Object[]> data = getContactsData { CriteriaUtils.inList(delegate, "id", contactIds) }
    		findDuplicatesHelper(buildContactsData(data))
    	}
    	else {
    		IOCUtils.resultFactory.failWithCodeAndStatus(
                "duplicateService.findDuplicates.missingContactIds",
    			ResultStatus.UNPROCESSABLE_ENTITY)
    	}
    }

    Result<List<MergeGroup>> findAllDuplicates(Long phoneId) {
    	findDuplicatesHelper(buildContactsData(getContactsData({ eq("context.phone.id", phoneId) })))
    }

    protected List<Object[]> getContactsData(Closure<?> filterAction) {
        Contact.createCriteria()
            .list {
                // order of property projections dictates order in object array!!
                // IMPORTANT: must use createCriteria to specify this query. Using DetachedCriteria
                // only returns the id and does not also return the second projection property
                // example output: [[3, 1112223333], [6, 3006518300], [9, 1175729624], [9, 1994427797]]
                // the output above looks like a list of Object arrays, but it's actually a list of
                // Object arrays containing one item, which is another 2-member Object array
                projections {
                    property("id")
                    numbers { property("number") }
                }
                eq("isDeleted", false)
                // compose the additional query conditions into this query
                filterAction.delegate = delegate
                filterAction()
            }
    }

	@GrailsTypeChecked
    protected Map<String, HashSet<Long>> buildContactsData(List<Object[]> contactsData) {
        Map<String, HashSet<Long>> numToContactIds = new HashMap<>()
		contactsData.each { Object[] itemWrapper ->
			Object[] items = itemWrapper[0] as Object[] // each item is a 1-item array, inner is a 2-item array
			Long id = TypeConversionUtils.to(Long, items[0]) // inner array has contact id as first element
			String num = items[1] as String // inner array has phone number as second element
			if (numToContactIds.containsKey(num)) {
                numToContactIds[num] << id
            }
            else { numToContactIds[num] = new HashSet<Long>([id]) }

		}
		numToContactIds
	}

	@GrailsTypeChecked
    protected Result<List<MergeGroup>> findDuplicatesHelper(Map<String, HashSet<Long>> numToContactIds) {
		buildPossibleMerges(numToContactIds)
			.then({ Tuple<Map<Long, Collection<String>>, Collection<String>> outcomes ->
				Map<Long, Collection<String>> contactIdToMergeNums = outcomes.first
				Collection<String> possibleMergeNums = outcomes.second
				confirmPossibleMerges(numToContactIds, contactIdToMergeNums, possibleMergeNums)
			})
			.then({ Map<Long,Collection<String>> targetIdToConfirmedNums ->
				buildMergeGroups(numToContactIds, targetIdToConfirmedNums)
			})
    }

    @GrailsTypeChecked
    protected Result<Tuple<Map<Long, Collection<String>>, Collection<String>>> buildPossibleMerges(
    	Map<String, HashSet<Long>> numToContactIds) {

    	Map<Long, Collection<String>> contactIdToMergeNums = new HashMap<>()
		Collection<String> possibleMergeNums = []
		numToContactIds.each { String str, HashSet<Long> cIds ->
			// str only qualifies as a merge group if the set of contact ids attributable to it has
			// more than one contact id in it. If the set of contact ids only has one id, it is not
			// a merge group because merging by definition requires more than one item
			if (cIds.size() > 1) {
				possibleMergeNums << str
				cIds.each { Long cId ->
                    if (contactIdToMergeNums.containsKey(cId)) {
                        contactIdToMergeNums[cId] << str
                    }
                    else { contactIdToMergeNums[cId] = [str] }
                }
			}
		}
		IOCUtils.resultFactory.success(contactIdToMergeNums, possibleMergeNums)
    }

    @GrailsTypeChecked
    protected Result<Map<Long,Collection<String>>> confirmPossibleMerges(
        Map<String, HashSet<Long>> numToContactIds, Map<Long, Collection<String>> contactIdToMergeNums,
        Collection<String> possibleMergeNums) {

    	Map<Long,Collection<String>> targetIdToConfirmedNums = new HashMap<>()
		possibleMergeNums.each { String str ->
			int numAmbiguous = 0
			Long ambiguousId
			List<Long> cIds = new ArrayList<Long>(numToContactIds[str] ?: [])
			for (cId in cIds) {
				// if a contact is in multiple merge groups, then it is ambiguous which
				// group should be merged
				if (contactIdToMergeNums[cId].size() > 1) {
					numAmbiguous += 1
					ambiguousId = cId
				}
				// if there is more than one ambiguous contact in this group we are
				// considering, then break because we already know we will not be
				// suggesting this as a possible merge group
				if (numAmbiguous > 1) { break }
			}
			// if there is at most 1 ambiguous contact in the merge group, then we can merge this
			// because the ambiguous contact will be the one that we merge all of the other
			// nonambiguous contacts into
			if (numAmbiguous <= 1) {
                Long idToAdd = (ambiguousId != null) ? ambiguousId : cIds[0]
                if (targetIdToConfirmedNums.containsKey(idToAdd)) {
                    targetIdToConfirmedNums[idToAdd] << str
                }
                else { targetIdToConfirmedNums[idToAdd] = [str] }
			}
		}
		IOCUtils.resultFactory.success(targetIdToConfirmedNums)
    }

    @GrailsTypeChecked
    protected Result<List<MergeGroup>> buildMergeGroups(Map<String, HashSet<Long>> numToContactIds,
    	Map<Long,Collection<String>> targetIdToConfirmedNums) {

    	List<MergeGroup> mGroups = []
		List<Result<MergeGroup>> errors = []
		targetIdToConfirmedNums.each { Long ambigId, Collection<String> manyStrings ->
			MergeGroup m1 = new MergeGroup(targetContactId:ambigId)
			manyStrings.each { String str -> m1.add(new PhoneNumber(number:str), numToContactIds[str]) }
			if (m1.deepValidate()) {
				mGroups << m1
			}
			else { errors << IOCUtils.resultFactory.failWithValidationErrors(m1.errors) }
		}
    	if (errors) {
    		IOCUtils.resultFactory.failWithResultsAndStatus(errors, ResultStatus.INTERNAL_SERVER_ERROR)
    	}
    	else { IOCUtils.resultFactory.success(mGroups) }
    }
}
