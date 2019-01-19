package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import org.hibernate.criterion.CriteriaSpecification
import groovy.util.logging.Log4j

@GrailsTypeChecked
@Log4j
class IndividualPhoneRecordWrappers {

    static Result<IndividualPhoneRecordWrapper> tryCreate(Phone p1) {
        IndividualPhoneRecord.tryCreate(p1).then { IndividualPhoneRecord ipr1 ->
            IOCUtils.resultFactory.success(ipr1.toWrapper())
        }
    }

    static Result<IndividualPhoneRecordWrapper> mustFindForId(Long iprId) {
        PhoneRecord pr1 = PhoneRecord.get(prId)
        PhoneRecordWrapper w1 = pr1?.toWrapper()
        if (w1 instanceof IndividualPhoneRecordWrapper) {
            IOCUtils.resultFactory.success(w1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("contactService.update.notFound", //TODO
                ResultStatus.NOT_FOUND, [prId])
        }
    }

    static DetachedCriteria<PhoneRecord> buildForPhoneIdWithOptions(Long phoneId,
        String query = null, Collection<PhoneRecordStatus> statuses = PhoneRecordStatus.VISIBLE_STATUSES,
        boolean onlyShared = false) {

        DetachedCriteria<PhoneRecord> query = buildBase(query, statuses)
            .build { eq("phone.id", phoneId) }
        onlyShared ? query.build { isNotNull("shareSource") } : query // inner join
    }

    static DetachedCriteria<PhoneRecord> buildForSharedByIdWithOptions(Long sharedById,
        String query = null, Collection<PhoneRecordStatus> statuses = PhoneRecordStatus.VISIBLE_STATUSES) {

        buildBase(query, statuses).build { eq("shareSource.phone.id", phoneId) } // inner join
    }

    // For why sorting is separate, see `RecordItems.forSort`
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Closure forSort() {
        return {
            order("status", "desc") // unread first then active
            order("record.lastRecordActivity", "desc") // more recent first
        }
    }

    // TODO returns both contacts and shared contacts
    static Result<List<IndividualPhoneRecordWrapper>> tryFindEveryByNumbers(Phone p1,
        List<? extends BasePhoneNumber> bNums, boolean createIfAbsent)

    }

    // TODO
    // IndividualPhoneRecords.tryFindEveryByNumbers(p1, [pNum], true)
    //     .then { Map<PhoneNumber, List<Contact>> numberToContacts ->
    //         Result.<Contact>createSuccess(CollectionUtils.mergeUnique(*numberToContacts.values()))
    //     }
    //     .then { Collection<Contact> contacts ->
    //         ResultGroup<Void> resGroup = new ResultGroup<>()
    //         contacts.each { c1 ->
    //             resGroup << storeAndUpdateStatusForContact(storeContact, c1)
    //         }
    //         if (resGroup.anyFailures) {
    //             IOCUtils.resultFactory.failWithGroup(resGroup)
    //         }
    //         else {
    //             socketService.sendIndividualWrappers(contacts)
    //             IOCUtils.resultFactory.success(contacts)
    //         }
    //     }

    // TODO
    // protected Result<Void> storeAndUpdateStatusForContact(Closure<Void> storeContact, Contact c1) {
    //     storeContact.call(c1)
    //     // only change status to unread
    //     // dont' have to worry about blocked contacts since we already filtered those out
    //     c1.status = PhoneRecordStatus.UNREAD
    //     // NOTE: because we've already screened out all contacts that have been blocked
    //     // by the owner of the contact, this effectively means that blocking also effectively
    //     // stops all sharing relationships because we do not even attempt to deliver
    //     // message to shared contacts that have had their original contact blocked by
    //     // the original owner
    //     List<SharedContact> sharedContacts = c1.sharedContacts
    //     for (SharedContact sc1 in sharedContacts) {
    //         // only marked the shared contact's status as unread IF the shared contact's
    //         // status is NOT blocked. If the collaborator has blocked this contact then
    //         // we want to respect that decision.
    //         if (sc1.status != PhoneRecordStatus.BLOCKED) {
    //             sc1.status = PhoneRecordStatus.UNREAD
    //             if (!sc1.save()) {
    //                 return IOCUtils.resultFactory.failWithValidationErrors(sc1.errors)
    //             }
    //         }
    //     }
    //     c1.save() ?
    //         IOCUtils.resultFactory.success() :
    //         IOCUtils.resultFactory.failWithValidationErrors(c1.errors)
    // }

    // Helpers
    // -------

    static DetachedCriteria<PhoneRecord> buildBase(String query,
        Collection<PhoneRecordStatus> statuses) {

        new DetachedCriteria(PhoneRecord)
            .build(forStatuses(phoneId, statuses))
            .build(forQuery(query))
            .build(PhoneRecords.forActive())
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure forStatuses(Long phoneId,
        Collection<PhoneRecordStatus> statuses) {

        return { CriteriaUtils.inList(delegate, "status", statuses) }
    }

    // For hasMany left join, see: https://stackoverflow.com/a/45193881
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure forQuery(String query) {
        if (!query) {
            return { }
        }
        return {
            // need to use createAlias because this property is not on the superclass
            createAlias("numbers", "indNumbers1", CriteriaSpecification.LEFT_JOIN)
            or {
                // TODO how does same property name for two subclasses work??
                String formattedQuery = StringUtils.toQuery(query)
                ilike("name", formattedQuery)
                shareSource(CriteriaSpecification.LEFT_JOIN) { ilike("name", formattedQuery) }
                // don't include the numbers constraint if not number or else will match all
                String cleanedAsNumber = StringUtils.cleanPhoneNumber(query)
                if (cleanedAsNumber) {
                    String numberQuery = StringUtils.toQuery(cleanedAsNumber)
                    ilike("indNumbers1.number", numberQuery)
                    shareSource(CriteriaSpecification.LEFT_JOIN) {
                        numbers(CriteriaSpecification.LEFT_JOIN) { ilike("number", numberQuery) }
                    }
                }
            }
        }
    }
}
