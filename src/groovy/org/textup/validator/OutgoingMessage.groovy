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

	String message
	RecordItemType type = RecordItemType.TEXT
	VoiceLanguage language = VoiceLanguage.ENGLISH
	List<Contact> contacts = []
	List<SharedContact> sharedContacts = []
	List<ContactTag> tags = []

	Phone phone //set by validator

	static constraints = {
		message blank:false, nullable:false, maxSize:(2 * Constants.TEXT_LENGTH)
		contacts validator: { List<Contact> thisContacts, OutgoingMessage obj ->
			List<Contact> doNotBelong = []
			thisContacts.each { Contact c1 ->
				if (c1.phone != obj?.phone) {
					doNotBelong << c1
				}
			}
			if (doNotBelong) {
				return ['foreign', doNotBelong]
			}
		}
		sharedContacts validator: { List<SharedContact> thisShareds, OutgoingMessage obj ->
			List<SharedContact> invalidShare = []
			thisShareds.each { SharedContact sc1 ->
				if (!sc1.isActive || sc1.sharedWith != obj?.phone) {
					invalidShare << sc1
				}
			}
			if (invalidShare) {
				return ['notShared', invalidShare]
			}
		}
		tags validator: { List<ContactTag> thisTags, OutgoingMessage obj ->
			List<ContactTag> doNotBelong = []
			thisTags.each { ContactTag t1 ->
				if (t1.phone != obj?.phone) {
					doNotBelong << t1
				}
			}
			if (doNotBelong) {
				return ['foreign', doNotBelong]
			}
		}
		phone nullable:false
	}

	// Validation
	// ----------

	def validateSetPhone(Phone p1) {
		this.phone = p1
		// after setting phone, then run validations
		boolean isValid = this.validate()
		// then manually check the number of recipients
		HashSet<Contactable> recipients = this.toRecipients()
		// check number of recipients below maximum (to avoid abuse)
        Integer maxNumRecip = Helpers.to(Integer, Holders.flatConfig["textup.maxNumText"])
        if (recipients.size() > maxNumRecip) {
        	isValid = false
            this.errors.reject('outgoingMessage.tooManyRecipients')
        }
        else if (recipients.size() == 0) {
        	isValid = false
        	this.errors.reject('outgoingMessage.noRecipients')
        }
        // finally, return validation result
		isValid
	}

	// Property Access
	// ---------------

	HashSet<Contactable> toRecipients() {
		HashSet<Contactable> recipients = new HashSet<>()
        // add all contactables to a hashset to avoid duplication
        recipients.addAll(this.contacts)
        recipients.addAll(this.sharedContacts)
        this.tags.each { ContactTag ct1 -> recipients.addAll(ct1.members ?: []) }

        recipients
	}

	String getName() {
		Long id = this.contacts?.find { Contact c1 -> c1.id }?.id
		if (id) { // don't return contact name, instead id, for PHI
			getMessageSource()?.getMessage("outgoingMessage.getName.contactId",
            	[id] as Object[], LCH.getLocale()) ?: ""
		}
		else { this.tags?.find { ContactTag ct1 -> ct1.name }?.name ?: "" }
    }
	boolean getIsText() {
		this.type == RecordItemType.TEXT
	}
	HashSet<Phone> getPhones() {
		HashSet<Phone> phones = new HashSet<>()
		this.contacts.each { Contact c1 ->
			phones.add(c1.phone)
		}
		this.sharedContacts.each { SharedContact sc1 ->
			phones.add(sc1.sharedBy)
			phones.add(sc1.sharedWith)
		}
		this.tags.each { ContactTag ct1 -> phones.add(ct1.phone) }

		phones
	}

	protected MessageSource getMessageSource() {
		Holders
			.applicationContext
			.getBean('messageSource') as MessageSource
	}
}
