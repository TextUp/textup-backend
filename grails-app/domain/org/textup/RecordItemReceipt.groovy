package org.textup

import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*

@EqualsAndHashCode
@RestApiObject(name="Receipt", description="A receipt indicating the status of a communication sent to a phone number.")
class RecordItemReceipt {

    @RestApiObjectField(
        description="Status of the communication. Allowed: failed, pending, success", 
        useForCreation=false, 
        defaultValue="pending")
	String status = Constants.RECEIPT_PENDING
	//unique id assigned to this record by the communications provider
    //used for finding the RecordItem in a StatusCallback
	String apiId
	//if outgoing, then record the number that received the communication
    @RestApiObjectField(
        description="Phone number that this communication was sent to", 
        useForCreation=false, 
        allowedType="String")
	PhoneNumber receivedBy 

    static constraints = {
    	status inList:[Constants.RECEIPT_FAILED, Constants.RECEIPT_PENDING, Constants.RECEIPT_SUCCESS]
    }
    static embedded = ["receivedBy"]
    static belongsTo = [item:RecordItem]
    static namedQueries = {
    	failed { recordItem ->
    		eq("item", recordItem)
    		eq("status", Constants.RECEIPT_FAILED)
    	}
    	pending { recordItem -> 
    		eq("item", recordItem)
    		eq("status", Constants.RECEIPT_PENDING)
    	}
    	success { recordItem ->
    		eq("item", recordItem)
    		eq("status", Constants.RECEIPT_SUCCESS)
    	}
    }

    /*
	Has many:
	*/
    
    ////////////////////
    // Helper methods //
    ////////////////////

    /////////////////////
    // Property Access //
    /////////////////////

    void setReceivedByAsString(String num) {
    	this.receivedBy = new PhoneNumber(number:num)
        this.receivedBy.save()
    }
}
