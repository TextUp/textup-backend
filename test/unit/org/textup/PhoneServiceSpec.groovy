package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.DirtiesRuntime
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import java.util.concurrent.atomic.AtomicInteger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.hibernate.Session
import org.joda.time.DateTime
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*
import spock.lang.*
import static org.springframework.http.HttpStatus.*

@TestFor(PhoneService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, NotificationPolicy,
    MediaInfo, MediaElement, MediaElementVersion])
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

    @DirtiesRuntime
    void "test deactivating phone"() {
        given:
        boolean isCalled = false
        service.numberService = [
            freeExistingNumber: { String oldApiId -> isCalled = true; new Result() }
        ] as NumberService

        when:
        p1.apiId = UUID.randomUUID().toString()
        p1.save(flush: true, failOnError: true)
        Result<Phone> res = service.deactivatePhone(p1)

        then:
        res.success == true
        res.payload instanceof Phone
        res.payload.numberAsString == null
        res.payload.apiId == null
        isCalled == true
    }

    void "test updating phone number given a phone number error conditions"() {
        given: "baseline"
        int baseline = Phone.count()
        String oldApiId = "kikiIsCute"
        s1.phone.apiId = oldApiId
        s1.phone.save(flush:true, failOnError:true)
        addToMessageSource("phoneService.changeNumber.duplicate")

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
        String oldApiId = "kikiIsCute",
            anotherApiId = "tingtingIsAwesome"
        s1.phone.apiId = oldApiId
        s2.phone.apiId = anotherApiId
        [s1, s2]*.save(flush:true, failOnError:true)
        addToMessageSource("phoneService.changeNumber.duplicate")

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
        given: "an alternate mock for extracting messages from ValidationErrors"
        service.resultFactory.messageSource = TestHelpers.mockMessageSourceWithResolvable()
        addToMessageSource("actionContainer.invalidActions")

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
    }

    @DirtiesRuntime
    void "test handling phone actions overall"() {
        given:
        boolean isCalled = false
        service.metaClass.deactivatePhone = { Phone p1 -> isCalled = true; new Result(); }
        service.metaClass.transferPhone = { Phone p1, Long id, PhoneOwnershipType type ->
            isCalled = true; new Result();
        }
        service.metaClass.updatePhoneForNumber = { Phone p1, PhoneNumber pNum ->
            isCalled = true; new Result();
        }
        service.metaClass.updatePhoneForApiId = { Phone p1, String apiId ->
            isCalled = true; new Result();
        }

        when: "no phone actions"
        Result<Phone> res = service.handlePhoneActions(p1, [:])

        then:
        res.success == true
        res.payload == p1

        when: "valid deactivate"
        isCalled = false
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [action: Constants.PHONE_ACTION_DEACTIVATE]
        ]])

        then:
        true == isCalled

        when: "valid transfer"
        isCalled = false
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_TRANSFER,
                id: 88L,
                type: PhoneOwnershipType.GROUP.toString()
            ]
        ]])

        then:
        true == isCalled

        when: "new number via number"
        isCalled = false
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_NEW_NUM_BY_NUM,
                number: TestHelpers.randPhoneNumber()
            ]
        ]])

        then:
        true == isCalled

        when: "new number via api id"
        isCalled = false
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_NEW_NUM_BY_ID,
                numberId: UUID.randomUUID().toString()
            ]
        ]])

        then:
        true == isCalled
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
        res.payload.awayMessage.contains(Constants.AWAY_EMERGENCY_MESSAGE)
    }

    void "test updating voice type"() {
        when: "invalid voice type"
        Result<Phone> res = service.updateFields(s1.phone, [voice:"invalid"])

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0].contains("voice")

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
        res.errorMessages[0].contains("language")

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
        boolean isCalled = false
        service.notificationService = [
            update: { NotificationPolicy np1, Map body, String timezone ->
                assert np1.save() // need to this to mark newly-created policy as persistent
                isCalled = true
                new Result()
            }
        ] as NotificationService
        Staff staff1 = new Staff(username: UUID.randomUUID().toString(),
            password: "password",
            name: "hi",
            email:"hi@textup.org",
            org: org,
            personalPhoneAsString: TestHelpers.randPhoneNumber(),
            lockCode:Constants.DEFAULT_LOCK_CODE)
        staff1.save(flush: true, failOnError: true)
        int npBaseline = NotificationPolicy.count()

        when: "no availability in body"
        Result<Phone> res = service.handleAvailability(p1, [:], null)

        then: "bypassed"
        false == isCalled
        NotificationPolicy.count() == npBaseline

        when: "has availability in body"
        assert p1.owner.getPolicyForStaff(staff1.id) == null
        service.authService = [getLoggedIn: { -> staff1 }] as AuthService
        res = service.handleAvailability(p1, [availability:[hello: 'there!']], null)

        then: "a new notification policy is created and policy is updated"
        true == isCalled
        NotificationPolicy.count() == npBaseline + 1
    }

    // Merging
    // -------

    void "test short circuiting when logged-in user is not active"() {
        given:
        boolean isCalled = false
        service.authService = [getIsActive: { false }] as AuthService
        service.metaClass.mergeHelper = { Phone p1, Map body, String timezone ->
            isCalled = true; new Result();
        }

        when: "merge for staff"
        Result<Staff> staffRes = service.mergePhone(s1, [phone: [awayMessage: "hi"]], null)

        then:
        staffRes.payload instanceof Staff
        false == isCalled

        when: "merge for team"
        Result<Team> teamRes = service.mergePhone(t1, [phone: [awayMessage: "hi"]], null)

        then:
        teamRes.payload instanceof Team
        false == isCalled

        when: "becomes active"
        service.authService = [getIsActive: { true }] as AuthService
        teamRes = service.mergePhone(t1, [phone: [awayMessage: "hi"]], null)

        then:
        true == isCalled
    }

    @DirtiesRuntime
    void "test merging phone creates a new phone if no phone"() {
        given: "staff and team with no phone"
        Staff staff1 = new Staff(username: UUID.randomUUID().toString(),
                password: "password",
                name: "hi",
                email:"hi@textup.org",
                org: org,
                personalPhoneAsString: TestHelpers.randPhoneNumber(),
                lockCode:Constants.DEFAULT_LOCK_CODE)
        Team team1 = new Team(name: UUID.randomUUID().toString(),
                org: org,
                location: new Location(address: "address", lat: 8G, lon: 10G))
        [staff1, team1]*.save(flush: true, failOnError: true)
        service.metaClass.mergeHelper = { Phone p1, Map body, String timezone ->
            assert p1.save() // need to save newly-created phone
            new Result()
        }
        service.authService = [getIsActive: { true }] as AuthService
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
        service.notificationService = [
            update: { NotificationPolicy np1, Map body, String timezone ->
                assert np1.save() // need to save newly-created policy
                new Result()
            }
        ] as NotificationService
        service.authService = [
            getIsActive: { true },
            getLoggedIn: { s1 }
        ] as AuthService
        Long staffId = 888L
        assert p1.owner.getPolicyForStaff(staffId) == null
        int policyBaseline = NotificationPolicy.count()

        when: "update without availability"
        Map body = [awayMessage: "hello there!"]
        Result<Phone> res = service.mergeHelper(p1, body, utcTimezone)

        then: "policy and schedule not created"
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.awayMessage.contains(body.awayMessage)
        res.payload.awayMessage.contains(Constants.AWAY_EMERGENCY_MESSAGE)
        NotificationPolicy.count() == policyBaseline

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
}
