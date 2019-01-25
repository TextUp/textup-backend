package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

// Receipt validator object for outgoing receipts
// Received by is the client number that is the recipient of the outgoing message

@GrailsTypeChecked
@EqualsAndHashCode
@Validateable
class TempRecordReceipt implements CanValidate {

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
		contactNumberAsString phoneNumber: true
        numSegments nullable: true, min: 0
	}

    static Result<TempRecordReceipt> tryCreate(String apiId, BasePhoneNumber bNum) {
        TempRecordReceipt tRpt1 = new TempRecordReceipt(apiId: apiId, contactNumber: bNum)
        DomainUtils.tryValidate(tRpt1, ResultStatus.CREATED)
    }

	// Property Access
    // ---------------

    void setContactNumber(BasePhoneNumber pNum) { contactNumberAsString = pNum?.number }

    PhoneNumber getContactNumber() { PhoneNumber.create(contactNumberAsString) }
}
