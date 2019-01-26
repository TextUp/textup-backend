package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*

// TempRecordReceipts are created at the time a call or text is initially sent out. Only text
// messages have the billable units (segments) known at the time of sending. For calls, we only
// know how long a call has lasted AFTER a call has finished. Therefore, we handle billable units
// for calls in status callback webhooks on the Number tags for the child calls.

@EqualsAndHashCode
@GrailsTypeChecked
@TupleConstructor(includeFields = true, includes = ["contactNumber", "apiId"])
@Validateable
class TempRecordReceipt implements CanValidate {

    final PhoneNumber contactNumber
    final String apiId //used for finding the RecordItem in a StatusCallback

    Integer numBillable
    ReceiptStatus status = ReceiptStatus.PENDING

	static constraints = {
		contactNumber cascadeValidation: true
        numBillable nullable: true, min: 0
	}

    static Result<TempRecordReceipt> tryCreate(String apiId, BasePhoneNumber bNum) {
        TempRecordReceipt tempRpt1 = new TempRecordReceipt(PhoneNumber.create(bNum), apiId)
        DomainUtils.tryValidate(tempRpt1, ResultStatus.CREATED)
    }
}
