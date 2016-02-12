package org.textup.validator

import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@EqualsAndHashCode
@Validateable
class OutgoingText {

	String message
	List<Contact> contacts = []
	List<SharedContact> sharedContacts = []
	List<ContactTag> tags = []

	Phone phone //set by validator

	static constraints = {
		message blank:false, nullable:false, maxSize:320
		contacts validator: { List<Contact> thisContacts, OutgoingText obj ->
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
		sharedContacts validator: { List<SharedContact> thisShareds, OutgoingText obj ->
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
		tags validator: { List<ContactTag> thisTags, OutgoingText obj ->
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
		this.validate()
	}
}
