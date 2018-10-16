package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.type.RecordItemType
import org.textup.type.VoiceLanguage

@GrailsTypeChecked
@EqualsAndHashCode
@Validateable
class OutgoingMessage {

	String message
	MediaInfo media
	RecordItemType type = RecordItemType.TEXT
	VoiceLanguage language = VoiceLanguage.ENGLISH

	Recipients<Long, Contact> contacts
	Recipients<Long, SharedContact> sharedContacts
	Recipients<Long, ContactTag> tags

	static constraints = {
		// [SHARED maxSize] 65535 bytes max for `text` column divided by 4 bytes per character ut8mb4
		message blank: true, nullable: true, maxSize: Constants.MAX_TEXT_COLUMN_SIZE
		media nullable: true, validator: { MediaInfo mInfo, OutgoingMessage obj ->
			// message must have at least one of text and media
			if ((!mInfo || mInfo.isEmpty()) && !obj.message) { ["noInfo"] }
		}
	}

	// Methods
	// -------

	HashSet<Contactable> toRecipients() {
		HashSet<Contactable> recipients = new HashSet<>()
        // add all contactables to a hashset to avoid duplication
        if (contacts) { recipients.addAll(contacts.recipients) }
        if (sharedContacts) { recipients.addAll(sharedContacts.recipients) }
        if (tags) {
        	tags.recipients.each { ContactTag ct1 -> recipients.addAll(ct1.members ?: []) }
        }
        recipients
	}

	HashSet<WithRecord> toRecordOwners() {
		HashSet<WithRecord> recordOwners = new HashSet<>()
		if (contacts) { recordOwners.addAll(contacts.recipients) }
        if (sharedContacts) { recordOwners.addAll(sharedContacts.recipients) }
        if (tags) { recordOwners.addAll(tags.recipients) }
        recordOwners
	}

	Map<Long, List<ContactTag>> getContactIdToTags() {
		Map<Long, List<ContactTag>> contactIdToTags = [:].withDefault { [] as List<ContactTag> }
		tags?.recipients?.each { ContactTag ct1 ->
			ct1.members?.each { Contact c1 -> contactIdToTags[c1.id] << ct1 }
		}
		contactIdToTags
	}

	// Property Access
	// ---------------

	// called during validation so needs to null-safe
	String getName() {
		if (!contacts || !tags) {
			return ""
		}
		Long id = contacts.recipients?.find { Contact c1 -> c1.id }?.id
		if (id) { // don't return contact name, instead id, for PHI
			Helpers.getMessage("outgoingMessage.getName.contactId", [id])
		}
		else { tags.recipients?.find { ContactTag ct1 -> ct1.name }?.name ?: "" }
    }

	boolean getIsText() { type == RecordItemType.TEXT }

	HashSet<Phone> getPhones() {
		HashSet<Phone> phones = new HashSet<>()
		if (!contacts || !sharedContacts || !tags) {
			return phones
		}
		phones.addAll(contacts.recipients*.phone)
		phones.addAll(sharedContacts.recipients*.sharedBy)
		phones.addAll(sharedContacts.recipients*.sharedWith)
		phones.addAll(tags.recipients*.phone)
		phones
	}
}
