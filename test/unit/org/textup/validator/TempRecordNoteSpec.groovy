package org.textup.validator

import com.amazonaws.HttpMethod
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.util.Holders
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.*
import org.textup.util.CustomSpec
import spock.lang.Shared
import spock.lang.Specification

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    RecordNote, RecordNoteRevision])
@TestMixin(HibernateTestMixin)
class TempRecordNoteSpec extends CustomSpec {

	RecordNote _note1
	int _maxNumImages = 2
	String _urlRoot = "http://www.example.com/?key="

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		setupData()
		TempRecordNote.metaClass.getResultFactory = { -> getResultFactory() }
		RecordNote.metaClass.constructor = { Map props ->
            RecordNote note1 = new RecordNote()
            note1.properties = props
            note1.grailsApplication = [getFlatConfig:{
                ['textup.maxNumImages':_maxNumImages]
            }] as GrailsApplication
            note1.storageService = [generateAuthLink:{
                String k, HttpMethod v, Map m=[:] ->
                new Result(success:true, payload:new URL("${_urlRoot}${k}"))
            }] as StorageService
            note1
        }

		Record rec = new Record([:])
		assert rec.validate()
		_note1 = new RecordNote(record:rec)
		assert _note1.validate()
		_note1.record.save(flush:true, failOnError:true)
		_note1.save(flush:true, failOnError:true)
	}
	def cleanup() {
		cleanupData()
	}

	void "test validation for new note"() {
		when: "missing for whom we are creating note"
		Map info = [contents:"hi"]
		TempRecordNote temp1 = new TempRecordNote(phone:p1, info:info)

		then:
		temp1.doValidate() == false
		temp1.errors.errorCount == 1
		temp1.errors.globalErrorCount == 1
		temp1.errors.globalErrors*.codes
			.flatten()
			.contains("tempRecordNote.missingInfoForNewNote")

		when: "try to create an empty new note"
		temp1 = new TempRecordNote(phone:c1.phone, contact:c1, info:[:])

		then: "empty info is NOT VALID for new notes"
		temp1.doValidate() == false
		temp1.errors.errorCount == 1
		temp1.errors.getFieldErrorCount("info") == 1

		when: "specify some info"
		temp1.info = [contents:"hi"]

		then: "is valid"
		temp1.doValidate() == true
		temp1.record == c1.record
		temp1.toNote().validate() == true
	}

	void "test validation for existing note"() {
		when: "missing target for the existing note we are trying to update"
		TempRecordNote temp1 = new TempRecordNote(info:[contents:"hi"])

		then: "implicit assumption that we are trying to create new note"
		temp1.doValidate() == false
		temp1.errors.errorCount == 1
		temp1.errors.globalErrorCount == 1
		temp1.errors.globalErrors*.codes
			.flatten()
			.contains("tempRecordNote.missingInfoForNewNote")

		when: "we provide empty info"
		temp1.note = _note1
		temp1.info = [:]

		then: "valid for empty info"
		temp1.doValidate() == true

		when: "provide some info"
		temp1.info = [contents:"hi"]

		then: "still valid"
		temp1.doValidate() == true
		temp1.toNote().validate() == true
	}

	void "validating image actions"() {
		when: "a valid temp note with no image actions on map"
		Map info = [contents:"hi"]
		TempRecordNote temp1 = new TempRecordNote(info:info, note:_note1)

		then: "short circuits with no errors"
		temp1.doValidate()

		when: "images actions is not a list"
		temp1.info = [doImageActions:[i:"am not a list"]]

		then:
		temp1.doValidate() == false
		temp1.errors.errorCount == 1
		temp1.errors.globalErrorCount == 1
		temp1.errors.globalErrors*.codes
			.flatten()
			.contains("tempRecordNote.images.notList")

		when: "an action is not a map, \
			an action trying to add not an image, \
			an action trying to upload too-large image, \
			an action trying to remove without key, \
			an action without of an invalid type"
		Integer maxBytes = Helpers.toInteger(
			Holders.flatConfig['textup.maxImageSizeInBytes'])
		temp1.info = [doImageActions:[
			["i am not a map"], // not a map
			[ // uploading not an image
				action:Constants.NOTE_IMAGE_ACTION_ADD,
				sizeInBytes:100,
				mimeType:"invalid mimetype"
			],
			[ // uploading too-large image
				action:Constants.NOTE_IMAGE_ACTION_ADD,
				sizeInBytes:maxBytes * 2,
				mimeType:"image/png"
			],
			[ // removing image with specifying an image key
				action:Constants.NOTE_IMAGE_ACTION_REMOVE,
			],
			[ // action of invalid type
				action:"i am an invalid action",
			]
		]]
		temp1.doValidate()
		Collection<String> errorCodes = temp1.errors.globalErrors*.codes.flatten()

		then:
		temp1.doValidate() == false
		temp1.errors.errorCount == 5
		temp1.errors.globalErrorCount == 5
		[
			"tempRecordNote.images.actionNotMap",
			"tempRecordNote.images.addNotImage",
			"tempRecordNote.images.addTooLarge",
			"tempRecordNote.images.removeMissingKey",
			"tempRecordNote.images.invalidAction"
		].every { it in errorCodes }

		when: "valid image actions"
		temp1.info = [doImageActions:[
			[ // valid add action
				action:Constants.NOTE_IMAGE_ACTION_ADD,
				sizeInBytes:maxBytes / 2,
				mimeType:"image/png"
			],
			[ // valid remove action
				action:Constants.NOTE_IMAGE_ACTION_REMOVE,
				key:"valid image key"
			],
		]]

		then:
		temp1.doValidate() == true
	}

	void "test modify whenCreated for appropriate positioning in record"() {
		when: "missing after time"
		TempRecordNote temp1 = new TempRecordNote(note:_note1, info:[contents:"hi"])
		assert temp1.doValidate()
		DateTime originalWhenCreated = _note1.whenCreated
		RecordNote updatedNote = temp1.toNote()

		then: "keep existing whenCreated"
		updatedNote.whenCreated == originalWhenCreated

		when: "specify an after time in the future"
		temp1.after = DateTime.now().plusDays(3)
		updatedNote = temp1.toNote()

		then: "keep existing whenCreated"
		updatedNote.whenCreated == originalWhenCreated

		when: "specify an after time in the past"
		RecordText text1 = _note1.record.addText(contents:"text1").payload,
			text2 = _note1.record.addText(contents:"text1").payload
		DateTime time1 = DateTime.now().minusDays(2),
			time2 = DateTime.now().minusHours(5)
		text1.whenCreated = time1
		text2.whenCreated = time2
		[text1, text2]*.save(flush:true, failOnError:true)

		temp1.after = time1
		updatedNote = temp1.toNote()

		then: "updated whenCreated to be after that time and before item \
			immediately after the specified time"
		updatedNote.whenCreated != originalWhenCreated
		updatedNote.whenCreated.isAfter(time1)
		updatedNote.whenCreated.isBefore(time2)
	}

	void "updating fields"() {
		given: "a valid temp note for an existing note with images and no location"
		assert _note1.location == null
		_note1.addImage("mimeType", 0L)

		TempRecordNote temp1 = new TempRecordNote(note:_note1, info:[contents:"hi"])
		assert temp1.doValidate()
		int lBaseline = Location.count()
		int iBaseline = _note1.imageKeys.size()
		assert iBaseline > 0
		String existingImageKey = _note1.imageKeys[0]

		when: "try to add images"
		temp1.info.doImageActions = [
			[
				action:Constants.NOTE_IMAGE_ACTION_ADD,
				mimeType:"image/png",
				sizeInBytes:100
			]
		]
		RecordNote updatedNote1 = temp1.toNote()

		then: "this method won't do that for you, must manually call iterator \
		over the images to add because you might need to create upload links"
		_note1.imageKeys.size() == iBaseline

		when: "try to remove images from an existing note with images"
		temp1.info.doImageActions = [
			[
				action:Constants.NOTE_IMAGE_ACTION_REMOVE,
				key:existingImageKey
			]
		]
		updatedNote1 = temp1.toNote()

		then:
		_note1.imageKeys.size() == iBaseline - 1

		when: "update field with location"
		temp1.info.location = [
			address:"123 Main Street",
			lat: 8G,
			lon: -8G
		]
		updatedNote1 = temp1.toNote()
		updatedNote1.save(flush:true, failOnError:true)

		then: "created a new location"
		Location.count() == lBaseline + 1
		updatedNote1.location != null
	}

	void "test iterating over validated image actions"() {
		given: "a valid temp note with image actions"
		Integer maxBytes = Helpers.toInteger(
			Holders.flatConfig['textup.maxImageSizeInBytes'])
		Map info = [
			contents:"hi",
			doImageActions:[
				[ // valid add action
					action:Constants.NOTE_IMAGE_ACTION_ADD,
					sizeInBytes:maxBytes / 2,
					mimeType:"image/png"
				],
				[ // valid remove action
					action:Constants.NOTE_IMAGE_ACTION_REMOVE,
					key:"valid image key"
				],
			]
		]
		TempRecordNote temp1 = new TempRecordNote(note:_note1, info:info)
		assert temp1.doValidate()

		expect:
		// for all images
		temp1.forEachImage({ Map m -> 1 }).payload.sum() == 2
		// for images to add
		temp1.forEachImageToAdd({ String type, Long num -> 1 }).payload.sum() == 1
		// for images to remove
		temp1.forEachImageToRemove({ String key -> 1 }).payload.sum() == 1
	}
}
