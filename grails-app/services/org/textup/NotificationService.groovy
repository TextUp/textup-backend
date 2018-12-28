package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class NotificationService {

    GrailsApplication grailsApplication
    ResultFactory resultFactory
    TextService textService
    TokenService tokenService

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

    Result<NotificationPolicy> update(NotificationPolicy np1, Map body, String timezone) {
        if (TypeConversionUtils.to(Boolean, body.useStaffAvailability) != null) {
            np1.useStaffAvailability = TypeConversionUtils.to(Boolean, body.useStaffAvailability)
        }
        if (TypeConversionUtils.to(Boolean, body.manualSchedule) != null) {
            np1.manualSchedule = TypeConversionUtils.to(Boolean, body.manualSchedule)
        }
        if (TypeConversionUtils.to(Boolean, body.isAvailable) != null) {
            np1.isAvailable = TypeConversionUtils.to(Boolean, body.isAvailable)
        }
        if (body.schedule instanceof Map) {
            Result<Schedule> res = np1.updateSchedule(body.schedule as Map, timezone)
            if (!res.success) { return res }
        }
        if (np1.save()) {
            resultFactory.success(np1)
        }
        else { resultFactory.failWithValidationErrors(np1.errors) }
    }

	// Building notifications
	// ----------------------

	List<BasicNotification> build(Phone targetPhone, Collection<Contact> contacts,
        Collection<ContactTag> tags = []) {

		Map<Long, Record> phoneIdToRecord = [:]
        Map<Long, String> phoneIdToClientName = [:]
		Map<Long, Long> staffIdToPersonalPhoneId = [:]
		Map<Phone, List<Staff>> phonesToCanNotify = [:]
		// populate these data maps from the phone and list of contacts
		populateData(phoneIdToRecord, phoneIdToClientName, staffIdToPersonalPhoneId,
            phonesToCanNotify, targetPhone, contacts, tags)
		// then build basic notifications with these data maps
		buildNotifications(phoneIdToRecord, phoneIdToClientName, staffIdToPersonalPhoneId,
            phonesToCanNotify)
	}

    ResultGroup<Void> send(List<BasicNotification> notifs, Boolean outgoing, String contents) {
        ResultGroup<Void> outcomes = new ResultGroup<>()
        notifs.each { BasicNotification bn1 ->
            outcomes << sendForNotification(bn1, outgoing, contents)
        }
        outcomes.logFail("NotificationService.send for records with ids: ${notifs*.record*.id}")
    }

    @RollbackOnResultFailure
    Result<Notification> show(String token) {
        tokenService.findNotification(token).then { Token tok ->
            Map data = tok.data
            Notification notif = new Notification(contents:data.contents as String,
                owner:Phone.get(TypeConversionUtils.to(Long, data.phoneId))?.owner,
                outgoing:TypeConversionUtils.to(Boolean, data.outgoing),
                tokenId:TypeConversionUtils.to(Long, tok.id))
            notif.record = Record.get(TypeConversionUtils.to(Long, data.recordId))
            if (notif.validate()) {
                resultFactory.success(notif)
            }
            else { resultFactory.failWithValidationErrors(notif.errors) }
        }
    }

	// Helpers
	// -------

	protected void populateData(Map<Long, Record> phoneIdToRecord,
        Map<Long, String> phoneIdToClientName, Map<Long, Long> staffIdToPersonalPhoneId,
        Map<Phone, List<Staff>> phonesToCanNotify, Phone targetPhone, Collection<Contact> contacts,
        Collection<ContactTag> tags = []) {

		List<SharedContact> sharedContacts = SharedContact
		    .findEveryByContactIdsAndSharedBy(contacts*.id, targetPhone)
		List<Phone> allPhones = [targetPhone]
		HashSet<Long> recordIds = new HashSet<>()
		// if multiple contacts from the same phone, then
        // this will take the last contact's record. In the future,
        // if we wanted to support showing all the records that received
        // the text, we can do so by exhaustively listing all record ids
		contacts.each { Contact c1 ->
            // Do not use `getNameOrNumber` on contact because we only want to show the initials, NOT
            // accidentally leak the contact's phone number. Note that StringUtils.buildInitials
            // also strips digits for an additional safeguard against leaking client numbers
            phoneIdToClientName[c1.phone.id] = c1.name
		    phoneIdToRecord[c1.phone.id] = c1.record
		    recordIds << c1.record.id
		}
        // again, currently only support associating one record with each notification. If there's a
        // tag record for a group of contacts as well, prefer this record because the tag
        // brings together a group of contacts
        tags.each { ContactTag ct1 ->
            phoneIdToClientName[ct1.phone.id] = ct1.name
            phoneIdToRecord[ct1.phone.id] = ct1.record
            recordIds << ct1.record.id
        }
        // respect the gated getRecord method on the SharedContact, then those with view-only
        // permissions will not receive notifications or incoming calls
		sharedContacts.each { SharedContact sc1 ->
            sc1.tryGetRecord().thenEnd { Record rec1 ->
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
        Map<Long, String> phoneIdToClientName, Map<Long, Long> staffIdToPersonalPhoneId,
        Map<Phone, List<Staff>> phonesToCanNotify) {

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
                BasicNotification notif = new BasicNotification(owner: p1.owner,
                	record: phoneIdToRecord[p1.id],
                    staff: s1,
                    otherName: phoneIdToClientName[p1.id])
                if (notif.validate()) {
                    notifs << notif
                }
                else { log.error("NotificationService.buildNotifications: ${notif.errors}") }
            }
        }
        notifs
	}

    protected Result<Void> sendForNotification(BasicNotification bn1, Boolean isOut, String msg1) {
        Phone p1 = bn1.owner.phone
        Staff s1 = bn1.staff
        // short circuit if no staff specified or staff has no personal phone
        if (!s1?.personalPhoneAsString) {
            return resultFactory.success()
        }
        Map tokenData = [
            phoneId: p1.id,
            recordId: bn1.record.id,
            contents: msg1,
            outgoing: isOut
        ]
        String notifyLink = grailsApplication.flatConfig["textup.links.notifyMessage"],
            suffix = IOCUtils.getMessage("notificationService.send.notificationSuffix"),
            instr = buildInstructions(bn1, isOut)
        // Surround the link with messages to prevent iMessage from removing the link from the message
        // in order to generate a preview
        tokenService.generateNotification(tokenData).then { Token tok1 ->
            String notification = "${instr} \n\n${notifyLink + tok1.token} \n\n${suffix}"
            textService.send(p1.number, [s1.personalPhoneNumber], notification, p1.customAccountId)
        }
    }
    // Outgoing notification is always BasicNotification and never Notification. Notification objects
    // are only created when the preview message token is redeemed
    protected String buildInstructions(BasicNotification bn1, Boolean isOutgoing) {
        String ownerName = bn1.owner.buildName(),
            otherInitials = StringUtils.buildInitials(bn1.otherName)
        if (otherInitials) {
            String code = isOutgoing
                ? "notificationService.outgoing.withFrom"
                : "notificationService.incoming.withFrom"
            IOCUtils.getMessage(code, [ownerName, otherInitials])
        }
        else {
            String code = isOutgoing
                ? "notificationService.outgoing.noFrom"
                : "notificationService.incoming.noFrom"
            IOCUtils.getMessage(code, [ownerName])
        }
    }
}
