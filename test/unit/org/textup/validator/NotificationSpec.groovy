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
class NotificationSpec extends CustomSpec {

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
		when: "an empty notification"
		Notification notif = new Notification()

		then: "invalid"
		notif.validate() == false

		when: "fill out info"
		notif.tokenId = 1L
		notif.owner = p1.owner
		notif.record = c1.record
		notif.contents = "hi"
		notif.outgoing = true

		then: "valid"
		notif.validate() == true
		notif.ownerType ==
			(p1.owner.type == PhoneOwnershipType.INDIVIDUAL ? "staff" : "team")
		notif.otherType == "contact"
		notif.otherId == c1.id.toString()
		notif.otherName == c1.nameOrNumber
		notif.id == notif.tokenId // alias tokenId as id for link generation in BaseController.getId
	}
}
