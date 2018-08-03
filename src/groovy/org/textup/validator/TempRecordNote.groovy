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
		info nullable: false, validator:{ Map noteInfo, TempRecordNote obj ->
			// short circuit, handled by nullable error
			if (noteInfo == null) { return }
			// For existing notes, it's okay if the passed-in info doesn't have so much info as
			// long as the existing note has enough info
			if (!noteInfo.noteContents && !noteInfo.location && !obj.note?.noteContents &&
				!obj.note?.location && (!obj.note?.media || obj.note.media.isEmpty())) {
				["noInfo"]
			}
		}
	}

	// Methods
	// -------

	// This method will update fields and will NOT handle media or revisions
	Result<RecordNote> toNote(Author auth) {
		if (!this.validate()) {
            return Helpers.resultFactory.failWithValidationErrors(this.errors)
        }
		RecordNote note1 = note
		// we manually associate note with record and author instead of using
		// the addAny methods in the record because adding a note should not
		// trigger a record activity update like adding a text or a call should
		updateFields(note1, auth)
		// If there is a item we need to before, we will modify the whenCreated
		// time to artificially insert this note into the appropriate position
		// in the record. Otherwise, we will preserve the default value
		tryModifyWhenCreated(note1, this.after)
        // cascades validation and saving to location -- see `cascadeValidation: true` in constraints
        if (note1.save() && (!note1.location || note1.location.save())) {
            Helpers.resultFactory.success(note1)
        }
        else { Helpers.resultFactory.failWithValidationErrors(note1.errors) }
	}

	protected RecordNote updateFields(RecordNote note1, Author auth) {
		if (!note1 || !info) {
			return note1
		}
		note1.author = auth
		Boolean isDeleted = Helpers.to(Boolean, info.isDeleted)
		if (isDeleted != null) {
			note1.isDeleted = isDeleted
		}
		if (info.noteContents != null) {
			note1.noteContents = Helpers.to(String, info.noteContents)
		}
		if (info.location instanceof Map) {
			// no need to create a separate location because the process of creating
			// a new Location object exists in `RecordNote.createRevision`
			Location loc = note1.getLocation() ?: new Location()
			Map lInfo = info.location as Map
			if (lInfo.address) { loc.address = lInfo.address }
            if (lInfo.lat) { loc.lat = Helpers.to(BigDecimal, lInfo.lat) }
            if (lInfo.lon) { loc.lon = Helpers.to(BigDecimal, lInfo.lon) }
            note1.location = loc
		}
		note1
	}

	protected RecordNote tryModifyWhenCreated(RecordNote note1, DateTime afterTime) {
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
