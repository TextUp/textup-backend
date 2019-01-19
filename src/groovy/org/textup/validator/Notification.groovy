package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.type.*

@EqualsAndHashCode
@GrailsTypeChecked
@Validateable
class Notification implements Validateable, Dehydratable<Notification.Dehydrated> {

	final Phone phone
	private final Map<PhoneRecordWrapper, NotificationDetail> wrapperToDetails = [:]

	static constraints = {
		details cascadeValidation: true
		wrapperToDetails validation: { Map<PhoneRecordWrapper, NotificationDetail> val, Notification obj ->
			if (val && obj.phone) {
				List<Phone> wrappedPhones = RecordGroup
					.collect(val.keySet()) { PhoneRecordWrapper w1 -> w1.tryGetPhone() }
					.payload
				if (wrappedPhones.any { Phone p1 -> p1.id != obj.phone.id }) {
					["mismatched", obj.phone.id]
				}
			}
		}
	}

    @Validateable
    static class Dehydrated implements Validateable, Rehydratable<Notification> {

        final Long phoneId
        final Collection<Long> itemIds

        static Result<Notification.Dehydrated> tryCreate(Long phoneId, Collection<Long> itemIds) {
            Notification.Dehydrated dn1 = new Notification.Dehydrated(phoneId: phoneId,
                itemIds: Collections.unmodifiableCollection(itemIds))
            DomainUtils.tryValidate(dn1, ResultStatus.CREATED)
        }

        @Override
        Result<Notification> tryRehydrate() {
            NotificationUtils.tryBuildNotificationsForItems(itemIds, [phoneId])
                .then { List<Notification> notifs -> DomainUtils.tryValidate(notifs[0]) }
        }
    }

	static Result<Notification> tryCreate(Phone p1) {
		DomainUtils.tryValidate(new Notification(phone: p1), ResultStatus.CREATED)
	}

	// Methods
	// -------

    @Override
    Notification.Dehydrated dehydrate() {
        Collection<Long> itemIds = CollectionUtils.mergeUnique(*getDetails()*.items*.id)
        Notification.Dehydrated.create(phone.id, itemIds)
    }

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
		Collection<Long> itemIds = CollectionUtils.mergeUnique(*getDetails()*.items*.id)
		phone?.owner
			?.buildActivePoliciesForFrequency(freq1)
			?.findAll { OwnerPolicy op1 -> op1.canNotifyForAny(itemIds) }
			?: new ArrayList<OwnerPolicy>()
	}

	int countItems(boolean isOut, OwnerPolicy op1, Clazz<T extends RecordItem> clazz) {
		getDetails().inject(0) { int sum, NotificationDetail nd1 ->
    		sum + nd1.countItemsForOutgoingAndOptions(isOut, op1, clazz)
    	}
    }

    int countVoicemails(OwnerPolicy op1) {
        getDetails().inject(0) { int sum, NotificationDetail nd1 ->
        	sum + nd1.countVoicemails(op1)
        }
    }

	// Properties
	// ----------

	Collection<NotificationDetail> getDetails() { wrapperToDetails.values() }

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
