package org.textup

import org.textup.type.ReceiptStatus

class RecordItemStatus {
    private final List<RecordItemReceipt> success = []
    private final List<RecordItemReceipt> pending = []
    private final List<RecordItemReceipt> busy = []
    private final List<RecordItemReceipt> failed = []

    RecordItemStatus() {}
    RecordItemStatus(Collection<RecordItemReceipt> rpts) {
        rpts?.each(add)
    }

    void add(RecordItemReceipt rpt1) {
        switch (rpt1.status) {
            case ReceiptStatus.SUCCESS: success << rpt1; break;
            case ReceiptStatus.PENDING: pending << rpt1; break;
            case ReceiptStatus.BUSY: busy << rpt1; break;
            default: // ReceiptStatus.FAILED
                failed << rpt1
        }
    }

    Set<String> getSuccess() { transformForDisplay(success) }
    Set<String> getPending() { transformForDisplay(pending) }
    Set<String> getBusy() { transformForDisplay(busy) }
    Set<String> getFailed() { transformForDisplay(failed) }

    protected Set<String> transformForDisplay(List<RecordItemReceipt> rpts) {
        new HashSet<String>(rpts.collect { RecordItemReceipt rpt1 -> rpt1.contactNumber?.e164PhoneNumber })
    }
}
