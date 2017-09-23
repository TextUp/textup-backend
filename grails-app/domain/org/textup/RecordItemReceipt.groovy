package org.textup

import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import org.textup.ReceiptStatus
import org.textup.type.ReceiptStatus
import org.textup.validator.BasePhoneNumber
import org.textup.validator.PhoneNumber
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@EqualsAndHashCode
@RestApiObject(name="Receipt", description="A receipt indicating the status \
    of a communication sent to a phone number.")
class RecordItemReceipt {

    //unique id assigned to this record by the communications provider
    //used for finding the RecordItem in a StatusCallback
    String apiId
    String receivedByAsString

    @RestApiObjectField(
        description="Status of communication. Allowed: FAILED, PENDING, BUSY, SUCCESS",
        useForCreation=false,
        defaultValue="PENDING")
	ReceiptStatus status = ReceiptStatus.PENDING

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName = 'receivedBy',
            description="Phone number that this communication was sent to",
            useForCreation=false,
            allowedType="String")
    ])
    static transients = ['receivedBy']
    static belongsTo = [item:RecordItem]
    static constraints = {
        receivedByAsString validator:{ String val, RecordItemReceipt obj ->
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
