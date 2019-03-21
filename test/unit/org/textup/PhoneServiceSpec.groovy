package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import java.util.concurrent.*
import org.joda.time.*
import org.textup.action.*
import org.textup.cache.*
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
@TestFor(PhoneService)
@TestMixin(HibernateTestMixin)
class PhoneServiceSpec extends Specification {

    static doWithSpring = {
        resultFactory(ResultFactory)
        phoneCache(PhoneCache)
    }

    def setup() {
        TestUtils.standardMockSetup()
    }

    void "test finding active given owner"() {
        given:
        Team t1 = TestUtils.buildTeam()
        int pBaseline = Phone.count()

        when: "owner does not have phone"
        Result res = service.tryFindAnyIdOrCreateImmediatelyForOwner(t1.id, PhoneOwnershipType.GROUP)
        Phone newPhone = Phone.get(res.payload)

        then:
        res.status == ResultStatus.CREATED
        Phone.count() == pBaseline + 1
        newPhone != null
        newPhone.isActive() == false
        res.hasErrorBeenHandled == false

        when: "newly created phone is still not active yet"
        res = service.tryFindAnyIdOrCreateImmediatelyForOwner(t1.id, PhoneOwnershipType.GROUP)

        then:
        res.status == ResultStatus.OK
        res.payload == newPhone.id
        Phone.count() == pBaseline + 1

        when: "newly created phone is active"
        newPhone.tryActivate(TestUtils.randPhoneNumber(), TestUtils.randString())
        newPhone.save(flush: true, failOnError: true)

        res = service.tryFindAnyIdOrCreateImmediatelyForOwner(t1.id, PhoneOwnershipType.GROUP)

        then:
        res.status == ResultStatus.OK
        res.payload == newPhone.id
        Phone.count() == pBaseline + 1
    }

    void "test getting greeting call number"() {
        given:
        String num1 = TestUtils.randPhoneNumberString()
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        when:
        Result res = service.tryGetGreetingCallNum("true", pNum1)

        then:
        res.status == ResultStatus.OK
        res.payload == pNum1

        when:
        res = service.tryGetGreetingCallNum(num1, pNum1)

        then:
        res.status == ResultStatus.CREATED
        res.payload == PhoneNumber.create(num1)
    }

    void "test requesting voicemail greeting call"() {
        given:
        PhoneNumber pNum1 = TestUtils.randPhoneNumber()

        Staff s1 = TestUtils.buildStaff()
        Phone tp1 = TestUtils.buildActiveTeamPhone()

        service.callService = GroovyMock(CallService)
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(s1)
        }

        when:
        Result res = service.tryRequestVoicemailGreetingCall(tp1, null)

        then:
        tryGetActiveAuthUser.notCalled
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.tryRequestVoicemailGreetingCall(tp1, pNum1.number)

        then:
        tryGetActiveAuthUser.hasBeenCalled
        1 * service.callService.start(tp1.number, [pNum1], _, _) >> Result.void()
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test trying to update owner policy"() {
        given:
        String tz = TestUtils.randString()

        Staff s1 = TestUtils.buildStaff()
        Staff s2 = TestUtils.buildStaff()
        Phone tp1 = TestUtils.buildActiveTeamPhone()

        TypeMap oInfo1 = TestUtils.randTypeMap()
        TypeMap oInfo2 = TypeMap.create(staffId: s1.id)
        TypeMap oInfo3 = TestUtils.randTypeMap()

        int opBaseline = OwnerPolicy.count()
        service.ownerPolicyService = GroovyMock(OwnerPolicyService)

        when:
        Result res = service.tryUpdateOwnerPolicy(tp1, null, null, tz)

        then:
        res.status == ResultStatus.OK
        res.payload == tp1
        OwnerPolicy.count() == opBaseline

        when:
        res = service.tryUpdateOwnerPolicy(tp1, s2.id, [oInfo1, oInfo2, oInfo3], tz)

        then:
        res.status == ResultStatus.OK
        res.payload == tp1
        OwnerPolicy.count() == opBaseline

        when:
        res = service.tryUpdateOwnerPolicy(tp1, s1.id, [oInfo1, oInfo2, oInfo3], tz)

