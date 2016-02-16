package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.hibernate.Session
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.textup.types.RecordItemType

@GrailsTypeChecked
@EqualsAndHashCode
class FeaturedAnnouncement {

    ResultFactory resultFactory

    Phone owner
    String message
	DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
	DateTime expiresAt

    // holds receipts that we are about to save in the next flush
    private List<AnnouncementReceipt> _receiptsToBeSaved = []

    static transients = ["resultFactory", "_receiptsToBeSaved"]
    static constraints = {
    	expiresAt validator:{ DateTime val, FeaturedAnnouncement obj ->
    		if (!val?.isAfter(obj.whenCreated)) { ["expiresBeforeCreation"] }
    	}
    }
    static mapping = {
    	whenCreated type:PersistentDateTime
    	expiresAt type:PersistentDateTime
    }

    /*
    Has many:
        AnnouncementReceipt
     */

    // Static finders
    // --------------

    static int countForPhone(Phone p1) {
        FeaturedAnnouncement.countByOwnerAndExpiresAtGreaterThan(p1, DateTime.now())
    }
    static List<FeaturedAnnouncement> listForPhone(Phone p1, Map params=[:]) {
        FeaturedAnnouncement.findAllByOwnerAndExpiresAtGreaterThan(p1,
            DateTime.now(), params + [sort:"whenCreated", order:"desc"])
    }

    // Events
    // ------

    def afterInsert() {
        _receiptsToBeSaved?.clear()
    }
    def afterUpdate() {
        _receiptsToBeSaved?.clear()
    }

    // Expiration
    // ----------

    void expireNow() {
    	this.expiresAt = DateTime.now(DateTimeZone.UTC)
    }
    void setExpiresAt(DateTime exp) {
    	this.expiresAt = exp?.withZone(DateTimeZone.UTC)
    }
    boolean getIsExpired() {
        this.expiresAt?.isBefore(DateTime.now())
    }

    // Receipts
    // --------

    int getNumReceipts() {
        AnnouncementReceipt.countByAnnouncement(this)
    }
    ResultList<AnnouncementReceipt> addToReceipts(RecordItemType type, IncomingSession session) {
        addToReceipts(type, [session])
    }
    ResultList<AnnouncementReceipt> addToReceipts(RecordItemType type,
        Collection<IncomingSession> sessions) {
        ResultList<AnnouncementReceipt> resList = new ResultList<>()
        HashSet<IncomingSession> sessionsWithReceipt = repeatsForTypeAndSessions(type, sessions)
        sessions.each { IncomingSession session ->
            if (!sessionsWithReceipt.contains(session)) {
                AnnouncementReceipt receipt = new AnnouncementReceipt(type:type,
                    session:session, announcement:this)
                if (receipt.save()) {
                    _receiptsToBeSaved = _receiptsToBeSaved ?:
                        new ArrayList<AnnouncementReceipt>()
                    _receiptsToBeSaved << receipt
                    resList << resultFactory.success(receipt)
                }
                else {
                    resList << resultFactory.failWithValidationErrors(receipt.errors)
                }
            }
        }
        resList
    }
    protected HashSet<IncomingSession> repeatsForTypeAndSessions(RecordItemType type,
        Collection<IncomingSession> sessions) {
        Collection<AnnouncementReceipt> repeats = _receiptsToBeSaved?.findAll {
            it.type == type && it.session in sessions
        } ?: new ArrayList<AnnouncementReceipt>()
        HashSet<IncomingSession> sessionsWithReceipt = new HashSet<>(repeats*.session)
        AnnouncementReceipt.withNewSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                repeats = AnnouncementReceipt
                    .findAllByAnnouncementAndTypeAndSessionInList(this, type, sessions)
                sessionsWithReceipt.addAll(repeats*.session)
            }
            finally {
                session.flushMode = FlushMode.AUTO
            }
        }
        sessionsWithReceipt
    }
}
