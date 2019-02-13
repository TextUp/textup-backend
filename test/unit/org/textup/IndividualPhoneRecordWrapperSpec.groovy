package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import org.joda.time.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class IndividualPhoneRecordWrapperSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test wrapped null"() {
        when:
        IndividualPhoneRecordWrapper w1 = new IndividualPhoneRecordWrapper(null, null)

        then:
        w1.save() == null
        w1.tryUnwrap().status == ResultStatus.FORBIDDEN
        w1.tryDelete().status == ResultStatus.FORBIDDEN
        w1.isOverridden() == false
        w1.getWrappedClass() == null
        w1.tryGetIsDeleted().status == ResultStatus.FORBIDDEN
        w1.tryGetNote().status == ResultStatus.FORBIDDEN
        w1.tryGetSortedNumbers().status == ResultStatus.FORBIDDEN
        w1.tryGetOriginalPhone().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyOriginalPhone().status == ResultStatus.FORBIDDEN
        w1.tryGetSecureName().status == ResultStatus.FORBIDDEN
        w1.tryGetPublicName().status == ResultStatus.FORBIDDEN
        w1.trySetNameIfPresent(TestUtils.randString()).status == ResultStatus.FORBIDDEN
        w1.trySetNoteIfPresent(TestUtils.randString()).status == ResultStatus.FORBIDDEN
        w1.tryMergeNumber(null, 0).status == ResultStatus.FORBIDDEN
        w1.tryDeleteNumber(null).status == ResultStatus.FORBIDDEN
    }

    void "test wrapped owner"() {
        given:
        String name = TestUtils.randString()
        String note = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        MockedMethod tryCancelFutureMessages = TestUtils.mock(ipr1, "tryCancelFutureMessages") {
            Result.void()
        }

        when:
        IndividualPhoneRecordWrapper w1 = new IndividualPhoneRecordWrapper(ipr1, ipr1.toPermissions())

        then:
        w1.save() == w1
        w1.tryUnwrap().payload == ipr1
        w1.tryDelete().status == ResultStatus.NO_CONTENT
        w1.isOverridden() == false
        w1.getWrappedClass() == ipr1.class
        w1.tryGetMutablePhone().payload == ipr1.phone
        w1.tryGetReadOnlyMutablePhone().payload == ipr1.phone
        w1.tryGetOriginalPhone().payload == ipr1.phone
        w1.tryGetReadOnlyOriginalPhone().payload == ipr1.phone

        and:
        tryCancelFutureMessages.callCount == 1
        w1.tryGetIsDeleted().payload == true

        when:
        Result res = w1.trySetNameIfPresent(name)

        then:
        res.status == ResultStatus.NO_CONTENT
        w1.tryGetSecureName().payload == name
        w1.tryGetPublicName().payload instanceof String
        ipr1.name == name

        when:
        res = w1.trySetNoteIfPresent(note)

        then:
        res.status == ResultStatus.NO_CONTENT
        w1.tryGetNote().payload == note
        ipr1.note == note

        when:
        // in unit tests seem to not be able to add new number with same preference, ensure that this
        // is not the case in an integration test
        res = w1.tryMergeNumber(pNum1, -1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.number == pNum1.number
        res.payload in w1.tryGetSortedNumbers().payload
        pNum1.number in w1.tryGetSortedNumbers().payload*.number

        when:
        res = w1.tryDeleteNumber(pNum1)

        then:
        res.status == ResultStatus.NO_CONTENT
        (pNum1.number in w1.tryGetSortedNumbers().payload*.number) == false

        cleanup:
        tryCancelFutureMessages.restore()
    }

    void "test wrapped can modify"() {
        given:
        String name = TestUtils.randString()
        String note = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)
        spr1.permission = SharePermission.DELEGATE

        when:
        IndividualPhoneRecordWrapper w1 = new IndividualPhoneRecordWrapper(ipr1,
            spr1.toPermissions(), spr1)

        then:
        w1.save() == w1
        w1.tryUnwrap().status == ResultStatus.FORBIDDEN
        w1.tryDelete().status == ResultStatus.FORBIDDEN
        w1.isOverridden() == true
        w1.getWrappedClass() == ipr1.class
        w1.tryGetMutablePhone().payload == spr1.phone
        w1.tryGetReadOnlyMutablePhone().payload == spr1.phone
        w1.tryGetOriginalPhone().payload == ipr1.phone
        w1.tryGetReadOnlyOriginalPhone().payload == ipr1.phone
        w1.tryGetIsDeleted().payload == false

        when:
        Result res = w1.trySetNameIfPresent(name)

        then:
        res.status == ResultStatus.NO_CONTENT
        w1.tryGetSecureName().payload == name
        w1.tryGetPublicName().payload instanceof String
        ipr1.name == name

        when:
        res = w1.trySetNoteIfPresent(note)

        then:
        res.status == ResultStatus.NO_CONTENT
        w1.tryGetNote().payload == note
        ipr1.note == note

        when:
        // in unit tests seem to not be able to add new number with same preference, ensure that this
        // is not the case in an integration test
        res = w1.tryMergeNumber(pNum1, -1)

        then:
        res.status == ResultStatus.CREATED
        res.payload.number == pNum1.number
        res.payload in w1.tryGetSortedNumbers().payload
        pNum1.number in w1.tryGetSortedNumbers().payload*.number

        when:
        res = w1.tryDeleteNumber(pNum1)

        then:
        res.status == ResultStatus.NO_CONTENT
        (pNum1.number in w1.tryGetSortedNumbers().payload*.number) == false
    }

    void "test wrapped can view"() {
        given:
        String name = TestUtils.randString()
        String note = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)
        spr1.permission = SharePermission.VIEW

        when:
        IndividualPhoneRecordWrapper w1 = new IndividualPhoneRecordWrapper(ipr1,
            spr1.toPermissions(), spr1)

        then:
        w1.save() == w1
        w1.tryUnwrap().status == ResultStatus.FORBIDDEN
        w1.tryDelete().status == ResultStatus.FORBIDDEN
        w1.isOverridden() == true
        w1.getWrappedClass() == ipr1.class
        w1.tryGetMutablePhone().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyMutablePhone().payload == spr1.phone
        w1.tryGetOriginalPhone().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyOriginalPhone().payload == ipr1.phone
        w1.tryGetIsDeleted().payload == false
        w1.trySetNameIfPresent(name).status == ResultStatus.FORBIDDEN
        w1.trySetNoteIfPresent(note).status == ResultStatus.FORBIDDEN
        w1.tryMergeNumber(pNum1, 0).status == ResultStatus.FORBIDDEN
        w1.tryDeleteNumber(pNum1).status == ResultStatus.FORBIDDEN
    }

    void "test wrapped expired or no permissions"() {
        given:
        String name = TestUtils.randString()
        String note = TestUtils.randString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord(ipr1)
        spr1.permission = SharePermission.NONE

        when:
        IndividualPhoneRecordWrapper w1 = new IndividualPhoneRecordWrapper(ipr1,
            spr1.toPermissions(), spr1)

        then:
        w1.save() == w1
        w1.tryUnwrap().status == ResultStatus.FORBIDDEN
        w1.tryDelete().status == ResultStatus.FORBIDDEN
        w1.isOverridden() == true
        w1.getWrappedClass() == ipr1.class
        w1.tryGetMutablePhone().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyMutablePhone().status == ResultStatus.FORBIDDEN
        w1.tryGetOriginalPhone().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyOriginalPhone().status == ResultStatus.FORBIDDEN
        w1.tryGetIsDeleted().status == ResultStatus.FORBIDDEN
        w1.trySetNameIfPresent(name).status == ResultStatus.FORBIDDEN
        w1.trySetNoteIfPresent(note).status == ResultStatus.FORBIDDEN
        w1.tryMergeNumber(pNum1, 0).status == ResultStatus.FORBIDDEN
        w1.tryDeleteNumber(pNum1).status == ResultStatus.FORBIDDEN
    }

    void "test setting only if present"() {
        when:
        IndividualPhoneRecordWrapper w1 = new IndividualPhoneRecordWrapper(null, null)

        then:
        w1.trySetNameIfPresent(null).status == ResultStatus.NO_CONTENT
        w1.trySetNoteIfPresent(null).status == ResultStatus.NO_CONTENT
    }
}
