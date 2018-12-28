package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.joda.time.DateTime
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class OutgoingAnnouncementService {

    CallService callService
    ResultFactory resultFactory
    SocketService socketService
    TextService textService

    Result<FeaturedAnnouncement> send(Phone p1, String message, DateTime expiresAt, Staff staff) {
        // build announcements class
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            expiresAt:expiresAt, message:message)
        //mark as to-be-saved to avoid TransientObjectExceptions
        if (!announce.save()) {
            return resultFactory.failWithValidationErrors(announce.errors)
        }
        // collect relevant classes
        List<IncomingSession> textSubs = IncomingSession.findAllByPhoneAndIsSubscribedToText(p1, true),
            callSubs = IncomingSession.findAllByPhoneAndIsSubscribedToCall(p1, true)
        String identifier = p1.name
        // send announcements
        Map<String, Result<TempRecordReceipt>> textRes = sendTextAnnouncement(p1, message, identifier, textSubs, staff)
        Map<String, Result<TempRecordReceipt>> callRes = startCallAnnouncement(p1, message, identifier, callSubs, staff)
        // collect sessions that we successfully reached
        Collection<IncomingSession> successTexts =  textSubs
            .findAll { textRes[it.numberAsString]?.success}
        Collection<IncomingSession> successCalls = callSubs
            .findAll { callRes[it.numberAsString]?.success }
        // add sessions to announcement as receipts
        ResultGroup<AnnouncementReceipt> textResGroup = announce.addToReceipts(RecordItemType.TEXT, successTexts),
            callResGroup = announce.addToReceipts(RecordItemType.CALL, successCalls)
        textResGroup.logFail("Phone.sendAnnouncement: add text announce receipts")
        callResGroup.logFail("Phone.sendAnnouncement: add call announce receipts")
        // don't use announce.numReceipts here because the dynamic finder
        // will flush the session
        boolean noSubscribers = (!textSubs && !callSubs),
            anySuccessWithSubscribers = (textResGroup.anySuccesses || callResGroup.anySuccesses) && (textSubs || callSubs)
        if (noSubscribers || anySuccessWithSubscribers) {
            if (announce.save()) {
                resultFactory.success(announce, ResultStatus.CREATED)
            }
            else { resultFactory.failWithValidationErrors(announce.errors) }
        }
        // return error if all subscribers failed to receive announcement
        else {
            resultFactory.failWithResultsAndStatus(textRes.values() + callRes.values(),
                ResultStatus.INTERNAL_SERVER_ERROR)
        }
    }

    // Helpers
    // -------

    protected Map<String, Result<TempRecordReceipt>> sendTextAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {

        Author author1 = staff.toAuthor()
        String announcement = TwilioUtils.formatAnnouncementForSend(identifier, message)
        sendAnnouncementHelper(phone, sessions, { IncomingSession s1 ->
            textService.send(phone.number, [s1.number], announcement, phone.customAccountId)
        }, { Contact c1, TempRecordReceipt receipt ->
            c1.record.storeOutgoingText(message, author1)
                .then { RecordText rText1 ->
                    rText1.addReceipt(receipt)
                    resultFactory.success(rText1)
                }
        })
    }

    protected Map<String, Result<TempRecordReceipt>> startCallAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {

        Author author1 = staff.toAuthor()
        sendAnnouncementHelper(phone, sessions, { IncomingSession s1 ->
            callService.start(phone.number, s1.number,
                CallTwiml.infoForAnnouncementAndDigits(identifier, message),
                phone.customAccountId)
        }, { Contact c1, TempRecordReceipt receipt ->
            c1.record.storeOutgoingCall(author1, message)
                .then { RecordCall rCall1 ->
                    rCall1.addReceipt(receipt)
                    resultFactory.success(rCall1)
                }
        })
    }

    protected Map<String, Result<TempRecordReceipt>> sendAnnouncementHelper(Phone phone,
        List<IncomingSession> sessions, Closure<Result<TempRecordReceipt>> receiptAction,
        Closure<Result<? extends RecordItem>> addToRecordAction) {

        Map<String, Result<TempRecordReceipt>> resMap = new HashMap<>()
        Map<String,TempRecordReceipt> numberAsStringToReceipt = [:]
        sessions.each { IncomingSession s1 ->
            Result<TempRecordReceipt> res = receiptAction(s1)
                .logFail("AnnouncementService.sendAnnouncementHelper: sending and returning receipt")
            if (res.success) {
                TempRecordReceipt receipt = res.payload
                numberAsStringToReceipt[s1.numberAsString] = receipt
            }
            resMap[s1.numberAsString] = res
        }
        // find contacts that have the same number as this session, if any
        // if no contacts share this number, we will create a new contact
        List<Contact> newContacts = []
        ContactNumber
            .getContactsForPhoneAndNumbers(phone, numberAsStringToReceipt.keySet())
            .each { String numAsString, List<Contact> contacts ->
                TempRecordReceipt receipt = numberAsStringToReceipt[numAsString]
                if (!contacts) { // create new contact if none found for session
                    phone.createContact([:], [numAsString])
                        .logFail("AnnouncementService.sendAnnouncementHelper: create contact")
                        .thenEnd({ Contact newC ->
                            newContacts << newC
                            contacts << newC
                        })
                }
                if (receipt) {
                    contacts.each { Contact c1 ->
                        addToRecordAction(c1, receipt)
                            .logFail("AnnouncementService.sendAnnouncementHelper: add to record")
                            .thenEnd({ RecordItem item -> item.isAnnouncement = true })
                    }
                }
                else {
                    log.error("AnnouncementService.sendAnnouncementHelper: no receipt for $numAsString")
                }
            }
        // notify of new contacts
        socketService.sendContacts(newContacts)
        //return result map
        resMap
    }
}
