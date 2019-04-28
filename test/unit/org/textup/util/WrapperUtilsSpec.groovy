package org.textup.util

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
class WrapperUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test determining type of wrapper"() {
        given:
        PhoneRecordWrapper w1 = TestUtils.buildIndPhoneRecord().toWrapper()
        PhoneRecordWrapper w2 = TestUtils.buildGroupPhoneRecord().toWrapper()
        PhoneRecordWrapper w3 = TestUtils.buildSharedPhoneRecord().toWrapper()

        expect: "contact"
        WrapperUtils.isContact(w1) == true
        WrapperUtils.isSharedContact(w1) == false
        WrapperUtils.isTag(w1) == false

        and: "tag"
        WrapperUtils.isContact(w2) == false
        WrapperUtils.isSharedContact(w2) == false
        WrapperUtils.isTag(w2) == true

        and: "shared contact"
        WrapperUtils.isContact(w3) == false
        WrapperUtils.isSharedContact(w3) == true
        WrapperUtils.isTag(w3) == false
    }

    void "test getting properties from wrappers ignoring fails"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.name = TestUtils.randString()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        gpr1.name = TestUtils.randString()
        PhoneRecord spr1 = TestUtils.buildSharedPhoneRecord()
        spr1.permission = SharePermission.NONE
        spr1.shareSource.name = TestUtils.randString()

        Collection wraps = [ipr1, gpr1, spr1]*.toWrapper()

        expect:
        ipr1.record.id in WrapperUtils.recordIdsIgnoreFails(wraps)
        gpr1.record.id in WrapperUtils.recordIdsIgnoreFails(wraps)
        !(spr1.record.id in WrapperUtils.recordIdsIgnoreFails(wraps))

        ipr1.phone.id in WrapperUtils.mutablePhoneIdsIgnoreFails(wraps)
        gpr1.phone.id in WrapperUtils.mutablePhoneIdsIgnoreFails(wraps)
        !(spr1.phone.id in WrapperUtils.mutablePhoneIdsIgnoreFails(wraps))

        ipr1.secureName in WrapperUtils.secureNamesIgnoreFails(wraps) { true }
        gpr1.secureName in WrapperUtils.secureNamesIgnoreFails(wraps) { true }
        !(spr1.secureName in WrapperUtils.secureNamesIgnoreFails(wraps) { true })

        ipr1.publicName in WrapperUtils.publicNamesIgnoreFails(wraps)
        gpr1.publicName in WrapperUtils.publicNamesIgnoreFails(wraps)
        !(spr1.publicName in WrapperUtils.publicNamesIgnoreFails(wraps))
    }

    void "test filtering when getting secure names"() {
        given:
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        ipr1.name = TestUtils.randString()
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        ipr2.name = TestUtils.randString()

        Collection wraps = [ipr1, ipr2]*.toWrapper()

        when:
        Collection names = WrapperUtils.secureNamesIgnoreFails(wraps) { PhoneRecordWrapper w1 ->
            w1.id == ipr1.id
        }

        then:
        ipr1.name in names
        !(ipr2.name in names)

        when:
        names = WrapperUtils.secureNamesIgnoreFails(wraps) { false }

        then:
        names.isEmpty()

        when:
        names = WrapperUtils.secureNamesIgnoreFails(wraps) { true }

        then:
        names.size() ==2
        ipr1.name in names
        ipr2.name in names
    }
}
