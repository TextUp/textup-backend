package org.textup

import grails.compiler.GrailsTypeChecked
import groovy.transform.EqualsAndHashCode
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.jadira.usertype.dateandtime.joda.PersistentDateTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.quartz.SimpleTrigger
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.restapidoc.annotation.*
import org.textup.types.AuthorType
import org.textup.types.FutureMessageType
import org.textup.validator.Author

@EqualsAndHashCode
@GrailsTypeChecked
class FutureMessage {

	Trigger trigger

	DateTime whenCreated = DateTime.now()
	String key = UUID.randomUUID().toString()
	Record record

    String authorName
    Long authorId
    AuthorType authorType

	DateTime startDate = DateTime.now()
	String message
	long repeatIntervalInMillis
	FutureMessageType type
    boolean notifySelf = false

	Integer repeatCount
	DateTime endDate

	static transients = ["trigger", "author"]
    static constraints = {
    	message blank:false, nullable:false, maxSize:(Constants.TEXT_LENGTH * 2)
    	repeatIntervalInMillis minSize: TimeUnit.DAYS.toMillis(1)
		repeatCount nullable:true, validator { Integer rNum, FutureMessage msg ->
			if (rNum == SimpleTrigger.REPEAT_INDEFINITELY && msg.endDate) {
				["unboundedNeedsEndDate"]
			}
		}
		endDate nullable:true, validator { DateTime end, FutureMessage msg ->
			if (end && end.isBeforeNow()) {
				["notInFuture"]
			}
		}
    }
    static mapping = {
    	whenCreated type:PersistentDateTime
    	startDate type:PersistentDateTime
		endDate type:PersistentDateTime
    }

    // Events
    // ------

    def beforeInsert() {
    	schedule()
            .logFail("FutureMessage.beforeInsert")
            .success // return boolean false to cancel if error
    }
    def beforeUpdate() {
    	if (['repeatIntervalInMillis', 'repeatCount', 'endDate'].any(this.&isDirty) {
    		schedule()
                .logFail("FutureMessage.beforeUpdate")
                .success // return boolean false to cancel if error
    	}
    }
    def beforeDelete() {
    	unschedule()
            .logFail("FutureMessage.beforeDelete")
            .success // return boolean false to cancel if error
    }
    def afterLoad() {
    	this.trigger = quartzScheduler.getTrigger(this.triggerKey)
    }

    // Property Access
    // ---------------

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

    TriggerKey getTriggerKey() {
    	String recordId = this.record?.id?.toString()
    	recordId ? TriggerKey.triggerKey(this.key, recordId) : null
    }
    DateTime getNextFire() {
    	this.trigger ? new DateTime(this.trigger.nextFireTime) : null
    }
    Integer getTimesTriggered() {
    	(this.trigger instanceof SimpleTrigger) ? this.trigger.timesTriggered : null
    }
    boolean getWillEndOnDate() {
    	!!this.endDate
    }
    boolean getIsRepeating() {
    	this.repeatCount || this.endDate
    }
    boolean getIsDone() {
    	!this.trigger || !this.trigger.mayFireAgain()
    }
    void setRepeatIntervalInDays(int numDays) {
    	this.repeatIntervalInMillis = TimeUnit.DAYS.toMillis(numDays)
    }
}
