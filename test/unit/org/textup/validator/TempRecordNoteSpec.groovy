package org.textup.validator

import com.amazonaws.services.s3.model.PutObjectResult
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.util.Holders
import java.nio.charset.StandardCharsets
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.textup.*
import org.textup.type.SharePermission
import org.textup.util.CustomSpec
import org.textup.validator.UploadItem
import spock.lang.Shared
import spock.lang.Specification

@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization,
    Schedule, Location, WeeklySchedule, PhoneOwnership, Role, StaffRole,
    RecordNote, RecordNoteRevision, NotificationPolicy])
@TestMixin(HibernateTestMixin)
class TempRecordNoteSpec extends CustomSpec {

	RecordNote _note1
	int _maxNumImages = 2
	String _urlRoot = "http://www.example.com/?key="
	String _eTag = UUID.randomUUID().toString()

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		setupData()
		TempRecordNote.metaClass.getResultFactory = { -> getResultFactory() }
		RecordNote.metaClass.constructor = { Map props ->
            RecordNote note1 = new RecordNote()
            note1.properties = props
            note1.grailsApplication = [
            	getFlatConfig:{ ['textup.maxNumImages':_maxNumImages] }
            ] as GrailsApplication
            note1.storageService = [
            	generateAuthLink:{ String k ->
	                new Result(success:true, payload:new URL("${_urlRoot}${k}"))
	            },
	            upload: { String objectKey, UploadItem uItem ->
	            	new Result(success:true, payload:[getETag: { -> _eTag }] as PutObjectResult)
	            }
            ] as StorageService
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
		Map info = [noteContents:"hi"]
		TempRecordNote temp1 = new TempRecordNote(phone:p1, info:info)

		then:
		temp1.validate() == false
		temp1.errors.errorCount == 1
		temp1.errors.globalErrorCount == 0 // no longer a global error
		temp1.errors.fieldErrorCount == 1
		temp1.errors.getFieldError("note").code == "missingInfoForNewNote"

		when: "try to create an empty new note"
		temp1 = new TempRecordNote(phone:c1.phone, contact:c1, info:[:])

		then: "empty info is NOT VALID for new notes"
		temp1.validate() == false
		temp1.errors.errorCount == 1
		temp1.errors.getFieldErrorCount("info") == 1

		when: "specify some info"
		temp1.info = info

		then: "is valid"
		temp1.validate() == true
		temp1.record == c1.record
		temp1.toNote().validate() == true

		when: "change target from contact to a view-only shared contact"
		sc1.permission = SharePermission.VIEW
		sc1.save(flush:true, failOnError:true)

		temp1.contact = null
		temp1.sharedContact = sc1
		temp1.phone = sc1.sharedWith

		then: "creating notes is forbidden for view-only shared contacts"
		temp1.validate() == false
		temp1.errors.errorCount == 1
		temp1.errors.getFieldErrorCount("sharedContact") == 1

		when: "change to collaborate shared contact"
		sc1.permission = SharePermission.DELEGATE
		sc1.save(flush:true, failOnError:true)

		then: "is valid"
		temp1.validate() == true
		temp1.record == c1.record
		temp1.toNote().validate() == true
	}

	void "test validation for existing note"() {
		when: "missing target for the existing note we are trying to update"
		TempRecordNote temp1 = new TempRecordNote(info:[noteContents:"hi"])

		then: "implicit assumption that we are trying to create new note"
		temp1.validate() == false
		temp1.errors.errorCount == 1
		temp1.errors.globalErrorCount == 0 // no longer a global error
		temp1.errors.fieldErrorCount == 1
		temp1.errors.getFieldError("note").code == "missingInfoForNewNote"

		when: "we provide empty info"
		temp1.note = _note1
		temp1.info = [:]

		then: "valid for empty info"
		temp1.validate() == true

		when: "provide some info"
		temp1.info = [noteContents:"hi"]

		then: "still valid"
		temp1.validate() == true
		temp1.toNote().validate() == true
	}

