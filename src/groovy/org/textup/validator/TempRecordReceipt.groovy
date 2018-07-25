package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.type.ReceiptStatus

// Receipt validator object for outgoing receipts
// Received by is the client number that is the recipient of the outgoing message

@GrailsCompileStatic
@EqualsAndHashCode
@Validateable
class TempRecordReceipt {

	//unique id assigned to this record by the communications provider
    //used for finding the RecordItem in a StatusCallback
    String apiId
    String contactNumberAsString
	ReceiptStatus status = ReceiptStatus.PENDING

	static constraints = {
        apiId blank:false, nullable:false
        status blank:false, nullable:false
		contactNumberAsString nullable:false, validator:{ String val, TempRecordReceipt obj ->
            if (!(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
        }
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
