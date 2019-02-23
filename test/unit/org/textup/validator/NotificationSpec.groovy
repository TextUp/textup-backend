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
class NotificationSpec extends Specification {

	static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

	void "test creation + constraints"() {
		given:
		Phone p1 = TestUtils.buildActiveStaffPhone()

		when:
		Result res = Notification.tryCreate(null)

		then:
		res.status == ResultStatus.UNPROCESSABLE_ENTITY

		when:
		res = Notification.tryCreate(p1)

		then:
		res.status == ResultStatus.CREATED
		res.payload.mutablePhone == p1
		res.payload.details.isEmpty()
	}

	void "test adding details"() {
		given:
		Phone p1 = TestUtils.buildActiveStaffPhone()
		IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
		GroupPhoneRecord foreign = TestUtils.buildGroupPhoneRecord()

		NotificationDetail nd1 = NotificationDetail.tryCreate(ipr1.toWrapper()).payload
		NotificationDetail mismatched = NotificationDetail.tryCreate(foreign.toWrapper()).payload

		when:
		Notification notif1 = Notification.tryCreate(p1).payload
		notif1.addDetail(mismatched)

		then:
		notif1.validate() == false
		notif1.errors.getFieldErrorCount("wrapperToDetails") > 0

		when:
		notif1 = Notification.tryCreate(p1).payload
		notif1.addDetail(nd1)

		then: "duplicates ignored"
		notif1.validate()
		notif1.wrapperToDetails.size() == 1
		notif1.details.size() == 1
		notif1.details[0] == nd1
	}

	void "test adding multiple details for the same `PhoneRecordWrapper`"() {
		given:
		IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
		NotificationDetail nd1 = NotificationDetail.tryCreate(ipr1.toWrapper()).payload
		nd1.items << TestUtils.buildRecordItem(ipr1.record)
		NotificationDetail nd2 = NotificationDetail.tryCreate(ipr1.toWrapper()).payload
		nd2.items << TestUtils.buildRecordItem(ipr1.record)

		Notification notif1 = Notification.tryCreate(GroovyMock(Phone)).payload

		when:
		notif1.addDetail(nd1)

		then:
		notif1.wrapperToDetails.size() == 1
		nd1.items.size() == 1

		when:
		notif1.addDetail(nd2)

		then: "items from duplicative NotificationDetail added to already-added NotificationDetail"
		notif1.wrapperToDetails.size() == 1
		nd1 in notif1.details
		!(nd2 in notif1.details)
		nd1.items.size() == 2
	}

	void "test getting all items and item ids"() {
		given:
		Phone p1 = TestUtils.buildActiveStaffPhone()
		IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord(p1)
		GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord(p1)

		NotificationDetail nd1 = NotificationDetail.tryCreate(ipr1.toWrapper()).payload
		NotificationDetail nd2 = NotificationDetail.tryCreate(gpr1.toWrapper()).payload

		RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
		nd1.items << rItem1
		RecordItem rItem2 = TestUtils.buildRecordItem(gpr1.record)
		nd2.items << rItem2

		when:
		Notification notif1 = Notification.tryCreate(p1).payload
		notif1.addDetail(nd1)
		notif1.addDetail(nd2)

		then:
		notif1.validate()
		notif1.items.size() == 2
		rItem1 in notif1.items
		rItem2 in notif1.items
		notif1.itemIds == notif1.items*.id
	}

	void "test building can notify policies"() {
		given:
		Phone mockPhone = GroovyMock()
		PhoneOwnership mockOwner = GroovyMock()
		OwnerPolicy mockOwnerPolicy = GroovyMock()
		Notification notif1 = Notification.tryCreate(mockPhone).payload
		NotificationFrequency freq1 = NotificationFrequency.values()[0]

		when:
		Collection policies = notif1.buildCanNotifyReadOnlyPolicies(freq1)

		then:
		1 * mockPhone.owner >> mockOwner
		1 * mockOwner.buildActiveReadOnlyPoliciesForFrequency(freq1) >> [mockOwnerPolicy]
		1 * mockOwnerPolicy.canNotifyForAny(_ as Collection) >> true
		policies == [mockOwnerPolicy]

		when:
		policies = notif1.buildCanNotifyReadOnlyPolicies(freq1)

		then:
		1 * mockPhone.owner >> mockOwner
		1 * mockOwner.buildActiveReadOnlyPoliciesForFrequency(freq1) >> [mockOwnerPolicy]
		1 * mockOwnerPolicy.canNotifyForAny(_ as Collection) >> false
		policies.isEmpty()
	}

