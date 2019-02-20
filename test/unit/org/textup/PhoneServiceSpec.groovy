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
    }

    def setup() {
        TestUtils.standardMockSetup()
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
        TypeMap oInfo = TestUtils.randTypeMap()

        Staff s1 = TestUtils.buildStaff()
        Phone tp1 = TestUtils.buildActiveTeamPhone()

        int opBaseline = OwnerPolicy.count()

        service.ownerPolicyService = GroovyMock(OwnerPolicyService)
        MockedMethod tryGetAuthId = MockedMethod.create(AuthUtils, "tryGetAuthId") {
            Result.createSuccess(s1.id)
        }

        when:
        Result res = service.tryUpdateOwnerPolicy(tp1, null, tz)

        then:
        tryGetAuthId.notCalled
        res.status == ResultStatus.OK
        res.payload == tp1
        OwnerPolicy.count() == opBaseline

        when:
        res = service.tryUpdateOwnerPolicy(tp1, oInfo, tz)

        then:
        tryGetAuthId.hasBeenCalled
        1 * service.ownerPolicyService.tryUpdate(_, oInfo, tz) >> Result.void()
        res.status == ResultStatus.OK
        res.payload == tp1
        OwnerPolicy.count() == opBaseline + 1

        cleanup:
        tryGetAuthId?.restore()
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

    void "test updating overall"() {
        given:
        String errMsg1 = TestUtils.randString()
        String tz = TestUtils.randString()
        TypeMap selfMap = TestUtils.randTypeMap()
        TypeMap body = TypeMap.create(self: selfMap,
            requestVoicemailGreetingCall: TestUtils.randString())

        Phone p1 = TestUtils.buildStaffPhone()

        Future fut1 = GroovyMock()
        service.mediaService = GroovyMock(MediaService)
        service.phoneActionService = GroovyMock(PhoneActionService)
        MockedMethod tryUpdateOwnerPolicy = MockedMethod.create(service, "tryUpdateOwnerPolicy") { Result.void() }
        MockedMethod trySetFields = MockedMethod.create(service, "trySetFields") { Result.void() }
        MockedMethod tryRequestVoicemailGreetingCall = MockedMethod.create(service, "tryRequestVoicemailGreetingCall") { Result.void() }

        when:
        Result res = service.update(p1, body, tz)

        then:
        1 * service.mediaService.tryCreateOrUpdate(p1, body, true) >> Result.createSuccess(fut1)
        1 * service.phoneActionService.tryHandleActions(p1, body) >>
            Result.createError([errMsg1], ResultStatus.BAD_REQUEST)
        tryUpdateOwnerPolicy.notCalled
        trySetFields.notCalled
        tryRequestVoicemailGreetingCall.notCalled
        1 * fut1.cancel(true)
        res.status == ResultStatus.BAD_REQUEST
        res.errorMessages == [errMsg1]

        when:
        res = service.update(p1, body, tz)

        then:
        1 * service.mediaService.tryCreateOrUpdate(p1, body, true) >> Result.createSuccess(fut1)
        1 * service.phoneActionService.tryHandleActions(p1, body) >> Result.void()
        tryUpdateOwnerPolicy.latestArgs == [p1, selfMap, tz]
        trySetFields.latestArgs == [p1, body]
        tryRequestVoicemailGreetingCall.latestArgs == [p1, body.requestVoicemailGreetingCall]
        0 * fut1._
        res.status == ResultStatus.OK
        res.payload == p1

        cleanup:
        tryUpdateOwnerPolicy?.restore()
        trySetFields?.restore()
        tryRequestVoicemailGreetingCall?.restore()
    }
}
