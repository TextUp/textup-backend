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
class PhoneRecordWrapperSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test wrapped null"() {
        when:
        PhoneRecordWrapper w1 = new PhoneRecordWrapper(null, null)

        then:
        w1.save() == null
        w1.errors == null
        w1.validate() == false
        w1.tryUnwrap().status == ResultStatus.FORBIDDEN
        w1.isOverridden() == false
        w1.getWrappedClass() == null
        w1.getId() == null
        w1.tryGetLastTouched().status == ResultStatus.FORBIDDEN
        w1.tryGetWhenCreated().status == ResultStatus.FORBIDDEN
        w1.tryGetMutablePhone().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyMutablePhone().status == ResultStatus.FORBIDDEN
        w1.tryGetOriginalPhone().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyOriginalPhone().status == ResultStatus.FORBIDDEN
        w1.tryGetStatus().status == ResultStatus.FORBIDDEN
        w1.tryGetRecord().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyRecord().status == ResultStatus.FORBIDDEN
        w1.tryGetSecureName().status == ResultStatus.FORBIDDEN
        w1.tryGetPublicName().status == ResultStatus.FORBIDDEN
        w1.trySetStatusIfPresent(PhoneRecordStatus.values()[0]).status == ResultStatus.FORBIDDEN
        w1.trySetStatusIfHasVisibleStatus(PhoneRecordStatus.values()[0]).status == ResultStatus.FORBIDDEN
        w1.trySetLanguageIfPresent(VoiceLanguage.values()[0]).status == ResultStatus.FORBIDDEN
    }

    void "test wrapped owner"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()

        when:
        PhoneRecordWrapper w1 = new PhoneRecordWrapper(ipr1, ipr1.toPermissions())

        then:
        w1.save() == w1
        w1.errors instanceof ValidationErrors
        w1.validate() == true
        w1.tryUnwrap().payload == ipr1
        w1.isOverridden() == false
        w1.getWrappedClass() == ipr1.class
        w1.getId() == ipr1.id
        w1.tryGetLastTouched().payload == ipr1.lastTouched
        w1.tryGetWhenCreated().payload == ipr1.whenCreated
        w1.tryGetMutablePhone().payload == ipr1.phone
        w1.tryGetReadOnlyMutablePhone().payload == ipr1.phone
        w1.tryGetOriginalPhone().payload == ipr1.phone
        w1.tryGetReadOnlyOriginalPhone().payload == ipr1.phone
        w1.tryGetStatus().payload == ipr1.status
        w1.tryGetRecord().payload == ipr1.record
        w1.tryGetReadOnlyRecord().payload == ipr1.record
        w1.tryGetSecureName().payload == ipr1.secureName
        w1.tryGetPublicName().payload == ipr1.publicName

        when:
        Result res = w1.trySetStatusIfPresent(PhoneRecordStatus.UNREAD)

        then:
        res.status == ResultStatus.NO_CONTENT
        ipr1.status == PhoneRecordStatus.UNREAD

        when:
        res = w1.trySetStatusIfHasVisibleStatus(PhoneRecordStatus.ACTIVE)

        then:
        res.status == ResultStatus.NO_CONTENT
        ipr1.status == PhoneRecordStatus.ACTIVE

        when:
        res = w1.trySetLanguageIfPresent(VoiceLanguage.PORTUGUESE)

        then:
        res.status == ResultStatus.NO_CONTENT
        ipr1.record.language == VoiceLanguage.PORTUGUESE
    }

    void "test wrapped can modify"() {
        given:
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        spr1.permission = SharePermission.DELEGATE

        when:
        PhoneRecordWrapper w1 = new PhoneRecordWrapper(spr1, spr1.toPermissions())

        then:
        w1.save() == w1
        w1.errors instanceof ValidationErrors
        w1.validate() == true
        w1.tryUnwrap().status == ResultStatus.FORBIDDEN
        w1.isOverridden() == false
        w1.getWrappedClass() == spr1.class
        w1.getId() == spr1.id
        w1.tryGetLastTouched().payload == spr1.lastTouched
        w1.tryGetWhenCreated().payload == spr1.whenCreated
        w1.tryGetMutablePhone().payload == spr1.phone
        w1.tryGetReadOnlyMutablePhone().payload == spr1.phone
        w1.tryGetOriginalPhone().payload == spr1.phone
        w1.tryGetReadOnlyOriginalPhone().payload == spr1.phone
        w1.tryGetStatus().payload == spr1.status
        w1.tryGetRecord().payload == spr1.record
        w1.tryGetReadOnlyRecord().payload == spr1.record
        w1.tryGetSecureName().payload == spr1.secureName
        w1.tryGetPublicName().payload == spr1.publicName

        when:
        Result res = w1.trySetStatusIfPresent(PhoneRecordStatus.UNREAD)

        then:
        res.status == ResultStatus.NO_CONTENT
        spr1.status == PhoneRecordStatus.UNREAD

        when:
        res = w1.trySetStatusIfHasVisibleStatus(PhoneRecordStatus.ACTIVE)

        then:
        res.status == ResultStatus.NO_CONTENT
        spr1.status == PhoneRecordStatus.ACTIVE

        when:
        res = w1.trySetLanguageIfPresent(VoiceLanguage.PORTUGUESE)

        then:
        res.status == ResultStatus.NO_CONTENT
        spr1.record.language == VoiceLanguage.PORTUGUESE
    }

    void "test wrapped can view"() {
        given:
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        spr1.permission = SharePermission.VIEW

        when:
        PhoneRecordWrapper w1 = new PhoneRecordWrapper(spr1, spr1.toPermissions())

        then:
        w1.save() == w1
        w1.errors instanceof ValidationErrors
        w1.validate() == true
        w1.tryUnwrap().status == ResultStatus.FORBIDDEN
        w1.isOverridden() == false
        w1.getWrappedClass() == spr1.class
        w1.getId() == spr1.id
        w1.tryGetLastTouched().payload == spr1.lastTouched
        w1.tryGetWhenCreated().payload == spr1.whenCreated
        w1.tryGetMutablePhone().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyMutablePhone().payload == spr1.phone
        w1.tryGetOriginalPhone().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyOriginalPhone().payload == spr1.phone
        w1.tryGetStatus().payload == spr1.status
        w1.tryGetRecord().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyRecord().payload == spr1.record
        w1.tryGetSecureName().payload == spr1.secureName
        w1.tryGetPublicName().payload == spr1.publicName

        when:
        Result res = w1.trySetStatusIfPresent(PhoneRecordStatus.UNREAD)

        then: "all shared have own status"
        res.status == ResultStatus.NO_CONTENT
        spr1.status == PhoneRecordStatus.UNREAD

        when:
        res = w1.trySetStatusIfHasVisibleStatus(PhoneRecordStatus.ACTIVE)

        then: "all shared have own status"
        res.status == ResultStatus.NO_CONTENT
        spr1.status == PhoneRecordStatus.ACTIVE

        when:
        res = w1.trySetLanguageIfPresent(VoiceLanguage.PORTUGUESE)

        then:
        res.status == ResultStatus.FORBIDDEN
    }

    void "test wrapped expired or no permissions"() {
        given:
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        spr1.permission = SharePermission.NONE
        spr1.dateExpired = DateTime.now()

        when:
        PhoneRecordWrapper w1 = new PhoneRecordWrapper(spr1, spr1.toPermissions())

        then:
        w1.save() == w1
        w1.errors instanceof ValidationErrors
        w1.validate() == true
        w1.tryUnwrap().status == ResultStatus.FORBIDDEN
        w1.isOverridden() == false
        w1.getWrappedClass() == spr1.class
        w1.getId() == spr1.id
        w1.tryGetLastTouched().status == ResultStatus.FORBIDDEN
        w1.tryGetWhenCreated().status == ResultStatus.FORBIDDEN
        w1.tryGetMutablePhone().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyMutablePhone().status == ResultStatus.FORBIDDEN
        w1.tryGetOriginalPhone().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyOriginalPhone().status == ResultStatus.FORBIDDEN
        w1.tryGetStatus().status == ResultStatus.FORBIDDEN
        w1.tryGetRecord().status == ResultStatus.FORBIDDEN
        w1.tryGetReadOnlyRecord().status == ResultStatus.FORBIDDEN
        w1.tryGetSecureName().status == ResultStatus.FORBIDDEN
        w1.tryGetPublicName().status == ResultStatus.FORBIDDEN
        w1.trySetStatusIfPresent(PhoneRecordStatus.UNREAD).status == ResultStatus.FORBIDDEN
        w1.trySetStatusIfHasVisibleStatus(PhoneRecordStatus.ACTIVE).status == ResultStatus.FORBIDDEN
        w1.trySetLanguageIfPresent(VoiceLanguage.PORTUGUESE).status == ResultStatus.FORBIDDEN
    }

    void "test setting only if present"() {
        when:
        PhoneRecordWrapper w1 = new PhoneRecordWrapper(null, null)

        then:
        w1.trySetStatusIfPresent(null).status == ResultStatus.NO_CONTENT
        w1.trySetStatusIfHasVisibleStatus(null).status == ResultStatus.NO_CONTENT
        w1.trySetLanguageIfPresent(null).status == ResultStatus.NO_CONTENT
    }

    void "test setting status if not blocked"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        PhoneRecordWrapper w1 = new PhoneRecordWrapper(ipr1, ipr1.toPermissions())

        when:
        Result res = w1.trySetStatusIfHasVisibleStatus(PhoneRecordStatus.BLOCKED)

        then:
        res.status == ResultStatus.NO_CONTENT
        ipr1.status == PhoneRecordStatus.BLOCKED

        when:
        res = w1.trySetStatusIfHasVisibleStatus(PhoneRecordStatus.UNREAD)

        then:
        res.status == ResultStatus.NO_CONTENT
        ipr1.status == PhoneRecordStatus.BLOCKED
    }
}
