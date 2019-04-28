package org.textup

import grails.compiler.GrailsTypeChecked
import grails.gorm.DetachedCriteria
import groovy.transform.EqualsAndHashCode
import groovy.transform.TypeCheckingMode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

// [NOTE] Only the generated `hashCode` is used. The generated `equals` is superceded by the
// overriden `compareTo` method. Therefore, ensure the fields in the annotation match the ones
// used in the compareTo implementation exactly

@EqualsAndHashCode(includes = ["whenCreated", "id"])
@GrailsTypeChecked
class RecordItem implements ReadOnlyRecordItem, WithId, CanSave<RecordItem>, Comparable<RecordItem> {

    // If we want to include id in the equality comparator, we need to explicitly declare it
    // This was an issue when using `CollectionUtils.mergeUnique` on several record items as
    // record items with unique ids but same fields would be eliminated as non-unique
    // see: https://stokito.wordpress.com/2014/12/19/equalsandhashcode-on-grails-domains/
    Long id

    AuthorType authorType
    boolean hasAwayMessage = false
    boolean isAnnouncement = false
    boolean isDeleted = false
    boolean outgoing = true // true is CM->client, false is CM<-client
    boolean wasScheduled = false
    DateTime whenCreated = JodaUtils.utcNow()
    Integer numNotified = 0 // texts = # staff notified, future messages = # "notify-me"
    Long authorId
    MediaInfo media
    Record record // record this item belongs to
    String authorName
    String noteContents

	static transients = ["author"]
    static hasMany = [receipts: RecordItemReceipt]
    static mapping = {
        // [NOTE] one-to-many relationships should not have `fetch: "join"` because of GORM using
        // a left outer join to fetch the data runs into issues when a max is provided
        // see: https://stackoverflow.com/a/25426734
        receipts cascade: "all-delete-orphan"
        media fetch: "join", cascade: "save-update"
        whenCreated type: PersistentDateTime
        noteContents type: "text"
    }
    static constraints = {
    	authorName blank: true, nullable: true
    	authorId nullable: true
        authorType nullable: true
        media nullable: true, cascadeValidation: true // can be null for backwards compatibility for RecordItems that predate this
        noteContents blank: true, nullable: true, maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE
        numNotified min: 0
    }

    // Methods
    // -------

    RecordItem addAllReceipts(Collection<TempRecordReceipt> receipts) {
        receipts.each { TempRecordReceipt r1 -> addReceipt(r1) }
        this
    }

    RecordItem addReceipt(TempRecordReceipt r1) {
        RecordItemReceipt rpt1 = RecordItemReceipt.create(this, r1.apiId, r1.status, r1.contactNumber)
        rpt1.numBillable = r1.numBillable
        this
    }

    RecordItemReceiptInfo groupReceiptsByStatus() { new RecordItemReceiptInfo(receipts) }

    // Properties
    // ----------

    @Override
    ReadOnlyMediaInfo getReadOnlyMedia() { media }

    @Override
    ReadOnlyRecord getReadOnlyRecord() { record }

    // Domain classes with @GrailsTypeChecked seem to be unable to use @Sortable without
    // triggering a canonicalization error during type checking.
    // [NOTE] the `==` operator in Groovy calls `compareTo` INSTEAD OF `equals` if present
    // see https://stackoverflow.com/a/9682512
    @Override
    int compareTo(RecordItem rItem) {
        whenCreated <=> rItem?.whenCreated ?: id <=> rItem?.id
    }

    void setAuthor(Author author) {
        if (author?.validate()) {
            authorName = author.name
            authorId = author.id
            authorType = author.type
        }
    }

    Author getAuthor() { Author.create(authorId, authorName, authorType) }
}
