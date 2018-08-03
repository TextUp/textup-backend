package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.commons.lang3.tuple.Pair
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class IncomingMessageService {

    AnnouncementService announcementService
    NotificationService notificationService
    ResultFactory resultFactory
    SocketService socketService
    TokenService tokenService
    TwimlBuilder twimlBuilder
    MessageSource messageSource

    // Texts
    // -----

    Result<Closure> relayText(Phone p1, IncomingText text, IncomingSession sess1,
        MediaInfo mInfo = null) {

        List<RecordText> rTexts = []
        storeForNumber(p1, sess1.number, this.&storeIncomingText.curry(text, sess1, mInfo, rTexts.&add))
            // do not curry to enable mocking for testing
            .then { List<Contact> cList -> afterStoreForText(p1, text, sess1, rTexts, cList) }
    }

    protected void storeIncomingText(IncomingText text, IncomingSession session,
        MediaInfo mInfo, Closure<Void> storeText, Contact c1) {

        Result<RecordText> res = c1.record
            .storeIncomingText(text, session, mInfo)
            .logFail("IncomingMessageService.storeIncomingText: contact ${c1.id}")
        if (res.success) {
            storeText(res.payload)
        }
    }

    protected Result<Closure> afterStoreForText(Phone p1, IncomingText text, IncomingSession sess1,
        List<RecordText> rTexts, List<Contact> notBlockedContacts) {

        // if contacts is empty, then return a message notifying the texter
        // that he or she has been blocked by the user
        if (notBlockedContacts.isEmpty()) {
            return twimlBuilder.build(TextResponse.BLOCKED)
        }
        // try notify available staff members
        List<BasicNotification> notifs = notificationService.build(p1, notBlockedContacts)
        if (notifs) {
            handleNotificationsForIncomingText(p1, text, sess1, rTexts, notifs)
        }
        else { handleAwayForIncomingText(p1, sess1, rTexts) }
    }

    protected Result<Closure> handleNotificationsForIncomingText(Phone p1, IncomingText text,
        IncomingSession sess1, List<RecordText> rTexts, List<BasicNotification> notifs) {

        String instructions = messageSource.getMessage(
            "incomingMessageService.notifyStaff.notification",
            null,
            LCH.getLocale())
        notifs.each { BasicNotification bn1 ->
            tokenService
                .notifyStaff(bn1, false, text.message, instructions)
                .logFail("IncomingMessageService.notifyStaffAboutMessage: calling notifyStaff")
        }
        buildIncomingTextResponse(p1, sess1, rTexts)
    }

    protected Result<Closure> handleAwayForIncomingText(Phone p1, IncomingSession sess1,
        List<RecordText> rTexts) {

        rTexts.each { RecordText rText -> rText.hasAwayMessage = true }
        buildIncomingTextResponse(p1, sess1, rTexts, [p1.awayMessage])
    }

    protected Result<Closure> buildIncomingTextResponse(Phone p1, IncomingSession sess1,
        List<RecordText> rTexts, List<String> responses = []) {
        // remind about instructions if phone has announcements enabled
        announcementService
            .tryBuildTextInstructions(p1, sess1)
            .then { List<String> instructions ->
                // For outgoing messages and all calls, we rely on status callbacks
                // to push record items to the frontend. However, for incoming texts
                // no status callback happens so we need to push the item here
                socketService.sendItems(rTexts)
                twimlBuilder.buildTexts(responses + instructions)
            }
    }

    // Calls
    // -----

    Result<Closure> relayCall(Phone p1, String apiId, IncomingSession sess1) {
        List<RecordCall> rCalls = []
        storeForNumber(p1, sess1.number, this.&storeIncomingCall.curry(apiId, sess1, rCalls.&add))
            // do not curry to enable mocking for testing
            .then { List<Contact> cList -> afterStoreForCall(p1, sess1, rCalls, cList) }
    }

    protected void storeIncomingCall(String uid, IncomingSession sess1, Closure<Void> storeCall, Contact c1) {
        Result<RecordCall> res = c1.record
            .storeIncomingCall(uid, sess1)
            .logFail("IncomingMessageService.storeIncomingCall")
        if (res.success) {
            storeCall(res.payload)
        }
    }

    protected Result<Closure> afterStoreForCall(Phone p1, IncomingSession sess1,
        List<RecordCall> rCalls, List<Contact> notBlockedContacts) {

        // if contacts is empty, then he or she has been blocked by the user
        if (notBlockedContacts.isEmpty()) {
            return twimlBuilder.build(CallResponse.BLOCKED)
        }
        // try notify available staff members
        List<BasicNotification> notifs = notificationService.build(p1, notBlockedContacts)
        if (notifs) {
            handleNotificationsForIncomingCall(p1, sess1, notifs)
        }
        else { handleAwayForIncomingCall(p1, sess1, rCalls) }
    }

    protected Result<Closure> handleNotificationsForIncomingCall(Phone p1, IncomingSession sess1,
        List<BasicNotification> notifs) {

        HashSet<String> numsToCall = new HashSet<>()
        notifs.each { BasicNotification bn1 ->
            Staff s1 = bn1.staff
            if (s1?.personalPhoneAsString) {
                numsToCall << s1.personalPhoneNumber.e164PhoneNumber
            }
        }
        twimlBuilder.build(CallResponse.CONNECT_INCOMING, [
            displayedNumber:p1.number.e164PhoneNumber,
            numsToCall:numsToCall,
            linkParams:[handle:CallResponse.CHECK_IF_VOICEMAIL],
            screenParams:[
                handle:CallResponse.SCREEN_INCOMING,
                originalFrom: sess1.number.e164PhoneNumber
            ]
        ])
    }

    protected Result<Closure> handleAwayForIncomingCall(Phone p1, IncomingSession sess1,
        List<RecordCall> rCalls) {

        rCalls.each { RecordCall rCall -> rCall.hasAwayMessage = true }
        // must pass in a non-success status because we will not start voicemail
        // if the call has already completed successfully
        p1.tryStartVoicemail(sess1.number, p1.number, ReceiptStatus.PENDING)
    }

    // Other incoming call handlers
    // ----------------------------

    Result<Closure> screenIncomingCall(Phone phone, IncomingSession session) {
        List<Contact> notBlockedContacts = getDeliverableContacts(phone, session.number).right
        HashSet<String> idents = new HashSet<>()
        notBlockedContacts.each { Contact c1 -> idents.add(c1.getNameOrNumber()) }
        twimlBuilder.build(CallResponse.SCREEN_INCOMING, [
            callerId: Helpers.joinWithDifferentLast(new ArrayList<String>(idents), ", ", " or "),
            linkParams:[handle:CallResponse.DO_NOTHING]
        ])
    }

    Result<Closure> handleSelfCall(Phone phone, String apiId, String digits, Staff staff) {
        if (!digits) {
            return twimlBuilder.build(CallResponse.SELF_GREETING)
        }
        PhoneNumber pNum = new PhoneNumber(number:digits)
        if (!pNum.validate()) { //then is a valid phone number
            return twimlBuilder.build(CallResponse.SELF_INVALID_DIGITS, [digits:digits])
        }
        TempRecordReceipt rpt = new TempRecordReceipt(apiId: apiId)
        rpt.contactNumber = pNum
        if (!rpt.validate()) {
            log.error("IncomingMessageService.handleSelfCall: receipt errors: ${rpt.errors}")
            return twimlBuilder.errorForCall()
        }
        storeForNumber(phone, pNum, this.&storeOutgoingCall.curry(staff, rpt))
        twimlBuilder.build(CallResponse.SELF_CONNECTING,
            [displayedNumber:phone.number.e164PhoneNumber, numAsString:pNum.number])
    }

    protected void storeOutgoingCall(Staff staff, TempRecordReceipt rpt, Contact c1) {
        c1.record
            .storeOutgoingCall(staff.toAuthor())
            .logFail("IncomingMessageService.storeOutgoingCall")
            .thenEnd { RecordCall rCall1 -> rCall1.addReceipt(rpt) }
    }

    // Helpers
    // -------

    protected Pair<List<Contact>, List<Contact>> getDeliverableContacts(Phone phone, PhoneNumber pNum) {
        List<Contact> contacts = Contact.listForPhoneAndNum(phone, pNum),
            notBlockedContacts = contacts.findAll { Contact c1 ->
                c1.status != ContactStatus.BLOCKED
            } as List<Contact>
        Pair.of(contacts, notBlockedContacts)
    }

    protected Result<List<Contact>> storeForNumber(Phone phone, PhoneNumber pNum,
        Closure<Void> storeContact) {

        Pair<List<Contact>, List<Contact>> deliverables = getDeliverableContacts(phone, pNum)
        List<Contact> contacts = deliverables.left,
            notBlockedContacts = deliverables.right
        // only create new contact if no blocked contact either because
        // we don't want to create a new contact if there is a blocked contact associated
        // with the incoming number
        if (contacts.isEmpty()) {
            Result res = phone.createContact([:], [pNum.number])
            if (res.success) {
                notBlockedContacts = [res.payload]
            }
            else { return res }
        }
        //add text to contact records
        //note that blocked contacts will not even have the incoming message be stored
        for (Contact c1 in notBlockedContacts) {
            Result<Void> res = storeAndUpdateStatusForContact(storeContact, c1)
            if (!res.success) { return res }
        }
        // socket notify of new contact and/or unread contacts
        socketService.sendContacts(notBlockedContacts)
        resultFactory.success(notBlockedContacts)
    }

    protected Result<Void> storeAndUpdateStatusForContact(Closure<Void> storeContact, Contact c1) {
        storeContact(c1)
        // only change status to unread
        // dont' have to worry about blocked contacts since we already filtered those out
        c1.status = ContactStatus.UNREAD
        // NOTE: because we've already screened out all contacts that have been blocked
        // by the owner of the contact, this effectively means that blocking also effectively
        // stops all sharing relationships because we do not even attempt to deliver
        // message to shared contacts that have had their original contact blocked by
        // the original owner
        List<SharedContact> sharedContacts = c1.sharedContacts
        for (SharedContact sc1 in sharedContacts) {
            // only marked the shared contact's status as unread IF the shared contact's
            // status is NOT blocked. If the collaborator has blocked this contact then
            // we want to respect that decision.
            if (sc1.status != ContactStatus.BLOCKED) {
                sc1.status = ContactStatus.UNREAD
                if (!sc1.save()) {
                    return resultFactory.failWithValidationErrors(sc1.errors)
                }
            }
        }
        c1.save() ? resultFactory.success() : resultFactory.failWithValidationErrors(c1.errors)
    }
}
