package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.apache.commons.lang3.tuple.Pair
import org.springframework.transaction.annotation.Propagation
import org.textup.type.ContactStatus
import org.textup.validator.MergeGroup
import org.textup.validator.PhoneNumber

@Transactional
class DuplicateService {

    ResultFactory resultFactory

	// Building merge groups
	// ---------------------

    Result<List<MergeGroup>> findDuplicates(Collection<Long> contactIds) {
    	if (contactIds) {
    		findDuplicatesHelper(buildContactsData(getContactsData({ "in"("id", contactIds) })))
    	}
    	else {
    		resultFactory.failWithCodeAndStatus("duplicateService.findDuplicates.missingContactIds",
    			ResultStatus.UNPROCESSABLE_ENTITY)
    	}
    }
    Result<List<MergeGroup>> findDuplicates(Phone phone) {
    	findDuplicatesHelper(buildContactsData(getContactsData({ eq("phone", phone) })))
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
    @GrailsCompileStatic
	protected Map<Long, HashSet<String>> buildContactsData(List<Object[]> contactsData) {
        Map<String, HashSet<Long>> numToContactIds = new HashMap<>()
		contactsData.each { Object[] itemWrapper ->
			Object[] items = itemWrapper[0] as Object[] // each item is a 1-item array, inner is a 2-item array
			Long id = Helpers.to(Long, items[0]) // inner array has contact id as first element
			String num = items[1] as String // inner array has phone number as second element
			if (numToContactIds.containsKey(num)) {
                numToContactIds[num] << id
            }
            else { numToContactIds[num] = new HashSet<Long>([id]) }

		}
		numToContactIds
	}
    @GrailsCompileStatic
	protected Result<List<MergeGroup>> findDuplicatesHelper(Map<String, HashSet<Long>> numToContactIds) {
		buildPossibleMerges(numToContactIds)
			.then({ Pair<Map<Long, Collection<String>>, Collection<String>> pair ->
				Map<Long, Collection<String>> contactIdToMergeNums = pair.left
				Collection<String> possibleMergeNums = pair.right
				confirmPossibleMerges(numToContactIds, contactIdToMergeNums, possibleMergeNums)
			})
			.then({ Map<Long,Collection<String>> targetIdToConfirmedNums ->
				buildMergeGroups(numToContactIds, targetIdToConfirmedNums)
			})
    }
    @GrailsCompileStatic
    protected Result<Pair<Map<Long, Collection<String>>, Collection<String>>> buildPossibleMerges(
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
		resultFactory.success(Pair.of(contactIdToMergeNums, possibleMergeNums))
    }
    @GrailsCompileStatic
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
		resultFactory.success(targetIdToConfirmedNums)
    }
    @GrailsCompileStatic
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
			else { errors << resultFactory.failWithValidationErrors(m1.errors) }
		}
    	if (errors) {
    		resultFactory.failWithResultsAndStatus(errors, ResultStatus.INTERNAL_SERVER_ERROR)
    	}
    	else { resultFactory.success(mGroups) }
    }

    // Merging
    // -------

    // (1) We made the merge method require a new transaction in order to insulate it from
    // potential changes made to the merged-in contacts. Changes to the merged-in contacts
    // will trigger a cascade on save that will undo the merge operations. For example, if we
    // set the isDeleted flag to true for the merged-in contacts within this method, then the
    // tags that contact the merged-in contacts will continue to have these as their members
    // because their membership we re-saved in the save cascade.
    // (2) Requiring a new transaction for this method only works when this method is called
    // from another service. Self-calls by another method in DuplicateService will not trigger
    // the interceptor and this merge method will execute in the same transaction. This is the
    // behavior of the underlying Spring Transaction abstraction. See
    // http://www.tothenew.com/blog/grails-transactions-using-transactional-annotations-and-propagation-requires_new/
    @GrailsCompileStatic
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    Result<Contact> merge(Contact targetContact, Collection<Contact> toMergeIn) {
    	if (!toMergeIn) {
    		return resultFactory.failWithCodeAndStatus("duplicateService.merge.missingMergeContacts",
    			ResultStatus.BAD_REQUEST)
    	}
        // BECAUSE OF THE PROPAGATION REQUIRES NEW, all of the contacts passed in as parameters
        // are detached from the session since opening a new transaction also starts a new session
        // We don't make any modifications on the merged-in contacts so it is all right if they
        // are detached. However, we do modify the targetContact so we must manually re-fetch it
        // before we can proceed. We can't just reattach it because we can't have one object
        // attached simultaneously to two open sessions
        if (!targetContact.isAttached()) {
            targetContact = Contact.get(targetContact.id)
        }
        // find the tags we are interested BEFORE we delete mark the contact we are merging in
        // as deleted because this finder will ignore deleted contacts
        Collection<ContactTag> mergeContactTags = ContactTag.findEveryByContactIds(toMergeIn*.id)
    	// collate items
    	Collection<ContactStatus> statuses = [targetContact.status]
    	Collection<Record> toMergeRecords = []
    	Collection<ContactNumber> mergeNums = []
    	for (Contact mergeContact in toMergeIn) {
      		mergeNums += mergeContact.numbers
    		toMergeRecords << mergeContact.record
    		statuses << mergeContact.status
    	}
    	// transfer appropriate associations
        targetContact.status = findMostPermissibleStatus(statuses)
        Result<Void> res = mergeTags(targetContact, mergeContactTags, toMergeIn)
            .then({ mergeSharedContacts(targetContact, toMergeIn) })
            .then({ mergeRecords(targetContact.record, toMergeRecords) })
            .then({ mergeNumbers(targetContact, mergeNums) })
		if (!res.success) {
			return resultFactory.failWithResultsAndStatus([res], res.status)
		}
		// save target contact
        if (targetContact.record.save()) {
        	if (targetContact.save()) {
        		resultFactory.success(targetContact)
        	}
        	else { resultFactory.failWithValidationErrors(targetContact.errors) }
        }
        else { resultFactory.failWithValidationErrors(targetContact.record.errors) }
    }
    @GrailsCompileStatic
    protected ContactStatus findMostPermissibleStatus(Collection<ContactStatus> statuses) {
    	if ([ContactStatus.UNREAD, ContactStatus.ACTIVE].any { ContactStatus s -> s in statuses }) {
    		ContactStatus.ACTIVE
    	}
    	else if (ContactStatus.ARCHIVED in statuses) {
    		ContactStatus.ARCHIVED
    	}
    	else { ContactStatus.BLOCKED }
    }
    @GrailsCompileStatic
    protected Result<Void> mergeNumbers(Contact targetContact, Collection<ContactNumber> mergeNums) {
    	for (ContactNumber num in mergeNums) {
    		Result<ContactNumber> res = targetContact.mergeNumber(num.number, [preference:num.preference])
    		if (!res.success) {
    			return resultFactory.failWithResultsAndStatus([res], res.status)
    		}
    	}
    	resultFactory.success()
    }
    @GrailsCompileStatic
    protected Result<Void> mergeTags(Contact targetContact, Collection<ContactTag> mergeContactTags,
        Collection<Contact> mergeContacts) {
        try {
            HashSet<Long> mergeIds = new HashSet<>(mergeContacts*.id)
            mergeContactTags
                .each { ContactTag tag1 ->
                    Collection<Contact> contactsToRemove = tag1.members.findAll { Contact c1 -> c1.id in mergeIds }
                    contactsToRemove.each { Contact c1 ->
                        tag1.removeFromMembers(c1)
                    }
                    tag1.addToMembers(targetContact)
                    tag1.save()
                }
            resultFactory.success()
        }
        catch (Throwable e) {
            log.error("DuplicateService.mergeTags: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
    @GrailsCompileStatic
    protected Result<Void> mergeSharedContacts(Contact targetContact, Collection<Contact> mergeContacts) {
        try {
            SharedContact
                .buildForContacts(mergeContacts)
                .updateAll(contact:targetContact)
            resultFactory.success()
        }
        catch (Throwable e) {
            log.error("DuplicateService.mergeSharedContacts: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
    @GrailsCompileStatic
    protected Result<Void> mergeRecords(Record targetRecord, Collection<Record> toMergeRecords) {
        try {
            RecordItem
                .buildForRecords(toMergeRecords)
                .updateAll(record:targetRecord)
            FutureMessage
                .buildForRecords(toMergeRecords)
                .updateAll(record:targetRecord)
            resultFactory.success()
        }
        catch (Throwable e) {
            log.error("DuplicateService.mergeRecords: ${e.message}")
            resultFactory.failWithThrowable(e)
        }
    }
}
