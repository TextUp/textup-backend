package org.textup

import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordText", description="A text message entry in a contact's record.")
class RecordText extends RecordItem implements ReadOnlyRecordText {

    @RestApiObjectField(description = "Contents of the text")
	String contents

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "sendToPhoneNumbers",
            description    = "List of phone numbers to send this text to",
            allowedType    = "List<String>",
            defaultValue   = "[ ]",
            mandatory      = false,
            useForCreation = true,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName   = "sendToContacts",
            description    = "List of contact ids to send this text to",
            allowedType    = "List<Number>",
            defaultValue   = "[ ]",
            mandatory      = false,
            useForCreation = true,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName   = "sendToSharedContacts",
            description    = "List of contact ids that are shared with this phone \
                to send this text to",
            allowedType    = "List<Number>",
            defaultValue   = "[ ]",
            mandatory      = false,
            useForCreation = true,
            presentInResponse = false),
        @RestApiObjectField(
            apiFieldName   = "sendToTags",
            description    = "List of tag ids to send this text to",
            allowedType    = "List<Number>",
            defaultValue   = "[ ]",
            mandatory      = false,
            useForCreation = true,
            presentInResponse = false)
    ])
    static transients = []
    static constraints = {
        // (1) removed the length constraint on contents (maxSize:(Constants.TEXT_LENGTH * 2))
        // presence of this constraint will reject incoming messages that are too long
        // this should not happen since we have no control over the contents of incoming
        // messages and we do not want to knowingly fail to deliver incoming messages
        // (2) removed the constraint the prohibited contents from being null because a text message
        // can now have media too so outgoing message can have either text only, media only, or both.
        // [SHARED maxSize] 65535 bytes max for `text` column divided by 4 bytes per character ut8mb4
    	contents blank: true, nullable: true, maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE
    }
    static mapping = {
        contents type:"text"
    }

    // Properties
    // ----------

    int getNumSegments() {
        int totalNumSegments = 0
        this.receipts?.each { RecordItemReceipt rpt1 ->
            if (rpt1.numBillable) {
                totalNumSegments += rpt1.numBillable
            }
        }
        totalNumSegments
    }
}
