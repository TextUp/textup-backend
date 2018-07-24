package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import org.textup.*

@GrailsCompileStatic
@Validateable
class SharedContactRecipients extends Recipients<Long, SharedContact> {

    // For shared contacts, ids are actually ids of the CONTACTS themselves
    // because the API only present contacts, never shared contacts. Therefore, when
    // we transform these ids to recipients, we need to account for transfering these contact
    // ids into shared contact domain objects
    private List<SharedContact> recipients = Collections.emptyList()

    static constraints = { // all nullable: false by default
        recipients validator: { Collection<SharedContact> recips, SharedContactRecipients obj ->
            List<SharedContact> invalidShare = []
            recips?.each { SharedContact sc1 ->
                if (!sc1.canModify || sc1.sharedWith != obj.phone) { invalidShare << sc1 }
            }
            if (invalidShare) {
                return ['notShared', invalidShare*.contactId]
            }
        }
    }

    // Events
    // ------

    def beforeValidate() {
        if (ids && phone && !recipients) {
            recipients = SharedContact.findEveryByContactIdsAndSharedWith(ids, phone)
        }
    }

    // Property access
    // ---------------

    List<SharedContact> getRecipients() { this.recipients }
}
