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
class TempRecordItemSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + constraints"() {
    	given:
    	String text = TestUtils.randString()
    	Location loc1 = TestUtils.buildLocation()
    	MediaInfo emptyMedia = new MediaInfo()
    	MediaInfo mInfo1 = new MediaInfo()
    	mInfo1.addToMediaElements(TestUtils.buildMediaElement())

    	when: "empty"
    	Result res = TempRecordItem.tryCreate(null, null, null)

    	then:
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages == ["atLeastOneRequired"]

    	when: "empty media"
    	res = TempRecordItem.tryCreate(null, emptyMedia, null)

    	then:
    	res.status == ResultStatus.UNPROCESSABLE_ENTITY
    	res.errorMessages == ["atLeastOneRequired"]

    	when: "text"
    	res = TempRecordItem.tryCreate(text, null, null)

    	then:
    	res.status == ResultStatus.CREATED
    	res.payload.text == text

    	when: "non-empty media"
    	res = TempRecordItem.tryCreate(null, mInfo1, null)

    	then:
    	res.status == ResultStatus.CREATED
    	res.payload.media == mInfo1

    	when: "location"
    	res = TempRecordItem.tryCreate(null, null, loc1)

    	then:
    	res.status == ResultStatus.CREATED
    	res.payload.location == loc1

    	when: "everything"
    	res = TempRecordItem.tryCreate(text, mInfo1, loc1)

    	then:
    	res.status == ResultStatus.CREATED
    }

    void "test determining if supports call"() {
    	given:
    	String text = TestUtils.randString()
    	Location loc1 = TestUtils.buildLocation()
    	MediaElement audioEl1 = TestUtils.buildMediaElement()
    	audioEl1.sendVersion.type = MediaType.AUDIO_MP3
    	MediaElement imageEl1 = TestUtils.buildMediaElement()
    	imageEl1.sendVersion.type = MediaType.IMAGE_JPEG

		MediaInfo audioMedia = TestUtils.buildMediaInfo(audioEl1)
		MediaInfo imageMedia = TestUtils.buildMediaInfo(imageEl1)

		when:
		TempRecordItem tempItem1 = TempRecordItem.tryCreate(null, imageMedia, null).payload

		then:
		tempItem1.supportsCall() == false

		when:
		tempItem1 = TempRecordItem.tryCreate(null, null, loc1).payload

		then:
		tempItem1.supportsCall() == false

		when:
		tempItem1 = TempRecordItem.tryCreate(null, audioMedia, null).payload

		then:
		tempItem1.supportsCall()

		when:
		tempItem1 = TempRecordItem.tryCreate(text, null, null).payload

		then:
		tempItem1.supportsCall()
    }
}
