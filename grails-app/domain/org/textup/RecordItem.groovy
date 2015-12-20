package org.textup

import groovy.transform.EqualsAndHashCode
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.restapidoc.annotation.*

@EqualsAndHashCode
class RecordItem {

    @RestApiObjectField(
        description    = "Date this item was added to the record",
        allowedType    = "DateTime",
        useForCreation = false)
	DateTime dateCreated = DateTime.now(DateTimeZone.UTC)
    Record record //record this item belongs to
    @RestApiObjectField(
        description    = "The direction of communication. Outgoing is from staff to client.",
        useForCreation = false)
	boolean outgoing = true //true is CM->client, false is CM<-client

	//non-null when this RecordItem is authored by someone
	//other than the owner of the record
    @RestApiObjectField(
        description    = "If available, the author of this entry.",
        useForCreation = false)
	String authorName
    //id of the author, which class it pertains to depends on the
    //specific implementation of the phone
    @RestApiObjectField(
        description    = "If available, the id of the author of this entry.",
        allowedType    = "Number",
        useForCreation = false)
	Long authorId

    @RestApiObjectFields(params=[
        @RestApiObjectField(
            apiFieldName= "incoming",
            description = "The direction of communication. Incoming is from client to staff",
            allowedType =  "Boolean",
            useForCreation = false),
        @RestApiObjectField(
            apiFieldName= "type",
            description = "Type of record item. One of: call, text, note",
            allowedType =  "String",
            useForCreation = false)
    ])
	static transients = ["incoming", "author"]
    static constraints = {
    	authorName blank:true, nullable:true
    	authorId nullable:true
    }
    static mapping = {
        autoTimestamp false
        dateCreated type:PersistentDateTime
        receipts lazy:false, cascade:"all-delete-orphan"
    }
    @RestApiObjectField(
        apiFieldName   = "receipts",
        description    = "Statuses of all phone numbers who were sent this response",
        allowedType    = "List<Receipt>",
        useForCreation = false)
    static hasMany = [receipts:RecordItemReceipt]
    static namedQueries = {
        forRecord { Record rec ->
            eq("record", rec)
            order("dateCreated", "desc")
        }
        forThisIdAndPhoneId { Long thisId, Long phoneId ->
            eq("id", thisId)
            record { "in"("id", Contact.recordIdsForPhoneId(phoneId)) }
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

    /*
	Has many:
	*/

    ////////////
    // Events //
    ////////////

    def beforeDelete() {
        RecordItem.withNewSession {
            this.receipts.clear()
            //in before delete using batch delete works but
            //calling delete on each RecordItemReceipt doesn't
            RecordItemReceipt.where { item == this }.deleteAll()
        }
    }

    ////////////////////
    // Helper methods //
    ////////////////////

    /////////////////////
    // Property Access //
    /////////////////////

    int getNumReceived() { RecordItemReceipt.success(this).count() }
    List<RecordItemReceipt> getReceived() { RecordItemReceipt.success(this).list() }
    int getNumPending() { RecordItemReceipt.pending(this).count() }
    List<RecordItemReceipt> getPending() { RecordItemReceipt.pending(this).list() }
    int getNumFailed() { RecordItemReceipt.failed(this).count() }
    List<RecordItemReceipt> getFailed() { RecordItemReceipt.failed(this).list() }

    void setAuthor(Author auth) {
        this.with {
            authorName = auth?.name
            authorId = auth?.id
        }
    }
    Author getAuthor() {
        new Author(name:this.authorName, id:this.authorId)
    }

    boolean getIncoming() { !this.outgoing }
    void setIncoming(boolean i) {
    	this.outgoing = !i
    }
}
