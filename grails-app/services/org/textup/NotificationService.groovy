package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.type.PhoneOwnershipType
import org.textup.validator.action.ActionContainer
import org.textup.validator.action.NotificationPolicyAction
import org.textup.validator.BasicNotification

@GrailsTypeChecked
@Transactional(readOnly=true)
class NotificationService {

	ResultFactory resultFactory

	// Modifying notification policies
	// -------------------------------

	Result<Void> handleNotificationActions(Phone p1, Long recordId, Object rawActions) {
		ActionContainer ac1 = new ActionContainer(rawActions)
        List<NotificationPolicyAction> actions = ac1.validateAndBuildActions(NotificationPolicyAction)
        if (ac1.hasErrors()) {
            return resultFactory.failWithValidationErrors(ac1.errors)
        }
        for (NotificationPolicyAction a1 in actions) {
            NotificationPolicy np1 = p1.owner.getOrCreatePolicyForStaff(a1.id)
            switch (a1) {
                case Constants.NOTIFICATION_ACTION_DEFAULT:
                    np1.level = a1.levelAsEnum
                    break
                case Constants.NOTIFICATION_ACTION_ENABLE:
                    np1.enable(recordId)
                    break
                default: // Constants.NOTIFICATION_ACTION_DISABLE
                    np1.disable(recordId)
            }
            if (!np1.save()) {
                return resultFactory.failWithValidationErrors(np1.errors)
            }
        }
        resultFactory.success()
	}

	// Building notifications
	// ----------------------

	List<BasicNotification> build(Phone targetPhone, List<Contact> contacts,
        List<ContactTag> tags = []) {

		Map<Long, Record> phoneIdToRecord = [:]
		Map<Long, Long> staffIdToPersonalPhoneId = [:]
		Map<Phone, List<Staff>> phonesToCanNotify = [:]
		// populate these data maps from the phone and list of contacts
		populateData(phoneIdToRecord, staffIdToPersonalPhoneId, phonesToCanNotify,
			targetPhone, contacts, tags)
		// then build basic notifications with these data maps
		buildNotifications(phoneIdToRecord, staffIdToPersonalPhoneId, phonesToCanNotify)
	}

	// Helpers
	// -------

	protected void populateData(Map<Long, Record> phoneIdToRecord,
		Map<Long, Long> staffIdToPersonalPhoneId, Map<Phone, List<Staff>> phonesToCanNotify,
		Phone targetPhone, List<Contact> contacts, List<ContactTag> tags = []) {

		List<SharedContact> sharedContacts = SharedContact
		    .findEveryByContactIdsAndSharedBy(contacts*.id, targetPhone)
		List<Phone> allPhones = [targetPhone]
		HashSet<Long> recordIds = new HashSet<>()
		// if multiple contacts from the same phone, then
        // this will take the last contact's record. In the future,
        // if we wanted to support showing all the records that received
        // the text, we can do so by exhaustively listing all record ids
		contacts.each { Contact c1 ->
		    phoneIdToRecord[c1.phone.id] = c1.record
		    recordIds << c1.record.id
		}
        // again, currently on support assocating one record with each notification. If there's a
        // tag record for a group of contacts as well, prefer this record because the tag
        // brings together a group of contacts
        tags.each { ContactTag ct1 ->
            phoneIdToRecord[ct1.phone.id] = ct1.record
            recordIds << ct1.record.id
        }
        // respect the gated getRecord method on the SharedContact, then those with view-only
        // permissions will not receive notifications or incoming calls
		sharedContacts.each { SharedContact sc1 ->
            Record rec1 = sc1.record
            if (rec1) {
                phoneIdToRecord[sc1.sharedWith.id] = rec1
                allPhones << sc1.sharedWith
                recordIds << rec1.id
            }
		}
		allPhones.each { Phone p1 ->
			List<Staff> canNotify = p1.owner.getCanNotifyAndAvailable(recordIds)
			if (canNotify) {
				phonesToCanNotify[p1] = canNotify
			}
			// if a staff member is part of a team that has a TextUp phone
	        // and also has an individual TextUp phone, then we don't want
	        // to send multiple notifications. Therefore, this map associates
	        // each staff member with his/her personal TextUp phone
	        // if the staff member has a personal TextUp phone
	        // FOR THE PHONES THAT HAVE ACCESS TO THIS RECORD. Since we
	        // are looping through the phones that have access to this record
	        // we don't have to worry about the case where the staff member
	        // has a personal TextUp phone and is part of the team but is not
	        // shared on the contact on the personal phone. If this were the case,
	        // then the personal phone wouldn't even be a key in this map.
			if (p1.owner.type == PhoneOwnershipType.INDIVIDUAL) {
				staffIdToPersonalPhoneId[p1.owner.ownerId] = p1.id
			}
		}
	}
	protected List<BasicNotification> buildNotifications(Map<Long, Record> phoneIdToRecord,
		Map<Long, Long> staffIdToPersonalPhoneId, Map<Phone, List<Staff>> phonesToCanNotify) {
		List<BasicNotification> notifs = []
		// if you are concerned about several tokens generated
        // for one message, remember that we also send unique tokens
        // to those who are members of a team. So if a contact
        // from a team is shared with a staff and that staff is
        // also a member of that team, the staff member will
        // receive two notification texts each with a unique token
        // And these tokens will appear to be the same when you
        // look at the database because the token's data does not
        // specify the from and to numbers of the notification
		phonesToCanNotify.each { Phone p1, List<Staff> staffs ->
            for (Staff s1 in staffs) {
                // if the staff member has a personal phone AND
                // this phone p1 is NOT the personal phone,
                // then skip notifying until we get to the personal phone
                if (staffIdToPersonalPhoneId.containsKey(s1.id) &&
                    staffIdToPersonalPhoneId[s1.id] != p1.id) {
                    continue
                }
                BasicNotification notif = new BasicNotification(owner:p1.owner,
                	record:phoneIdToRecord[p1.id], staff:s1)
                if (notif.validate()) { notifs << notif }
                else { log.error("NotificationService.buildNotifications: ${notif.errors}") }
            }
        }
        notifs
	}
}
