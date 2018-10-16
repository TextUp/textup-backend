package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.type.AuthorType
import org.textup.type.ReceiptStatus
import org.textup.validator.Author
import org.textup.validator.TempRecordReceipt

@EqualsAndHashCode
class RecordItem implements ReadOnlyRecordItem {

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

    // Outgoing texts: # staff members who received notifications for this message
    // Outgoing future message: # staff who received "notify-me" notifications
    Integer numNotified = 0

    @RestApiObjectField(
        description    = "If this was part of an announcement",
        allowedType    = "Boolean",
        useForCreation = false)
    boolean isAnnouncement = false

    @RestApiObjectField(
        description    = "Internal notes added by staff for this record item",
        allowedType    = "String",
        useForCreation = true)
    String noteContents

    @RestApiObjectField(
        apiFieldName   = "media",
        description    = "Media associated with this item in the record",
        allowedType    = "MediaInfo",
        useForCreation = false)
    MediaInfo media

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName   = "type",
            description    = "Type of record item. One of: TEXT, or CALL",
            allowedType    =  "String",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName      = "doMediaActions",
            description       = "List of actions to perform related to media assets",
            allowedType       = "List<[mediaAction]>",
            useForCreation    = false,
            presentInResponse = false)
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
        media nullable:true, cascadeValidation: true // can be null for backwards compatibility for RecordItems that predate this
        noteContents blank:true, nullable:true, maxSize: Constants.MAX_TEXT_COLUMN_SIZE
        numNotified min: 0
    }
    static mapping = {
        receipts lazy: false, cascade: "all-delete-orphan"
        media lazy: false, cascade: "save-update"
        whenCreated type: PersistentDateTime
        noteContents type: "text"
    }
    static namedQueries = {
        forRecord { Record rec, Collection<Class<? extends RecordItem>> types = [] ->
            eq("record", rec)
            if (types) {
                "in"("class", types*.canonicalName)
            }
            // from newer to older so we return more recent messages first
            order("whenCreated", "desc")
        }
        forRecordDateSince { Record rec, DateTime s, Collection<Class<? extends RecordItem>> types = [] ->
            eq("record", rec)
            ge("whenCreated", s)
            if (types) {
                "in"("class", types*.canonicalName)
            }
            // from newer to older so we return more recent messages first
            order("whenCreated", "desc")
        }
        forRecordDateBetween { Record rec, DateTime s, DateTime e,
            Collection<Class<? extends RecordItem>> types = [] ->

            eq("record", rec)
            between("whenCreated", s, e)
            if (types) {
                "in"("class", types*.canonicalName)
            }
            // from newer to older so we return more recent messages first
            order("whenCreated", "desc")
        }
    }

    // Static Finders
    // --------------

    @GrailsTypeChecked
    static List<RecordItem> findEveryByApiId(String apiId) {
        List<RecordItem> results = []
        HashSet<Long> itemIds = new HashSet<>()
        List<RecordItemReceipt> receipts = RecordItemReceipt.findAllByApiId(apiId)
        receipts.each { RecordItemReceipt receipt ->
            if (!itemIds.contains(receipt.item.id)) {
                results << receipt.item
                itemIds << receipt.item.id
            }
        }
        results
    }
    static DetachedCriteria<RecordItem> buildForRecords(Collection<Record> records) {
        new DetachedCriteria(RecordItem)
            .build {
                if (records) { "in"("record", records) }
                else { eq("record", null) }
            }
    }

    // Methods
    // -------

    @GrailsTypeChecked
    RecordItem addAllReceipts(Collection<TempRecordReceipt> receipts) {
        receipts.each { TempRecordReceipt r1 -> addReceipt(r1) }
        this
    }

    @GrailsTypeChecked
    RecordItem addReceipt(TempRecordReceipt r1) {
        RecordItemReceipt receipt = new RecordItemReceipt(status: r1.status, apiId: r1.apiId,
            contactNumberAsString: r1.contactNumberAsString, numBillable: r1.numSegments)
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
    RecordItemStatus groupReceiptsByStatus() {
        new RecordItemStatus(this.receipts)
    }

    @GrailsTypeChecked
    ReadOnlyMediaInfo getReadOnlyMedia() { media }

    @GrailsTypeChecked
    ReadOnlyRecord getReadOnlyRecord() { record }

    @GrailsTypeChecked
    void setAuthor(Author author) {
        if (author?.validate()) {
            this.with {
                authorName = author.name
                authorId = author.id
                authorType = author.type
            }
        }
    }
    @GrailsTypeChecked
    Author getAuthor() {
        new Author(name:this.authorName, id:this.authorId, type:this.authorType)
    }
}
