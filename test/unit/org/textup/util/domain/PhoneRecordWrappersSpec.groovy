package org.textup.util.domain

import grails.gorm.DetachedCriteria
import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.joda.time.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.validator.*
import org.textup.util.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class PhoneRecordWrappersSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finding for id"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()

        when:
        Result res = PhoneRecordWrappers.mustFindForId(null)

        then:
        res.status == ResultStatus.NOT_FOUND

        when:
        res = PhoneRecordWrappers.mustFindForId(ipr1.id)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof IndividualPhoneRecordWrapper
        res.payload.wrappedClass == IndividualPhoneRecord
        res.payload.isOverridden() == false
        res.payload.id == ipr1.id

        when:
        res = PhoneRecordWrappers.mustFindForId(gpr1.id)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof PhoneRecordWrapper
        res.payload.wrappedClass == GroupPhoneRecord
        res.payload.isOverridden() == false
        res.payload.id == gpr1.id

        when:
        res = PhoneRecordWrappers.mustFindForId(spr1.id)

        then:
        res.status == ResultStatus.OK
        res.payload instanceof PhoneRecordWrapper
        res.payload.wrappedClass == IndividualPhoneRecord
        res.payload.isOverridden()
        res.payload.id == spr1.id
    }
}
