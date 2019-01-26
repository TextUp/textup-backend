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
@TupleConstructor(includeFields = true, includes = ["mutablePhone"])
@Validateable
class Notification implements CanValidate {

	final Phone mutablePhone
	private final Map<PhoneRecordWrapper, NotificationDetail> wrapperToDetails = [:]

    final Collection<NotificationDetail> details

	static constraints = {
		details cascadeValidation: true
		wrapperToDetails validation: { Map<PhoneRecordWrapper, NotificationDetail> val, Notification obj ->
			if (val && obj.mutablePhone) {
				Collection<Long> pIds = WrapperUtils.mutablePhoneIdsIgnoreFails(val.keySet())
				if (pIds.any { Long pId -> pId != obj.mutablePhone.id }) {
					["mismatched", obj.mutablePhone.id]
				}
			}
		}
	}

	static Result<Notification> tryCreate(Phone mutPhone1) {
		DomainUtils.tryValidate(new Notification(mutPhone1), ResultStatus.CREATED)
	}

	// Methods
	// -------

	void addDetail(NotificationDetail nd1) {
		NotificationDetail existing1 = wrapperToDetails[nd1.wrapper]
		if (existing1) {
			existing1.items.addAll(nd1.items)
		}
		else { wrapperToDetails[nd1.wrapper] = nd1 }
	}

	boolean canNotifyAny(NotificationFrequency freq1) {
		buildCanNotifyPolicies(freq1).isEmpty() == false
	}

	Collection<OwnerPolicy> buildCanNotifyPolicies(NotificationFrequency freq1) {
		Collection<Long> itemIds = getItemIds()
		mutablePhone?.owner
			?.buildActivePoliciesForFrequency(freq1)
			?.findAll { OwnerPolicy op1 -> op1.canNotifyForAny(itemIds) }
			?: new ArrayList<OwnerPolicy>()
	}

	int countItems(boolean isOut, OwnerPolicy op1, Class<? extends RecordItem> clazz) {
		getDetails().inject(0) { int sum, NotificationDetail nd1 ->
    		sum + nd1.countItemsForOutgoingAndOptions(isOut, op1, clazz)
    	} as Integer
    }

    int countVoicemails(OwnerPolicy op1) {
        getDetails().inject(0) { int sum, NotificationDetail nd1 ->
        	sum + nd1.countVoicemails(op1)
        } as Integer
    }

    Collection<? extends RecordItem> buildAllowedItemsForOwnerPolicy(OwnerPolicy op1) {
        Collection<? extends RecordItem> allowedItems = []
        getDetails().each { NotificationDetail nd1 ->
            allowedItems.addAll(nd1.buildAllowedItemsForOwnerPolicy(op1))
        }
        allowedItems
    }

	// Properties
	// ----------

	Collection<NotificationDetail> getDetails() { wrapperToDetails.values() }

    Collection<? extends RecordItem> getItems() { CollectionUtils.mergeUnique(getDetails()*.items) }

    Collection<Long> getItemIds() { getItems()*.id }

	int getNumNotifiedForItem(NotificationFrequency freq1, RecordItem item) {
        getDetails().any { NotificationDetail nd1 -> nd1.items.contains(item) } ?
        	buildCanNotifyPolicies(freq1).size() :
        	0
    }

    Collection<PhoneRecordWrapper> getWrappersForOutgoing(boolean isOut) {
    	getDetails()
    		.findAll { NotificationDetail nd1 -> nd1.countItemsForOutgoingAndOptions(isOut) > 0 }
    		*.wrapper
    }
}
