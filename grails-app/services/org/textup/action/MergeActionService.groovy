package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.springframework.transaction.annotation.Propagation
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.action.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class MergeActionService implements HandlesActions<Long, Void> {

    @Override
    boolean hasActions(Map body) { !!body.doMergeActions }

    @Override
    @RollbackOnResultFailure
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    Result<Void> tryHandleActions(Long iprId, Map body) {
        IndividualPhoneRecord.mustFindActiveForId(iprId)
            .then { IndividualPhoneRecord ipr1 ->
                ActionContainer.tryProcess(MergeIndividualAction, body.doMergeActions).curry(ipr1)
            }
            .then { IndividualPhoneRecord ipr1, List<MergeIndividualAction> actions ->
                ResultGroup<Contact> resGroup = new ResultGroup<>()
                actions.each { MergeIndividualAction a1 ->
                    Collection<IndividualPhoneRecord> toBeMerged = IndividualPhoneRecords
                        .findEveryByIdsAndPhoneId(a1.toBeMergedIds, ipr1.phone.id)
                    switch (a1) {
                        case MergeIndividualAction.DEFAULT:
                            resGroup << tryMerge(ipr1, toBeMerged)
                            break
                        default: // MergeIndividualAction.RECONCILE
                            resGroup << tryMerge(ipr1, toBeMerged, a1.buildName(), a1.buildNote())
                    }
                }
                resGroup.toEmptyResult(false)
            }
    }

    // Helpers
    // -------

    // (1) We made the merge method require a new transaction in order to insulate it from
    // potential changes made to the merged-in contacts. Changes to the merged-in contacts
    // will trigger a cascade on save that will undo the merge operations. For example, if we
    // set the isDeleted flag to true for the merged-in contacts within this method, then the
    // tags that contact the merged-in contacts will continue to have these as their members
    // because their membership we re-saved in the save cascade.
    // (2) Requiring a new transaction for this method only works when this method is called
    // from another service. Self-calls by another method in DuplicateService will not trigger
    // the interceptor and this merge method will execute in the same transaction. This is the
    // behavior of the underlying Spring Transaction abstraction. See
    // http://www.tothenew.com/blog/grails-transactions-using-transactional-annotations-and-propagation-requires_new/
    protected Result<IndividualPhoneRecord> tryMerge(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        mergeFields(ipr1, toBeMerged)
            .then { mergeNumbers(ipr1, toBeMerged) }
            .then { mergeGroups(ipr1, toBeMerged) }
            .then { mergeSharing(ipr1, toBeMerged) }
            .then { mergeRecords(ipr1, toBeMerged) }
            .then { DomainUtils.trySave(ipr1) }
    }

    protected Result<IndividualPhoneRecord> tryMerge(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged, String newName, String newNote) {

        tryMerge(ipr1, toBeMerged).then { IndividualPhoneRecord ipr1 ->
            ipr1.name = newName
            ipr1.note = newNote
            DomainUtils.trySave(ipr1)
        }
    }

    protected Result<Void> mergeFields(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        ipr1.status = PhoneRecordStatus
            .reconcile(CollectionUtils.mergeUnique([ipr1.status], *toBeMerged*.status))
        toBeMerged.each { it.isDeleted = true }
        DomainUtils.trySaveAll(CollectionUtils.mergeUnique([ipr1], toBeMerged))
    }

    protected Result<Void> mergeNumbers(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        ResultGroup<ContactNumber> resGroup = new ResultGroup<>()
        CollectionUtils
            .mergeUnique(*toBeMerged*.numbers)
            .each { ContactNumber cNum -> resGroup << ipr1.mergeNumber(cNum, cNum.preference) }
        resGroup.toEmptyResult(false)
    }

    protected Result<Void> mergeGroups(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        try {
            List<GroupPhoneRecord> gprs = GroupPhoneRecords
                .buildForMemberIds(toBeMerged*.id)
                .list()
            gprs.each { GroupPhoneRecord gpr1 -> gpr1.addToMembers(ipr1) }
            DomainUtils.trySaveAll(gprs)
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "mergeTags", true)
        }
    }

    protected Result<Void> mergeSharing(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        try {
            new DetachedCriteria(PhoneRecords)
                .build(PhoneRecords.forShareSourceIds(toBeMerged*.id))
                .updateAll(record: ipr1.record, shareSource: ipr1)
            IOCUtils.resultFactory.success()
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "mergeSharing", true)
        }
    }

    protected Result<Void> mergeRecords(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        try {
            RecordItems
                .buildForRecordIdsWithOptions(toMergeRecords*.record*.id)
                .updateAll(record: ipr1.record)
            FutureMessage
                .buildForRecordIds(toMergeRecords*.record*.id)
                .updateAll(record: ipr1.record)
            IOCUtils.resultFactory.success()
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "mergeRecords", true)
        }
    }
}
