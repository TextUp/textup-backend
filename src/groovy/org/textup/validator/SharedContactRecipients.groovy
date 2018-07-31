package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import org.textup.*

// For shared contacts, ids are actually ids of the CONTACTS themselves
// because the API only present contacts, never shared contacts. Therefore, when
// we transform these ids to recipients, we need to account for transfering these contact
// ids into shared contact domain objects

@GrailsTypeChecked
@Validateable
class SharedContactRecipients extends Recipients<Long, SharedContact> {

    static constraints = { // all nullable: false by default
        recipients validator: { Collection<SharedContact> recips, SharedContactRecipients obj ->
            if (!recips) { return }
            // static finder only returns valid shared contacts so we just compare the
            // recipients that were returned by the static finder with the ids we have
            // to find the ids that weren't valid
            List<Long> invalidContactIds = []
            HashSet<Long> validContactIds = new HashSet<Long>(recips*.contactId)
            obj.ids?.each { Long cId ->
                if (!validContactIds.contains(cId)) { invalidContactIds << cId }
            }
            if (invalidContactIds) {
                return ['notShared', invalidContactIds]
            }
        }
    }

    @Override
    protected List<SharedContact> buildRecipientsFromIds(List<Long> ids) {
        // this static finder only returns valid shared contacts
        phone ? SharedContact.findEveryByContactIdsAndSharedWith(ids, phone) : []
    }
}