        then:
        1 * service.ownerPolicyService.tryUpdate({ it.staff == s1 }, oInfo2, tz) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == tp1
        OwnerPolicy.count() == opBaseline + 1
    }

    void "test trying to update fields"() {
        given:
        TypeMap body = TypeMap.create(awayMessage: TestUtils.randString(),
            voice: VoiceType.values()[0],
            language: VoiceLanguage.values()[0],
            useVoicemailRecordingIfPresent: true,
            allowSharingWithOtherTeams: false)
        Phone p1 = TestUtils.buildStaffPhone()

        when:
        Result res = service.trySetFields(p1, body)

        then:
        res.status == ResultStatus.OK
        res.payload == p1
        p1.awayMessage == body.awayMessage
        p1.voice == body.voice
        p1.language == body.language
        p1.useVoicemailRecordingIfPresent == body.useVoicemailRecordingIfPresent
        p1.owner.allowSharingWithOtherTeams == body.allowSharingWithOtherTeams
    }

    void "test handling phone actions"() {
        given:
        TypeMap body = TestUtils.randTypeMap()
        Long pId = TestUtils.randIntegerUpTo(88)
        Phone p1 = GroovyMock() { getId() >> pId }
        Staff authUser = GroovyMock()

        service.phoneActionService = GroovyMock(PhoneActionService)
        MockedMethod tryGetActiveAuthUser = MockedMethod.create(AuthUtils, "tryGetActiveAuthUser") {
            Result.createSuccess(authUser)
        }

        when:
        Result res = service.tryHandlePhoneActionsImmediatelyAndRefresh(p1, body)

        then:
        1 * service.phoneActionService.hasActions(body) >> false
        res.status == ResultStatus.NO_CONTENT

        when:
        res = service.tryHandlePhoneActionsImmediatelyAndRefresh(p1, body)

        then:
        1 * service.phoneActionService.hasActions(body) >> true
        1 * authUser.status >> StaffStatus.ADMIN
        1 * service.phoneActionService.tryHandleActions(pId, body) >> Result.void()
        1 * p1.refresh()
        res.status == ResultStatus.NO_CONTENT

        cleanup:
        tryGetActiveAuthUser?.restore()
    }

    void "test updating overall"() {
        given:
        String errMsg1 = TestUtils.randString()
        String tz = TestUtils.randString()
        Long staffId = TestUtils.randIntegerUpTo(88)
        TypeMap selfMap = TestUtils.randTypeMap()
        TypeMap body = TypeMap.create(policies: [selfMap],
            requestVoicemailGreetingCall: TestUtils.randString())

        Phone p1 = TestUtils.buildStaffPhone()

        Future fut1 = GroovyMock()
        service.mediaService = GroovyMock(MediaService)
        MockedMethod tryHandlePhoneActionsImmediatelyAndRefresh = MockedMethod.create(service, "tryHandlePhoneActionsImmediatelyAndRefresh") { Result.void() }
        MockedMethod tryUpdateOwnerPolicy = MockedMethod.create(service, "tryUpdateOwnerPolicy") {
            Result.createError([errMsg1], ResultStatus.BAD_REQUEST)
        }
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") { Result.void() }
        MockedMethod tryRequestVoicemailGreetingCall = MockedMethod.create(service, "tryRequestVoicemailGreetingCall") { Result.void() }

        when:
        Result res = service.tryUpdate(p1, body, staffId, tz)

        then:
        tryHandlePhoneActionsImmediatelyAndRefresh.latestArgs == [p1, body]
        1 * service.mediaService.tryCreateOrUpdate(p1, body, true) >> Result.createSuccess(fut1)
        tryUpdateOwnerPolicy.hasBeenCalled
        trySetFields.notCalled
        tryRequestVoicemailGreetingCall.notCalled
        1 * fut1.cancel(true)
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages == [errMsg1]

        when:
        tryUpdateOwnerPolicy = MockedMethod.create(tryUpdateOwnerPolicy) { Result.void() }
        res = service.tryUpdate(p1, body, staffId, tz)

        then:
        tryHandlePhoneActionsImmediatelyAndRefresh.latestArgs == [p1, body]
        1 * service.mediaService.tryCreateOrUpdate(p1, body, true) >> Result.createSuccess(fut1)
        tryUpdateOwnerPolicy.latestArgs == [p1, staffId, [selfMap], tz]
        trySetFields.latestArgs == [p1, body]
        tryRequestVoicemailGreetingCall.latestArgs == [p1, body.requestVoicemailGreetingCall]
        0 * fut1._
        res.status == ResultStatus.OK
        res.payload == p1

        cleanup:
        tryHandlePhoneActionsImmediatelyAndRefresh?.restore()
        tryUpdateOwnerPolicy?.restore()
        trySetFields?.restore()
        tryRequestVoicemailGreetingCall?.restore()
    }
}
