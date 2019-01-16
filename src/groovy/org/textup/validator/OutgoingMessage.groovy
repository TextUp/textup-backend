package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.type.RecordItemType
import org.textup.type.VoiceLanguage
import org.textup.util.*

// TODO remove this class

@GrailsTypeChecked
@EqualsAndHashCode
@Validateable
class OutgoingMessage implements Validateable {

	String message
	MediaInfo media
	RecordItemType type = RecordItemType.TEXT
	VoiceLanguage language = VoiceLanguage.ENGLISH

	Recipients<Long, Contact> contacts
	Recipients<Long, SharedContact> sharedContacts
	Recipients<Long, ContactTag> tags

	static constraints = { // default nullable: false
		// [SHARED maxSize] 65535 bytes max for `text` column divided by 4 bytes per character ut8mb4
		message blank: true, nullable: true, maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE
		media nullable: true, validator: { MediaInfo mInfo, OutgoingMessage obj ->
			// message must have at least one of text and media
			if ((!mInfo || mInfo.isEmpty()) && !obj.message) { ["noInfo"] }
		}
		contacts cascadeValidation: true
        sharedContacts cascadeValidation: true
        tags cascadeValidation: true
	}

	// Methods
	// -------

	HashSet<Contactable> toRecipients() {
		HashSet<Contactable> recipients = new HashSet<>()
		if (hasErrors()) {
			return recipients
		}
        // add all contactables to a hashset to avoid duplication
        if (contacts) { recipients.addAll(contacts.recipients) }
        if (sharedContacts) { recipients.addAll(sharedContacts.recipients) }
        if (tags) {
        	tags.recipients.each { ContactTag ct1 -> recipients.addAll(ct1.members ?: []) }
        }
        recipients
	}

	Map<Long, List<ContactTag>> getContactIdToTags() {
		Map<Long, List<ContactTag>> contactIdToTags = [:].withDefault { [] as List<ContactTag> }
		if (hasErrors()) {
			return contactIdToTags
		}
		tags?.recipients?.each { ContactTag ct1 ->
			ct1.members?.each { Contact c1 -> contactIdToTags[c1.id] << ct1 }
		}
		contactIdToTags
	}

	// Property Access
	// ---------------

	// called during validation so needs to null-safe
	String getName() {
		if (hasErrors()) {
			return ""
		}
		Long id = contacts?.recipients?.find { Contact c1 -> c1.id }?.id
		if (id) { // don't return contact name, instead id, for PHI
			IOCUtils.getMessage("outgoingMessage.getName.contactId", [id])
		}
		else { tags?.recipients?.find { ContactTag ct1 -> ct1.name }?.name ?: "" }
    }

	boolean getIsText() {
		hasErrors() ? false : type == RecordItemType.TEXT
	}

	HashSet<Phone> getPhones() {
		HashSet<Phone> phones = new HashSet<>()
		if (hasErrors()) {
			return phones
		}
		if (contacts?.recipients) {
			phones.addAll(contacts.recipients*.phone)
		}
		if (sharedContacts?.recipients) {
			phones.addAll(sharedContacts.recipients*.sharedBy)
			phones.addAll(sharedContacts.recipients*.sharedWith)
		}
		if (tags?.recipients) {
			phones.addAll(tags.recipients*.phone)
		}
		phones
	}
}
