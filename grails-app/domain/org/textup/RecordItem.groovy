package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*

@EqualsAndHashCode
class RecordItem {

    Record record //record this item belongs to

    @RestApiObjectField(
        description    = "Date this item was added to the record",
        allowedType    = "DateTime",
        useForCreation = false)
	DateTime dateCreated = DateTime.now(DateTimeZone.UTC)

    @RestApiObjectField(
        description    = "The direction of communication. Outgoing is from staff to client.",
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
        autoTimestamp false
        dateCreated type:PersistentDateTime
        receipts lazy:false, cascade:"all-delete-orphan"
    }
    static namedQueries = {
        forRecord { Record rec ->
            eq("record", rec)
            order("dateCreated", "desc")
        }
        forRecordDateSince { Record rec, DateTime s ->
            eq("record", rec)
            ge("dateCreated", s)
            order("dateCreated", "desc")
        }
        forRecordDateBetween { Record rec, DateTime s, DateTime e ->
            eq("record", rec)
            between("dateCreated", s, e)
            order("dateCreated", "desc")
        }

    }

    // Property Access
    // ---------------

    int countReceipts(ReceiptStatus stat) {
        RecordItemReceipt.forItemAndStatus(this, stat).count()
    }
    List<RecordItemReceipt> getReceipts(ReceiptStatus stat) {
        RecordItemReceipt.forItemAndStatus(this, stat).list()
    }
    void setAuthor(Author author) {
        if (author) {
            this.with {
                authorName = author?.name
                authorId = author?.id
                authorType = author?.type
            }
        }
    }
    Author getAuthor() {
        new Author(name:this.authorName, id:this.authorId, type:this.authorType)
    }
}
