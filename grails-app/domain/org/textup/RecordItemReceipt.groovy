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
class RecordItemReceipt {

    //unique id assigned to this record by the communications provider
    //used for finding the RecordItem in a StatusCallback
    String apiId
    String contactNumberAsString
    Integer numSegments // only for text message receipts

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
        numSegments nullable: true, min: 0
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
