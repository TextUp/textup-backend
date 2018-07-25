package org.textup.validator

import grails.compiler.GrailsTypeChecked
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

	RecordNote note
	DateTime after
	Map info

	static constraints = { // default nullable: false
		after nullable: true
		// ensures that note will have at least one of text, location or media
		// leaves text and location validation to respective domain objects
		info validator:{ Map noteInfo, TempRecordNote obj ->
			if (!noteInfo.noteContents && !noteInfo.location && (!obj.media || obj.media.isEmpty())) {
				['noInfo']
			}
		}
	}

	// Methods
	// -------

	// This method will update fields and will NOT handle media or revisions
	Result<RecordNote> toNote(Author auth) {
		RecordNote note1 = note
		// we manually associate note with record and author instead of using
		// the addAny methods in the record because adding a note should not
		// trigger a record activity update like adding a text or a call should
		updateFields(note1, auth)
		// If there is a item we need to before, we will modify the whenCreated
		// time to artificially insert this note into the appropriate position
		// in the record. Otherwise, we will preserve the default value
		modifyWhenCreatedIfNeeded(note1, this.after)
		// validate and save
        if (!tempNote.validate()) {
            return resultFactory.failWithValidationErrors(tempNote.errors)
        }
        if (note1.location && !note1.location.save()) {
            return resultFactory.failWithValidationErrors(note1.location.errors)
        }
        if (note1.save()) {
            resultFactory.success(note1)
        }
        else { resultFactory.failWithValidationErrors(note1.errors) }
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
