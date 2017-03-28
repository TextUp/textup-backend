package org.textup.validator

import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import grails.validation.Validateable
import groovy.transform.EqualsAndHashCode
import org.hibernate.FlushMode
import org.hibernate.Session
import org.joda.time.DateTime
import org.joda.time.Duration
import org.springframework.http.HttpStatus
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
		note nullable:true
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
		// DOES validate images for size and number
		info nullable:false, validator:{ Map noteInfo, TempRecordNote tempNote ->
			if (!tempNote.note && !noteInfo.noteContents && !noteInfo.location &&
				!noteInfo.doImageActions) {
				['noInfo']
			}
		}
	}

	// Validation
	// ----------

	boolean doValidate() {
		this.clearErrors()
		this.validate()
		validateImageActionsIfAny(this.info)

		boolean isValid = !this.hasErrors()
		if (!this.note && (!this.phone || !this.hasTargetForNewNote)) {
			isValid = false
			this.errors.reject('tempRecordNote.missingInfoForNewNote')
		}
		isValid
	}
	protected boolean getHasTargetForNewNote() {
		this.contact || this.sharedContact || this.tag
	}

	// Methods
	// -------

	public <T> Result<List<T>> forEachImageToAdd(Closure<T> doThis) {
		String toMatch = Constants.NOTE_IMAGE_ACTION_ADD
		forEachImage({ Map info ->
			doThis(buildUploadItem(info))
		}, { Map info -> Helpers.toLowerCaseString(info.action) == toMatch })
	}
	public <T> Result<List<T>> forEachImageToRemove(Closure<T> doThis) {
		String toMatch = Constants.NOTE_IMAGE_ACTION_REMOVE
		forEachImage({ Map info ->
			doThis(Helpers.toString(info.key))
		}, { Map info -> Helpers.toLowerCaseString(info.action) == toMatch })
	}
	public <T> Result<List<T>> forEachImage(Closure<T> doThis, Closure doesMatch = null) {
		List actions = this.info.doImageActions instanceof List ?
			this.info.doImageActions as List : []
		List<T> results = []
		for (Object action in actions) {
			if (!(action instanceof Map)) continue
			Map actionMap = action as Map
			if (!doesMatch || (doesMatch && doesMatch(actionMap))) {
				results << doThis(actionMap)
			}
		}
		getResultFactory().success(results)
	}
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
		Boolean isDeleted = Helpers.toBoolean(this.info.isDeleted)
		if (isDeleted != null) {
			note1.isDeleted = isDeleted
		}
		if (this.info.noteContents != null) {
			note1.noteContents = Helpers.toString(this.info.noteContents)
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
                if (lInfo.lat) lat = Helpers.toBigDecimal(lInfo.lat)
                if (lInfo.lon) lon = Helpers.toBigDecimal(lInfo.lon)
    		}
    		note1.location = loc
		}
		this.<String>forEachImageToRemove(note1.&removeImage)
			.logFail('TempRecordNote.updateFields')
		note1
	}
	protected RecordNote modifyWhenCreatedIfNeeded(RecordNote note1, DateTime afterTime) {
		RecordItem beforeItem = afterTime ?
			note1.record?.getSince(afterTime, [max:1])[0] : null
		if (beforeItem) {
			BigDecimal midpointMillis  = (new Duration(afterTime,
				beforeItem.whenCreated).millis / 2);
			long lowerBound = Constants.MIN_NOTE_SPACING_MILLIS,
				upperBound = Constants.MAX_NOTE_SPACING_MILLIS
			// # millis to be add should be half the # of millis between time we need to be after
			// and the time that we need to be before (to avoid passing next item)
			// BUT this # must be between the specified lower and upper bounds
			long plusAmount = Math.max(lowerBound,
				Math.min(Helpers.toLong(midpointMillis), upperBound))
			// set note's whenCreated to the DateTime we need to be after plus an offset
			note1.whenCreated = afterTime.plus(plusAmount)

		}
		note1
	}

	// Validation Helpers
	// ------------------

	protected void validateImageActionsIfAny(Map noteInfo) {
		// short circuit if no image actions
		if (!noteInfo.doImageActions) {
			return
		}
		// image actions must be a list
		if (!(noteInfo.doImageActions instanceof List)) {
			this.errors.reject('tempRecordNote.images.notList')
			return
		}
		Helpers.toList(noteInfo.doImageActions)
			.each(this.&validateImageAction)
	}
	protected void validateImageAction(Object action) {
		// each action must be a map
		if (!(action instanceof Map)) {
			this.errors.reject('tempRecordNote.images.actionNotMap')
			return
		}

		Map actionMap = action as Map
		// check each individual image action for structure and completeness
		switch (Helpers.toLowerCaseString(actionMap.action)) {
			case Constants.NOTE_IMAGE_ACTION_REMOVE:
				if (!actionMap.key) {
					this.errors.reject("tempRecordNote.images.removeMissingKey")
				}
				break
			case Constants.NOTE_IMAGE_ACTION_ADD:
				UploadItem uItem = buildUploadItem(actionMap)
                if (!uItem.validate()) {
                	Result res = getResultFactory().failWithValidationErrors(uItem.errors)
                	this.errors.reject("tempRecordNote.images.invalidUploadItem",
                		[res.errorMessages] as Object[], null)
                }
				break
			default:
				this.errors.reject("tempRecordNote.images.invalidAction",
					[actionMap.action] as Object[], "Invalid image action")
		}
	}

	protected UploadItem buildUploadItem(Map info) {
		UploadItem uItem = new UploadItem()
		uItem.with {
			mimeType = info.mimeType
			data = info.data
			checksum = info.checksum
		}
		uItem
	}
}
