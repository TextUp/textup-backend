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

@EqualsAndHashCode(includeFields = true)
@GrailsTypeChecked
@TupleConstructor(includeFields = true, includes = ["mutablePhone", "wrapperToDetails"])
@Validateable
class Notification implements CanValidate {

	final Phone mutablePhone
	final Map<PhoneRecordWrapper, NotificationDetail> wrapperToDetails

    final Collection<NotificationDetail> details

	static constraints = {
		details cascadeValidation: true
		wrapperToDetails validator: { Map<PhoneRecordWrapper, NotificationDetail> val, Notification obj ->
			if (val && obj.mutablePhone) {
				Collection<Long> pIds = WrapperUtils.mutablePhoneIdsIgnoreFails(val.keySet())
				if (pIds.any { Long pId -> pId != obj.mutablePhone.id }) {
					["mismatched", obj.mutablePhone.id]
				}
			}
		}
	}

	static Result<Notification> tryCreate(Phone mutPhone1) {
		DomainUtils.tryValidate(new Notification(mutPhone1, [:]), ResultStatus.CREATED)
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
		buildCanNotifyReadOnlyPolicies(freq1).isEmpty() == false
	}

	Collection<? extends ReadOnlyOwnerPolicy> buildCanNotifyReadOnlyPolicies(NotificationFrequency freq1) {
		Collection<Long> itemIds = getItemIds()
		mutablePhone?.owner
			?.buildActiveReadOnlyPolicies(freq1)
			?.findAll { ReadOnlyOwnerPolicy op1 -> op1.canNotifyForAny(itemIds) }
			?: new ArrayList<ReadOnlyOwnerPolicy>()
	}

	int countItems(boolean isOut, ReadOnlyOwnerPolicy rop1, Class<? extends RecordItem> clazz) {
		getDetails().inject(0) { int sum, NotificationDetail nd1 ->
    		sum + nd1.countItemsForOutgoingAndOptions(isOut, rop1, clazz)
    	} as Integer
    }

    int countVoicemails(ReadOnlyOwnerPolicy rop1) {
        getDetails().inject(0) { int sum, NotificationDetail nd1 ->
        	sum + nd1.countVoicemails(rop1)
        } as Integer
    }

    Collection<NotificationDetail> buildDetailsWithAllowedItemsForOwnerPolicy(ReadOnlyOwnerPolicy rop1) {
        getDetails().findAll { NotificationDetail nd1 -> nd1.anyAllowedItemsForOwnerPolicy(rop1) }
    }

	// Properties
	// ----------

	Collection<NotificationDetail> getDetails() { wrapperToDetails.values() }

    Collection<? extends RecordItem> getItems() { CollectionUtils.mergeUnique(getDetails()*.items) }

    Collection<Long> getItemIds() { getItems()*.id }

	int getNumNotifiedForItem(RecordItem item, NotificationFrequency freq1 = null) {
        getDetails().any { NotificationDetail nd1 -> nd1.items.contains(item) } ?
        	buildCanNotifyReadOnlyPolicies(freq1).size() :
        	0
    }

    Collection<PhoneRecordWrapper> getWrappersForOutgoing(boolean isOut) {
    	getDetails()
    		.findAll { NotificationDetail nd1 -> nd1.countItemsForOutgoingAndOptions(isOut) > 0 }
            *.wrapper
    }
}
