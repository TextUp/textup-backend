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
				['foreign', c1.id]
			}
		}
		sharedContact nullable:true, validator:{ SharedContact sc1, TempRecordNote tempNote ->
			if (sc1 && (!sc1.isActive || sc1.sharedWith != tempNote?.phone)) {
				['notShared', sc1.id]
			}
		}
		tag nullable:true, validator:{ ContactTag tag1, TempRecordNote tempNote ->
			if (tag1 && tag1.phone != tempNote?.phone) {
				['foreign', tag1.id]
			}
		}
		after nullable:true
		// ensures that note will have at least one of text, location or images
		// leaves text and location validation to respective domain objects
		// DOES validate images for size and number
		info nullable:false, validator:{ Map noteInfo, TempRecordNote tempNote ->
			if (!tempNote.note && !noteInfo.contents && !noteInfo.location &&
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
		forEachImage({ Map action ->
			doThis(Helpers.toString(action.mimeType),
				Helpers.toLong(action.sizeInBytes))
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
	RecordNote toNote(Author auth) {
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
		if (this.info.isDeleted == false) {
			note1.isDeleted = false
		}
		if (this.info.contents) {
			note1.contents = Helpers.toString(this.info.contents)
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
			// set note's whenCreated to the DateTime we need to be after
			note1.whenCreated = afterTime
				// ...the greater of 1 millisecond or
				.plus(Math.max(1,
					//...the number of milliseconds between time we need to be after
					Helpers.toInteger(new Duration(afterTime,
						//...and the time we need to be before divided by two
						beforeItem.whenCreated).millis / 2)))
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
		Integer maxBytes = Helpers.toInteger(
			Holders.flatConfig['textup.maxImageSizeInBytes'])
		// check each individual image action for structure and completeness
		switch (Helpers.toLowerCaseString(actionMap.action)) {
			case Constants.NOTE_IMAGE_ACTION_ADD:
				if (!Helpers.toString(actionMap.mimeType)?.contains('image')) {
					this.errors.reject('tempRecordNote.images.addNotImage',
						[actionMap.mimeType] as Object[], "Not an image")
				}
				if (Helpers.toInteger(actionMap.sizeInBytes) > maxBytes) {
					this.errors.reject('tempRecordNote.images.addTooLarge')
				}
				break
			case Constants.NOTE_IMAGE_ACTION_REMOVE:
				if (!actionMap.key) {
					this.errors.reject('tempRecordNote.images.removeMissingKey')
				}
				break
			default:
				this.errors.reject('tempRecordNote.images.invalidAction',
					[actionMap.action] as Object[], "Invalid image action")
		}
	}
}
