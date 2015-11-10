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
	int durationInSeconds = 0
    @RestApiObjectField(
        description    = "Whether or not this call has a voicemail",
        useForCreation = false)
    boolean hasVoicemail = false
    @RestApiObjectField(
        description    = "Duration of the voicemail",
        allowedType    = "Number",
        useForCreation = false)
    int voicemailInSeconds = 0

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
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "voicemailUrl",
            description       = "Url to access the voicemail at",
            allowedType       = "String",
            mandatory         = false,
            useForCreation    = false,
            presentInResponse = true)
    ])
    static transients = []
    static constraints = {
        durationInSeconds minSize:0
        voicemailInSeconds minSize:0
    }
    static namedQueries = {
        forRecord { Record rec ->
            eq("record", rec)
            order("dateCreated", "desc")
        }
        forRecordAndApiId { Record rec, String thisApiId ->
            eq("record", rec)
            receipts { eq("apiId", thisApiId) }
            order("dateCreated", "desc")
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

}
