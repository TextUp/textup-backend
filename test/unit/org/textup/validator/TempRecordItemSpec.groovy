package org.textup.validator

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class TempRecordItemSpec extends CustomSpec {

	static doWithSpring = {
		resultFactory(ResultFactory)
	}
	def setup() {
		setupData()
	}
	def cleanup() {
		cleanupData()
	}

	void "test constraints"() {
		when: "empty obj"
		TempRecordItem temp1 = new TempRecordItem()

		then:
		temp1.validate() == false
		temp1.errors.getFieldErrorCount("note") == 1
		temp1.errors.getFieldErrorCount("info") == 1
		temp1.toNote().status == ResultStatus.UNPROCESSABLE_ENTITY

		when: "populated, but no info"
		temp1.note = new RecordNote()
		temp1.info = [:]

		then:
		temp1.validate() == false
		temp1.errors.getFieldErrorCount("note") == 0
		temp1.errors.getFieldErrorCount("info") == 1
		temp1.errors.getFieldError("info").codes.contains("noInfo")
		temp1.toNote().status == ResultStatus.UNPROCESSABLE_ENTITY

		when: "has some info"
		temp1.info.noteContents = "hi there!"

		then: "temp record note is valid, but record note itself is missing a record reference"
		temp1.validate() == true

		when: "note info is empty but the existing note already has info on it"
		temp1.info = [:]
		temp1.note.noteContents = "hello!"

		then: "is valid to enable updating other non-info fields (e.g., readOnly or isDeleted)t"
		temp1.validate() == true

		when: "add record reference to note"
		temp1.note.record = new Record().save(flush:true, failOnError: true)

		then: "can call `toNote` when the note is valid"
		temp1.toNote().status == ResultStatus.OK
	}

	void "test modify whenCreated for appropriate positioning in record"() {
		given:
		Record rec = new Record()
		assert rec.save(flush:true, failOnError:true)
		RecordNote note1 = new RecordNote(record: rec)
		assert note1.save(flush:true, failOnError:true)

		when: "missing after time"
		TempRecordItem temp1 = new TempRecordItem(note:note1, info:[noteContents:"hi"])
		assert temp1.validate()
		DateTime originalWhenCreated = note1.whenCreated
		RecordNote updatedNote = temp1.toNote().payload

		then: "keep existing whenCreated"
		updatedNote.whenCreated == originalWhenCreated

		when: "specify an after time in the future"
		temp1.after = DateTime.now().plusDays(3)
		updatedNote = temp1.toNote().payload

		then: "keep existing whenCreated"
		updatedNote.whenCreated == originalWhenCreated

		when: "specify an after time in the past with an EMPTY record to test bounds"
		Record newRec = new Record([:]) // create a fresh record
		newRec.save(flush:true, failOnError:true)

		temp1.info.forContact = c1.id // so that new note is associated with a fresh record
		c1.record = newRec
		c1.save(flush:true, failOnError:true)

		assert RecordItem.forRecordIdsWithOptions([newRec.id]).count() == 0
		RecordText text1 = newRec.storeOutgoingText("hello").payload,
			text2 = newRec.storeOutgoingText("hello").payload
		DateTime time1 = DateTime.now().minusMonths(2),
			time2 = DateTime.now().minusHours(5)
		text1.whenCreated = time1
		text2.whenCreated = time2
		[text1, text2]*.save(flush:true, failOnError:true)
		assert RecordItem.forRecordIdsWithOptions([newRec.id]).count() == 2

		temp1.after = time1
		updatedNote = temp1.toNote().payload
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
		given:
		Record rec = new Record()
		assert rec.save(flush:true, failOnError:true)
		RecordNote note1 = new RecordNote(record: rec)
		assert note1.save(flush:true, failOnError:true)
		TempRecordItem temp1 = new TempRecordItem(note: note1, info: [noteContents: "hi"])
		assert temp1.validate()
		int lBaseline = Location.count()

		when: "try to add media"
		String contentType = MediaType.IMAGE_PNG.mimeType
        String data = Base64.encodeBase64String("hello".getBytes(StandardCharsets.UTF_8))
        String checksum = DigestUtils.md5Hex(data)
		temp1.info.doMediaActions = [
			[
				action: Constants.MEDIA_ACTION_ADD,
				mimeType: contentType,
				data: data,
				checksum: checksum
			]
		]
		RecordNote updatedNote1 = temp1.toNote().payload

		then: "media actions are completed ignored and NOT validated here"
		temp1.validate() == true
		updatedNote1.media == null

		when: "update field with location"
		temp1.info.location = [
			address:"123 Main Street",
			lat: 8G,
			lon: -8G
		]
		updatedNote1 = temp1.toNote().payload
		updatedNote1.save(flush:true, failOnError:true)

		then: "created a new location"
		Location.count() == lBaseline + 1
		updatedNote1.location != null

		when: "delete and clear contents"
		temp1.info.isDeleted = true
		temp1.info.noteContents = ""

		updatedNote1 = temp1.toNote().payload
		updatedNote1.save(flush:true, failOnError:true)

		then:
		updatedNote1.isDeleted == true
		temp1.info.noteContents == ""

		when: "undelete"
		temp1.info.isDeleted = false

		updatedNote1 = temp1.toNote().payload
		updatedNote1.save(flush:true, failOnError:true)

		then:
		updatedNote1.isDeleted == false
		temp1.info.noteContents == ""
	}
}
