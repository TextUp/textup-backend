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
import org.textup.types.PhoneOwnershipType
import org.textup.types.RecordItemType
import org.textup.types.ResultType
import org.textup.types.StaffStatus
import org.textup.types.ContactStatus
import org.textup.types.TextResponse
import org.textup.types.CallResponse
import org.textup.util.CustomSpec
import org.textup.validator.BasePhoneNumber
import org.textup.validator.IncomingText
import org.textup.validator.OutgoingText
import org.textup.validator.PhoneNumber
import org.textup.validator.TempRecordReceipt
import spock.lang.Ignore
import spock.lang.Shared

@TestFor(PhoneService)
@Domain([Contact, Phone, ContactTag, ContactNumber, Record, RecordItem, RecordText,
    RecordCall, RecordItemReceipt, SharedContact, Staff, Team, Organization, Schedule,
    Location, WeeklySchedule, PhoneOwnership, FeaturedAnnouncement, IncomingSession,
    AnnouncementReceipt])
@TestMixin(HibernateTestMixin)
class PhoneServiceSpec extends CustomSpec {

    static doWithSpring = {
        resultFactory(ResultFactory)
    }

    String _apiId = "iamsospecial!!!"
    int _maxNumReceipients = 100
    String _textedMessage

    def setup() {
        setupData()
        service.resultFactory = getResultFactory()
        service.grailsApplication = [getFlatConfig: {
            ["textup.maxNumText":_maxNumReceipients]
        }] as GrailsApplication
        service.textService = [send:{ BasePhoneNumber fromNum,
            List<? extends BasePhoneNumber> toNums, String message ->
            _textedMessage = message
            new Result(type:ResultType.SUCCESS, success:true,
                payload: new TempRecordReceipt(apiId:_apiId,
                    receivedByAsString:toNums[0].number))
        }] as TextService
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
    }

    def cleanup() {
        cleanupData()
    }

    // Numbers
    // -------

    void "test update phone number degenerate cases"() {
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

        when: "with same phone number apiId"
        res = service.updatePhoneForApiId(s1.phone, oldApiId)

        then:
        res.success == true
        res.payload instanceof Phone
        res.payload.apiId == oldApiId
        Phone.count() == baseline
    }

    // Outgoing
    // --------

    void "test outgoing text"() {
        given: "baselines"
        int tBaseline = RecordText.count()
        int rBaseline = RecordItemReceipt.count()

        when: "too many recipients"
        _maxNumReceipients = 1
        OutgoingText text = new OutgoingText(message:"hello",
            contacts:[c1, c1_1, c1_2])
        assert text.validateSetPhone(p1)
        ResultList resList = service.sendText(p1, text, s1)

        then:
        resList.isAnySuccess == false
        resList.results.size() == 1
        resList.results[0].success == false
        resList.results[0].type == ResultType.MESSAGE
        resList.results[0].payload.code == "phone.sendText.tooMany"
        RecordText.count() == tBaseline
        RecordItemReceipt.count() == rBaseline

        when: "we send to contacts, shared contacts and tags"
        _maxNumReceipients = 100
        text = new OutgoingText(message:"hello",
            contacts:[c1, c1_1, c1_2], sharedContacts:[sc2],
            tags:[tag1, tag1_1])
        assert text.validateSetPhone(p1)
        resList = service.sendText(p1, text, s1)
        p1.save(flush:true, failOnError:true)

        HashSet<Contact> uniqueContacts = new HashSet<>(text.contacts)
        text.tags.each { ContactTag ct1 ->
            if (ct1.members) { uniqueContacts.addAll(ct1.members) }
        }
        List<Integer> tagNumMembers = text.tags.collect { it.members.size() ?: 0 }
        int totalTagMembers = tagNumMembers.sum()

        then: "no duplication, also stored in tags"
        resList.isAllSuccess == true
        // one result for each contact
        // one result for each shared contact
        // one result for each tag
        resList.results.size() == uniqueContacts.size() +
            text.sharedContacts.size() + text.tags.size()
        resList.results.each { it.payload instanceof RecordText }
        // one text for each contact
        // one text for each shared contact
        // one text for each tag
        RecordText.count() == tBaseline + uniqueContacts.size() +
            text.sharedContacts.size() + text.tags.size()
        // one receipt for each contact
        // one receipt for each shared contact
        // one receipt for each member of each tag
        RecordItemReceipt.count() == rBaseline + uniqueContacts.size() +
            text.sharedContacts.size() + totalTagMembers
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
        n1 == session.numberAsString

        when: "some contacts"
        c1.name = "kiki's chicken"
        c1.save(flush:true, failOnError:true)
        n1 = service.getNameOrNumber([c1], session)

        then:
        n1 == c1.name
    }

    void "test notify staff"() {
        when: "notify for message longer than text length"
        String message = Helpers.randomAlphanumericString(Constants.TEXT_LENGTH)
        String ident = "kiki bai"
        IncomingText text = new IncomingText(apiId:"apiId", message:message)
        Result<TempRecordReceipt> res = service.notifyStaff(s1, text, ident)

        then:
        res.success == true
        res.payload instanceof TempRecordReceipt
        _textedMessage.size() == Constants.TEXT_LENGTH

        when: "shorter than text length"
        message = Helpers.randomAlphanumericString(2)
        text = new IncomingText(apiId:"apiId", message:message)
        res = service.notifyStaff(s1, text, ident)

        then:
        res.success == true
        res.payload instanceof TempRecordReceipt
        _textedMessage.size() < Constants.TEXT_LENGTH
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

        when: "digits, subscribe"
        res = service.handleAnnouncementCall(p1, "apiId",
            Constants.CALL_SUBSCRIBE, session)

        then:
        Contact.count() == cBaseline + 1
        RecordCall.count() == iBaseline + 1
        RecordItemReceipt.count() == rBaseline + 1
        session.isSubscribedToCall == true
        res.success == true
        res.payload == CallResponse.SUBSCRIBED

        when: "digits, greeting unsubscribe"
        res = service.handleAnnouncementCall(p1, "apiId",
            Constants.CALL_GREETING_UNSUBSCRIBE, session)

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
