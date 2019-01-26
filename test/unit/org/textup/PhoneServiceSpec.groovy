package org.textup

import grails.test.mixin.*
import grails.test.mixin.gorm.*
import grails.test.mixin.hibernate.*
import grails.test.mixin.support.*
import grails.test.runtime.*
import grails.validation.*
import java.util.concurrent.*
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
@TestFor(PhoneService)
@TestMixin(HibernateTestMixin)
@Unroll
class PhoneServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    def setup() {
        setupData()
    }

    def cleanup() {
        cleanupData()
    }

    // Phone actions
    // -------------

    void "test deactivating phone"() {
        given:
        service.numberService = Mock(NumberService)

        when:
        p1.apiId = TestUtils.randString()
        p1.save(flush: true, failOnError: true)
        Result<Phone> res = service.deactivatePhone(p1)

        then:
        1 * service.numberService.freeExistingNumberToInternalPool(*_) >> new Result()
        res.success == true
        res.payload instanceof Phone
        res.payload.numberAsString == null
        res.payload.apiId == null
    }

    void "test updating phone number given a phone number error conditions"() {
        given: "baseline"
        int baseline = Phone.count()
        String oldApiId = TestUtils.randString()
        s1.phone.apiId = oldApiId
        s1.phone.save(flush:true, failOnError:true)

        when: "invalid number"
        PhoneNumber pNum = new PhoneNumber(number:"invalid123")
        Result<Phone> res = service.updatePhoneForNumber(s1.phone, pNum)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        Phone.count() == baseline

        when: "same number"
        res = service.updatePhoneForNumber(s1.phone, s1.phone.number)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.numberAsString == s1.phone.numberAsString
        Phone.count() == baseline

        when: "duplicate number"
        res = service.updatePhoneForNumber(s1.phone, p2.number)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "phoneService.changeNumber.duplicate"
    }

    @FreshRuntime
    void "test updating phone number given an api id error conditions"() {
        given: "baseline"
        int baseline = Phone.count()
        String oldApiId = TestUtils.randString(),
            anotherApiId = TestUtils.randString()
        s1.phone.apiId = oldApiId
        s2.phone.apiId = anotherApiId
        [s1, s2]*.save(flush:true, failOnError:true)

        when: "with same phone number apiId"
        Result<Phone> res = service.updatePhoneForApiId(s1.phone, oldApiId)

        then:
        res.success == true
        res.payload instanceof Phone
        res.payload.apiId == oldApiId
        Phone.count() == baseline

        when: "with apiId belonging to another phone"
        res = service.updatePhoneForApiId(s1.phone, anotherApiId)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "phoneService.changeNumber.duplicate"
    }

    @DirtiesRuntime
    void "test phone action errors"() {
        given:
        CustomAccountDetails cad1 = TestUtils.buildCustomAccountDetails()

        when: "not a list"
        Result<Phone> res = service.handlePhoneActions(p1, [doPhoneActions: [
            hello:"i am not a list"
        ]])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0] == "emptyOrNotACollection"

        when: "item is not a map"
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            ["i", "am", "not", "a", "map"]
        ]])

        then: "ignored"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0] == "emptyOrNotAMap"

        when: "item has an invalid action"
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [
                action: "i am an invalid action",
                test:"I'm a property that doesn't exist"
            ]
        ]])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0] == "actionContainer.invalidActions"

        when: "phone is on a subaccount and is in debugging mode"
        p1.customAccount = cad1
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [action: Constants.PHONE_ACTION_DEACTIVATE]
        ]])

        then:
        res.status == ResultStatus.FORBIDDEN
        res.errorMessages[0] == "phoneService.handlePhoneActions.disabledWhenDebugging"
    }

    @DirtiesRuntime
    void "test handling phone actions overall"() {
        given:
        MockedMethod deactivatePhone = TestUtils.mock(service, "deactivatePhone") { new Result() }
        MockedMethod transferPhone = TestUtils.mock(service, "transferPhone") { new Result() }
        MockedMethod updatePhoneForNumber = TestUtils.mock(service, "updatePhoneForNumber") { new Result() }
        MockedMethod updatePhoneForApiId = TestUtils.mock(service, "updatePhoneForApiId") { new Result() }

        when: "no phone actions"
        Result<Phone> res = service.handlePhoneActions(p1, [:])

        then:
        res.success == true
        res.payload == p1
        deactivatePhone.callCount == 0
        transferPhone.callCount == 0
        updatePhoneForNumber.callCount == 0
        updatePhoneForApiId.callCount == 0

        when: "valid deactivate"
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [action: Constants.PHONE_ACTION_DEACTIVATE]
        ]])

        then:
        deactivatePhone.callCount == 1
        transferPhone.callCount == 0
        updatePhoneForNumber.callCount == 0
        updatePhoneForApiId.callCount == 0

        when: "valid transfer"
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_TRANSFER,
                id: 88L,
                type: PhoneOwnershipType.GROUP.toString()
            ]
        ]])

        then:
        deactivatePhone.callCount == 1
        transferPhone.callCount == 1
        updatePhoneForNumber.callCount == 0
        updatePhoneForApiId.callCount == 0

        when: "new number via number"
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_NEW_NUM_BY_NUM,
                number: TestUtils.randPhoneNumberString()
            ]
        ]])

        then:
        deactivatePhone.callCount == 1
        transferPhone.callCount == 1
        updatePhoneForNumber.callCount == 1
        updatePhoneForApiId.callCount == 0

        when: "new number via api id"
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_NEW_NUM_BY_ID,
                numberId: TestUtils.randString()
            ]
        ]])

        then:
        deactivatePhone.callCount == 1
        transferPhone.callCount == 1
        updatePhoneForNumber.callCount == 1
        updatePhoneForApiId.callCount == 1
    }

    // Updating
    // --------

    void "test updating phone away message"() {
        when:
        String msg = "ting ting 123"
        Result<Phone> res = service.updateFields(s1.phone, [awayMessage: msg])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.awayMessage.contains(msg)

        and: "no longer contains suffix because this lives on the org now"
        res.payload.awayMessage.contains(Constants.DEFAULT_AWAY_MESSAGE_SUFFIX) == false
        res.payload.buildAwayMessage().contains(Constants.DEFAULT_AWAY_MESSAGE_SUFFIX)
    }

    void "test updating useVoicemailRecordingIfPresent"() {
        when:
        boolean originalVal = s1.phone.useVoicemailRecordingIfPresent
        Result<Phone> res = service.updateFields(s1.phone, [useVoicemailRecordingIfPresent: null])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.useVoicemailRecordingIfPresent == originalVal

        when:
        res = service.updateFields(s1.phone, [useVoicemailRecordingIfPresent: !originalVal])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.useVoicemailRecordingIfPresent == !originalVal

        when:
        res = service.updateFields(s1.phone, [useVoicemailRecordingIfPresent: originalVal])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.useVoicemailRecordingIfPresent == originalVal

        when:
        res = service.updateFields(s1.phone, [useVoicemailRecordingIfPresent: 'NOT A BOOL'])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.useVoicemailRecordingIfPresent == originalVal
    }

    void "test updating voice type"() {
        when: "invalid voice type"
        Result<Phone> res = service.updateFields(s1.phone, [voice:"invalid"])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "nullable"

        when: "valid voice type"
        res = service.updateFields(s1.phone, [voice:VoiceType.FEMALE.toString()])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.voice == VoiceType.FEMALE
    }

    void "test updating voice language"() {
        when: "invalid voice language"
        Result<Phone> res = service.updateFields(s1.phone, [language:"invalid"])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "nullable"

        when: "valid voice language"
        res = service.updateFields(s1.phone, [language:VoiceLanguage.RUSSIAN.toString()])

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.language == VoiceLanguage.RUSSIAN
    }

    void "testing handling availability"() {
        given:
        service.notificationService = Mock(NotificationService)
        service.authService = Mock(AuthService)
        Staff staff1 = new Staff(username: TestUtils.randString(),
            password: "password",
            name: "hi",
            email:"hi@textup.org",
            org: org,
            personalPhoneAsString: TestUtils.randPhoneNumberString(),
            lockCode:Constants.DEFAULT_LOCK_CODE)
        staff1.save(flush: true, failOnError: true)
        int npBaseline = NotificationPolicy.count()

        when: "no availability in body"
        Result<Phone> res = service.handleAvailability(p1, [:], null)

        then: "bypassed"
        0 * service.authService._
        0 * service.notificationService._
        NotificationPolicy.count() == npBaseline

        when: "has availability in body"
        assert p1.owner.findPolicyForStaff(staff1.id) == null
        res = service.handleAvailability(p1, [availability:[hello: 'there!']], null)

        then: "a new notification policy is created and policy is updated"
        1 * service.authService.loggedIn >> staff1
        1 * service.notificationService.update(*_) >> { args ->
            assert args[0].save() // need to this to mark newly-created policy as persistent
            new Result()
        }
        NotificationPolicy.count() == npBaseline + 1
    }

    // Voicemail greeting
    // ------------------

    void "test getting number to call for voicemail greeting"() {
        given:
        String randNum1 = TestUtils.randPhoneNumberString()
        String randNum2 = TestUtils.randPhoneNumberString()
        service.authService = Mock(AuthService)

        when: "fall back to personal phone"
        String num = service.getNumberToCallForVoicemailGreeting("true")

        then:
        1 * service.authService.loggedInAndActive >> [personalPhoneAsString: randNum1]
        num == randNum1

        when: "specify different phone number to call"
        num = service.getNumberToCallForVoicemailGreeting(randNum2)

        then:
        0 * service.authService._
        num == randNum2
    }

    void "test requesting voicemail greeting"() {
        given:
        String customAccountId = TestUtils.randString()
        service.callService = GroovyMock(CallService)
        Phone p1 = GroovyMock(Phone)
        String randNum1 = TestUtils.randPhoneNumberString()

        when: "not requesting voicemail greeting"
        Result<?> res = service.requestVoicemailGreetingCall(p1, [:])

        then:
        0 * service.callService._
        res.status == ResultStatus.NO_CONTENT

        when: "invalid phone number to call"
        res = service.requestVoicemailGreetingCall(p1, [requestVoicemailGreetingCall: "invalid"])

        then:
        0 * service.callService._
        res.status == ResultStatus.UNPROCESSABLE_ENTITY

        when: "valid phone number to call"
        res = service.requestVoicemailGreetingCall(p1, [requestVoicemailGreetingCall: randNum1])

        then:
        1 * p1.customAccountId >> customAccountId
        1 * service.callService.start(_, _, _, customAccountId) >> new Result()
    }

    // Merging
    // -------

    @DirtiesRuntime
    void "test short circuiting when logged-in user is not active"() {
        given:
        service.authService = Mock(AuthService)
        MockedMethod mergeHelper = TestUtils.mock(service, "mergeHelper") { new Result() }

        when: "merge for staff"
        Result<Staff> staffRes = service.mergePhone(s1, [phone: [awayMessage: "hi"]], null)

        then:
        (1.._) * service.authService.isActive >> false
        staffRes.payload instanceof Staff
        mergeHelper.callCount == 0

        when: "merge for team"
        Result<Team> teamRes = service.mergePhone(t1, [phone: [awayMessage: "hi"]], null)

        then:
        (1.._) * service.authService.isActive >> false
        teamRes.payload instanceof Team
        mergeHelper.callCount == 0

        when: "becomes active"
        teamRes = service.mergePhone(t1, [phone: [awayMessage: "hi"]], null)

        then:
        (1.._) * service.authService.isActive >> true
        mergeHelper.callCount == 1
    }

    @DirtiesRuntime
    void "test merging phone creates a new phone if no phone"() {
        given: "staff and team with no phone"
        Staff staff1 = new Staff(username: TestUtils.randString(),
            password: "password",
            name: "hi",
            email:"hi@textup.org",
            org: org,
            personalPhoneAsString: TestUtils.randPhoneNumberString(),
            lockCode:Constants.DEFAULT_LOCK_CODE)
        Team team1 = new Team(name: TestUtils.randString(),
            org: org,
            location: new Location(address: "address", lat: 8G, lon: 10G))
        [staff1, team1]*.save(flush: true, failOnError: true)
        service.authService = Stub(AuthService) { getIsActive() >> true }
        MockedMethod mergeHelper = TestUtils.mock(service, "mergeHelper")
            { Phone p1 -> p1.save(); new Result(); }
        int pBaseline = Phone.count()
        int oBaseline = PhoneOwnership.count()

        when: "merge for staff"
        Result<Staff> staffRes = service.mergePhone(staff1, [phone:[:]], null)

        then: "new phone created"
        staffRes.payload instanceof Staff
        Phone.count() == pBaseline + 1
        PhoneOwnership.count() == oBaseline + 1

        when: "merge for team"
        Result<Team> teamRes = service.mergePhone(team1, [phone:[:]], null)

        then: "new phone created"
        teamRes.payload instanceof Team
        Phone.count() == pBaseline + 2
        PhoneOwnership.count() == oBaseline + 2
    }

    @DirtiesRuntime
    void "test lazy creation of notification policy and schedule when merging"() {
        given: "phone without notification policy for owner"
        String utcTimezone = "Etc/UTC"
        service.notificationService = Stub(NotificationService) {
            update(*_) >> { args -> args[0].save(); new Result(); }
        }
        service.authService = Stub(AuthService) {
            getIsActive() >> true
            getLoggedIn() >> s1
        }
        service.mediaService = Stub(MediaService) {
            tryProcess(_, _, true) >> new Result(payload: Tuple.create(null, null))
        }
        Long staffId = 888L
        assert p1.owner.findPolicyForStaff(staffId) == null
        int policyBaseline = NotificationPolicy.count()

        when: "update without availability"
        Map body = [awayMessage: "hello there!"]
        Result<Phone> res = service.mergeHelper(p1, body, utcTimezone)

        then: "policy and schedule not created"
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.awayMessage.contains(body.awayMessage)
        NotificationPolicy.count() == policyBaseline

        and: "no longer contains suffix because this lives on the org now"
        res.payload.awayMessage.contains(Constants.DEFAULT_AWAY_MESSAGE_SUFFIX) == false
        res.payload.buildAwayMessage().contains(Constants.DEFAULT_AWAY_MESSAGE_SUFFIX)

        when: "update with availability"
        body = [availability: [hi: "there"]]
        res = service.mergeHelper(p1, body, utcTimezone)

        then: "policy and schedule ARE created"
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        NotificationPolicy.count() == policyBaseline + 1

        when: "update again with availability"
        res = service.mergeHelper(p1, body, utcTimezone)

        then: "use already-created policy and schedule"
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        NotificationPolicy.count() == policyBaseline + 1
    }

    @DirtiesRuntime
    void "test cancelling future processing if error during merging"() {
        given:
        service.mediaService = Mock(MediaService)
        MockedMethod handlePhoneActions = TestUtils.mock(service, "handlePhoneActions")
            { new Result(status: ResultStatus.UNPROCESSABLE_ENTITY) }
        MockedMethod requestVoicemailGreetingCall = TestUtils.mock(service, "requestVoicemailGreetingCall")
        Future fut1 = Mock()

        when:
        Result<Phone> res = service.mergeHelper(null, null, null)

        then:
        1 * service.mediaService.tryProcess(_, _, true) >> new Result(payload: Tuple.create(null, fut1))
        1 * fut1.cancel(true)
        handlePhoneActions.callCount == 1
        requestVoicemailGreetingCall.callCount == 0
    }

    @DirtiesRuntime
    void "test requesting voicemail greeting call during merging"() {
        given:
        service.mediaService = Mock(MediaService)
        MockedMethod handlePhoneActions = TestUtils.mock(service, "handlePhoneActions")
            { new Result() }
        MockedMethod handleAvailability = TestUtils.mock(service, "handleAvailability")
            { new Result() }
        MockedMethod updateFields = TestUtils.mock(service, "updateFields")
            { new Result() }
        MockedMethod requestVoicemailGreetingCall = TestUtils.mock(service, "requestVoicemailGreetingCall")
            { new Result() }
        Future fut1 = Mock()

        when:
        Result<Phone> res = service.mergeHelper(null, null, null)

        then:
        1 * service.mediaService.tryProcess(_, _, true) >> new Result(payload: Tuple.create(null, fut1))
        0 * fut1._
        handlePhoneActions.callCount == 1
        handleAvailability.callCount == 1
        updateFields.callCount == 1
        requestVoicemailGreetingCall.callCount == 1
    }
}
