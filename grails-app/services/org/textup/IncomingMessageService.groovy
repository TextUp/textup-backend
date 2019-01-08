package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import java.util.concurrent.TimeUnit
import org.codehaus.groovy.grails.web.util.TypeConvertingMap
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class IncomingMessageService {

    AnnouncementService announcementService
    IncomingMediaService incomingMediaService
    NotificationService notificationService
    OutgoingNotificationService outgoingNotificationService
    SocketService socketService
    ThreadService threadService
    VoicemailService voicemailService

    // Texts
    // -----

    Result<Closure> processText(Phone p1, IncomingText text, IncomingSession sess1,
        TypeConvertingMap params) {

        // step 1: build texts
        buildTexts(p1, text, sess1)
            .then { Tuple<List<RecordText>, List<Contact>> processed ->
                List<RecordText> rTexts = processed.first
                notificationService.build(rTexts).curry(rTexts)
            }
            .then { List<RecordText> rTexts, List<OutgoingNotification> notifs ->
                // step 2: in a new thread, handle long-running tasks. Delay required to allow texts
                // created in this thread to save. Otherwise, when we try to get texts with the
                // following ids, they will have be saved yet and we will get `null` in return
                threadService.delay(5, TimeUnit.SECONDS) {
                    finishProcessingText(text, rTexts*.id, notifs, params)
                        .logFail("IncomingMessageService.processText: finishing processing")
                }
                // step 3: return the appropriate response while long-running tasks still processing
                buildTextResponse(p1, sess1, rTexts, notifs)
            }
    }

    // TODO simplify signature?
    protected Result<Tuple<List<RecordText>, List<Contact>>> buildTexts(Phone p1, IncomingText text,
        IncomingSession sess1) {
        List<RecordText> rTexts = []
        storeForNumber(p1, sess1.number, this.&buildTextsHelper.curry(text, sess1, rTexts.&add))
            .then { List<Contact> cList -> IOCUtils.resultFactory.success(rTexts, cList) }
    }
    protected void buildTextsHelper(IncomingText text, IncomingSession sess1,
        Closure<Void> doStoreText, Contact c1) {

        Result<RecordText> res = c1.record
            .storeIncomingText(text, sess1)
            .logFail("IncomingMessageService.buildTextsHelper: contact ${c1.id}")
        if (res.success) { doStoreText(res.payload) }
    }

    protected Result<Closure> buildTextResponse(Phone p1, IncomingSession sess1,
        List<RecordText> rTexts, List<OutgoingNotification> notifs) {

        if (rTexts) {
            List<String> responses = []
            if (notifs.isEmpty()) {
                rTexts.each { RecordText rText -> rText.hasAwayMessage = true }
                responses << p1.buildAwayMessage()
            }
            // remind about instructions if phone has announcements enabled
            announcementService
                .tryBuildTextInstructions(p1, sess1)
                .then { List<String> instructions -> TextTwiml.build(responses + instructions) }
        }
        else { TextTwiml.blocked() }
    }

    protected Result<Void> finishProcessingText(IncomingText text, List<Long> textIds,
        List<OutgoingNotification> notifs, TypeConvertingMap params) {

        Integer numMedia = params.int("NumMedia", 0)
        // if needed, process media, which includes generating versions, uploading versions,
        // and deleting copies stored by Twilio
        if (numMedia > 0) {
            ResultGroup<MediaElement> outcomes = incomingMediaService
                .process(TwilioUtils.buildIncomingMedia(numMedia, text.apiId, params))
            MediaInfo mInfo = new MediaInfo()
            for (MediaElement el1 in outcomes.payload) {
                mInfo.addToMediaElements(el1)
            }
            if (mInfo.save()) {
                finishProcessingTextHelper(text, textIds, notifs, mInfo)
            }
            else { IOCUtils.resultFactory.failWithValidationErrors(mInfo.errors) }
        }
        else { finishProcessingTextHelper(text, textIds, notifs) }
    }
    protected Result<Void> finishProcessingTextHelper(IncomingText text, List<Long> textIds,
        List<OutgoingNotification> notifs, MediaInfo mInfo = null) {

        Collection<RecordText> rTexts = rebuildRecordTexts(textIds)
        int numNotified = notifs.size()
        ResultGroup<RecordText> outcomes = new ResultGroup<>()
        rTexts.each { RecordText rText ->
            rText.media = mInfo
            rText.numNotified = numNotified
            if (!rText.save()) {
                outcomes << IOCUtils.resultFactory.failWithValidationErrors(rText.errors)
            }
        }
        if (!outcomes.anyFailures) {
            // send out notifications
            outgoingNotificationService.send(notifs, false, text.message)
                .logFail("IncomingMessageService.finishProcessingTextHelper: notifying staff")
            // For outgoing messages and all calls, we rely on status callbacks
            // to push record items to the frontend. However, for incoming texts
            // no status callback happens so we need to push the item here
            socketService.sendItems(rTexts)
                .logFail("IncomingMessageService.finishProcessingTextHelper: sending via socket")
            IOCUtils.resultFactory.success()
        }
        else { IOCUtils.resultFactory.failWithGroup(outcomes) }
    }
    protected Collection<RecordText> rebuildRecordTexts(Collection<Long> textIds) {
        Collection<RecordText> rTexts = []
        boolean didFindAll = true
        RecordText
            .getAll(textIds as Iterable<Serializable>)
            .each { RecordText rText ->
                if (rText) {
                    rTexts << rText
                }
                else { didFindAll = false }
            }
        if (!didFindAll) {
            log.error("rebuildRecordTexts: not all texts found from ids `${textIds}`")
        }
        rTexts
    }

    // Calls
    // -----

    Result<Closure> receiveCall(Phone p1, String apiId, String digits, IncomingSession session) {
        //if staff member is calling from personal phone to TextUp phone
        if (p1.owner.buildAllStaff().any { it.personalPhoneAsString == session.numberAsString }) {
            Staff staff = p1.owner.buildAllStaff().find { it.personalPhoneAsString == session.numberAsString }
            handleSelfCall(p1, apiId, digits, staff)
        }
        else if (p1.getAnnouncements()) {
            announcementService.handleAnnouncementCall(p1, digits, session,
                { relayCall(p1, apiId, session) })
        }
        else {
            relayCall(p1, apiId, session)
        }
    }

    protected Result<Closure> relayCall(Phone p1, String apiId, IncomingSession sess1) {
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
            return CallTwiml.blocked()
        }
        // try notify available staff members
        notificationService.build(rCalls)
            .then { List<OutgoingNotification> notifs ->
                if (notifs) {
                    handleNotificationsForIncomingCall(p1, sess1, notifs)
                }
                else { handleAwayForIncomingCall(p1, sess1, rCalls) }
            }
    }

    protected Result<Closure> handleNotificationsForIncomingCall(Phone p1, IncomingSession sess1,
        List<OutgoingNotification> notifs) {

        // TODO restore
        // HashSet<String> numsToCall = new HashSet<>()
        // notifs.each { OutgoingNotification bn1 ->
        //     Staff s1 = bn1.staff
        //     if (s1?.personalPhoneAsString) {
        //         numsToCall << s1.personalPhoneNumber.e164PhoneNumber
        //     }
        // }
        // CallTwiml.connectIncoming(p1.number, sess1.number, numsToCall)
    }

    protected Result<Closure> handleAwayForIncomingCall(Phone p1, IncomingSession sess1,
        List<RecordCall> rCalls) {

        rCalls.each { RecordCall rCall -> rCall.hasAwayMessage = true }
        CallTwiml.recordVoicemailMessage(p1, sess1.number)
    }

    // Self call
    // ---------

    protected Result<Closure> handleSelfCall(Phone phone, String apiId, String digits, Staff staff) {
        if (!digits) {
            return CallTwiml.selfGreeting()
        }
        PhoneNumber pNum = new PhoneNumber(number: digits)
        if (!pNum.validate()) { //then is a valid phone number
            return CallTwiml.selfInvalid(digits)
        }
        TempRecordReceipt rpt = new TempRecordReceipt(apiId: apiId)
        rpt.contactNumber = pNum
        if (!rpt.validate()) {
            log.error("IncomingMessageService.handleSelfCall: receipt errors: ${rpt.errors}")
            return CallTwiml.error()
        }
        storeForNumber(phone, pNum, this.&storeOutgoingCall.curry(staff, rpt))
        CallTwiml.selfConnecting(phone.number.e164PhoneNumber, pNum.number)
    }

    protected void storeOutgoingCall(Staff staff, TempRecordReceipt rpt, Contact c1) {
        c1.record
            .storeOutgoingCall(staff.toAuthor())
            .logFail("IncomingMessageService.storeOutgoingCall")
            .thenEnd { RecordCall rCall1 -> rCall1.addReceipt(rpt) }
    }

    // Screening
    // ---------

    Result<Closure> screenIncomingCall(Phone p1, IncomingSession sess1) {

        Contact.findEveryByNumbers(p1, [sess1.number], false)
            .then { Map<PhoneNumber, List<Contact>> numberToContacts ->
                HashSet<String> idents = new HashSet<>()
                numberToContacts.each { PhoneNumber pNum, List<Contact> contacts ->
                    contacts.each { Contact c1 ->
                        idents.add(c1.getNameOrNumber())
                    }
                }
                CallTwiml.screenIncoming(idents)
            }
    }

    // Helpers
    // -------

    protected Result<List<Contact>> storeForNumber(Phone p1, PhoneNumber pNum,
        Closure<Void> storeContact) {

        Contact.findEveryByNumbers(p1, [pNum], true)
            .then { Map<PhoneNumber, List<Contact>> numberToContacts ->
                Result.<Contact>createSuccess(CollectionUtils.flattenValues(numberToContacts))
            }
            .then { Collection<Contact> contacts ->
                ResultGroup<Void> resGroup = new ResultGroup<>()
                contacts.each { c1 ->
                    resGroup << storeAndUpdateStatusForContact(storeContact, c1)
                }
                if (resGroup.anyFailures) {
                    IOCUtils.resultFactory.failWithGroup(resGroup)
                }
                else {
                    socketService.sendContacts(contacts)
                    IOCUtils.resultFactory.success(contacts)
                }
            }
    }

    protected Result<Void> storeAndUpdateStatusForContact(Closure<Void> storeContact, Contact c1) {
        storeContact.call(c1)
        // only change status to unread
        // dont' have to worry about blocked contacts since we already filtered those out
        c1.status = PhoneRecordStatus.UNREAD
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
            if (sc1.status != PhoneRecordStatus.BLOCKED) {
                sc1.status = PhoneRecordStatus.UNREAD
                if (!sc1.save()) {
                    return IOCUtils.resultFactory.failWithValidationErrors(sc1.errors)
                }
            }
        }
        c1.save() ?
            IOCUtils.resultFactory.success() :
            IOCUtils.resultFactory.failWithValidationErrors(c1.errors)
    }
}
