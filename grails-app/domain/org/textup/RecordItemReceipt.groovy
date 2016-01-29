package org.textup

import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import org.textup.ReceiptStatus

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

    //if outgoing, then record the number that received the communication
    @RestApiObjectField(
        apiFieldName = 'receivedBy'
        description="Phone number that this communication was sent to",
        useForCreation=false,
        allowedType="String")
    static transients = ['receivedBy']
    static constraints = {
        receivedByAsString shared: 'phoneNumber'
    }
    static belongsTo = [item:RecordItem]
    static namedQueries = {
        forItemAndStatus { RecordItem rItem, ReceiptStatus stat ->
            eq("item", rItem)
            eq("status", stat)
        }
    }

    // Helper methods
    // --------------

    static RecordItemReceipt copy() {
        new RecordItemReceipt(status:this.status, apiId:this.apiId,
            receivedByAsString:this.receivedByAsString)
    }

    // Property Access
    // ---------------

    void setReceivedBy(PhoneNumber pNum) {
        this.receivedByAsString = pNum?.number
    }
    PhoneNumber getReceivedBy() {
        new PhoneNumber(number:this.receivedByAsString)
    }
}
