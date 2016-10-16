package org.textup

import groovy.transform.EqualsAndHashCode
import org.restapidoc.annotation.*
import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordText", description="A text message entry in a contact's record.")
class RecordText extends RecordItem {

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
    // removed the constraint maxSize:(Constants.TEXT_LENGTH * 2)
    // because will reject incoming texts longer than this limit,
    // resulting in failure to deliver incoming messages whose
    // content we have no control over
    static constraints = {
    	contents blank:false, nullable:false
    }
}