	void "test determining if can notify any for a given notification frequency"() {
		given:
		Notification notif1 = Notification.tryCreate(GroovyMock(Phone)).payload

		when:
		MockedMethod buildCanNotifyReadOnlyPolicies = MockedMethod.create(notif1, "buildCanNotifyReadOnlyPolicies") { [] }

		boolean retVal = notif1.canNotifyAny(NotificationFrequency.QUARTER_HOUR)

		then:
		buildCanNotifyReadOnlyPolicies.callCount == 1
		buildCanNotifyReadOnlyPolicies.allArgs[0] == [NotificationFrequency.QUARTER_HOUR]
		retVal == false

		when:
		buildCanNotifyReadOnlyPolicies.restore()
		buildCanNotifyReadOnlyPolicies = MockedMethod.create(notif1, "buildCanNotifyReadOnlyPolicies") { [GroovyMock(OwnerPolicy)] }

		retVal = notif1.canNotifyAny(NotificationFrequency.HOUR)

		then:
		buildCanNotifyReadOnlyPolicies.callCount == 1
		buildCanNotifyReadOnlyPolicies.allArgs[0] == [NotificationFrequency.HOUR]
		retVal == true

		cleanup:
		buildCanNotifyReadOnlyPolicies.restore()
	}

	void "test building allowed items given owner policy"() {
		given:
		OwnerPolicy mockOwnerPolicy = GroovyMock()
		NotificationDetail mockDetail = GroovyMock()
		RecordItem mockItem = GroovyMock()
		Notification notif1 = Notification.tryCreate(GroovyMock(Phone)).payload
		notif1.addDetail(mockDetail)

		when:
		Collection allowedItems = notif1.buildAllowedItemsForOwnerPolicy(null)

		then:
		1 * mockDetail.buildAllowedItemsForOwnerPolicy(null) >> []
		allowedItems.isEmpty()

		when:
		allowedItems = notif1.buildAllowedItemsForOwnerPolicy(mockOwnerPolicy)

		then:
		1 * mockDetail.buildAllowedItemsForOwnerPolicy(mockOwnerPolicy) >> [mockItem]
		allowedItems == [mockItem]
	}

	void "test counting items and voicemail"() {
		given:
		OwnerPolicy mockOwnerPolicy = GroovyMock()
		NotificationDetail mockDetail = GroovyMock()
		Notification notif1 = Notification.tryCreate(GroovyMock(Phone)).payload
		notif1.addDetail(mockDetail)

		int randNum1 = TestUtils.randIntegerUpTo(88)
		int randNum2 = TestUtils.randIntegerUpTo(88)

		when:
		int sum = notif1.countItems(true, mockOwnerPolicy, RecordNote)

		then:
		1 * mockDetail.countItemsForOutgoingAndOptions(true, mockOwnerPolicy, RecordNote) >> randNum1
		sum == randNum1

		when:
		sum = notif1.countVoicemails(mockOwnerPolicy)

		then:
		1 * mockDetail.countVoicemails(mockOwnerPolicy) >> randNum2
		sum == randNum2
	}

	void "test getting wrappers with outgoing record items"() {
		given:
		NotificationDetail mockDetail = GroovyMock()
		Notification notif1 = Notification.tryCreate(GroovyMock(Phone)).payload
		notif1.addDetail(mockDetail)

		when:
		Collection wraps = notif1.getWrappersForOutgoing(true)

		then:
		1 * mockDetail.countItemsForOutgoingAndOptions(true) >> 88
		wraps.size() == 1 // can't mock getter for wrapper because it is a final field

		when:
		wraps = notif1.getWrappersForOutgoing(false)

		then:
		1 * mockDetail.countItemsForOutgoingAndOptions(false) >> 0
		wraps.isEmpty()
	}
}
