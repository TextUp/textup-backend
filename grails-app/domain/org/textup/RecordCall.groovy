package org.textup

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.restapidoc.annotation.*
import org.textup.types.ReceiptStatus
import groovy.transform.TypeCheckingMode
import org.textup.validator.TempRecordReceipt

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordCall", description="A phone call entry in a contact's record.")
class RecordCall extends RecordItem {

    GrailsApplication grailsApplication
    AmazonS3Client s3Service

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
    static transients = ["hasVoicemail", "grailsApplication", "s3Service"]
    static constraints = {
        durationInSeconds minSize:0
        voicemailInSeconds minSize:0
    }

    // Property Access
    // ---------------

    void setHasVoicemail(boolean hasV) {
        this.hasAwayMessage = hasV
    }
    boolean getHasVoicemail() {
        this.hasAwayMessage
    }
    String getVoicemailUrl() {
        if (!this.hasVoicemail) {
            return ""
        }
        RecordItemReceipt receipt = getReceiptsByStatus(ReceiptStatus.SUCCESS)[0]
        if (!receipt) {
            return ""
        }
        try {
            Date expires = DateTime.now().plusHours(1).toDate()
            String bucket = grailsApplication.flatConfig["textup.voicemailBucketName"],
                objectKey = receipt.apiId
            GeneratePresignedUrlRequest req =
                new GeneratePresignedUrlRequest(bucket, objectKey)
            req.with {
                method = HttpMethod.GET
                expiration = expires
            }
            s3Service.generatePresignedUrl(req)?.toString()
        }
        catch (e) {
            log.error("RecordCall.getVoicemailUrl: ${e.message}")
            return ""
        }
    }
}
