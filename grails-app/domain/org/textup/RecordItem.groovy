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

@EqualsAndHashCode
@GrailsTypeChecked
class RecordItem implements ReadOnlyRecordItem, WithId, CanSave<RecordItem> {

    AuthorType authorType
    boolean hasAwayMessage = false
    boolean isAnnouncement = false
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
        receipts cascade: "all-delete-orphan"
        media fetch: "join", cascade: "save-update"
        whenCreated type: PersistentDateTime
        noteContents type: "text"
    }
    static constraints = {
    	authorName blank:true, nullable:true
    	authorId nullable:true
        authorType nullable:true
        media nullable:true, cascadeValidation: true // can be null for backwards compatibility for RecordItems that predate this
        noteContents blank:true, nullable:true, maxSize: ValidationUtils.MAX_TEXT_COLUMN_SIZE
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

    void setAuthor(Author author) {
        if (author?.validate()) {
            authorName = author.name
            authorId = author.id
            authorType = author.type
        }
    }

    Author getAuthor() { Author.create(authorId, authorName, authorType) }
}
