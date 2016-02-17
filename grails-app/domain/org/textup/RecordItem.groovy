package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.types.AuthorType
import org.textup.types.ReceiptStatus
import org.textup.validator.Author
import org.textup.validator.TempRecordReceipt

@EqualsAndHashCode
class RecordItem {

    @RestApiObjectField(
        description    = "Date this item was added to the record",
        allowedType    = "DateTime",
        useForCreation = false)
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    Record record //record this item belongs to

    @RestApiObjectField(
        description    = "The direction of communication. Outgoing is from staff to client.",
        allowedType    = "Boolean",
        useForCreation = false)
	boolean outgoing = true //true is CM->client, false is CM<-client

    @RestApiObjectField(
        description    = "Author of this entry.",
        useForCreation = false)
	String authorName

    @RestApiObjectField(
        description    = "Id of the author of this entry.",
        allowedType    = "Number",
        useForCreation = false)
	Long authorId

    @RestApiObjectField(
        description    = "Type of author for this item",
        allowedType    = "AuthorType",
        useForCreation = false)
    AuthorType authorType

    @RestApiObjectField(
        description    = "If we auto-responded with the away message",
        allowedType    = "Boolean",
        useForCreation = false)
    boolean hasAwayMessage = false

    @RestApiObjectField(
        description    = "If this was part of an announcement",
        allowedType    = "Boolean",
        useForCreation = false)
    boolean isAnnouncement = false

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName= "type",
            description = "Type of record item. One of: TEXT, or CALL",
            allowedType =  "String",
            useForCreation = false)
    ])
	static transients = ["author"]
    @RestApiObjectField(
        apiFieldName   = "receipts",
        description    = "Statuses of all phone numbers who were sent this response",
        allowedType    = "List<Receipt>",
        useForCreation = false)
    static hasMany = [receipts:RecordItemReceipt]
    static constraints = {
    	authorName blank:true, nullable:true
    	authorId nullable:true
        authorType nullable:true
    }
    static mapping = {
        receipts lazy:false, cascade:"all-delete-orphan"
        whenCreated type:PersistentDateTime
    }
    static namedQueries = {
        forRecord { Record rec ->
            eq("record", rec)
            order("whenCreated", "desc")
        }
        forRecordDateSince { Record rec, DateTime s ->
            eq("record", rec)
            ge("whenCreated", s)
            order("whenCreated", "desc")
        }
        forRecordDateBetween { Record rec, DateTime s, DateTime e ->
            eq("record", rec)
            between("whenCreated", s, e)
            order("whenCreated", "desc")
        }
    }

    @GrailsTypeChecked
    RecordItem addReceipt(TempRecordReceipt r1) {
        RecordItemReceipt receipt = new RecordItemReceipt(status:r1.status,
            apiId:r1.apiId, receivedByAsString:r1.receivedByAsString)
        addToReceipts(receipt)
        this
    }

    // Property Access
    // ---------------

    @GrailsTypeChecked
    List<RecordItemReceipt> getReceiptsByStatus(ReceiptStatus stat) {
        RecordItemReceipt.findAllByItemAndStatus(this, stat)
    }
    @GrailsTypeChecked
    void setAuthor(Author author) {
        if (author) {
            this.with {
                authorName = author?.name
                authorId = author?.id
                authorType = author?.type
            }
        }
    }
    @GrailsTypeChecked
    Author getAuthor() {
        new Author(name:this.authorName, id:this.authorId, type:this.authorType)
    }
}
