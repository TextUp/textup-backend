package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.type.RecordItemType
import org.textup.util.*

@GrailsTypeChecked
@EqualsAndHashCode
class FeaturedAnnouncement implements WithId {

    DateTime expiresAt
    DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    Phone owner
    String message

    // holds receipts that we are about to save in the next flush
    private List<AnnouncementReceipt> _receiptsToBeSaved = []

    static transients = ["_receiptsToBeSaved"]
    static mapping = {
        whenCreated type:PersistentDateTime
        expiresAt type:PersistentDateTime
    }
    static constraints = {
    	expiresAt validator:{ DateTime val, FeaturedAnnouncement obj ->
    		if (!val?.isAfter(obj.whenCreated)) { ["expiresBeforeCreation"] }
    	}
    }

    def afterInsert() {
        _receiptsToBeSaved?.clear()
    }

    def afterUpdate() {
        _receiptsToBeSaved?.clear()
    }

    // Methods
    // -------

    void expireNow() {
    	expiresAt = DateTime.now(DateTimeZone.UTC)
    }

    ResultGroup<AnnouncementReceipt> addToReceipts(RecordItemType type, IncomingSession session) {
        addToReceipts(type, [session])
    }

    ResultGroup<AnnouncementReceipt> addToReceipts(RecordItemType type,
        Collection<IncomingSession> sessions) {

        ResultGroup<AnnouncementReceipt> resGroup = new ResultGroup<>()
        HashSet<Long> sessionIdsWithReceipt = repeatsForTypeAndSessions(type, sessions)
        sessions.each { IncomingSession session ->
            if (!sessionIdsWithReceipt.contains(session.id)) {
                AnnouncementReceipt receipt = new AnnouncementReceipt(type:type,
                    session:session, announcement:this)
                if (receipt.save()) {
                    _receiptsToBeSaved = _receiptsToBeSaved ?:
                        new ArrayList<AnnouncementReceipt>()
                    _receiptsToBeSaved << receipt
                    resGroup << IOCUtils.resultFactory.success(receipt)
                }
                else {
                    resGroup << IOCUtils.resultFactory.failWithValidationErrors(receipt.errors)
                }
            }
        }
        resGroup
    }

    // Properties
    // ----------

    int getNumReceipts() {
        AnnouncementReceipt.countByAnnouncement(this)
    }

    int getNumCallReceipts() {
        AnnouncementReceipt.countByAnnouncementAndType(this, RecordItemType.CALL)
    }

    int getNumTextReceipts() {
        AnnouncementReceipt.countByAnnouncementAndType(this, RecordItemType.TEXT)
    }

    void setExpiresAt(DateTime exp) {
        expiresAt = exp?.withZone(DateTimeZone.UTC)
    }

    boolean getIsExpired() {
        expiresAt?.isBefore(DateTime.now())
    }

    // Helpers
    // -------

    protected HashSet<Long> repeatsForTypeAndSessions(RecordItemType type,
        Collection<IncomingSession> sessions) {

        Utils.<HashSet<Long>>doWithoutFlush({
            Collection<AnnouncementReceipt> repeats = _receiptsToBeSaved?.findAll {
                it.type == type && it.session in sessions
            } ?: new ArrayList<AnnouncementReceipt>()
            repeats += AnnouncementReceipt.findAllByAnnouncementAndTypeAndSessionInList(this,
                type, sessions)
            new HashSet<Long>(repeats*.session*.id)
        })
    }
}
