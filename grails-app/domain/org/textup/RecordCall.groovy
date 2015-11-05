package org.textup

import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*

@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordCall", description="A phone call entry in a contact's record.")
class RecordCall extends RecordItem {

    @RestApiObjectField(
        description    = "Duration of the call", 
        allowedType    = "Number",
        useForCreation = false)
	int durationInSeconds
    @RestApiObjectField(
        description    = "Link to where the voicemail recording is stored, if needed.", 
        useForCreation = false)
	String voicemailUrl

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName      = "callPhoneNumber",
            description       = "A phone number to call",
            allowedType       = "String",
            mandatory         = false,
            useForCreation    = true,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "callContact",
            description       = "Id of a contact to call",
            allowedType       = "Number",
            mandatory         = false,
            useForCreation    = true,
            presentInResponse = false)
    ])
    static transients = []
    static constraints = {
    	voicemailUrl blank:true, nullable:true
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
    
}
