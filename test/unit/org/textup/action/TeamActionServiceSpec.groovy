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
@TestFor(TeamActionService)
class TeamActionServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test has actions"() {
        expect:
        service.hasActions(doTeamActions: null) == false
        service.hasActions(doTeamActions: "hi")
    }

    void "test handling actions"() {
        given:
        String str1 = TestUtils.randString()
        Long authId = TestUtils.randIntegerUpTo(88)

        Team t1 = TestUtils.buildTeam()
        Staff s1 = TestUtils.buildStaff()

        TeamAction a1 = GroovyMock()
        MockedMethod tryProcess = MockedMethod.create(ActionContainer, "tryProcess") {
            Result.createSuccess([a1])
        }
        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") {
            Result.createSuccess(authId)
        }
        MockedMethod tryIfAdmin = MockedMethod.create(Organizations, "tryIfAdmin") { Result.void() }

        when:
        Result res = service.tryHandleActions(t1, [doTeamActions: str1])

        then:
        1 * a1.toString() >> TeamAction.ADD
        1 * a1.buildStaff() >> s1
        tryIfAdmin.latestArgs == [t1.org.id, authId]
        tryProcess.latestArgs == [TeamAction, str1]
        res.status == ResultStatus.OK
        res.payload == t1
        s1 in t1.members

        when:
        res = service.tryHandleActions(t1, [doTeamActions: str1])

        then:
        1 * a1.toString() >> TeamAction.REMOVE
        1 * a1.buildStaff() >> s1
        tryIfAdmin.latestArgs == [t1.org.id, authId]
        tryProcess.latestArgs == [TeamAction, str1]
        res.status == ResultStatus.OK
        res.payload == t1
        !(s1 in t1.members)

        cleanup:
        tryProcess?.restore()
        tryGetAuthId?.restore()
        tryIfAdmin?.restore()
    }
}
