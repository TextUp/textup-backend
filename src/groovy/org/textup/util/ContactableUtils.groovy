package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j

@GrailsTypeChecked
@Log4j
class ContactableUtils {

    static DetachedCriteria<Contact> sharedAndOwnedForPhoneIdWithOptions(Long phoneId,
        String query = null, Collection<ContactStatus> statuses = ContactStatus.VISIBLE_STATUSES) {

        new DetachedCriteria<Contact>()
            .build { eq("isDeleted", false) }
            .build(ContactableUtils.buildForOwnedAndSharedStatuses(phoneId, statuses))
            .build(ContactableUtils.buildForOptionalQuery(query))
    }

    // For why sorting is separate, see `RecordItem.buildForSort`
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Closure buildForSort(boolean recentFirst) {
        return {
            order("status", "desc") // unread first then active
            order("context.record.lastRecordActivity", "desc") // more recent first
            order("id", "desc") // by contact id
        }
    }

    // some contacts returned may not be mine and may instead have a SharedContact
    // that is shared with me. For these, we want to do an in-place replacement
    // of the contact that doesn't belong to me with the SharedContact
    static List<Contactable> replaceContactsWithShared(Phone p1, List<Contact> contacts) {
        //identify those contacts that are shared with me and their index
        HashSet<Long> notMyContactIds = new HashSet<>()
        contacts.each { Contact contact ->
            if (contact.phone?.id != p1.id) { notMyContactIds << contact.id }
        }
        //if all contacts are mine, we can return
        if (notMyContactIds.isEmpty()) { return contacts }
        //retrieve the corresponding SharedContact instance
        Map<Long,SharedContact> contactIdToSharedContact = (SharedContact.createCriteria()
            .list {
                CriteriaUtils.inList(delegate, "contact.id", notMyContactIds)
                // ensure that we only fetch SharedContacts that belong to this phone
                eq("sharedWith", p1)
                // Ensure that SharedContact is not expired. This might become a problem
                // when a contact has two SharedContacts, one active and one expired.
                // The contact will show up in the contacts list and when we find the shared
                // contacts from the contact ids, we want to only get the SharedContact
                // that is active and exclude the one that is expired
                or {
                    isNull("dateExpired") //not expired if null
                    gt("dateExpired", DateTime.now())
                }
            } as List<SharedContact>)
            .collectEntries { [(it.contact.id):it] }
        //merge the found shared contacts into a consensus list of contactables
        List<Contactable> contactables = []
        contacts.each { Contact contact ->
            if (notMyContactIds.contains(contact.id)) {
                if (contactIdToSharedContact.containsKey(contact.id)) {
                    contactables << contactIdToSharedContact[contact.id]
                }
                // if shared contact not found, silently ignore this contact
            }
            else { contactables << contact }
        }
        contactables
    }

    // Helpers
    // -------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure buildForOwnedAndSharedStatuses(Long phoneId,
        Collection<ContactStatus> statuses) {

        return {
            or {
                // if my contact, check status directly on this contact
                and {
                    eq("context.phone.id", thisPhone)
                    CriteriaUtils.inList(delegate, "status", statuses)
                }



                // TODO finish after changing SharedContact to use DetachedCriteria
                // // if not my contact (shared with me), check the status on the shared contact
                // // NOTE: by default, this finder will NOT show shared contacts for contacts
                // // blocked by the original owner. See `ContactStatus.VISIBLE_STATUSES`
                // Collection<SharedContact> shareds = SharedContact
                //     .sharedWithMe(thisPhone, statuses)
                //     .list()
                // if (shareds) {
                //     // critical that this `and` criteria builder clause is nested INSIDE of
                //     // this `if` statement. Otherwise, an empty `and` clause inside of an
                //     // `or` clause will be interpreted as permitting ALL contacts to be displayed
                //     and {
                //         CriteriaUtils.inList(delegate, "id", shareds*.contact*.id)
                //     }
                // }
            }
        }
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure buildForOptionalQuery(String query) {
        if (query) {
            String cleanedAsNumber = StringUtils.cleanPhoneNumber(query)
            return {
                or {
                    ilike("name", query)
                    // don't include the numbers constraint if the cleaned query
                    // is not a number because the cleaning process will return
                    // an empty string and transforming this to a query string will
                    // yield '%' which matches all results
                    if (cleanedAsNumber) {
                        numbers { ilike("number", StringUtils.toQuery(cleanedAsNumber)) }
                    }
                }
            }
        }
        else { return { } }
    }
}
