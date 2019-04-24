package org.textup.util.domain

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

class RecordItems {

    @GrailsTypeChecked
    static Result<Long> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId()
            .then { Long authId -> AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0) }
            .then { IOCUtils.resultFactory.success(thisId) }
    }

    @GrailsTypeChecked
    static Result<? extends RecordItem> mustFindForId(Long thisId) {
        RecordItem rItem1 = RecordItem.get(thisId)
        if (rItem1) {
            IOCUtils.resultFactory.success(rItem1)
        }
        else {
            IOCUtils.resultFactory.failWithCodeAndStatus("recordItems.notFound",
                ResultStatus.NOT_FOUND, [thisId])
        }
    }

    @GrailsTypeChecked
    static List<RecordItem> findEveryForApiId(String apiId) {
        List<RecordItem> results = []
        if (apiId) {
            HashSet<Long> itemIds = new HashSet<>()
            List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(apiId)
            receipts.each { RecordItemReceipt receipt ->
                if (!itemIds.contains(receipt.item.id)) {
                    results << receipt.item
                    itemIds << receipt.item.id
                }
            }
        }
        results
    }

    static Collection<RecordItem> findAllByNonGroupOwner(Collection<RecordItem> rItems) {
        if (!rItems) {
            return []
        }
        new DetachedCriteria(RecordItem)
            .build { CriteriaUtils.inList(delegate, "id", rItems*.id) }
            .build(RecordItems.forNonGroupOwnerOnly())
            .list()
    }

    static DetachedCriteria<RecordItem> buildForOutgoingScheduledOrIncomingMessagesAfter(DateTime afterTime) {
        new DetachedCriteria(RecordItem)
            .build {
                ne("class", RecordNote)
                or {
                    and { // outgoing scheduled
                        eq("outgoing", true)
                        eq("wasScheduled", true)
                    }
                    ClosureUtils.compose(delegate, RecordItems.forIncoming())
                }
            }
            .build(forDates(afterTime, null))
            .build(RecordItems.forNonGroupOwnerOnly())
    }

    // Subqueries cannot include an `or` clause or else results in an NPE because of an existing bug.
    // see: https://github.com/grails/grails-data-mapping/issues/655
    static DetachedCriteria<RecordItem> buildForPhoneIdWithOptions(Long phoneId,
        DateTime start = null, DateTime end = null,
        Collection<Class<? extends RecordItem>> types = null) {

        Collection<Long> recIds = PhoneRecords.buildActiveForPhoneIds([phoneId])
            .build(PhoneRecords.returnsRecordId())
            .list()
        new DetachedCriteria(RecordItem)
            .build { CriteriaUtils.inList(delegate, "record.id", recIds) }
            .build(forOptionalDates(start, end))
            .build(forTypes(types))
    }

    static DetachedCriteria<RecordItem> buildForRecordIdsWithOptions(Collection<Long> recordIds,
        DateTime start = null, DateTime end = null,
        Collection<Class<? extends RecordItem>> types = null) {

        new DetachedCriteria(RecordItem)
            .build { CriteriaUtils.inList(delegate, "record.id", recordIds) }
            .build(forOptionalDates(start, end))
            .build(forTypes(types))
    }

    static Closure forIncoming() {
        return { eq("outgoing", false) }
    }

    static Closure forNonGroupOwnerOnly() {
        return {
            "in"("record.id", new DetachedCriteria(PhoneRecord)
                .build(PhoneRecords.forNonGroupOnly())
                .build(PhoneRecords.returnsRecordId()))
        }
    }

    // Specify sort order separately because when we call `count()` on a DetachedCriteria
    // we are grouping fields and, according to the SQL spec, we need to specify a GROUP BY
    // if we also have an ORDER BY clause. Therefore, to avoid GROUP BY errors when calling `count()`
    // we don't include the sort order by default and we have to separately add it in
    // before calling `list()`. See https://stackoverflow.com/a/19602031
    static Closure forSort(boolean recentFirst = true) {
        return { // from newer (larger # millis) to older (smaller $ millis)
            if (recentFirst) {
                order("whenCreated", "desc")
            }
            else { order("whenCreated", "asc") }
        }
    }

    // Helpers
    // -------

    protected static Closure forOptionalDates(DateTime start, DateTime end) {
        return start || end ? forDates(start, end) : { }
    }

    protected static Closure forDates(DateTime start, DateTime end) {
        return {
            if (start && end) {
                between("whenCreated", start, end)
            }
            else if (start) {
                ge("whenCreated", start)
            }
            else if (end) {
                le("whenCreated", end)
            }
            else { eq("whenCreated", null) }
        }
    }

    protected static Closure forTypes(Collection<Class<? extends RecordItem>> types) {
        return {
            CriteriaUtils.inList(delegate, "class", types*.canonicalName, true) // optional
        }
    }

    protected static DetachedCriteria<RecordItem> buildForAuth(Long thisId, Long authId) {
        Collection<Long> recIds = PhoneRecords.findEveryAllowedRecordIdForStaffId(authId)
        new DetachedCriteria(RecordItem).build {
            idEq(thisId)
            CriteriaUtils.inList(delegate, "record.id", recIds)
        }
    }
}
