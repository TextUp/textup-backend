package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*
import org.textup.util.*

@GrailsTypeChecked
@Validateable
class ContactTagRecipients extends Recipients<Long, ContactTag> {

    static constraints = { // all nullable: false by default
        recipients validator: { Collection<ContactTag> recips, ContactTagRecipients obj ->
            List<ContactTag> doNotBelong = []
            recips?.each { ContactTag ct1 ->
                if (ct1 && ct1.phone?.id != obj.phone?.id) { doNotBelong << ct1}
            }
            if (doNotBelong) {
                return ['foreign', doNotBelong*.id]
            }
        }
    }

    @Override
    protected List<ContactTag> buildRecipientsFromIds(List<Long> ids) {
        CollectionUtils.ensureNoNull(ContactTag.getAll(ids as Iterable<Serializable>))
    }
}
