package org.textup

import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*

@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordText", description="A text message entry in a contact's record.")
class RecordText extends RecordItem {

    def resultFactory

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
    	contents shared: 'textMessage'
    }
}
