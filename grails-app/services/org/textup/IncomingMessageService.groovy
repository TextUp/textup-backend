package org.textup

import grails.compiler.GrailsCompileStatic
import grails.transaction.Transactional
import org.apache.commons.lang3.tuple.Pair
import org.springframework.context.i18n.LocaleContextHolder as LCH
import org.springframework.context.MessageSource
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.validator.*

@GrailsCompileStatic
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

    Result<Closure> relayText(Phone phone, IncomingText text, IncomingSession session,
        MediaInfo mInfo = null) {

        List<RecordText> rTexts = []
        storeForNumber(phone, session.number, { Contact c1 ->
            Result<RecordText> res = c1.record
                .storeIncomingText(text, session, mInfo)
                .logFail("IncomingMessageService.relayText: store text for contact ${c1.id}")
            if (res.success) {
                rTexts << res.payload
            }
        }).then({ List<Contact> contacts ->
            // if contacts is empty, then return a message notifying the texter
            // that he or she has been blocked by the user
            if (contacts.isEmpty()) {
                return twimlBuilder.build(TextResponse.BLOCKED)
            }
            // notify available staff members
            List<String> responses = [] // list of string and text resonses
            List<BasicNotification> notifs = notificationService.build(phone, contacts)
            if (notifs) {
                String instructions = messageSource.getMessage("incomingMessageService.notifyStaff.notification",
                    null, LCH.getLocale())
                notifs.each { BasicNotification bn1 ->
                    tokenService
                        .notifyStaff(bn1, false, text.message, instructions)
                        .logFail("IncomingMessageService.relayText: calling notifyStaff")
                }
            }
            else {
                rTexts.each { RecordText rText -> rText.hasAwayMessage = true }
                responses << phone.awayMessage
            }
            // remind about instructions if phone has announcements enabled
            announcementService
                .tryBuildTextInstructions(phone, session)
                .then { List<String> instructions ->
                    // For outgoing messages and all calls, we rely on status callbacks
                    // to push record items to the frontend. However, for incoming texts
                    // no status callback happens so we need to push the item here
                    socketService.sendItems(rTexts)
                    twimlBuilder.buildTexts(responses + instructions)
                }
        })
    }

    // Calls
    // -----

    Result<Closure> relayCall(Phone phone, String apiId, IncomingSession session) {
        List<RecordCall> rCalls = []
        storeForNumber(phone, session.number, { Contact c1 ->
            Result<RecordCall> res = c1.record
                .storeIncomingCall(apiId, session)
                .logFail("IncomingMessageService.relayCall")
            if (res.success) {
                rCalls << res.payload
            }
        }).then({ List<Contact> contacts ->
            // if contacts is empty, then he or she has been blocked by the user
            if (contacts.isEmpty()) {
                return twimlBuilder.build(CallResponse.BLOCKED)
            }
            HashSet<String> numsToCall = new HashSet<>()
            notificationService.build(phone, contacts).each { BasicNotification bn1 ->
                Staff s1 = bn1.staff
                if (s1?.personalPhoneAsString) {
                    numsToCall << s1.personalPhoneNumber.e164PhoneNumber
                }
            }
            if (numsToCall) {
                twimlBuilder.build(CallResponse.CONNECT_INCOMING, [
                    displayedNumber:phone.number.e164PhoneNumber,
                    numsToCall:numsToCall,
                    linkParams:[handle:CallResponse.CHECK_IF_VOICEMAIL],
                    screenParams:[
                        handle:CallResponse.SCREEN_INCOMING,
                        originalFrom: session.number.e164PhoneNumber
                    ]
                ])
            }
            else {
                rCalls.each { RecordCall rCall -> rCall.hasAwayMessage = true }
                // must pass in a non-success status because we will not start voicemail
                // if the call has already completed successfully
                phone.tryStartVoicemail(session.number, phone.number, ReceiptStatus.PENDING)
            }
        })
    }

    Result<Closure> screenIncomingCall(Phone phone, IncomingSession session) {
        List<Contact> notBlockedContacts = getDeliverableContacts(phone, session.number).right
        List<String> uniques = []
        notBlockedContacts.each { Contact c1 -> uniques << c1.getNameOrNumber() }
        uniques.unique() // in-place modification
        twimlBuilder.build(CallResponse.SCREEN_INCOMING, [
            callerId: Helpers.joinWithDifferentLast(uniques, ", ", " or "),
            linkParams:[handle:CallResponse.DO_NOTHING]
        ])
    }

    Result<Closure> handleSelfCall(Phone phone, String apiId, String digits, Staff staff) {
        if (digits) {
            PhoneNumber pNum = new PhoneNumber(number:digits)
            if (pNum.validate()) { //then is a valid phone number
                TempRecordReceipt receipt = new TempRecordReceipt(apiId:apiId)
                receipt.contactNumber = pNum
                if (receipt.validate()) {
                    storeForNumber(phone, pNum, { Contact c1 ->
                        c1.record
                            .storeOutgoingCall(staff.toAuthor())
                            .logFail("IncomingMessageService.handleSelfCall")
                            .then { RecordCall rCall1 -> rCall1.addReceipt(receipt) }
                    })
                    twimlBuilder.build(CallResponse.SELF_CONNECTING,
                        [displayedNumber:phone.number.e164PhoneNumber, numAsString:pNum.number])
                }
                else {
                    log.error("IncomingMessageService.handleSelfCall: could not save \
                        receipt: ${receipt.errors}")
                    twimlBuilder.errorForCall()
                }
            }
            else {
                twimlBuilder.build(CallResponse.SELF_INVALID_DIGITS, [digits:digits])
            }
        }
        else {
            twimlBuilder.build(CallResponse.SELF_GREETING)
        }
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
        Closure<Void> contactStoreAction) {

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
        notBlockedContacts.each { Contact c1 ->
            contactStoreAction(c1)
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
            if (!c1.save()) {
                return resultFactory.failWithValidationErrors(c1.errors)
            }
        }
        // socket notify of new contact and/or unread contacts
        socketService.sendContacts(notBlockedContacts)
        resultFactory.success(notBlockedContacts)
    }
}
