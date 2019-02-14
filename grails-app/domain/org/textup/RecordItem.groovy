package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.type.AuthorType
import org.textup.type.ReceiptStatus
import org.textup.validator.Author
import org.textup.validator.TempRecordReceipt

@GrailsTypeChecked
@EqualsAndHashCode
class RecordItem implements ReadOnlyRecordItem, WithId {

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
        description    = "If this was originally a scheduled message",
        allowedType    = "Boolean",
        useForCreation = false)
    boolean wasScheduled = false

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

    @RestApiObjectField(
        description    = "Whether this item is deleted",
        allowedType    = "Boolean",
        useForCreation = false)
    boolean isDeleted = false

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

    // Static Finders
    // --------------

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

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<RecordItem> forRecords(Collection<Record> records) {
        new DetachedCriteria(RecordItem)
            .build {
                if (records) { "in"("record", records) }
                else { eq("record", null) }
            }
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<RecordItem> forPhoneIdWithOptions(Long phoneId,
        DateTime start = null, DateTime end = null,
        Collection<Class<? extends RecordItem>> types = null) {

        new DetachedCriteria(RecordItem)
            .build {
                or {
                    "in"("record.id", RecordItem.forRecordOwnerPhone(Contact, phoneId))
                    "in"("record.id", RecordItem.forRecordOwnerPhone(ContactTag, phoneId))
                }
            }
            .build(RecordItem.buildForOptionalDates(start, end))
            .build(RecordItem.buildForOptionalTypes(types))
    }

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static DetachedCriteria<RecordItem> forRecordIdsWithOptions(Collection<Long> recIds,
        DateTime start = null, DateTime end = null,
        Collection<Class<? extends RecordItem>> types = null) {

        new DetachedCriteria(RecordItem)
            .build {
                if (recIds) {
                    "in"("record.id", recIds)
                }
                else { eq("record.id", null) }
            }
            .build(RecordItem.buildForOptionalDates(start, end))
            .build(RecordItem.buildForOptionalTypes(types))
    }

    // Specify sort order separately because when we call `count()` on a DetachedCriteria
    // we are grouping fields and, according to the SQL spec, we need to specify a GROUP BY
    // if we also have an ORDER BY clause. Therefore, to avoid GROUP BY errors when calling `count()`
    // we don't include the sort order by default and we have to separately add it in
    // before calling `list()`. See https://stackoverflow.com/a/19602031
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure buildForSort(boolean recentFirst = true) {
        return {
            if (recentFirst) {
                // from newer (larger # millis) to older (smaller $ millis)
                order("whenCreated", "desc")
            }
            else { order("whenCreated", "asc") }
        }
    }


    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static DetachedCriteria<Long> forRecordOwnerPhone(Class<? extends WithRecord> ownerClass,
        Long phoneId) {

        return new DetachedCriteria(ownerClass).build {
            projections { property("record.id") }
            eq("phone.id", phoneId)
        }
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure buildForOptionalDates(DateTime s = null, DateTime e = null) {
        return {
            if (s && e) {
                between("whenCreated", s, e)
            }
            else if (s) {
                ge("whenCreated", s)
            }
        }
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    protected static Closure buildForOptionalTypes(Collection<Class<? extends RecordItem>> types = null) {
        return {
            if (types) {
                "in"("class", types*.canonicalName)
            }
        }
    }

    // Methods
    // -------

    RecordItem addAllReceipts(Collection<TempRecordReceipt> receipts) {
        receipts.each { TempRecordReceipt r1 -> addReceipt(r1) }
        this
    }

    RecordItem addReceipt(TempRecordReceipt r1) {
        RecordItemReceipt receipt = new RecordItemReceipt(status: r1.status, apiId: r1.apiId,
            contactNumberAsString: r1.contactNumberAsString, numBillable: r1.numSegments)
        addToReceipts(receipt)
        this
    }

    // Property Access
    // ---------------

    List<RecordItemReceipt> getReceiptsByStatus(ReceiptStatus stat) {
        RecordItemReceipt.findAllByItemAndStatus(this, stat)
    }

    RecordItemStatus groupReceiptsByStatus() {
        new RecordItemStatus(this.receipts)
    }

    ReadOnlyMediaInfo getReadOnlyMedia() { media }

    ReadOnlyRecord getReadOnlyRecord() { record }

    void setAuthor(Author author) {
        if (author?.validate()) {
            this.with {
                authorName = author.name
                authorId = author.id
                authorType = author.type
            }
        }
    }
    Author getAuthor() {
        new Author(name:this.authorName, id:this.authorId, type:this.authorType)
    }
}
