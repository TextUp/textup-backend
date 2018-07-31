package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*

@GrailsTypeChecked
@Validateable
class ContactTagRecipients extends Recipients<Long, ContactTag> {

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

    @Override
    protected List<ContactTag> buildRecipientsFromIds(List<Long> ids) {
        ContactTag.getAll(ids as Iterable<Serializable>)
    }
}
