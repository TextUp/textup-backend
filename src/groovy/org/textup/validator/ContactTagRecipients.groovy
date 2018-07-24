package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import org.textup.*

@GrailsCompileStatic
@Validateable
class ContactTagRecipients extends Recipients<Long, ContactTag> {

    private List<ContactTag> recipients = Collections.emptyList()

    static constraints = { // all nullable: false by default
        recipients validator: { Collection<ContactTag> recips, ContactTagRecipients obj ->
            List<ContactTag> doNotBelong = []
            recips?.each { ContactTag ct1 ->
                if (ct1.phone != obj.phone) { doNotBelong << ct1}
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
            recipients = ContactTag.getAll(ids)
        }
    }

    // Property access
    // ---------------

    List<ContactTag> getRecipients() { this.recipients }
}
