package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import org.textup.*

@GrailsCompileStatic
@Validateable
class ContactRecipients extends Recipients<Long, Contact> {

    private List<Contact> recipients = Collections.emptyList()

    static constraints = { // all nullable: false by default
        recipients validator: { Collection<Contact> recips, ContactRecipients obj ->
            List<Contact> doNotBelong = []
            recips?.each { Contact c1 ->
                if (c1.phone != obj.phone) { doNotBelong << c1 }
            }
            if (doNotBelong) {
                return ['foreign', doNotBelong*.id]
            }
        }
    }

    // Events
    // ------

    def beforeValidate() {
        if (ids && !recipients) {
            recipients = Contact.getAll(ids)
        }
    }

    // Property access
    // ---------------

    List<Contact> getRecipients() { this.recipients }
}
