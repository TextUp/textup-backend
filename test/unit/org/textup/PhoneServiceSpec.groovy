package org.textup

import com.twilio.sdk.resource.instance.IncomingPhoneNumber
import com.twilio.sdk.TwilioRestClient
import grails.test.mixin.gorm.Domain
import grails.test.mixin.hibernate.HibernateTestMixin
import grails.test.mixin.TestMixin
import grails.test.runtime.FreshRuntime
import grails.validation.ValidationErrors
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.joda.time.DateTime
import org.springframework.context.MessageSource
import org.textup.rest.TwimlBuilder
import org.textup.types.CallResponse
import org.textup.types.ContactStatus
import org.textup.types.PhoneOwnershipType
import org.textup.types.RecordItemType
import org.textup.types.ResultType
import org.textup.types.SharePermission
import org.textup.types.StaffStatus
import org.textup.types.TextResponse
import org.textup.util.CustomSpec
import org.textup.validator.BasePhoneNumber
import org.textup.validator.IncomingText
import org.textup.validator.OutgoingMessage
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Unroll
import static org.springframework.http.HttpStatus.*

@TestFor(PhoneService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt, Role, StaffRole])
@TestMixin(HibernateTestMixin)
@Unroll
class PhoneServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    String _apiId = "iamsospecial!!!"
    int _numTextsSent = 0
    List<Long> _notifyRecordIds = []
    List<Long> _notifyPhoneIds = []

    def setup() {
        setupData()
        service.resultFactory = getResultFactory()
        service.messageSource = mockMessageSource()
        service.textService = [send:{ BasePhoneNumber fromNum,
            List<? extends BasePhoneNumber> toNums, String message ->
            _numTextsSent++
            new Result(type:ResultType.SUCCESS, success:true,
                payload: new TempRecordReceipt(apiId:_apiId,
                    receivedByAsString:toNums[0].number))
        }] as TextService
        service.tokenService = [notifyStaff:{ Phone p1, Staff s1, Long recordId,
            Boolean outgoing, String msg, String instructions ->
            _numTextsSent++
            _notifyRecordIds << recordId
            _notifyPhoneIds << p1.id
            new Result(type:ResultType.SUCCESS, success:true)
        }] as TokenService
        service.callService = [start:{ PhoneNumber fromNum, PhoneNumber toNum,
            Map afterPickup ->
            new Result(type:ResultType.SUCCESS, success:true,
                payload: new TempRecordReceipt(apiId:_apiId,
                    receivedByAsString:toNum.number))
        }] as CallService
        service.socketService = [sendContacts:{ List<Contact> contacts ->
            new ResultList()
        }, sendItems:{ List<RecordItem> items ->
            new ResultList()
        }] as SocketService
        service.twimlBuilder = [build:{ code, params=[:] ->
            new Result(type:ResultType.SUCCESS, success:true, payload:code)
        }, buildTexts:{ List<String> responses ->
            new Result(type:ResultType.SUCCESS, success:true, payload:responses)
        }, translate:{ code, params=[:] ->
            new Result(type:ResultType.SUCCESS, success:true, payload:code)
        }] as TwimlBuilder
        service.authService = [getIsActive: { true }] as AuthService

        OutgoingMessage.metaClass.getMessageSource = { -> mockMessageSource() }
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
        Result<Phone> res = service.update(s1.phone, [awayMessage:msg])

        then:
        res.success == true
        res.payload instanceof Phone
        res.payload.awayMessage == msg
    }
    void "test updating phone number when not active"() {
        given:
        service.authService = [getIsActive: { false }] as AuthService

        when:
        PhoneNumber newNum = new PhoneNumber(number:'1112223333')
        assert newNum.validate()
        Result<Phone> res = service.update(s1.phone, [number:newNum.number])
        String originalNum = s1.phone.numberAsString

        then: "new number is ignored"
        res.success == true
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
        res = service.updatePhoneForApiId(s1.phone, anotherApiId)

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == UNPROCESSABLE_ENTITY
        res.payload.message == "phoneService.changeNumber.duplicate"
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
        res.payload instanceof Phone
        res.payload.numberAsString == s1.phone.numberAsString
        Phone.count() == baseline

        when: "with invalid change"
        PhoneNumber pNum = new PhoneNumber(number:"invalid123")
        res = service.updatePhoneForNumber(s1.phone, pNum)

        then:
        res.success == false
        res.payload instanceof ValidationErrors
        res.payload.errorCount == 1
        Phone.count() == baseline
    }

    @Unroll
    void "test create or update phone for staff OR team"() {
        given:
        def noPhoneEntity = forStaff ?
            new Staff(username:"6sta$iterationCount", password:"password",
                name:"Staff$iterationCount", email:"staff$iterationCount@textup.org",
                org:org, personalPhoneAsString:"1112223333",
                lockCode:Constants.DEFAULT_LOCK_CODE) :
            new Team(name:"kiki's mane", org:org.id,
                location:new Location(address:"address", lat:8G, lon:10G))
        noPhoneEntity.save(flush:true, failOnError:true)
        def withPhoneEntity = forStaff ? s1 : t1

        int pBaseline = Phone.count()
        int oBaseline = PhoneOwnership.count()
        String msg = "I am away",
            msg2 = "I am a different message!",
            msg3 = "still another message!"

        when: "for staff without a phone with invalidly formatted body"
        Result<Staff> res = service.createOrUpdatePhone(
            forStaff ? noPhoneEntity as Staff : noPhoneEntity as Team, [
            phone:msg
        ])
        assert res.success
        noPhoneEntity.save(flush:true, failOnError:true)

        then:
        if (forStaff) {
            assert res.payload instanceof Staff
        }
        else { assert res.payload instanceof Team }
        res.payload.id == noPhoneEntity.id
        Phone.count() == pBaseline
        PhoneOwnership.count() == oBaseline

        when: "when logged in staff is inactive"
        service.authService = [getIsActive: { false }] as AuthService
        withPhoneEntity.phone.awayMessage = msg2
        withPhoneEntity.phone.save(flush:true, failOnError:true)
        res = service.createOrUpdatePhone(
            forStaff ? withPhoneEntity as Staff : withPhoneEntity as Team,
            [ phone:[awayMessage:msg] ])

        then:
        res.success == true
        if (forStaff) {
            assert res.payload instanceof Staff
        }
        else { assert res.payload instanceof Team }
        res.payload.phone.awayMessage != msg
        res.payload.phone.awayMessage == msg2
        Phone.count() == pBaseline
        PhoneOwnership.count() == oBaseline

        when: "for ACTIVE staff with existing phone"
        service.authService = [getIsActive: { true }] as AuthService
        res = service.createOrUpdatePhone(
            forStaff ? withPhoneEntity as Staff : withPhoneEntity as Team,
            [ phone:[awayMessage:msg] ])

        then:
        res.success == true
        if (forStaff) {
            assert res.payload instanceof Staff
        }
        else { assert res.payload instanceof Team }
        res.payload.phone.awayMessage == msg
        Phone.count() == pBaseline
        PhoneOwnership.count() == oBaseline

        when: "for staff without a phone with validly formatted body"
        res = service.createOrUpdatePhone(
            forStaff ? noPhoneEntity as Staff : noPhoneEntity as Team,
            [ phone:[awayMessage:msg] ])
        assert res.success
        noPhoneEntity.save(flush:true, failOnError:true)

        then: "phone is created, but inactive because no number"
        Phone.count() == pBaseline + 1
        PhoneOwnership.count() == oBaseline + 1
        if (forStaff) {
            assert res.payload instanceof Staff
        }
        else { assert res.payload instanceof Team }
        res.payload.id == noPhoneEntity.id
        res.payload.hasInactivePhone == true
        res.payload.phone == null
        res.payload.phoneWithAnyStatus.awayMessage == msg
        res.payload.phoneWithAnyStatus.isActive == false

        when: "for active staff with inactive phone with validly formatted body"
        res = service.createOrUpdatePhone(
            forStaff ? noPhoneEntity as Staff : noPhoneEntity as Team,
            [ phone:[awayMessage:msg3] ])
        assert res.success
        noPhoneEntity.save(flush:true, failOnError:true)

        then: "no new phone created"
        Phone.count() == pBaseline + 1
        PhoneOwnership.count() == oBaseline + 1
        if (forStaff) {
            assert res.payload instanceof Staff
        }
        else { assert res.payload instanceof Team }
        res.payload.id == noPhoneEntity.id
        res.payload.hasInactivePhone == true
        res.payload.phone == null
        res.payload.phoneWithAnyStatus.awayMessage == msg3
        res.payload.phoneWithAnyStatus.isActive == false

        where:
        forStaff | _
        true     | _
        false    | _
    }

    void "test phone action error handling"() {
        when: "no phone actions"
        Result<Phone> res = service.handlePhoneActions(p1, [:])

        then: "return passed-in phone with no modifications"
        res.success == true
        res.payload == p1

        when: "phone actions not a list"
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            hello:"i am not a list"
        ]])

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == BAD_REQUEST
        res.payload.code == "phoneService.update.phoneActionNotList"

        when: "item in phone actions is not a map"
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            ["i", "am", "not", "a", "map"]
        ]])

        then: "ignored"
        res.success == true
        res.payload == p1

        when: "item in phone actions has an invalid action"
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [action: "i am an invalid action"]
        ]])

        then:
        res.success == false
        res.type == ResultType.MESSAGE_STATUS
        res.payload.status == BAD_REQUEST
        res.payload.code == "phoneService.update.phoneActionInvalid"
    }

    void "test phone actions calling each branch"() {
        when: "deactivating phone"
        Result<Phone> res = service.handlePhoneActions(p1, [doPhoneActions: [
            [action: Constants.PHONE_ACTION_DEACTIVATE]
        ]])
        p1.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.payload == p1
        res.payload.isActive == false

        when: "transferring phone"
        p1.apiId = null // to avoid calling freeExisting number
        p1.save(flush:true, failOnError:true)
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_TRANSFER,
                id: t1.id,
                type: PhoneOwnershipType.GROUP.toString()
            ]
        ]])
        p1.save(flush:true, failOnError:true)

        then:
        res.success == true
        res.payload == p1
        res.payload.owner.ownerId == t1.id
        res.payload.owner.type == PhoneOwnershipType.GROUP

        when: "picking new number by passing in a number"
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_NEW_NUM_BY_NUM,
                number: "invalidNum123" // so that short circuits
            ]
        ]])

        then:
        res.success == false
        res.type == ResultType.VALIDATION
        res.payload.errorCount == 1

        when: "picking new number by passing in an apiId"
        p1.apiId = "iamsospecial"
        p1.save(flush:true, failOnError:true)
        res = service.handlePhoneActions(p1, [doPhoneActions: [
            [
                action: Constants.PHONE_ACTION_NEW_NUM_BY_ID,
                numberId: p1.apiId // so that short circuits
            ]
        ]])

        then:
        res.success == true
        res.payload == p1
        res.payload.isActive == false
    }

    // Outgoing
    // --------

    void "test outgoing message that is a #type"() {
        given: "baselines"
        int iBaseline = RecordItem.count()
        int rBaseline = RecordItemReceipt.count()

        service.callService = [start:{ PhoneNumber fromNum,
            List<? extends BasePhoneNumber> toNums, Map afterPickup ->
            new Result(type:ResultType.SUCCESS, success:true,
                payload: new TempRecordReceipt(apiId:_apiId,
                    receivedByAsString:toNums[0].number))
        }] as CallService

        when: "we send to contacts, shared contacts and tags"
        OutgoingMessage msg = new OutgoingMessage(message:"hello", type:type,
            contacts:[c1, c1_1, c1_2], sharedContacts:[sc2],
            tags:[tag1, tag1_1])
        assert msg.validateSetPhone(p1)
        ResultList resList = service.sendMessage(p1, msg, s1)
        p1.save(flush:true, failOnError:true)

        HashSet<Contact> uniqueContacts = new HashSet<>(msg.contacts)
        msg.tags.each { ContactTag ct1 ->
            if (ct1.members) { uniqueContacts.addAll(ct1.members) }
        }
        List<Integer> tagNumMembers = msg.tags.collect { it.members.size() ?: 0 }
        int totalTagMembers = tagNumMembers.sum()

        then: "no duplication, also stored in tags"
        resList.isAllSuccess == true
        // one result for each contact
        // one result for each shared contact
        // one result for each tag
        resList.results.size() == uniqueContacts.size() +
            msg.sharedContacts.size() + msg.tags.size()
        resList.results.each { it.payload instanceof RecordItem }
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

        where:
        type                | _
        RecordItemType.TEXT | _
        RecordItemType.CALL | _
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
            new Result(type:ResultType.SUCCESS, success:true, payload:[params.message])
        }] as TwimlBuilder

        when: "for session with no contacts"
        String message = "hello"
        String ident = "kiki bai"
        ResultMap resMap = service.sendTextAnnouncement(p1, message, ident,
            [session], s1)

        then:
        RecordText.count() == tBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        Contact.count() == cBaseline + 1
        ContactNumber.count() == nBaseline + 1
        resMap[session.numberAsString].success == true
        resMap[session.numberAsString].payload instanceof TempRecordReceipt
        resMap[session.numberAsString].payload
            .receivedByAsString == session.numberAsString

        when: "for session with multiple contacts"
        resMap = service.sendTextAnnouncement(p1, message, ident, [session], s1)

        then:
        RecordText.count() == tBaseline + 2
        RecordItemReceipt.count() == rBaseline + 2
        Contact.count() == cBaseline + 1
        ContactNumber.count() == nBaseline + 1
        resMap[session.numberAsString].success == true
        resMap[session.numberAsString].payload instanceof TempRecordReceipt
        resMap[session.numberAsString].payload
            .receivedByAsString == session.numberAsString
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
        ResultMap resMap = service.startCallAnnouncement(p1, message, ident,
            [session], s1)

        then:
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        Contact.count() == cBaseline + 1
        ContactNumber.count() == nBaseline + 1
        resMap[session.numberAsString].success == true
        resMap[session.numberAsString].payload instanceof TempRecordReceipt
        resMap[session.numberAsString].payload
            .receivedByAsString == session.numberAsString
    }

    // Incoming
    // --------

    void "test name or number"() {
        given: "a session"
        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString:"1238470294")
        assert IncomingSession.findByPhoneAndNumberAsString(p1,
            session.numberAsString) == null
        session.save(flush:true, failOnError:true)

        when: "empty list of contacts"
        String n1 = service.getNameOrNumber([], session)

        then:
        n1 == Helpers.formatNumberForSay(session.numberAsString)

        when: "some contacts"
        c1.name = "kiki's chicken"
        c1.save(flush:true, failOnError:true)
        n1 = service.getNameOrNumber([c1], session)

        then:
        n1 == c1.name
    }

    @FreshRuntime
    void "test relay text"() {
        given: "none available and should send instructions"
        int cBaseline = Contact.count()
        int tBaseline = RecordText.count()
        int rBaseline = RecordItemReceipt.count()
        p1.availableNow.each { Staff s1 ->
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
        }
        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString:"1112223456",
            lastSentInstructions:DateTime.now().minusDays(1))
        session.save(flush:true, failOnError:true)
        assert p1.announcements.isEmpty()

        when: "for session without contact"
        IncomingText text = new IncomingText(apiId:"iamsosecret", message:"hello")
        assert text.validate()
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
        res.payload == [p1.awayMessage, TextResponse.INSTRUCTIONS_UNSUBSCRIBED]

        when: "for session with one blocked contact"
        Contact c1 = Contact.listForPhoneAndNum(p1, session.number)[0]
        c1.status = ContactStatus.BLOCKED
        c1.save(flush:true, failOnError:true)
        res = service.relayText(p1, text, session)

        then: "new contact not created, instructions not sent"
        Contact.count() == cBaseline + 1
        RecordText.count() == tBaseline + 3
        RecordItemReceipt.count() == rBaseline + 3
        Contact.listForPhoneAndNum(p1, session.number).size() == 1
        Contact.listForPhoneAndNum(p1, session.number)[0]
            .status == ContactStatus.BLOCKED
        session.shouldSendInstructions == false
        res.success == true
        res.payload == [p1.awayMessage]

        when: "for session with multiple contacts"
        Contact dupCont = p1.createContact([:], [session.number]).payload
        dupCont.save(flush:true, failOnError:true)
        res = service.relayText(p1, text, session)

        then: "no contact created, instructions not sent"
        Contact.count() == cBaseline + 2 // one more from our duplicate contact
        RecordText.count() == tBaseline + 5
        RecordItemReceipt.count() == rBaseline + 5
        Contact.listForPhoneAndNum(p1, session.number).size() == 2
        session.shouldSendInstructions == false
        res.success == true
        res.payload == [p1.awayMessage]
    }

    @FreshRuntime
    void "test relaying text for contact that is shared"() {
        given: "baselines, availability, new session"
        int cBaseline = Contact.count()
        int tBaseline = RecordText.count()
        int rBaseline = RecordItemReceipt.count()
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
        sBy.owner.all.each { Staff s1 ->
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
        }
        // phone that contact is shared with has staff that are available
        sWith.owner.all.each { Staff s1 ->
            s1.manualSchedule = true
            s1.isAvailable = true
            s1.save(flush:true, failOnError:true)
        }
        [sBy, sWith]*.save(flush:true, failOnError:true)

        IncomingText text = new IncomingText(apiId:"iamsosecret", message:"hello")
        assert text.validate()
        _numTextsSent = 0
        _notifyRecordIds = []
        _notifyPhoneIds = []
        Result res = service.relayText(sBy, text, session)

        then: "no away message sent and only available staff notified"
        res.success == true
        res.payload == []
        _numTextsSent == sWith.owner.all.size()
        _notifyRecordIds.every { it == sc1.contact.record.id }
        _notifyPhoneIds.every { it == sWith.id }

        when: "that shared with phone's staff owner is no longer available"
        // phone that contact is shared with has staff that are available
        sWith.owner.all.each { Staff s1 ->
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
        }
        sWith.save(flush:true, failOnError:true)
        _numTextsSent = 0
        res = service.relayText(sBy, text, session)

        then: "away message is sent since no staff are available"
        res.success == true
        res.payload == [sBy.awayMessage]
        _numTextsSent == 0
    }

    void "test notify staff"() {
        given: "a staff member with a personal phone who is a member of a team \
            and who has a contact shared by the team to the staff's personal phone"
        // asserting relationships
        assert s1 in t1.members
        assert s1.phone == p1 && c1.phone == p1
        assert t1.phone == tPh1 && tC1.phone == tPh1
        // sharing contact
        tPh1.stopShare(tC1)
        SharedContact shared1 = tPh1.share(tC1, p1, SharePermission.DELEGATE).payload
        shared1.save(flush:true, failOnError:true)
        // make all othe staff members unavailable!
        t1.members.each { Staff otherStaff ->
            otherStaff.manualSchedule = true
            otherStaff.isAvailable = false
            otherStaff.save(flush:true, failOnError:true)
        }
        // ensuring that staff is available
        s1.manualSchedule = true
        s1.isAvailable = true
        s1.save(flush:true, failOnError:true)
        assert s1.isAvailableNow() == true

        when: "incoming text to the team's phone number"
        String msg = "hi!"
        _numTextsSent = 0
        _notifyRecordIds = []
        _notifyPhoneIds = []
        boolean didNotify = service.tryNotifyStaff(tPh1, msg, [tC1])

        then: "staff should only receive notification from the staff's personal \
            TextUp phone through the shared relationship"
        didNotify == true
        _numTextsSent == 1
        _notifyRecordIds == [tC1.record.id]
        _notifyPhoneIds == [s1.phone.id]

        when: "staff is no longer shared on contact and incoming text to team number"
        shared1.stopSharing()
        shared1.save(flush:true, failOnError:true)

        _numTextsSent = 0
        _notifyRecordIds = []
        _notifyPhoneIds = []
        didNotify = service.tryNotifyStaff(tPh1, msg, [tC1])

        then: "notification from the team TextUp phone only"
        didNotify == true
        _numTextsSent == 1
        _notifyRecordIds == [tC1.record.id]
        _notifyPhoneIds == [tC1.phone.id]

        when: "staff is shared again but is no longer member of team and \
            incoming text to the team number"
        SharedContact shared2 = tPh1.share(tC1, p1, SharePermission.DELEGATE).payload
        shared2.save(flush:true, failOnError:true)

        t1.removeFromMembers(s1)
        t1.save(flush:true, failOnError:true)
        assert (s1 in t1.members) == false

        _numTextsSent = 0
        _notifyRecordIds = []
        _notifyPhoneIds = []
        didNotify = service.tryNotifyStaff(tPh1, msg, [tC1])

        then: "notification from the staff's personal TextUp phone only because \
            of the shared relationship"
        didNotify == true
        _numTextsSent == 1
        _notifyRecordIds == [tC1.record.id]
        _notifyPhoneIds == [s1.phone.id]
    }

    @FreshRuntime
    void "test relay call"() {
        given: "session with no corresponding contact"
        int cBaseline = Contact.count()
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        p1.owner.all.each { Staff s1 ->
            s1.manualSchedule = true
            s1.isAvailable = true
            s1.save(flush:true, failOnError:true)
        }
        IncomingSession session = new IncomingSession(phone:p1,
            numberAsString:"1112223456",
            lastSentInstructions:DateTime.now().minusDays(1))
        session.save(flush:true, failOnError:true)

        when: "call for available"
        Result res = service.relayCall(p1, "apiId", session)

        then: "contact created, call connecting"
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        res.success == true
        res.payload == CallResponse.CONNECT_INCOMING

        when: "none avabilable"
        p1.availableNow.each { Staff s1 ->
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
        }
        res = service.relayCall(p1, "apiId", session)

        then: "no additional contact created, voicemail"
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 2
        RecordItemReceipt.count() == rBaseline + 2
        res.success == true
        res.payload == CallResponse.VOICEMAIL
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

        when: "call sharedBy is unavailable but sharedWith is available"
        sBy.owner.all.each { Staff s1 ->
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
        }
        sWith.owner.all.each { Staff s1 ->
            s1.manualSchedule = true
            s1.isAvailable = true
            s1.save(flush:true, failOnError:true)
        }
        Result res = service.relayCall(sBy, "apiId", session)

        then: "call connecting"
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        res.success == true
        res.payload == CallResponse.CONNECT_INCOMING

        when: "none avabilable"
        sWith.owner.all.each { Staff s1 ->
            s1.manualSchedule = true
            s1.isAvailable = false
            s1.save(flush:true, failOnError:true)
        }
        res = service.relayCall(sBy, "apiId", session)

        then: "voicemail"
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline + 2
        RecordItemReceipt.count() == rBaseline + 2
        res.success == true
        res.payload == CallResponse.VOICEMAIL
    }

    @FreshRuntime
    void "test handling call announcements"() {
        given: "no announcements and session"
        int cBaseline = Contact.count()
        int iBaseline = RecordCall.count()
        int rBaseline = RecordItemReceipt.count()
        int fBaseline = FeaturedAnnouncement.count()
        int aBaseline = AnnouncementReceipt.count()
        p1.owner.all.each { Staff s1 ->
            s1.manualSchedule = true
            s1.isAvailable = true
            s1.save(flush:true, failOnError:true)
        }
        IncomingSession session = new IncomingSession(phone:p1, numberAsString:"1112223333")
        session.save(flush:true, failOnError:true)
        assert p1.announcements.isEmpty()

        when: "no digits or announcements"
        Result res = service.handleAnnouncementCall(p1, "apiId", null, session)

        then: "relay call"
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        res.success == true
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
        res.payload == CallResponse.UNSUBSCRIBED

        when: "digits, no matching valid"
        res = service.handleAnnouncementCall(p1, "apiId", "blah", session)

        then: "relay call"
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 2
        RecordItemReceipt.count() == rBaseline + 2
        res.success == true
        res.payload == CallResponse.CONNECT_INCOMING
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
        res.payload == CallResponse.SELF_GREETING
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline

        when: "invalid digits"
        res = service.handleSelfCall(p1, "apiId", "invalidNumber", s1)

        then:
        res.success == true
        res.payload == CallResponse.SELF_INVALID_DIGITS
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline

        when: "valid digits"
        res = service.handleSelfCall(p1, "apiId", "1112223333", s1)

        then: "self connecting"
        res.success == true
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
        ResultList resList = service.storeVoicemail("nonexistent", 12)

        then: "empty result list"
        resList.results.isEmpty()
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline

        when: "apiId corresponds to NOT RecordCall"
        String apiId = "thisoneisunique!!!"
        rText1.addToReceipts(apiId:apiId, receivedByAsString:"1234449309")
        rText1.save(flush:true, failOnError:true)
        iBaseline = RecordCall.count()
        rBaseline = RecordItemReceipt.count()
        resList = service.storeVoicemail(apiId, 12)

        then: "empty result list"
        resList.results.isEmpty()
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline

        when: "apiId corresponds to multiple RecordCalls"
        apiId = "thisisthe?!best!!!"
        RecordCall rCall1 = c1.record.addCall(null, null).payload,
            rCall2 = c1.record.addCall(null, null).payload
        [rCall1, rCall2].each {
            it.addToReceipts(apiId:apiId, receivedByAsString:"1234449309")
            it.save(flush:true, failOnError:true)
        }
        int dur = 12
        DateTime recAct = rCall1.record.lastRecordActivity
        iBaseline = RecordCall.count()
        rBaseline = RecordItemReceipt.count()
        resList = service.storeVoicemail(apiId, dur)

        then:
        resList.isAllSuccess == true
        resList.results.size() == 2
        resList.results[0].payload.item.hasVoicemail == true
        resList.results[0].payload.item.voicemailInSeconds == dur
        resList.results[0].payload.item.record.lastRecordActivity.isAfter(recAct)
        resList.results[1].payload.item.hasVoicemail == true
        resList.results[1].payload.item.voicemailInSeconds == dur
        resList.results[1].payload.item.record.lastRecordActivity.isAfter(recAct)
        Contact.count() == cBaseline
        RecordCall.count() == iBaseline
        RecordItemReceipt.count() == rBaseline
    }
}
