package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import org.textup.type.ReceiptStatus
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber

@GrailsTypeChecked
@EqualsAndHashCode
@RestApiObject(name="Receipt", description="A receipt indicating the status \
    of a communication sent to a phone number.")
class RecordItemReceipt implements WithId, Saveable {

    //unique id assigned to this record by the communications provider
    //used for finding the RecordItem in a StatusCallback
    String apiId
    String contactNumberAsString
    Integer numBillable // text messages = segments, calls = duration in seconds

    @RestApiObjectField(
        description="Status of communication. Allowed: FAILED, PENDING, BUSY, SUCCESS",
        useForCreation=false,
        defaultValue="PENDING")
	ReceiptStatus status = ReceiptStatus.PENDING

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = "contactNumber",
            description="Contact number that is responsible for this receipt, whether incoming or outgoing",
            useForCreation=false,
            allowedType="String")
    ])
    static transients = ["contactNumber"]
    static belongsTo = [item: RecordItem]
    static constraints = {
        contactNumberAsString validator:{ String val, RecordItemReceipt obj ->
            if (!(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
        }
        numBillable nullable: true, min: 0
    }
    static mapping = {
        // an index because we often look up via apiId and lots of rows in this table
        // makes this a very slow query (from sql performance monitoring)
        apiId index: "ix_record_item_receipt_api_id"
    }

    // Property Access
    // ---------------

    void setContactNumber(BasePhoneNumber pNum) {
        this.contactNumberAsString = pNum?.number
    }
    PhoneNumber getContactNumber() {
        new PhoneNumber(number: this.contactNumberAsString)
    }
}
