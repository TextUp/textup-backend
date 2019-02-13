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
class PhoneRecordUtilsSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test building member id to groups"() {
        given:
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        GroupPhoneRecord gpr2 = TestUtils.buildGroupPhoneRecord()

        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()
        gpr1.members.addToPhoneRecords(ipr1)
        gpr2.members.addToPhoneRecords(ipr1)
        IndividualPhoneRecord ipr2 = TestUtils.buildIndPhoneRecord()
        IndividualPhoneRecord ipr3 = TestUtils.buildIndPhoneRecord()
        gpr2.members.addToPhoneRecords(ipr3)

        PhoneRecord.withSession { it.flush() }

        when:
        Map idToGroups = PhoneRecordUtils.buildMemberIdToGroups(null)

        then:
        idToGroups.isEmpty()

        when:
        idToGroups = PhoneRecordUtils.buildMemberIdToGroups([gpr1, gpr2])

        then:
        idToGroups.size() == 2
        idToGroups.containsKey(ipr1.id)
        idToGroups.containsKey(ipr2.id) == false
        idToGroups.containsKey(ipr3.id)
        idToGroups[ipr1.id] == [gpr1, gpr2]
        idToGroups[ipr3.id] == [gpr2]
    }

    void "test trying to mark unread for phone and number"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()
        Phone mockPhone = GroovyMock()
        IndividualPhoneRecordWrapper mockWrap = GroovyMock()
        MockedMethod tryFindOrCreateEveryByPhoneAndNumbers = TestUtils.mock(IndividualPhoneRecordWrappers,
            "tryFindOrCreateEveryByPhoneAndNumbers") {
            Result.createSuccess([mockWrap])
        }

        when:
        Result res = PhoneRecordUtils.tryMarkUnread(mockPhone, pNum1)

        then:
        tryFindOrCreateEveryByPhoneAndNumbers.callCount == 1
        tryFindOrCreateEveryByPhoneAndNumbers.callArguments[0] == [mockPhone, [pNum1], true]
        1 * mockWrap.trySetStatusIfNotBlocked(PhoneRecordStatus.UNREAD) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == [mockWrap]

        cleanup:
        tryFindOrCreateEveryByPhoneAndNumbers.restore()
    }
}
