package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*
import org.textup.util.*

@GrailsTypeChecked
@Validateable
class ContactRecipients extends Recipients<Long, Contact> {

    static constraints = { // all nullable: false by default
        recipients validator: { Collection<Contact> recips, ContactRecipients obj ->
            List<Contact> doNotBelong = []
            recips?.each { Contact c1 ->
                if (c1 && c1.phone != obj.phone) { doNotBelong << c1 }
            }
            if (doNotBelong) {
                return ['foreign', doNotBelong*.id]
            }
        }
    }

    @Override
    protected List<Contact> buildRecipientsFromIds(List<Long> ids) {
        CollectionUtils.ensureNoNull(Contact.getAll(ids as Iterable<Serializable>))
    }
}
