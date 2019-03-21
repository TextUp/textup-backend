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
class NotificationDetailSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test creation + constraints"() {
        given:
        RecordItem rItem1 = TestUtils.buildRecordItem()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)
        spr1.permission = SharePermission.NONE

        when:
        Result res = NotificationDetail.tryCreate(null)

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when:
        res = NotificationDetail.tryCreate(spr1.toWrapper())

        then:
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages == ["insufficientPermission"]

        when:
        res = NotificationDetail.tryCreate(ipr1.toWrapper())

        then:
        res.status == ResultStatus.CREATED
        res.payload.wrapper == ipr1.toWrapper()
        res.payload.items.isEmpty() == true

        when:
        res.payload.items << rItem1

        then:
        res.payload.validate() == false
        res.payload.errors.getFieldErrorCount("items") > 0
    }

    void "test checking and building all allowed items given an owner policy"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        RecordItem rItem1 = TestUtils.buildRecordItem(ipr1.record)
        RecordItem rItem2 = TestUtils.buildRecordItem()
        PhoneRecordWrapper w1 = ipr1.toWrapper()
        OwnerPolicy op1 = GroovyStub() {
            isAllowed(rItem1.record.id) >> true
            isAllowed(rItem2.record.id) >> false
        }

        when:
        NotificationDetail nd1 = NotificationDetail.tryCreate(w1).payload

        then:
        nd1.buildAllowedItemsForOwnerPolicy(null).isEmpty()
        nd1.buildAllowedItemsForOwnerPolicy(op1).isEmpty()
        nd1.anyAllowedItemsForOwnerPolicy(null) == false
        nd1.anyAllowedItemsForOwnerPolicy(op1) == false

        when:
        nd1.items << rItem1
        nd1.items << null
        nd1.items << rItem2
        Collection rItems = nd1.buildAllowedItemsForOwnerPolicy(op1)

        then:
        rItems.size() == 1
        rItem1 in rItems
        nd1.buildAllowedItemsForOwnerPolicy(null).isEmpty()
        nd1.anyAllowedItemsForOwnerPolicy(null) == false
        nd1.anyAllowedItemsForOwnerPolicy(op1)
    }

    void "test counting outgoing items for various options"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        OwnerPolicy op1 = GroovyMock() { asBoolean() >> true }
        NotificationDetail nd1 = NotificationDetail.tryCreate(ipr1.toWrapper()).payload
        nd1.items << new RecordText(record: ipr1.record, outgoing: true)
        nd1.items << new RecordText(record: ipr1.record, outgoing: false)
        nd1.items << new RecordCall(record: ipr1.record, outgoing: false)

        when:
        int num = nd1.countItemsForOutgoingAndOptions(false)

        then:
        num == 2

        when:
        num = nd1.countItemsForOutgoingAndOptions(false, op1)

        then:
        (1.._) * op1.isAllowed(ipr1.record.id) >> false
        num == 0

        when:
        num = nd1.countItemsForOutgoingAndOptions(false, op1)

        then:
        (1.._) * op1.isAllowed(ipr1.record.id) >> true
        num == 2

        when:
        num = nd1.countItemsForOutgoingAndOptions(true, null, RecordText)

        then:
        num == 1

        when:
        num = nd1.countItemsForOutgoingAndOptions(false, op1, RecordText)

        then:
        (1.._) * op1.isAllowed(ipr1.record.id) >> true
        num == 1
    }

    void "test counting voicemails"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        OwnerPolicy op1 = GroovyMock()
        NotificationDetail nd1 = NotificationDetail.tryCreate(ipr1.toWrapper()).payload
        nd1.items << new RecordText(record: ipr1.record, outgoing: true)
        nd1.items << new RecordCall(record: ipr1.record, outgoing: false, hasAwayMessage: true, voicemailInSeconds: 88)
        nd1.items << new RecordCall(record: ipr1.record, outgoing: true)

        when:
        int num = nd1.countVoicemails(null)

        then:
        num == 0

        when:
        num = nd1.countVoicemails(op1)

        then:
        (1.._) * op1.isAllowed(ipr1.record.id) >> false
        num == 0

        when:
        num = nd1.countVoicemails(op1)

        then:
        (1.._) * op1.isAllowed(ipr1.record.id) >> true
        num == 1
    }
}
