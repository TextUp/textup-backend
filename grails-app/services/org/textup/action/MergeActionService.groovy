package org.textup.action

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import grails.transaction.Transactional
import org.springframework.transaction.annotation.Propagation
import org.textup.*
import org.textup.annotation.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*

@GrailsTypeChecked
@Transactional
class MergeActionService implements HandlesActions<Long, Void> {

    @Override
    boolean hasActions(Map body) { !!body.doMergeActions }

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
    @Override
    @RollbackOnResultFailure
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Result<Void> tryHandleActions(Long iprId, Map body) {
        IndividualPhoneRecords.mustFindActiveForId(iprId)
            .then { IndividualPhoneRecord ipr1 ->
                ActionContainer.tryProcess(MergeIndividualAction, body.doMergeActions).curry(ipr1)
            }
            .then { IndividualPhoneRecord ipr1, List<MergeIndividualAction> actions ->
                ResultGroup
                    .collect(actions) { MergeIndividualAction a1 ->
                        Collection<IndividualPhoneRecord> toBeMerged = IndividualPhoneRecords
                            .findEveryByIdsAndPhoneId(a1.toBeMergedIds, ipr1.phone.id)
                        switch (a1) {
                            case MergeIndividualAction.DEFAULT:
                                tryMerge(ipr1, toBeMerged)
                                break
                            default: // MergeIndividualAction.RECONCILE
                                // For some reason, directly calling `.then` here to set
                                // name and note exposes an error in Groovy's generics inference
                                // implementation, resulting in  NPE at
                                // org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.applyGenericsContext(StaticTypeCheckingSupport.java:1738)
                                tryMergeWithInfo(ipr1, toBeMerged, a1)
                        }
                    }
                    .toEmptyResult(false)
            }
    }

    // Helpers
    // -------

    protected Result<IndividualPhoneRecord> tryMergeWithInfo(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged, MergeIndividualAction a1) {

        tryMerge(ipr1, toBeMerged).then { IndividualPhoneRecord ipr2 ->
            ipr2.with {
                name = a1.buildName()
                note = a1.buildNote()
            }
            DomainUtils.trySave(ipr2)
        }
    }

    protected Result<IndividualPhoneRecord> tryMerge(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        tryMergeFields(ipr1, toBeMerged)
            .then { tryMergeNumbers(ipr1, toBeMerged) }
            .then { tryMergeGroups(ipr1, toBeMerged) }
            .then { tryMergeSharing(ipr1, toBeMerged) }
            .then { tryMergeRecords(ipr1, toBeMerged) }
            .then { DomainUtils.trySave(ipr1) }
    }

    protected Result<Void> tryMergeFields(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        ipr1.status = PhoneRecordStatus.reconcile([ipr1.status] + toBeMerged*.status)
        toBeMerged.each { it.isDeleted = true }
        DomainUtils.trySaveAll(CollectionUtils.mergeUnique([[ipr1], toBeMerged]))
    }

    protected Result<Void> tryMergeNumbers(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        ResultGroup
            .collect(CollectionUtils.mergeUnique(toBeMerged*.numbers)) { ContactNumber cNum ->
                ipr1.mergeNumber(cNum, cNum.preference)
            }
            .toEmptyResult(false)
    }

    protected Result<Void> tryMergeGroups(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        try {
            List<GroupPhoneRecord> gprs = GroupPhoneRecords
                .buildForMemberIdsAndOptions(toBeMerged*.id)
                .list()
            gprs.each { GroupPhoneRecord gpr1 -> gpr1.members.addToPhoneRecords(ipr1) }
            DomainUtils.trySaveAll(gprs)
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "mergeTags", true)
        }
    }

    protected Result<Void> tryMergeSharing(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        try {
            CriteriaUtils.updateAll(PhoneRecord, [record: ipr1.record, shareSource: ipr1]) {
                new DetachedCriteria(PhoneRecord)
                    .build(PhoneRecords.forShareSourceIds(toBeMerged*.id))
                    .build(CriteriaUtils.returnsId())
                    .list() as Collection<Long>
            }
            Result.void()
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "tryMergeSharing", true)
        }
    }

    protected Result<Void> tryMergeRecords(IndividualPhoneRecord ipr1,
        Collection<IndividualPhoneRecord> toBeMerged) {

        try {
            CriteriaUtils.updateAll(RecordItem, [record: ipr1.record]) {
                RecordItems
                    .buildForRecordIdsWithOptions(toBeMerged*.record*.id)
                    .build(CriteriaUtils.returnsId())
                    .list() as Collection<Long>
            }
            CriteriaUtils.updateAll(FutureMessage, [record: ipr1.record]) {
                FutureMessages
                    .buildForRecordIds(toBeMerged*.record*.id)
                    .build(CriteriaUtils.returnsId())
                    .list() as Collection<Long>
            }
            Result.void()
        }
        catch (Throwable e) {
            IOCUtils.resultFactory.failWithThrowable(e, "tryMergeRecords", true)
        }
    }
}
