package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.util.Holders
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.types.RecordItemType

@GrailsCompileStatic
@EqualsAndHashCode
@Validateable
class OutgoingMessage {

	String message
	RecordItemType type = RecordItemType.TEXT
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

	def validateSetPhone(Phone p1) {
		this.phone = p1
		// after setting phone, then run validations
		boolean isValid = this.validate()
		// then manually check the number of recipients
		HashSet<Contactable> recipients = this.toRecipients()
		// check number of recipients below maximum (to avoid abuse)
        Integer maxNumRecip = Helpers.toInteger(Holders.flatConfig["textup.maxNumText"])
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

	HashSet<Contactable> toRecipients() {
		HashSet<Contactable> recipients = new HashSet<>()
        // add all contactables to a hashset to avoid duplication
        recipients.addAll(this.contacts)
        recipients.addAll(this.sharedContacts)
        this.tags.each { ContactTag ct1 -> recipients.addAll(ct1.members ?: []) }

        recipients
	}
}
