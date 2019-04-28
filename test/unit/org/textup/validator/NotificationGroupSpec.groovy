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
class NotificationGroupSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Notification notif1 = Notification.tryCreate(p1).payload
        Notification notif2 = Notification.tryCreate(p1).payload

        NotificationDetail detail1 = NotificationDetail
            .tryCreate(TestUtils.buildIndPhoneRecord(p1).toWrapper())
            .payload
        notif1.addDetail(detail1)
        NotificationDetail detail2 = NotificationDetail
            .tryCreate(TestUtils.buildGroupPhoneRecord(p1).toWrapper())
            .payload
        notif2.addDetail(detail2)

        when:
        Result res = NotificationGroup.tryCreate(null)

        then:
        res.status == ResultStatus.CREATED
        res.payload.notifications.isEmpty()

        when:
        res = NotificationGroup.tryCreate([notif1,  notif2])

        then:
        res.status == ResultStatus.CREATED
        res.payload.notifications.size() == 1
        res.payload.notifications[0].mutablePhone == p1
        res.payload.notifications[0].details.size() == 2
        detail1 in res.payload.notifications[0].details
        detail2 in res.payload.notifications[0].details
    }

    void "test determining if can notify any owner policies of any frequency"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Notification notif1 = Notification.tryCreate(p1).payload
        NotificationGroup notifGroup1 = NotificationGroup.tryCreate([notif1]).payload

        MockedMethod canNotifyAny = MockedMethod.create(notifGroup1.notifications[0], "canNotifyAny") { true }

        when:
        boolean retBool = notifGroup1.canNotifyAnyAllFrequencies()

        then:
        canNotifyAny.callCount == 1
        canNotifyAny.allArgs[0] == [null]
        retBool == true

        cleanup:
        canNotifyAny?.restore()
    }

    void "test building can notify policies"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Notification notif1 = Notification.tryCreate(p1).payload
        NotificationGroup notifGroup1 = NotificationGroup.tryCreate([notif1]).payload

        OwnerPolicy mockOwnerPolicy = GroovyMock()
        MockedMethod buildCanNotifyReadOnlyPolicies = MockedMethod.create(notifGroup1.notifications[0], "buildCanNotifyReadOnlyPolicies") {
                [mockOwnerPolicy]
            }

        when:
        Collection policies = notifGroup1.buildCanNotifyReadOnlyPolicies(null)

        then:
        buildCanNotifyReadOnlyPolicies.callCount == 1
        buildCanNotifyReadOnlyPolicies.allArgs[0] == [null]
        policies.size() == 1
        policies[0] == mockOwnerPolicy

        cleanup:
        buildCanNotifyReadOnlyPolicies?.restore()
    }

    void "test getting all policies of any frequency"() {
        given:
        Notification notif1 = TestUtils.buildNotification()
        NotificationGroup notifGroup1 = NotificationGroup.tryCreate([notif1]).payload

        ReadOnlyOwnerPolicy rop1 = GroovyMock()
        MockedMethod buildCanNotifyReadOnlyPolicies = MockedMethod.create(notifGroup1, "buildCanNotifyReadOnlyPolicies") {
            [rop1]
        }

        when:
        def retVal = notifGroup1.buildCanNotifyReadOnlyPoliciesAllFrequencies()

        then:
        buildCanNotifyReadOnlyPolicies.latestArgs == [null]
        retVal == [rop1]

        cleanup:
        buildCanNotifyReadOnlyPolicies?.restore()
    }

    void "test iterating through all owner policy + notification pairs"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Notification notif1 = Notification.tryCreate(p1).payload
        NotificationGroup notifGroup1 = NotificationGroup.tryCreate([notif1]).payload

        OwnerPolicy mockOwnerPolicy = GroovyMock()
        MockedMethod buildCanNotifyReadOnlyPolicies = MockedMethod.create(notifGroup1.notifications[0], "buildCanNotifyReadOnlyPolicies") { [mockOwnerPolicy] }

        Collection args = []
        Closure doAction = { op1, notif2 -> args << [op1, notif2] }

        when:
        notifGroup1.eachNotification(null, doAction)

        then:
        buildCanNotifyReadOnlyPolicies.callCount == 1
        buildCanNotifyReadOnlyPolicies.allArgs[0] == [null]
        args.size() == 1
        args[0] == [mockOwnerPolicy, notifGroup1.notifications[0]]

        cleanup:
        buildCanNotifyReadOnlyPolicies?.restore()
    }

    void "test iterating through all items contained in this notification"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Phone p2 = TestUtils.buildStaffPhone()
        Notification notif1 = Notification.tryCreate(p1).payload
        Notification notif2 = Notification.tryCreate(p2).payload
        NotificationGroup notifGroup1 = NotificationGroup.tryCreate([notif1, notif2]).payload
        RecordItem rItem1 = TestUtils.buildRecordItem()
        RecordItem rItem2 = TestUtils.buildRecordItem()

        MockedMethod getItems1 = MockedMethod.create(notifGroup1.notifications[0], "getItems") { [rItem1] }
        MockedMethod getItems2 = MockedMethod.create(notifGroup1.notifications[1], "getItems") { [rItem2] }

        Collection rItems = []
        Closure doAction = { arg1 -> rItems << arg1 }

        when:
        notifGroup1.eachItem(doAction)

        then:
        rItems.size() == 2
        rItem1 in rItems
        rItem2 in rItems

        cleanup:
        getItems1?.restore()
        getItems2?.restore()
    }

    void "test getting number of owner policies to be notified for a given item and frequency"() {
        given:
        Phone p1 = TestUtils.buildStaffPhone()
        Notification notif1 = Notification.tryCreate(p1).payload
        NotificationGroup notifGroup1 = NotificationGroup.tryCreate([notif1]).payload
        int randNum = TestUtils.randIntegerUpTo(88, true)

        OwnerPolicy mockOwnerPolicy = GroovyMock()
        MockedMethod getNumNotifiedForItem = MockedMethod.create(notifGroup1.notifications[0], "getNumNotifiedForItem") { randNum }

        NotificationFrequency freq1 = NotificationFrequency.values()[0]
        RecordItem rItem1 = GroovyMock()

        when:
        int retInt = notifGroup1.getNumNotifiedForItem(rItem1, freq1)

        then:
        getNumNotifiedForItem.callCount == 1
        getNumNotifiedForItem.allArgs[0] == [rItem1, freq1]
        retInt == randNum

        cleanup:
        getNumNotifiedForItem?.restore()
    }
}
