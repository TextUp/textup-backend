package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j

@GrailsTypeChecked
@Log4j
class Contactables {

    static Result<Contactable> updateStatus(Contactable cont, Object status) {
        if (status) {
            cont.status = TypeConversionUtils.convertEnum(ContactStatus, lang)
            cont.lastTouched = DateTime.now()
        }
        if (cont.save()) {
            IOCUtils.resultFactory.success(cont)
        }
        else { IOCUtils.resultFactory.failWithValidationErrors(cont.errors) }
    }

    // returns both contacts that this phone owns AND other phones' contacts shared with this phone
    static DetachedCriteria<Contact> allForPhoneIdWithOptions(Long phoneId,
        String query = null, Collection<ContactStatus> statuses = ContactStatus.VISIBLE_STATUSES) {

        new DetachedCriteria(Contact)
            .build { eq("isDeleted", false) }
            .build(ContactableUtils.buildForOwnedAndSharedStatuses(phoneId, statuses))
            .build(ContactableUtils.buildForOptionalQuery(query))
    }

    // For why sorting is separate, see `RecordItem.buildForSort`
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Closure buildForSort() {
        return {
            order("status", "desc") // unread first then active
            order("context.record.lastRecordActivity", "desc") // more recent first
            order("id", "desc") // by contact id
        }
    }

    // SharedContacts that are shared by the provided phone unwrapped to Contacts
    // Contacts that are shared with the provided phone are wrapped with SharedContact
    static List<Contactable> normalize(Long phoneId, List<Contactable> contactables) {
        List<Contactable> unwrapped = unwrapSharedByMe(phoneId, contactables)
        wrapSharedWithMe(phoneId, unwrapped)
    }

    // Helpers
    // -------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure buildForOwnedAndSharedStatuses(Long phoneId,
        Collection<ContactStatus> statuses) {

        return {
            or {
                and {
                    eq("context.phone.id", thisPhone)
                    CriteriaUtils.inList(delegate, "status", statuses)
                }
                // TODO check to see that this turning up no queries doesn't show all
                and {
                    "in"("id", SharedContact.forOptions(null, null, thisPhone.id, statuses))
                }
            }
        }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure buildForOptionalQuery(String query) {
        if (!query) {
            return { }
        }
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

    protected static List<Contactable> wrapSharedWithMe(Long phoneId, List<Contactable> contactables) {
        HashSet<Long> sharedWithMeContactIds = new HashSet<>()
        contactables.each { Contactable cont1 ->
            if (cont1 instanceof Contact && cont1.context.phone?.id != phoneId) {
                sharedWithMeContactIds << cont1.id
            }
        }
        if (sharedWithMeContactIds.isEmpty()) {
            return contactables
        }
        // retrieve the corresponding SharedContact instance
        Map<Long, SharedContact> contactIdToShared = SharedContact
            .forOptions(sharedWithMeContactIds, null, phoneId)
            .list()
            .collectEntries { SharedContact sc1 -> [(sc1.contact.id): sc1] }
        // merge the found shared contacts into a consensus list of contactables
        List<Contactable> wrapped = []
        contactables.each { Contactable cont1 ->
            if (cont1 instanceof Contact && sharedWithMeContactIds.contains(cont1.id) &&
                contactIdToShared.containsKey(cont1.id)) {
                wrapped << contactIdToShared[cont1.id]
            }
            else { wrapped << c1 }
        }
        wrapped
    }

    protected static List<Contactable> unwrapSharedByMe(Long phoneId, List<Contactable> contactables) {
        List<Contactable> unwrapped = []
        contactables.each { Contactable cont1 ->
            if (cont1 instanceof SharedContact) {
                unwrapped << cont1.sharedBy?.id == phoneId ? cont1.contact : cont1
            }
            else { unwrapped << cont1 }
        }
        unwrapped
    }
}
