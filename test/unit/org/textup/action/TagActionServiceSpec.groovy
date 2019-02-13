package org.textup.action

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import org.textup.*
import org.textup.structure.*
import org.textup.test.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*
import org.textup.validator.action.*
import spock.lang.*

@Domain([AnnouncementReceipt, ContactNumber, CustomAccountDetails, FeaturedAnnouncement,
    FutureMessage, GroupPhoneRecord, IncomingSession, IndividualPhoneRecord, Location, MediaElement,
    MediaElementVersion, MediaInfo, Organization, OwnerPolicy, Phone, PhoneNumberHistory,
    PhoneOwnership, PhoneRecord, PhoneRecordMembers, Record, RecordCall, RecordItem,
    RecordItemReceipt, RecordNote, RecordNoteRevision, RecordText, Role, Schedule,
    SimpleFutureMessage, Staff, StaffRole, Team, Token])
@TestMixin(HibernateTestMixin)
@TestFor(TagActionService)
class TagActionServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test has actions"() {
        expect:
        service.hasActions(doTagActions: null) == false
        service.hasActions(doTagActions: "hi")
    }

    void "test handling actions"() {
        given:
        String str1 = TestUtils.randString()
        GroupPhoneRecord gpr1 = TestUtils.buildGroupPhoneRecord()
        IndividualPhoneRecord ipr1 = TestUtils.buildIndPhoneRecord()

        GroupMemberAction a1 = GroovyMock()
        MockedMethod tryProcess = MockedMethod.create(ActionContainer, "tryProcess") {
            Result.createSuccess([a1])
        }

        when:
        Result res = service.tryHandleActions(gpr1, [doTagActions: str1])

        then:
        1 * a1.toString() >> GroupMemberAction.ADD
        1 * a1.buildPhoneRecord() >> ipr1
        tryProcess.latestArgs == [GroupMemberAction, str1]
        res.status == ResultStatus.OK
        res.payload == gpr1
        ipr1 in gpr1.members.phoneRecords

        when:
        res = service.tryHandleActions(gpr1, [doTagActions: str1])

        then:
        1 * a1.toString() >> GroupMemberAction.REMOVE
        1 * a1.buildPhoneRecord() >> ipr1
        tryProcess.latestArgs == [GroupMemberAction, str1]
        res.status == ResultStatus.OK
        res.payload == gpr1
        !(ipr1 in gpr1.members.phoneRecords)

        cleanup:
        tryProcess?.restore()
    }
}