	void "test modify whenCreated for appropriate positioning in record"() {
		when: "missing after time"
		TempRecordNote temp1 = new TempRecordNote(note:_note1, info:[noteContents:"hi"])
		assert temp1.validate()
		DateTime originalWhenCreated = _note1.whenCreated
		RecordNote updatedNote = temp1.toNote()

		then: "keep existing whenCreated"
		updatedNote.whenCreated == originalWhenCreated

		when: "specify an after time in the future"
		temp1.after = DateTime.now().plusDays(3)
		updatedNote = temp1.toNote()

		then: "keep existing whenCreated"
		updatedNote.whenCreated == originalWhenCreated

		when: "specify an after time in the past with an EMPTY record to test bounds"
		Record newRec = new Record([:]) // create a fresh record
		newRec.save(flush:true, failOnError:true)

		temp1.info.forContact = c1.id // so that new note is associated with a fresh record
		c1.record = newRec
		c1.save(flush:true, failOnError:true)

		assert newRec.countItems() == 0
		RecordText text1 = newRec.addText(contents:"text1").payload,
			text2 = newRec.addText(contents:"text1").payload
		DateTime time1 = DateTime.now().minusMonths(2),
			time2 = DateTime.now().minusHours(5)
		text1.whenCreated = time1
		text2.whenCreated = time2
		[text1, text2]*.save(flush:true, failOnError:true)
		assert newRec.countItems() == 2

		temp1.after = time1
		updatedNote = temp1.toNote()
		int whenCreatedDifference = updatedNote.whenCreated.millis - time1.millis

		then: "updated whenCreated to be after that time and before item \
			immediately after the specified time WITHIN specified bounds"
		updatedNote.whenCreated != originalWhenCreated
		updatedNote.whenCreated.isAfter(time1)
		updatedNote.whenCreated.isBefore(time2)
		whenCreatedDifference > Constants.MIN_NOTE_SPACING_MILLIS
		whenCreatedDifference == Constants.MAX_NOTE_SPACING_MILLIS
	}

	void "updating fields"() {
		given: "a valid temp note for an existing note with images and no location"
		String contentType = "image/png"
        String data = Base64.encodeBase64String("hello".getBytes(StandardCharsets.UTF_8))
        String checksum = DigestUtils.md5Hex(data)
		assert _note1.location == null
		UploadItem uItem = new UploadItem(mimeType:contentType, data:data, checksum:checksum)
		assert uItem.validate() == true
		assert _note1.addImage(uItem).payload.getETag() == _eTag

		TempRecordNote temp1 = new TempRecordNote(note:_note1, info:[noteContents:"hi"])
		assert temp1.validate()
		int lBaseline = Location.count()
		int iBaseline = _note1.imageKeys.size()
		assert iBaseline > 0
		String existingImageKey = _note1.imageKeys[0]

		when: "try to add images"
		temp1.info.doImageActions = [
			[
				action:Constants.NOTE_IMAGE_ACTION_ADD,
				mimeType:contentType,
				data:data,
				checksum:checksum
			]
		]
		int originalNumImages = _note1.imageKeys.size()
		RecordNote updatedNote1 = temp1.toNote()

		then: "valid, but tempRecordNote does not handle image actions"
		temp1.validate() == true
		updatedNote1.imageKeys.size() == originalNumImages

		when: "try to remove images from an existing note with images"
		temp1.info.doImageActions = [
			[
				action:Constants.NOTE_IMAGE_ACTION_REMOVE,
				key:existingImageKey
			]
		]
		updatedNote1 = temp1.toNote()

		then: "valid, but tempRecordNote does not handle image actions"
		temp1.validate() == true
		_note1.imageKeys.size() == iBaseline
		updatedNote1.imageKeys.size() == originalNumImages

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

		when: "delete and clear contents"
		temp1.info.isDeleted = true
		temp1.info.noteContents = ""

		updatedNote1 = temp1.toNote()
		updatedNote1.save(flush:true, failOnError:true)

		then:
		updatedNote1.isDeleted == true
		temp1.info.noteContents == ""

		when: "undelete"
		temp1.info.isDeleted = false

		updatedNote1 = temp1.toNote()
		updatedNote1.save(flush:true, failOnError:true)

		then:
		updatedNote1.isDeleted == false
		temp1.info.noteContents == ""
	}
}
