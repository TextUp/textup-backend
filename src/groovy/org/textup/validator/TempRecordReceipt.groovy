package org.textup.validator

import grails.compiler.GrailsCompileStatic
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.textup.*
import org.textup.types.ReceiptStatus

@GrailsCompileStatic
@EqualsAndHashCode
@Validateable
class TempRecordReceipt {

	//unique id assigned to this record by the communications provider
    //used for finding the RecordItem in a StatusCallback
    String apiId
    String receivedByAsString
	ReceiptStatus status = ReceiptStatus.PENDING

	static constraints = {
        apiId blank:false, nullable:false
        status blank:false, nullable:false
		receivedByAsString nullable:false, validator:{ String val, TempRecordReceipt obj ->
            if (!(val?.toString() ==~ /^(\d){10}$/)) { ["format"] }
        }
	}

	// Property Access
    // ---------------

    void setReceivedBy(BasePhoneNumber pNum) {
        this.receivedByAsString = pNum?.number
    }
    PhoneNumber getReceivedBy() {
        new PhoneNumber(number:this.receivedByAsString)
    }
}
