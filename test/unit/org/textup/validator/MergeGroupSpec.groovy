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
class MergeGroupSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

	void "test creation + constraints"() {
		given:
		IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
		IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
		MergeGroupItem mItem1 = MergeGroupItem.create(TestUtils.randPhoneNumber(), [ipr1.id])
		MergeGroupItem mItem2 = MergeGroupItem.create(TestUtils.randPhoneNumber(), [ipr1.id])

		when:
		Result res = MergeGroup.tryCreate(null, null)

		then:
		res.status == ResultStatus.UNPROCESSABLE_ENTITY

		when:
		res = MergeGroup.tryCreate(-88L, [mItem1])

		then:
		res.status == ResultStatus.UNPROCESSABLE_ENTITY
		res.errorMessages == ["doesNotExist"]

		when:
		res = MergeGroup.tryCreate(ipr1.id, [mItem1])

		then:
		res.status == ResultStatus.UNPROCESSABLE_ENTITY
		res.errorMessages == ["cannotMergeWithSelf"]

		when:
		res = MergeGroup.tryCreate(ipr2.id, [mItem1, mItem2])

		then:
		res.status == ResultStatus.UNPROCESSABLE_ENTITY
		res.errorMessages == ["overlappingIds"]

		when:
		res = MergeGroup.tryCreate(ipr2.id, [mItem1])

		then:
		res.status == ResultStatus.CREATED
		res.payload.targetId == ipr2.id
		res.payload.possibleMerges.size() == 1
		mItem1 in res.payload.possibleMerges
		res.payload.buildTarget() == ipr2
	}
}
