package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*
import org.textup.type.RecordItemType

@GrailsTypeChecked
@EqualsAndHashCode
class FeaturedAnnouncement implements WithId {

    Phone owner

    @RestApiObjectField(
        description    = "Contents of this announcement",
        allowedType    = "String",
        useForCreation = true)
    String message
    @RestApiObjectField(
        description    = "Date this announcement was created",
        allowedType    = "DateTime",
        useForCreation = false)
	DateTime whenCreated = DateTime.now(DateTimeZone.UTC)
    @RestApiObjectField(
        description    = "Date this session should expire",
        allowedType    = "DateTime",
        useForCreation = true)
	DateTime expiresAt

    // holds receipts that we are about to save in the next flush
    private List<AnnouncementReceipt> _receiptsToBeSaved = []

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName= "isExpired",
            description = "If this announcement is already expired",
            allowedType =  "Boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName= "numReceipts",
            description = "Number of sessions that have received this announcement",
            allowedType =  "Number",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName= "numCallReceipts",
            description = "Number of sessions that have received via call",
            allowedType =  "Number",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName= "numTextReceipts",
            description = "Number of sessions that have received via text",
            allowedType =  "Number",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName= "staff",
            description = "Id of the staff that this session belongs to, if any",
            allowedType =  "Number",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName= "team",
            description = "Id of the team that this session belongs to, if any",
            allowedType =  "Number",
            useForCreation = false)
    ])
    static transients = ["_receiptsToBeSaved"]
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
    int getNumCallReceipts() {
        AnnouncementReceipt.countByAnnouncementAndType(this, RecordItemType.CALL)
    }
    int getNumTextReceipts() {
        AnnouncementReceipt.countByAnnouncementAndType(this, RecordItemType.TEXT)
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
                    resGroup << Helpers.resultFactory.success(receipt)
                }
                else {
                    resGroup << Helpers.resultFactory.failWithValidationErrors(receipt.errors)
                }
            }
        }
        resGroup
    }
    protected HashSet<Long> repeatsForTypeAndSessions(RecordItemType type,
        Collection<IncomingSession> sessions) {
        Helpers.<HashSet<Long>>doWithoutFlush({
            Collection<AnnouncementReceipt> repeats = _receiptsToBeSaved?.findAll {
                it.type == type && it.session in sessions
            } ?: new ArrayList<AnnouncementReceipt>()
            repeats += AnnouncementReceipt.findAllByAnnouncementAndTypeAndSessionInList(this,
                type, sessions)
            new HashSet<Long>(repeats*.session*.id)
        })
    }
}
