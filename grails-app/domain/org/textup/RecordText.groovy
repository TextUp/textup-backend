package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*

@EqualsAndHashCode(callSuper=true)
@RestApiObject(name="RecordText", description="A text message entry in a contact's record.")
class RecordText extends RecordItem {

    def resultFactory

    @RestApiObjectField(
        description  = "Whether or not this text is scheduled to be sent in the future",
        mandatory    = false,
        defaultValue = "false")
    boolean futureText = false
    @RestApiObjectField(
        description  = "If scheduled for the future, when this should be sent",
        allowedType  = "DateTime",
        mandatory    = false,
        defaultValue = "null")
    DateTime sendAt
    @RestApiObjectField(description = "Contents of the text")
	String contents
    //if text created as a part of a text out from a TeamContactTag
    Long teamContactTagId = null

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
    	contents blank:false, nullable:false, maxSize:320
        sendAt nullable:true
        teamContactTagId nullable:true 
    }
    static mapping = {
        sendAt type:PersistentDateTime
    }
    static namedQueries = {
        forRecordAndParams { Record rec, Map params ->
            eq("record", rec)
            order("dateCreated", "desc")
            if (params.outgoing != null) {
                eq("outgoing", params.outgoing)
            }
            if (params.futureText != null) {
                eq("futureText", params.futureText)
            }
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

    Result<RecordText> cancelScheduled() {
        //TODO: implement me
        this.futureText = false
        resultFactory.success(this)
    }

    /////////////////////
    // Property Access //
    /////////////////////

    void setSendAt(DateTime dt) {
        this.sendAt = dt?.withZone(DateTimeZone.UTC)
        if (this.sendAt?.isAfterNow()) { this.futureText = true }
        else if (this.sendAt?.isBeforeNow()) { this.futureText = false }
    }
}
