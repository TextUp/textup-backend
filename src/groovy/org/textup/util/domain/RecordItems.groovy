package org.textup.util.domain

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class RecordItems {

    // TODO hasPermissionsForItem
    static Result<Void> isAllowed(Long thisId) {
        AuthUtils.tryGetAuthId().then { Long authId ->
            AuthUtils.isAllowed(buildForAuth(thisId, authId).count() > 0)
        }
    }

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

    static DetachedCriteria<RecordItem> buildIncomingMessagesAfter(DateTime afterTime) {
        new DetachedCriteria(RecordItem)
            .build { ne("class", RecordNote.class) }
            .build(forDates(afterTime, null))
            .build(Recorditems.forIncoming())
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<RecordItem> buildForPhoneIdWithOptions(Long phoneId,
        DateTime start = null, DateTime end = null,
        Collection<Class<? extends RecordItem>> types = null) {

        new DetachedCriteria(RecordItem)
            .build {
                "in"("record", PhoneRecords
                    .buildActiveForPhoneIds([phoneId])
                    .build(PhoneRecords.returnsRecord()))
            }
            .build(forDates(start, end))
            .build(forTypes(types))
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<RecordItem> buildForRecordIdsWithOptions(Collection<Long> recordIds,
        DateTime start = null, DateTime end = null,
        Collection<Class<? extends RecordItem>> types = null) {

        new DetachedCriteria(RecordItem)
            .build { CriteriaUtils.inList(delegate, "record.id", recordIds) }
            .build(forDates(start, end))
            .build(forTypes(types))
    }

    static Closure forIncoming() {
        return { eq("outgoing", false) }
    }

    // Specify sort order separately because when we call `count()` on a DetachedCriteria
    // we are grouping fields and, according to the SQL spec, we need to specify a GROUP BY
    // if we also have an ORDER BY clause. Therefore, to avoid GROUP BY errors when calling `count()`
    // we don't include the sort order by default and we have to separately add it in
    // before calling `list()`. See https://stackoverflow.com/a/19602031
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
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

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure forDates(DateTime start, DateTime end) {
        return {
            if (start && end) {
                between("whenCreated", s, end)
            }
            else if (start) {
                ge("whenCreated", s)
            }
        }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure forTypes(Collection<Class<? extends RecordItem>> types) {
        return { CriteriaUtils.inList(delegate, "class", types*.canonicalName) }
    }

    protected static DetachedCriteria<RecordItem> buildForAuth(Long thisId, Long authId) {
        new DetachedCriteria(RecordItem).build {
            idEq(thisId)
            "in"("record", PhoneRecords.buildActiveForStaffId(authId)
                .build(PhoneRecords.returnsRecord())
        }
    }
}
