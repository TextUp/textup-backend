package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.textup.type.ReceiptStatus
import org.textup.validator.TempRecordReceipt

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordCall", description="A phone call entry in a contact's record.")
class RecordCall extends RecordItem implements ReadOnlyRecordCall {

    VoicemailService voicemailService

    @RestApiObjectField(
        description    = "Duration of the call",
        allowedType    = "Number",
        useForCreation = false)
	int durationInSeconds = 0

    @RestApiObjectField(
        description    = "Duration of the voicemail",
        allowedType    = "Number",
        useForCreation = false)
    int voicemailInSeconds = 0

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName      = "callContact",
            description       = "Id of a contact to call",
            allowedType       = "Number",
            mandatory         = false,
            useForCreation    = true,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName      = "callSharedContact",
            description       = "Id of a contact shared with us to call",
            allowedType       = "Number",
            mandatory         = false,
            useForCreation    = true,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName   = "hasVoicemail",
            description    = "Whether or not this call has a voicemail",
            allowedType    = "Boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName      = "voicemailUrl",
            description       = "Url to access the voicemail at",
            allowedType       = "String",
            mandatory         = false,
            useForCreation    = false,
            presentInResponse = true)
    ])
    static transients = ["voicemailService"]
    static constraints = {
        durationInSeconds minSize:0
        voicemailInSeconds minSize:0
    }

    // Property Access
    // ---------------

    boolean getHasVoicemail() {
        this.hasAwayMessage && this.voicemailInSeconds > 0
    }
    String getVoicemailUrl() {
        if (!this.hasVoicemail) {
            return ""
        }
        voicemailService.getVoicemailUrl(getReceiptsByStatus(ReceiptStatus.SUCCESS)[0])
    }
}
