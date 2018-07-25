package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.*
import org.textup.type.RecordItemType
import org.textup.type.VoiceLanguage

@GrailsTypeChecked
@EqualsAndHashCode
@Validateable
class OutgoingMessage {

	String message = ""
	MediaInfo media
	RecordItemType type = RecordItemType.TEXT
	VoiceLanguage language = VoiceLanguage.ENGLISH

	ContactRecipients contacts
	SharedContactRecipients sharedContacts
	ContactTagRecipients tags

	static constraints = {
		message blank: true, nullable: true, shared: "textSqlType"
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
        recipients.addAll(contacts?.recipients)
        recipients.addAll(sharedContacts?.recipients)
        tags?.recipients?.each { ContactTag ct1 -> recipients.addAll(ct1.members ?: []) }
        recipients
	}

	// Property Access
	// ---------------

	String getName() {
		Long id = contacts?.recipients?.find { Contact c1 -> c1.id }?.id
		if (id) { // don't return contact name, instead id, for PHI
			Helpers.messageSource.getMessage("outgoingMessage.getName.contactId",
            	[id] as Object[], LCH.getLocale()) ?: ""
		}
		else { tags?.recipients?.find { ContactTag ct1 -> ct1.name }?.name ?: "" }
    }

	boolean getIsText() { type == RecordItemType.TEXT }

	HashSet<Phone> getPhones() {
		HashSet<Phone> phones = new HashSet<>()
		phones.addAll(contact.recipients*.phones)
		phones.addAll(sharedContacts.recipients*.sharedBy)
		phones.addAll(sharedContacts.recipients*.sharedWith)
		phones.addAll(tags.recipients*.phone)
		phones
	}
}
