package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.type.ReceiptStatus
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber

@GrailsTypeChecked
@EqualsAndHashCode
class RecordItemReceipt implements WithId, Saveable<RecordItemReceipt> {

    String apiId
    String contactNumberAsString
    Integer numBillable // text messages = segments, calls = duration in seconds
	ReceiptStatus status

    static transients = ["contactNumber"]
    static belongsTo = [item: RecordItem]
    static mapping = {
        // an index because we often look up via apiId and lots of rows in this table
        // makes this a very slow query (from sql performance monitoring)
        apiId index: "ix_record_item_receipt_api_id"
    }
    static constraints = {
        contactNumberAsString validator:{ String val, RecordItemReceipt obj ->
            if (!ValidationUtils.isValidPhoneNumber(val)) { ["format"] }
        }
        numBillable nullable: true, min: 0
    }

    static Result<RecordItemReceipt> tryCreate(RecordItem rItem1, String apiId, ReceiptStatus status,
        BasePhoneNumber bNum) {

        RecordItemReceipt rpt1 = new RecordItemReceipt(apiId: apiId, status: status)
        rpt1.contactNumber = bNum
        rItem1.addToReceipts(rpt1)
        DomainUtils.trySave(rpt1, ResultStatus.CREATED)
    }

    // Property Access
    // ---------------

    void setContactNumber(BasePhoneNumber bNum) {
        this.contactNumberAsString = bNum?.number
    }
    PhoneNumber getContactNumber() {
        new PhoneNumber(number: this.contactNumberAsString)
    }
}
