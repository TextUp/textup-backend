package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@EqualsAndHashCode
@GrailsTypeChecked
class RecordItemReceipt implements WithId, CanSave<RecordItemReceipt> {

    // Need to declare id for it to be considered in equality operator
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    String apiId
    String contactNumberAsString
    Integer numBillable // text messages = segments, calls = duration in seconds
	ReceiptStatus status

    static transients = ["contactNumber"]
    static belongsTo = [item: RecordItem]
    static mapping = {
        item fetch: "join"
        // an index because we often look up via apiId and lots of rows in this table
        // makes this a very slow query (from sql performance monitoring)
        apiId index: "ix_record_item_receipt_api_id"
        // turn off optimistic locking because expect high concurrent writes
        version false
    }
    static constraints = {
        contactNumberAsString phoneNumber: true
        numBillable nullable: true, min: 0
    }

    static Result<RecordItemReceipt> tryCreate(RecordItem rItem1, String aId, ReceiptStatus stat,
        BasePhoneNumber bNum) {

        RecordItemReceipt rpt1 = RecordItemReceipt.create(rItem1, aId, stat, bNum)
        DomainUtils.trySave(rpt1, ResultStatus.CREATED)
    }

    static RecordItemReceipt create(RecordItem rItem1, String aId, ReceiptStatus stat,
        BasePhoneNumber bNum) {

        RecordItemReceipt rpt1 = new RecordItemReceipt(apiId: aId, status: stat, contactNumber: bNum)
        rItem1?.addToReceipts(rpt1)
        rpt1
    }

    // Methods
    // -------

    RecordItemReceiptCacheInfo toInfo() {
        RecordItemReceiptCacheInfo.create(id, item, status, numBillable)
    }

    // Properties
    // ----------

    void setContactNumber(BasePhoneNumber bNum) {
        contactNumberAsString = bNum?.number
    }

    PhoneNumber getContactNumber() {
        PhoneNumber.create(contactNumberAsString)
    }
}
