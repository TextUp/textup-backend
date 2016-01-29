package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.hibernate.FlushMode

@EqualsAndHashCode
class FeaturedAnnouncement {

    def resultFactory

    Phone owner
    String message
	DateTime dateCreated = DateTime.now(DateTimeZone.UTC)
	DateTime expiresAt

    // holds references to record items of contacts that have received
    // this announcement via text or call
    static constraints = {
    	expiresAt validator:{ val, obj ->
    		if (!val?.isAfter(obj.dateCreated)) { ["expiresBeforeCreation"] }
    	}
    }
    static mapping = {
        autoTimestamp false
    	dateCreated type:PersistentDateTime
    	expiresAt type:PersistentDateTime
    }
    static namedQueries = {
    	forPhone { Phone p1 ->
    		eq("owner", p1)
    		ge("expiresAt", DateTime.now(DateTimeZone.UTC)) //not expired
    		order("dateCreated", "desc")
    	}
    }

    /*
    Has many:
        AnnouncementReceipt
     */

    // Expiration
    // ----------

    void expireNow() {
    	this.expiresAt = DateTime.now(DateTimeZone.UTC)
    }
    void setExpiresAt(DateTime exp) {
    	this.expiresAt = exp?.withTimeZone(DateTimeZone.UTC)
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
        List<IncomingSession> sessions) {
        ResultList<AnnouncementReceipt> resList = new ResultList<>()
        List<IncomingSession> repeatSessions = AnnouncementReceipt.createCriteria().list {
            projections {
                property("session")
            }
            if (sessions) { "in"("session", sessions) }
            else { eq("session", null) }
            eq("announcement", this)
        }
        HashSet<IncomingSession> sessionsWithReceipt = new HashSet<>(repeatSessions)
        sessions.each { IncomingSession session ->
            if (!sessionsWithReceipt.contains(session)) {
                AnnouncementReceipt receipt = new AnnouncementReceipt(type:type,
                    session:session, announcement:announcement)
                if (receipt.save()) {
                    resList << resultFactory.success(receipt)
                }
                else {
                    resList << resultFactory.failWithValidationErrors(receipt.errors)
                }
            }
        }
        resList
    }
}
