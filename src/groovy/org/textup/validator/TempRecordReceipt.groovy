package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.type.ReceiptStatus

// Receipt validator object for outgoing receipts
// Received by is the client number that is the recipient of the outgoing message

@GrailsTypeChecked
@EqualsAndHashCode
@Validateable
class TempRecordReceipt {

	//unique id assigned to this record by the communications provider
    //used for finding the RecordItem in a StatusCallback
    String apiId
    String contactNumberAsString
	ReceiptStatus status = ReceiptStatus.PENDING
    // TempRecordReceipts are created at the time a call or text is initially sent out. Only text
    // messages have the billable units (segments) known at the time of sending. For calls, we only
    // know how long a call has lasted AFTER a call has finished. Therefore, we handle billable units
    // for calls in status callback webhooks on the Number tags for the child calls.
    Integer numSegments

	static constraints = {
        apiId blank:false, nullable:false
        status blank:false, nullable:false
		contactNumberAsString nullable:false, validator:{ String val, TempRecordReceipt obj ->
            if (!ValidationUtils.isValidPhoneNumber(val)) { ["format"] }
        }
        numSegments nullable: true, min: 0
	}

	// Property Access
    // ---------------

    void setContactNumber(BasePhoneNumber pNum) {
        this.contactNumberAsString = pNum?.number
    }
    PhoneNumber getContactNumber() {
        new PhoneNumber(number:this.contactNumberAsString)
    }
}
