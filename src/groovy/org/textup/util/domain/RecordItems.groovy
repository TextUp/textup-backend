package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class RecordItems {

    static List<RecordItem> findEveryForApiId(String apiId) {
        List<RecordItem> results = []
        HashSet<Long> itemIds = new HashSet<>()
        List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(apiId)
        receipts.each { RecordItemReceipt receipt ->
            if (!itemIds.contains(receipt.item.id)) {
                results << receipt.item
                itemIds << receipt.item.id
            }
        }
        results
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<RecordItem> forPhoneIdWithOptions(Long phoneId,
        DateTime start = null, DateTime end = null,
        Collection<Class<? extends RecordItem>> types = null) {

        new DetachedCriteria(RecordItem)
            .build {
                "in"("record.id", PhoneRecords
                    .forPhoneIds([phoneId])
                    .build(PhoneRecords.buildForRecordId()))
            }
            .build(RecordItem.buildForOptionalDates(start, end))
            .build(RecordItem.buildForOptionalTypes(types))
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<RecordItem> forRecordIdsWithOptions(Collection<Long> recordIds,
        DateTime start = null, DateTime end = null,
        Collection<Class<? extends RecordItem>> types = null) {

        new DetachedCriteria(RecordItem)
            .build { CriteriaUtils.inList(delegate, "record.id", recordIds) }
            .build(RecordItem.buildForOptionalDates(start, end))
            .build(RecordItem.buildForOptionalTypes(types))
    }

    // Specify sort order separately because when we call `count()` on a DetachedCriteria
    // we are grouping fields and, according to the SQL spec, we need to specify a GROUP BY
    // if we also have an ORDER BY clause. Therefore, to avoid GROUP BY errors when calling `count()`
    // we don't include the sort order by default and we have to separately add it in
    // before calling `list()`. See https://stackoverflow.com/a/19602031
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static Closure buildForSort(boolean recentFirst = true) {
        return { // from newer (larger # millis) to older (smaller $ millis)
            if (recentFirst) {
                order("whenCreated", "desc")
            }
            else { order("whenCreated", "asc") }
        }
    }

    // Helpers
    // -------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure buildForOptionalDates(DateTime s = null, DateTime e = null) {
        return {
            if (s && e) {
                between("whenCreated", s, e)
            }
            else if (s) {
                ge("whenCreated", s)
            }
        }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure buildForOptionalTypes(Collection<Class<? extends RecordItem>> types = null) {
        return { CriteriaUtils.inList(delegate, "class", types*.canonicalName) }
    }
}
