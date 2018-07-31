package org.textup

import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import java.util.concurrent.atomic.AtomicInteger
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.hibernate.Session
import org.joda.time.DateTime
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.util.CustomSpec
import org.textup.validator.*
import spock.lang.*
import static org.springframework.http.HttpStatus.*

@TestFor(PhoneService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole, NotificationPolicy])
@TestMixin(HibernateTestMixin)
@Unroll
class PhoneServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    String _utcTimezone = "Etc/UTC"
    String _apiId = "iamsospecial!!!"
    AtomicInteger _numTextsSent
    List<Long> _notifyRecordIds
    List<Long> _notifyPhoneIds
    List<Staff> _whoIsAvailable

    def setup() {
        setupData()

        _numTextsSent = new AtomicInteger(0)
        _notifyRecordIds = []
        _notifyPhoneIds = []
        _whoIsAvailable = []

        service.resultFactory = getResultFactory()
        service.resultFactory.messageSource = messageSource
        service.messageSource = messageSource
        service.notificationService = [
            build: { Phone targetPhone, List<Contact> contacts ->
                _whoIsAvailable ? _whoIsAvailable.collect {
                    new BasicNotification(staff:it, owner:targetPhone.owner,
                        record:contacts[0].record)
                } : []
            }
        ] as NotificationService
        service.tokenService = [
            notifyStaff:{ BasicNotification bn1, Boolean outgoing, String msg,
                String instructions ->
                _numTextsSent.getAndIncrement()
                _notifyRecordIds << bn1.record.id
                _notifyPhoneIds << bn1.owner.phone.id
                new Result(status:ResultStatus.OK)
            }
        ]  as TokenService
        service.textService = [send:{ BasePhoneNumber fromNum,
            List<? extends BasePhoneNumber> toNums, String message ->
            _numTextsSent.getAndIncrement()
            new Result(status:ResultStatus.OK,
                payload: new TempRecordReceipt(apiId:_apiId, contactNumberAsString:toNums[0].number))
        }] as TextService
        service.callService = [start:{ PhoneNumber fromNum, PhoneNumber toNum,
            Map afterPickup ->
            new Result(status:ResultStatus.OK,
                payload: new TempRecordReceipt(apiId:_apiId, contactNumberAsString:toNum.number))
        }] as CallService
        service.socketService = [sendContacts:{ List<Contact> contacts ->
            new ResultGroup()
        }, sendItems:{ List<RecordItem> items ->
            new ResultGroup()
        }] as SocketService
        service.twimlBuilder = [build:{ code, params=[:] ->
            new Result(status:ResultStatus.OK, payload:code)
        }, buildTexts:{ List<String> responses ->
            new Result(status:ResultStatus.OK, payload:responses)
        }, translate:{ code, params=[:] ->
            new Result(status:ResultStatus.OK, payload:code)
        }] as TwimlBuilder
        service.authService = [
            getIsActive: { true },
            getLoggedIn: { s1 }
        ] as AuthService

        OutgoingMessage.metaClass.getMessageSource = { -> messageSource }
        Phone.metaClass.tryStartVoicemail = { PhoneNumber fromNum, PhoneNumber toNum, ReceiptStatus stat ->
            new Result(status:ResultStatus.OK, payload:CallResponse.CHECK_IF_VOICEMAIL)
        }
    }

    def cleanup() {
        cleanupData()
    }

    // Updating
    // --------

    // Can only test edge cases because have not discovered a way to mock
    // Twilio api library classes such as IncomingPhoneNumber

    void "test updating phone away message"() {
        when:
        String msg = "ting ting 123"
        Result<Phone> res = service.update(s1.phone, [awayMessage:msg], null)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.awayMessage.contains(msg)
        res.payload.awayMessage.contains(Constants.AWAY_EMERGENCY_MESSAGE)
    }

    void "test updating voice type"() {
        when: "invalid voice type"
        Result<Phone> res = service.update(s1.phone, [voice:"invalid"], null)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0].contains("voice")

        when: "valid voice type"
        res = service.update(s1.phone, [voice:VoiceType.FEMALE.toString()], null)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.voice == VoiceType.FEMALE
    }

    void "test updating voice language"() {
        when: "invalid voice language"
        Result<Phone> res = service.update(s1.phone, [language:"invalid"], null)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0].contains("language")

        when: "valid voice language"
        res = service.update(s1.phone, [language:VoiceLanguage.RUSSIAN.toString()], null)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.language == VoiceLanguage.RUSSIAN
    }

    void "test updating phone number when not active"() {
        given:
        service.authService = [
            getIsActive: { false },
            getLoggedIn: { s1 }
        ] as AuthService

        when:
        PhoneNumber newNum = new PhoneNumber(number:'1112223333')
        assert newNum.validate()
        Result<Phone> res = service.update(s1.phone, [number:newNum.number], null)
        String originalNum = s1.phone.numberAsString

        then: "new number is ignored"
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.numberAsString == originalNum
    }

    @FreshRuntime
    void "test updating phone number with apiId"() {
        given: "baseline"
        int baseline = Phone.count()
        String oldApiId = "kikiIsCute",
            anotherApiId = "tingtingIsAwesome"
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
        addToMessageSource("phoneService.changeNumber.duplicate")
        res = service.updatePhoneForApiId(s1.phone, anotherApiId)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "phoneService.changeNumber.duplicate"
    }
    @FreshRuntime
    void "test updating phone number with new number"() {
        given: "baseline"
        int baseline = Phone.count()
        String oldApiId = "kikiIsCute"
        s1.phone.apiId = oldApiId
        s1.phone.save(flush:true, failOnError:true)

        when: "with same phone number"
        Result<Phone> res = service.updatePhoneForNumber(s1.phone, s1.phone.number)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload instanceof Phone
        res.payload.numberAsString == s1.phone.numberAsString
        Phone.count() == baseline

        when: "with invalid change"
        PhoneNumber pNum = new PhoneNumber(number:"invalid123")
        res = service.updatePhoneForNumber(s1.phone, pNum)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        Phone.count() == baseline
    }

    void "test updating schedule at policy level"() {
        given: "currently logged-in is a staff without policy for this phone"
        Staff staff1 = new Staff(username: UUID.randomUUID().toString(),
            name: "Name",
            password: "password",
            email: "hello@its.me",
            org: org)
        staff1.save(flush:true, failOnError:true)

        service.authService = [
            getIsActive: { true },
            getLoggedIn: { staff1 }
        ] as AuthService
        int pBaseline = NotificationPolicy.count()
        int sBaseline = Schedule.count()

        when: "handling availability with no schedule-related updates"
        Result<NotificationPolicy> res = service.handleAvailability(p1, [:], _utcTimezone)

        then: "a new policy is created even if no schedule-related updates"
        res.success == true
        res.payload instanceof NotificationPolicy
        NotificationPolicy.count() == pBaseline + 1
        Schedule.count() == sBaseline

        when: "update with valid non-schedule info"
        res = service.handleAvailability(p1, [
            useStaffAvailability: false,
            manualSchedule: false,
            isAvailable: false
        ], _utcTimezone)

        then: "updated"
        res.success == true
        res.payload instanceof NotificationPolicy
        res.payload.useStaffAvailability == false
        res.payload.manualSchedule == false
        res.payload.isAvailable == false
        NotificationPolicy.count() == pBaseline + 1
        Schedule.count() == sBaseline

        when: "update with some invalid non-schedule info"
        res = service.handleAvailability(p1, [
            useStaffAvailability: "invalid, not a boolean",
            manualSchedule: "invalid, not a boolean",
            isAvailable: true,
        ], _utcTimezone)

        then: "invalid values ignored, valid values updated"
        res.success == true
        res.payload instanceof NotificationPolicy
        res.payload.useStaffAvailability == false
        res.payload.manualSchedule == false
        res.payload.isAvailable == true
        NotificationPolicy.count() == pBaseline + 1
        Schedule.count() == sBaseline

        when: "handling schedule with valid schedule-related updates"
        String mondayString = "0000:1230"
        res = service.handleAvailability(p1, [schedule:[monday:[mondayString]]], _utcTimezone)

        then: "a new schedule is created and all updates are made"
        res.success == true
        res.payload instanceof NotificationPolicy
        NotificationPolicy.count() == pBaseline + 1
        Schedule.count() == sBaseline + 1
        res.payload.useStaffAvailability == false
        res.payload.manualSchedule == false
        res.payload.isAvailable == true
        res.payload.schedule.monday == mondayString.replaceAll(":", ",") // different delimiter when saved
    }

    // separate out error state from updating with valid information because when there is an
    // invalid update, the session will always roll back any changes. Due to limitations in the
    // testing environment, this means that any subsequent attempts to update the schedule with
    // valid information are rolled back, even if we do so in a new session.
    void "test updating availability at policy level with invalid schedule information"() {
        given: "currently logged-in is a staff without policy for this phone"
        Staff staff1 = new Staff(username: UUID.randomUUID().toString(),
            name: "Name",
            password: "password",
            email: "hello@its.me",
            org: org)
        staff1.save(flush:true, failOnError:true)

        service.authService = [
            getIsActive: { true },
            getLoggedIn: { staff1 }
        ] as AuthService
        int pBaseline = NotificationPolicy.count()
        int sBaseline = Schedule.count()

        when: "handling schedule with INVALID schedule-related updates"
        addToMessageSource("weeklySchedule.strIntsNotList")
        Result res
        // wrap this call in a transaction so we can rollback on any input errors
        // so we don't have a bunch of orphan schedules
        Phone.withTransaction {
            res = service.handleAvailability(p1, [schedule:[monday:"invalid time range"]], null)
        }

        then: "error and no new schedule is created"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages[0] == "weeklySchedule.strIntsNotList"
        NotificationPolicy.count() == pBaseline
        Schedule.count() == sBaseline
    }

    @FreshRuntime
    @Unroll
    void "test create or update phone for staff OR team"() {
        given:
        def noPhoneEntity = forStaff ?
            new Staff(username:"6sta${iterNum}", password:"password",
                name:"Staff${iterNum}", email:"staff${iterNum}@textup.org",
                org:org, personalPhoneAsString:"1112223333",
                lockCode:Constants.DEFAULT_LOCK_CODE) :
            new Team(name:"kiki's mane", org:org.id,
                location:new Location(address:"address", lat:8G, lon:10G))
        noPhoneEntity.save(flush:true, failOnError:true)
        def withPhoneEntity = forStaff ? s1 : t1

        Staff loggedInStaff = s1

        int pBaseline = Phone.count()
        int oBaseline = PhoneOwnership.count()
        int policyBaseline = NotificationPolicy.count()
        int schedBaseline = Schedule.count()
        String msg = "I am away",
            msg2 = "I am a different message!",
            msg3 = "still another message!"

        when: "for staff without a phone with invalidly formatted body"
        Result<Staff> res = service.mergePhone(
            forStaff ? noPhoneEntity as Staff : noPhoneEntity as Team, [
            phone:msg
        ], _utcTimezone)
        assert res.success
        noPhoneEntity.save(flush:true, failOnError:true)

        then: "no new phone or policy is created because short-circuited in mergePhone"
        res.status == ResultStatus.OK
        if (forStaff) {
            assert res.payload instanceof Staff
        }
        else { assert res.payload instanceof Team }
        res.payload.id == noPhoneEntity.id
        Phone.count() == pBaseline
        PhoneOwnership.count() == oBaseline
        NotificationPolicy.count() == policyBaseline
        Schedule.count() == schedBaseline

        when: "when logged in staff with existing phone is inactive"
        service.authService = [
            getIsActive: { false },
            getLoggedIn: { loggedInStaff }
        ] as AuthService
        withPhoneEntity.phone.awayMessage = msg2
        withPhoneEntity.phone.save(flush:true, failOnError:true)
        res = service.mergePhone(
            forStaff ? withPhoneEntity as Staff : withPhoneEntity as Team,
            [phone: [awayMessage:msg]], _utcTimezone)

        then: "phone is NOT updated AND no new phone is created because the logged-in user is inactive"
        res.success == true
        res.status == ResultStatus.OK
        if (forStaff) {
            assert res.payload instanceof Staff
        }
        else { assert res.payload instanceof Team }
        res.payload.phone.awayMessage.contains(msg) == false
        res.payload.phone.awayMessage.contains(msg2)
        res.payload.phone.awayMessage.contains(Constants.AWAY_EMERGENCY_MESSAGE)
        Phone.count() == pBaseline
        PhoneOwnership.count() == oBaseline
        NotificationPolicy.count() == policyBaseline
        Schedule.count() == schedBaseline

        when: "for ACTIVE staff with existing phone"
        service.authService = [
            getIsActive: { true },
            getLoggedIn: { loggedInStaff }
        ] as AuthService
        res = service.mergePhone(
            forStaff ? withPhoneEntity as Staff : withPhoneEntity as Team,
            [phone: [awayMessage:msg]], _utcTimezone)

        then: "phone is updated, no new phone is created, and new policy is NOT created \
            because we haven't updated anything related to availability yet"
        res.success == true
        res.status == ResultStatus.OK
        if (forStaff) {
            assert res.payload instanceof Staff
        }
        else { assert res.payload instanceof Team }
        res.payload.phone.awayMessage.contains(msg)
        res.payload.phone.awayMessage.contains(Constants.AWAY_EMERGENCY_MESSAGE)
        Phone.count() == pBaseline
        PhoneOwnership.count() == oBaseline
        NotificationPolicy.count() == policyBaseline
        Schedule.count() == schedBaseline

        when: "for staff without a phone with validly formatted body"
        String mondayString1 = "0000:1230"
        res = service.mergePhone(
            forStaff ? noPhoneEntity as Staff : noPhoneEntity as Team,
            [
                phone: [
                    awayMessage: msg,
                    availability: [
                        useStaffAvailability: false,
                        schedule: [
                            monday: [mondayString1]
                        ]
                    ]
                ]
            ], _utcTimezone)
        assert res.success
        noPhoneEntity.save(flush:true, failOnError:true)

        then: "phone is created, but inactive because no number, policy is created \
            because this is first time we've encountered no phone entity, schedule on policy is created"
        res.status == ResultStatus.OK
        Phone.count() == pBaseline + 1
        PhoneOwnership.count() == oBaseline + 1
        NotificationPolicy.count() == policyBaseline + 1
        Schedule.count() == schedBaseline + 1
        if (forStaff) {
            assert res.payload instanceof Staff
        }
        else { assert res.payload instanceof Team }
        res.payload.id == noPhoneEntity.id
        res.payload.hasInactivePhone == true
        res.payload.phone == null
        res.payload.phoneWithAnyStatus.awayMessage.contains(msg)
        res.payload.phoneWithAnyStatus.awayMessage.contains(Constants.AWAY_EMERGENCY_MESSAGE)
        res.payload.phoneWithAnyStatus.isActive == false
        res.payload.phoneWithAnyStatus.owner.getPolicyForStaff(loggedInStaff.id) instanceof NotificationPolicy
        res.payload.phoneWithAnyStatus.owner.getPolicyForStaff(loggedInStaff.id).schedule.monday ==
            mondayString1.replaceAll(":", ",")

        when: "for active staff with inactive phone with validly formatted body"
        String mondayString2 = "0100:0200"
        String tuesdayString1 = "0345:0445"
        res = service.mergePhone(
            forStaff ? noPhoneEntity as Staff : noPhoneEntity as Team,
            [
                phone:[
                    awayMessage: msg3,
                    availability: [
                        schedule: [
                            monday: [mondayString2],
                            tuesday: [tuesdayString1]
                        ]
                    ]
                ]
            ], _utcTimezone)
        assert res.success
        noPhoneEntity.save(flush:true, failOnError:true)

        then: "no new phone created and existing schedule is updated"
        res.status == ResultStatus.OK
        Phone.count() == pBaseline + 1
        PhoneOwnership.count() == oBaseline + 1
        NotificationPolicy.count() == policyBaseline + 1
        Schedule.count() == schedBaseline + 1
        if (forStaff) {
            assert res.payload instanceof Staff
        }
        else { assert res.payload instanceof Team }
        res.payload.id == noPhoneEntity.id
        res.payload.hasInactivePhone == true
        res.payload.phone == null
        res.payload.phoneWithAnyStatus.awayMessage.contains(msg3)
        res.payload.phoneWithAnyStatus.awayMessage.contains(Constants.AWAY_EMERGENCY_MESSAGE)
        res.payload.phoneWithAnyStatus.isActive == false
        res.payload.phoneWithAnyStatus.owner.getPolicyForStaff(loggedInStaff.id) instanceof NotificationPolicy
        res.payload.phoneWithAnyStatus.owner.getPolicyForStaff(loggedInStaff.id).schedule.monday ==
            mondayString2.replaceAll(":", ",")
        res.payload.phoneWithAnyStatus.owner.getPolicyForStaff(loggedInStaff.id).schedule.tuesday ==
            tuesdayString1.replaceAll(":", ",")

        where:
        forStaff | _
        true     | _
        false    | _
    }

    void "test phone action error handling"() {
        given: "an alternate mock for resultFactory's messageSource for extracting messages \
            from ValidationErrors"
        service.resultFactory.messageSource = mockMessageSourceWithResolvable()

        when: "no phone actions"
        Result<Phone> res = service.update(p1, [:], _utcTimezone)

        then: "return passed-in phone with no modifications"
        res.success == true
        res.payload == p1

        when: "phone actions not a list"
        res = service.update(p1, [doPhoneActions: [
            hello:"i am not a list"
        ]], _utcTimezone)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0] == "emptyOrNotACollection"

        when: "item in phone actions is not a map"
        res = service.update(p1, [doPhoneActions: [
            ["i", "am", "not", "a", "map"]
        ]], _utcTimezone)

        then: "ignored"
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0] == "emptyOrNotAMap"

        when: "item in phone actions has an invalid action"
        addToMessageSource("actionContainer.invalidActions")
        res = service.update(p1, [doPhoneActions: [
            [
                action: "i am an invalid action",
                test:"I'm a property that doesn't exist"
            ]
        ]], _utcTimezone)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1
        res.errorMessages[0] == "actionContainer.invalidActions"
    }

    void "test phone actions calling each branch"() {
        given: "an alternate mock for resultFactory's messageSource for extracting messages \
            from ValidationErrors"
        service.resultFactory.messageSource = mockMessageSourceWithResolvable()

        when: "deactivating phone"
        Result<Phone> res = service.update(p1, [doPhoneActions: [
            [action: Constants.PHONE_ACTION_DEACTIVATE]
        ]], _utcTimezone)
        p1.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == p1
        res.payload.isActive == false

        when: "transferring phone"
        p1.apiId = null // to avoid calling freeExisting number
        p1.save(flush:true, failOnError:true)
        res = service.update(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_TRANSFER,
                id: t1.id,
                type: PhoneOwnershipType.GROUP.toString()
            ]
        ]], _utcTimezone)
        p1.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == p1
        res.payload.owner.ownerId == t1.id
        res.payload.owner.type == PhoneOwnershipType.GROUP

        when: "picking new number by passing in a number"
        res = service.update(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_NEW_NUM_BY_NUM,
                number: "invalidNum123" // so that short circuits
            ]
        ]], _utcTimezone)

        then:
        res.success == false
        res.status == ResultStatus.UNPROCESSABLE_ENTITY
        res.errorMessages.size() == 1

        when: "picking new number by passing in an apiId"
        p1.apiId = "iamsospecial"
        p1.save(flush:true, failOnError:true)
        res = service.update(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_NEW_NUM_BY_ID,
                numberId: p1.apiId // so that short circuits
            ]
        ]], _utcTimezone)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == p1
        res.payload.isActive == false
    }

    // Outgoing
    // --------

    void "test outgoing message that is a #type"() {
        given: "baselines"
        int iBaseline = RecordItem.count()
        int rBaseline = RecordItemReceipt.count()
        addToMessageSource("outgoingMessage.getName.contactId")

        service.callService = [start:{ PhoneNumber fromNum,
            List<? extends BasePhoneNumber> toNums, Map afterPickup ->
            new Result(status:ResultStatus.OK, payload: new TempRecordReceipt(apiId:_apiId,
                contactNumberAsString:toNums[0].number))
        }] as CallService

        when: "we send to contacts, shared contacts and tags"
        OutgoingMessage msg = new OutgoingMessage(message:"hello", type:type,
            contacts:[c1, c1_1, c1_2], sharedContacts:[sc2],
            tags:[tag1, tag1_1])
        assert msg.validateSetPhone(p1)
        ResultGroup resGroup = service.sendMessage(p1, msg, s1)
        p1.save(flush:true, failOnError:true)

        HashSet<Contact> uniqueContacts = new HashSet<>(msg.contacts)
        msg.tags.each { ContactTag ct1 ->
            if (ct1.members) { uniqueContacts.addAll(ct1.members) }
        }
        List<Integer> tagNumMembers = msg.tags.collect { it.members.size() ?: 0 }
        int totalTagMembers = tagNumMembers.sum()

        then: "no duplication, also stored in tags"
        !resGroup.isEmpty && resGroup.failures.isEmpty() == true // all successes
        // one result for each contact
        // one result for each shared contact
        // one result for each tag
        resGroup.successes.size() == uniqueContacts.size() +
            msg.sharedContacts.size() + msg.tags.size()
        resGroup.successes.each { it.payload instanceof RecordItem }
        // one msg for each contact
        // one msg for each shared contact
        // one msg for each tag
        RecordItem.count() == iBaseline + uniqueContacts.size() +
            msg.sharedContacts.size() + msg.tags.size()
        // one receipt for each contact
        // one receipt for each shared contact
        // one receipt for each member of each tag
        RecordItemReceipt.count() == rBaseline + uniqueContacts.size() +
            msg.sharedContacts.size() + totalTagMembers
        // make sure that `storeMessageInTag` is storing appropriate record item type
        // with appropriate contents
        if (type == RecordItemType.TEXT) {
            msg.tags.each { ContactTag ct1 ->
                RecordItem latestItem = ct1.record.getItems([max:1])[0]
                // if an outgoing text, latest item is a text with contents set to message
                assert latestItem.instanceOf(RecordText)
                assert latestItem.contents == msg.message
            }
        }
        else {
            msg.tags.each { ContactTag ct1 ->
                RecordItem latestItem = ct1.record.getItems([max:1])[0]
                // if an outgoing call, latest item is a call with callContents set to message
                assert latestItem.instanceOf(RecordCall)
                assert latestItem.callContents == msg.message
            }
        }

        where:
        type                | _
        RecordItemType.TEXT | _
        RecordItemType.CALL | _
    }

    void "test language paramters for direct messaging"() {
        given:
        Map capturedAfterPickup
        addToMessageSource("outgoingMessage.getName.contactId")
        service.callService = [start:{ PhoneNumber fromNum,
            List<? extends BasePhoneNumber> toNums, Map afterPickup ->
            capturedAfterPickup = afterPickup
            new Result(status:ResultStatus.OK, payload: new TempRecordReceipt(apiId:_apiId,
                contactNumberAsString:toNums[0].number))
        }] as CallService

        when: "we send a valid call direct message"
        OutgoingMessage msg = new OutgoingMessage(
            message:"hello",
            type:RecordItemType.CALL,
            language: VoiceLanguage.PORTUGUESE,
            contacts:[c1]
        )
        assert msg.validateSetPhone(p1)
        capturedAfterPickup = null
        ResultGroup resGroup = service.sendMessage(p1, msg, s1)
        p1.save(flush:true, failOnError:true)

        then:
        !resGroup.isEmpty && resGroup.failures.isEmpty() == true // all successes
        capturedAfterPickup.handle == CallResponse.DIRECT_MESSAGE
        capturedAfterPickup.message == msg.message
        capturedAfterPickup.identifier == p1.name
        // we want the string representation of the enum NOT the Twiml value
        capturedAfterPickup.language == VoiceLanguage.PORTUGUESE.toString()
    }

    void "test starting bridge call"() {
        given: "baselines"
        int cBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        s1.personalPhoneAsString = "1232339309"
        s1.save(flush:true, failOnError:true)

        when:
        Result<RecordCall> res = service.startBridgeCall(p1, c1, s1)

        then:
        res.success == true
        res.status == ResultStatus.CREATED
        res.payload instanceof RecordCall
        RecordCall.count() == cBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
    }

    void "test starting text announcement"() {
        given: "baselines"
        int tBaseline = RecordText.count()
        int rBaseline = RecordItemReceipt.count()
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()
        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString:"1238470293")
        assert IncomingSession.findByPhoneAndNumberAsString(p1,
            session.numberAsString) == null
        session.save(flush:true, failOnError:true)
        // mock twimlbuilder to return a list of strings, as expected
        service.twimlBuilder = [translate:{ code, params=[:] ->
            new Result(status:ResultStatus.OK, payload:[params.message])
        }] as TwimlBuilder

        when: "for session with no contacts"
        String message = "hello"
        String ident = "kiki bai"
        Map<String, Result<TempRecordReceipt>> resMap = service.sendTextAnnouncement(p1, message, ident,
            [session], s1)

        then:
        RecordText.count() == tBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        Contact.count() == cBaseline + 1
        ContactNumber.count() == nBaseline + 1
        resMap[session.numberAsString].success == true
        resMap[session.numberAsString].payload instanceof TempRecordReceipt
        resMap[session.numberAsString].payload.contactNumberAsString == session.numberAsString

        when: "for session with multiple contacts"
        resMap = service.sendTextAnnouncement(p1, message, ident, [session], s1)

        then:
        RecordText.count() == tBaseline + 2
        RecordItemReceipt.count() == rBaseline + 2
        Contact.count() == cBaseline + 1
        ContactNumber.count() == nBaseline + 1
        resMap[session.numberAsString].success == true
        resMap[session.numberAsString].payload instanceof TempRecordReceipt
        resMap[session.numberAsString].payload.contactNumberAsString == session.numberAsString
    }

    void "test starting call announcement"() {
        given: "baselines"
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        int cBaseline = Contact.count()
        int nBaseline = ContactNumber.count()
        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString:"1238470294")
        assert IncomingSession.findByPhoneAndNumberAsString(p1,
            session.numberAsString) == null
        session.save(flush:true, failOnError:true)

        when:
        String message = "hello"
        String ident = "kiki bai"
        Map<String, Result<TempRecordReceipt>> resMap = service.startCallAnnouncement(p1, message, ident,
            [session], s1)

        then:
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        Contact.count() == cBaseline + 1
        ContactNumber.count() == nBaseline + 1
        resMap[session.numberAsString].success == true
        resMap[session.numberAsString].payload instanceof TempRecordReceipt
        resMap[session.numberAsString].payload.contactNumberAsString == session.numberAsString
    }

    // Incoming
    // --------

    @FreshRuntime
    void "test relay text"() {
        given: "none available and should send instructions"
        int cBaseline = Contact.count()
        int tBaseline = RecordText.count()
        int rBaseline = RecordItemReceipt.count()
        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString:"1112223456",
            lastSentInstructions:DateTime.now().minusDays(1))
        session.save(flush:true, failOnError:true)
        assert p1.announcements.isEmpty()

        when: "for session without contact"
        IncomingText text = new IncomingText(apiId:"iamsosecret", message:"hello")
        assert text.validate()
        _whoIsAvailable = []
        Result res = service.relayText(p1, text, session)

        then: "create new contact, instructions not sent because no announcements"
        Contact.count() == cBaseline + 1
        RecordText.count() == tBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        Contact.listForPhoneAndNum(p1, session.number).size() == 1
        Contact.listForPhoneAndNum(p1, session.number)[0]
            .status == ContactStatus.UNREAD
        session.shouldSendInstructions == true
        res.success == true
        res.status == ResultStatus.OK
        res.payload == [p1.awayMessage]

        when: "for session without contact with announcements"
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:"Hello!", expiresAt:DateTime.now().plusDays(2))
        announce.save(flush:true, failOnError:true)
        res = service.relayText(p1, text, session)

        then: "create new contact, instructions sent"
        Contact.count() == cBaseline + 1
        RecordText.count() == tBaseline + 2
        RecordItemReceipt.count() == rBaseline + 2
        Contact.listForPhoneAndNum(p1, session.number).size() == 1
        Contact.listForPhoneAndNum(p1, session.number)[0]
            .status == ContactStatus.UNREAD
        session.shouldSendInstructions == false
        res.success == true
        res.status == ResultStatus.OK
        res.payload == [p1.awayMessage, TextResponse.INSTRUCTIONS_UNSUBSCRIBED]

        when: "for session with one blocked contact"
        assert Contact.listForPhoneAndNum(p1, session.number).size() == 1
        Contact c1 = Contact.listForPhoneAndNum(p1, session.number)[0]
        c1.status = ContactStatus.BLOCKED
        c1.save(flush:true, failOnError:true)
        res = service.relayText(p1, text, session)

        then: "new contact not created, instructions not sent, text also not stored"
        Contact.count() == cBaseline + 1
        RecordText.count() == tBaseline + 2 // not text stored
        RecordItemReceipt.count() == rBaseline + 2
        Contact.listForPhoneAndNum(p1, session.number).size() == 1
        Contact.listForPhoneAndNum(p1, session.number)[0]
            .status == ContactStatus.BLOCKED
        session.shouldSendInstructions == false
        res.success == true
        res.status == ResultStatus.OK
        // since session has only one associated contact and that contact is blocked
        // then we also return a blocked message notifying the texter
        // also this payload isn't an array because instead of calling buildTexts at the
        // end of the method, we have short-circuited and directly build the blocked message
        res.payload == TextResponse.BLOCKED

        when: "for session with multiple contacts"
        Contact dupCont = p1.createContact([:], [session.number]).payload
        dupCont.save(flush:true, failOnError:true)
        res = service.relayText(p1, text, session)

        then: "no contact created, instructions not sent"
        Contact.count() == cBaseline + 2 // one more from our duplicate contact
        RecordText.count() == tBaseline + 3 // one text stored for new contact
        RecordItemReceipt.count() == rBaseline + 3
        Contact.listForPhoneAndNum(p1, session.number).size() == 2
        session.shouldSendInstructions == false
        res.success == true
        res.status == ResultStatus.OK
        // no longer return the blocked message since session now has one non-blocked
        // contact associated with it
        res.payload == [p1.awayMessage]
    }

    @FreshRuntime
    void "test relaying text for contact that is shared"() {
        given: "baselines, availability, new session"
        int cBaseline = Contact.count()
        int tBaseline = RecordText.count()
        int rBaseline = RecordItemReceipt.count()
        addToMessageSource("incomingMessageService.notifyStaff.notification")

        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString:sc1.numbers[0].number,
            lastSentInstructions:DateTime.now().minusDays(1))
        session.save(flush:true, failOnError:true)
        Phone sBy = sc1.sharedBy,
            sWith = sc1.sharedWith
        assert sBy.announcements.isEmpty()

        when: """
            we receive text to a TextUp phone that is unavailable but one of
            the contact that matches the incoming number is shared with a phone
            whose owner(s) are available
        """
        // phone that owns contact isn't available
        // phone that contact is shared with has staff that are available
        _whoIsAvailable = sWith.owner.all
        [sBy, sWith]*.save(flush:true, failOnError:true)

        // set status to something different to check to see if they will both
        // be set to unread afterwards
        sc1.status = ContactStatus.ACTIVE
        sc1.contact.status = ContactStatus.ARCHIVED
        [sc1, sc1.contact]*.save(flush:true, failOnError:true)

        IncomingText text = new IncomingText(apiId:"iamsosecret", message:"hello")
        assert text.validate()
        _numTextsSent.getAndSet(0)
        _notifyRecordIds = []
        _notifyPhoneIds = []
        Result res = service.relayText(sBy, text, session)

        then: "no away message sent and only available staff notified"
        res.success == true
        res.status == ResultStatus.OK
        res.payload == []
        _numTextsSent.intValue() == sWith.owner.all.size()
        _notifyRecordIds.every { it == sc1.contact.record.id }
        Contact.get(sc1.contact.id).status == ContactStatus.UNREAD
        SharedContact.get(sc1.id).status == ContactStatus.UNREAD
        // We no longer test the line below because this functionality of resolving
        // shared relationships is now encapsulated in notificationService and is
        // not present in our simple mock of the notificationService
        // _notifyPhoneIds.every { it == sWith.id }

        when: "that shared with phone's staff owner is no longer available"
        // phone that contact is shared with has staff that are available
        _whoIsAvailable = []
        _numTextsSent.getAndSet(0)
        res = service.relayText(sBy, text, session)

        then: "away message is sent since no staff are available"
        res.success == true
        res.status == ResultStatus.OK
        res.payload == [sBy.awayMessage]
        _numTextsSent.intValue() == 0
    }

    @FreshRuntime
    void "test relay call"() {
        given: "session with no corresponding contact"
        int cBaseline = Contact.count()
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString:"1112223456",
            lastSentInstructions:DateTime.now().minusDays(1))
        session.save(flush:true, failOnError:true)

        when: "call for available"
        _whoIsAvailable = p1.owner.all
        Result res = service.relayCall(p1, "apiId", session)

        then: "contact created, call connecting"
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.CONNECT_INCOMING

        when: "none avabilable"
        _whoIsAvailable = []
        res = service.relayCall(p1, "apiId", session)

        then: "no additional contact created, voicemail"
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 2
        RecordItemReceipt.count() == rBaseline + 2
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.CHECK_IF_VOICEMAIL

        when: "blocked contact"
        assert Contact.listForPhoneAndNum(p1, session.number).size() == 1
        Contact c1 = Contact.listForPhoneAndNum(p1, session.number)[0]
        c1.status = ContactStatus.BLOCKED
        c1.save(flush:true, failOnError:true)
        res = service.relayCall(p1, "apiId", session)

        then: "no contact created, no record items stored, blocked message"
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 2
        RecordItemReceipt.count() == rBaseline + 2
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.BLOCKED

        when: "contact for same number that is not blocked"
        Contact dupCont = p1.createContact([:], [session.number]).payload
        dupCont.save(flush:true, failOnError:true)
        res = service.relayCall(p1, "apiId", session)

        then: "no contact created, record items stored for duplicate contact, voicemail"
        Contact.count() == cBaseline + 2 // to account for duplicate contact
        RecordCall.count() == iBaseline + 3
        RecordItemReceipt.count() == rBaseline + 3
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.CHECK_IF_VOICEMAIL
    }

    @FreshRuntime
    void "test relaying call for contact that is shared"() {
        given: "session with no corresponding contact"
        int cBaseline = Contact.count()
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        Phone sWith = sc1.sharedWith,
            sBy = sc1.sharedBy
        IncomingSession session = new IncomingSession(phone:sBy,
            numberAsString:sc1.numbers[0].number,
            lastSentInstructions:DateTime.now().minusDays(1))
        session.save(flush:true, failOnError:true)

        // set status to something different to check to see if they will both
        // be set to unread afterwards
        sc1.status = ContactStatus.BLOCKED
        sc1.contact.status = ContactStatus.ACTIVE
        [sc1, sc1.contact]*.save(flush:true, failOnError:true)

        when: "call sharedBy is unavailable but sharedWith is available"
        _whoIsAvailable = sWith.owner.all
        Result res = service.relayCall(sBy, "apiId", session)

        then: "call connecting"
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.CONNECT_INCOMING
        // contact is marked as unread but the SharedContact is STILL BLOCKED
        // because we respect the collaborator's decision to block that contact.
        Contact.get(sc1.contact.id).status == ContactStatus.UNREAD
        SharedContact.get(sc1.id).status == ContactStatus.BLOCKED

        when: "none avabilable"
        _whoIsAvailable = []
        res = service.relayCall(sBy, "apiId", session)

        then: "voicemail"
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline + 2
        RecordItemReceipt.count() == rBaseline + 2
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.CHECK_IF_VOICEMAIL
    }

    // TODO finish
    void "test handling incoming for text announcements"() {
        when: "we don't have any announcements"
        res = p1.receiveText(text, session)

        then: "relay text"
        res.success == true
        res.status == ResultStatus.OK

        when: "we have announcements and message isn't a valid keyword"
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:"Hello!", expiresAt:DateTime.now().plusDays(2))
        announce.save(flush:true, failOnError:true)
        text.message = "invalid keyword"
        assert text.validate()
        res = p1.receiveText(text, session)

        then: "delegate to phoneService to handle announcements"
        _numTimesHandleAnnouncementText == 1
        res.success == true
        res.status == ResultStatus.OK

        when: "have announcements and see announcements"
        text.message = Constants.TEXT_SEE_ANNOUNCEMENTS
        assert text.validate()
        res = p1.receiveText(text, session)
        p1.merge(flush:true)

        then: "delegate to phoneService to handle announcements"
        _numTimesHandleAnnouncementText == 1
        res.success == true
        res.status == ResultStatus.OK
        res.payload == TextResponse.SEE_ANNOUNCEMENTS

        when: "multiple receipts are not added for same announcement and session"
        res = p1.receiveText(text, session)

        then: "delegate to phoneService to handle announcements"
        _numTimesHandleAnnouncementText == 1
        res.success == true
        res.status == ResultStatus.OK
        res.payload == TextResponse.SEE_ANNOUNCEMENTS

        when: "have announcements, is NOT subscribed, toggle subscription"
        session.isSubscribedToText = false
        Phone.withSession { Session hibernateSession -> hibernateSession.flush() }
        text.message = Constants.TEXT_TOGGLE_SUBSCRIBE
        assert text.validate()
        res = p1.receiveText(text, session)

        then: "delegate to phoneService to handle announcements"
        session.isSubscribedToText == true
        res.success == true
        res.status == ResultStatus.OK
        res.payload == TextResponse.SUBSCRIBED

        when: "have announcementsm, is subscribed, toggle subscription"
        session.isSubscribedToText = true
        Phone.withSession { Session hibernateSession -> hibernateSession.flush() }
        text.message = Constants.TEXT_TOGGLE_SUBSCRIBE
        assert text.validate()
        res = p1.receiveText(text, session)

        then: "delegate to phoneService to handle announcements"
        session.isSubscribedToText == false
        res.success == true
        res.status == ResultStatus.OK
        res.payload == TextResponse.UNSUBSCRIBED
    }

    @FreshRuntime
    void "test handling incoming for call announcements"() {
        given: "no announcements and session"
        int cBaseline = Contact.count()
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        int fBaseline = FeaturedAnnouncement.count()
        int aBaseline = AnnouncementReceipt.count()
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"1112223333")
        session.save(flush:true, failOnError:true)
        assert p1.announcements.isEmpty()

        _whoIsAvailable = p1.owner.all

        when: "no digits or announcements"
        Result res = service.handleAnnouncementCall(p1, "apiId", null, session)

        then: "relay call"
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.CONNECT_INCOMING

        when: "no digits, has announcements"
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            message:"Hello!", expiresAt:DateTime.now().plusDays(2))
        announce.save(flush:true, failOnError:true)
        res = service.handleAnnouncementCall(p1, "apiId", null, session)

        then: "anouncement greeting"
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        FeaturedAnnouncement.count() == fBaseline + 1
        AnnouncementReceipt.count() == aBaseline
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.ANNOUNCEMENT_GREETING

        when: "digits, hear announcements"
        res = service.handleAnnouncementCall(p1, "apiId",
            Constants.CALL_HEAR_ANNOUNCEMENTS, session)

        then: "hear announcements"
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        FeaturedAnnouncement.count() == fBaseline + 1
        AnnouncementReceipt.count() == aBaseline + 1
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.HEAR_ANNOUNCEMENTS

        when: "digits, is NOT subscriber, toggle subscribe"
        session.isSubscribedToCall = false
        session.save(flush:true, failOnError:true)
        res = service.handleAnnouncementCall(p1, "apiId",
            Constants.CALL_TOGGLE_SUBSCRIBE, session)

        then:
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        session.isSubscribedToCall == true
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.SUBSCRIBED

        when: "digits, is subscriber, toggle subscribe"
        session.isSubscribedToCall = true
        session.save(flush:true, failOnError:true)
        res = service.handleAnnouncementCall(p1, "apiId",
            Constants.CALL_TOGGLE_SUBSCRIBE, session)

        then:
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        session.isSubscribedToCall == false
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.UNSUBSCRIBED

        when: "digits, no matching valid"
        res = service.handleAnnouncementCall(p1, "apiId", "blah", session)

        then: "relay call"
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 2
        RecordItemReceipt.count() == rBaseline + 2
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.CONNECT_INCOMING
    }

    void "test screening incoming calls"() {
        given:
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:randPhoneNumber())
        assert IncomingSession.findByPhoneAndNumberAsString(p1, session.numberAsString) == null
        session.save(flush:true, failOnError:true)

        when:
        Result<Closure> res = service.screenIncomingCall(p1, session)

        then:
        res.status == ResultStatus.OK
        res.payload == CallResponse.SCREEN_INCOMING
    }

    void "test handle self call"() {
        given: "session with no corresponding contact"
        int cBaseline = Contact.count()
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()

        when: "no digits"
        Result<Closure> res = service.handleSelfCall(p1, "apiId", null, s1)

        then: "self greeting"
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.SELF_GREETING
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline

        when: "invalid digits"
        res = service.handleSelfCall(p1, "apiId", "invalidNumber", s1)

        then:
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.SELF_INVALID_DIGITS
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline

        when: "valid digits"
        res = service.handleSelfCall(p1, "apiId", "1112223333", s1)

        then: "self connecting"
        res.success == true
        res.status == ResultStatus.OK
        res.payload == CallResponse.SELF_CONNECTING
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
    }

    void "test storing voicemail"() {
        given: "session with no corresponding contact"
        int cBaseline = Contact.count()
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()

        when: "none voicemails found for apiId"
        ResultGroup<RecordItemReceipt> resGroup = service.storeVoicemail("nonexistent", 12)

        then: "empty result list"
        resGroup.isEmpty == true
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline

        when: "apiId corresponds to NOT RecordCall"
        String apiId = "thisoneisunique!!!"
        rText1.addToReceipts(apiId:apiId, contactNumberAsString:"1234449309")
        rText1.save(flush:true, failOnError:true)
        iBaseline = RecordCall.count()
        rBaseline = RecordItemReceipt.count()
        resGroup = service.storeVoicemail(apiId, 12)

        then: "empty result list"
        resGroup.isEmpty == true
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline

        when: "apiId corresponds to multiple RecordCalls"
        apiId = "thisisthe?!best!!!"
        RecordCall rCall1 = c1.record.addCall(null, null).payload,
            rCall2 = c1.record.addCall(null, null).payload
        [rCall1, rCall2].each {
            it.addToReceipts(apiId:apiId, contactNumberAsString:"1234449309")
            it.save(flush:true, failOnError:true)
        }
        int dur = 12
        DateTime recAct = rCall1.record.lastRecordActivity
        iBaseline = RecordCall.count()
        rBaseline = RecordItemReceipt.count()
        resGroup = service.storeVoicemail(apiId, dur)

        then:
        resGroup.anySuccesses == true
        resGroup.payload.size() == 2
        resGroup.successes.size() == 2
        resGroup.payload[0].item.hasVoicemail == true
        resGroup.payload[0].item.voicemailInSeconds == dur
        resGroup.payload[0].item.record.lastRecordActivity.isAfter(recAct)
        resGroup.payload[1].item.hasVoicemail == true
        resGroup.payload[1].item.voicemailInSeconds == dur
        resGroup.payload[1].item.record.lastRecordActivity.isAfter(recAct)
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline
    }
}
