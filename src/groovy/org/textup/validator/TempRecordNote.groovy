package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.hibernate.Session
import org.joda.time.DateTime
import org.joda.time.Duration
import org.textup.*

@GrailsTypeChecked
@EqualsAndHashCode
@Validateable
class TempRecordNote {

	// specify record note if we are updating
	RecordNote note

	// specify the following if we are creating a new note
	Phone phone
	Contact contact
	SharedContact sharedContact
	ContactTag tag

	// info to create or update the note with
	DateTime after
	Map info

	static constraints = {
		note nullable:true, validator:{ RecordNote note1, TempRecordNote tempNote ->
			if (!note1 && (!tempNote.phone || !tempNote.hasTargetForNewNote)) {
				["missingInfoForNewNote"]
			}
		}
		phone nullable:true
		contact nullable:true, validator:{ Contact c1, TempRecordNote tempNote ->
			if (c1 && c1.phone != tempNote?.phone) {
				["foreign", c1.id]
			}
		}
		sharedContact nullable:true, validator:{ SharedContact sc1, TempRecordNote tempNote ->
			if (sc1 && (!sc1.isActive || sc1.sharedWith != tempNote?.phone)) {
				["notShared", sc1.id]
			}
		}
		tag nullable:true, validator:{ ContactTag tag1, TempRecordNote tempNote ->
			if (tag1 && tag1.phone != tempNote?.phone) {
				["foreign", tag1.id]
			}
		}
		after nullable:true
		// ensures that note will have at least one of text, location or images
		// leaves text and location validation to respective domain objects
		info nullable:false, validator:{ Map noteInfo, TempRecordNote tempNote ->
			if (!tempNote.note && !noteInfo.noteContents && !noteInfo.location &&
				!noteInfo.doImageActions) {
				['noInfo']
			}
		}
	}

	// Validation
	// ----------

	protected boolean getHasTargetForNewNote() {
		this.contact || this.sharedContact || this.tag
	}

	// Methods
	// -------

	// this method will update fields, including removing images, but adding new
	// images and creating revisions must be handled manually. For adding new images,
	// you can call the iterator forEachImageToAdd and pass in a closure action
	// (for example, the addImage method on RecordNote) because we also need to generate
	// an upload link when adding a new image.
	RecordNote toNote(Author auth) {
		// we manually associate note with record and author instead of using
		// the addAny methods in the record because adding a note should not
		// trigger a record activity update like adding a text or a call should
		RecordNote note1 = this.note ?: new RecordNote(record:getRecord())
		updateFields(note1, auth)
		// If there is a item we need to before, we will modify the whenCreated
		// time to artificially insert this note into the appropriate position
		// in the record. Otherwise, we will preserve the default value
		modifyWhenCreatedIfNeeded(note1, this.after)
	}

	// Note Helpers
	// ------------

	protected ResultFactory getResultFactory() {
		Holders
			.applicationContext
			.getBean('resultFactory') as ResultFactory
	}
	protected Record getRecord() {
		this.contact ? this.contact.record :
			(this.sharedContact ? this.sharedContact.record :
				(this.tag ? this.tag.record : null))
	}
	protected RecordNote updateFields(RecordNote note1, Author auth) {
		if (!note1) {
			return note1
		}
		note1.author = auth
		Boolean isDeleted = Helpers.to(Boolean, this.info.isDeleted)
		if (isDeleted != null) {
			note1.isDeleted = isDeleted
		}
		if (this.info.noteContents != null) {
			note1.noteContents = Helpers.to(String, this.info.noteContents)
		}
		if (this.info.location instanceof Map) {
			// never use existing note location when updating location
			// because doing so would defeat the purpose of a revision history
			// that is, using the same location would cause all revisions
			// associated with that location to show the same updated values,
			// losing the stored previous values in the process
			Location loc = new Location()
			// if existing location, populate existing properties in case
			// we are looking to update only some of the properties
			if (note1.location) {
				loc.properties = note1.location.properties
			}
			Map lInfo = this.info.location as Map
			loc.with {
    			if (lInfo.address) address = lInfo.address
                if (lInfo.lat) lat = Helpers.to(BigDecimal, lInfo.lat)
                if (lInfo.lon) lon = Helpers.to(BigDecimal, lInfo.lon)
    		}
    		note1.location = loc
		}
		note1
	}
	protected RecordNote modifyWhenCreatedIfNeeded(RecordNote note1, DateTime afterTime) {
		RecordItem beforeItem = afterTime ?
			note1.record?.getSince(afterTime, [max:1])[0] : null
		if (beforeItem) {
			BigDecimal midpointMillis  = (new Duration(afterTime,
				beforeItem.whenCreated).millis / 2)
			long lowerBound = Constants.MIN_NOTE_SPACING_MILLIS,
				upperBound = Constants.MAX_NOTE_SPACING_MILLIS
			// # millis to be add should be half the # of millis between time we need to be after
			// and the time that we need to be before (to avoid passing next item)
			// BUT this # must be between the specified lower and upper bounds
			long plusAmount = Math.max(lowerBound,
				Math.min(Helpers.to(Long, midpointMillis), upperBound))
			// set note's whenCreated to the DateTime we need to be after plus an offset
			note1.whenCreated = afterTime.plus(plusAmount)
		}
		note1
	}
}
