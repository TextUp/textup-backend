package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.apache.commons.lang3.tuple.Pair
import org.joda.time.DateTime
import org.textup.rest.TwimlBuilder
import org.textup.type.*
import org.textup.util.RollbackOnResultFailure
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class AnnouncementService {

    AuthService authService
    CallService callService
    ResultFactory resultFactory
    SocketService socketService
    TextService textService
    TwimlBuilder twimlBuilder

	// Create
	// ------

    Result<FeaturedAnnouncement> createForTeam(Long tId, Map body) {
    	create(Team.get(tId)?.phone, body)
    }
	Result<FeaturedAnnouncement> createForStaff(Map body) {
		create(authService.loggedInAndActive?.phone, body)
	}

    Result<Closure> completeCallAnnouncement(String digits, String message,
        String identifier, IncomingSession session) {
        if (digits == Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE) {
            session.isSubscribedToCall = false
            twimlBuilder.build(CallResponse.UNSUBSCRIBED)
        }
        else {
            twimlBuilder.build(CallResponse.ANNOUNCEMENT_AND_DIGITS,
                [message:message, identifier:identifier])
        }
    }

    @RollbackOnResultFailure
	protected Result<FeaturedAnnouncement> create(Phone p1, Map body) {
		if (!p1) {
			return resultFactory.failWithCodeAndStatus("announcementService.create.noPhone",
                ResultStatus.UNPROCESSABLE_ENTITY)
		}
        String msg = body.message as String
        DateTime expires = Helpers.toUTCDateTime(body.expiresAt)
        Staff loggedIn = authService.loggedInAndActive

        // TODO
        if (!p1.isActive) {
            return resultFactory.failWithCodeAndStatus("phone.isInactive", ResultStatus.NOT_FOUND)
        }
        // validate expiration
        if (!expires || expires.isBeforeNow()) {
            return resultFactory.failWithCodeAndStatus("phone.sendAnnouncement.expiresInPast",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        // validate staff
        if (!p1.owner.all.contains(authService.loggedInAndActive)) {
            return resultFactory.failWithCodeAndStatus("phone.notOwner", ResultStatus.FORBIDDEN)
        }

        sendAnnouncement(p1, msg, expires, loggedIn)
            .then { FeaturedAnnouncement fa1 -> resultFactory.success(fa1, ResultStatus.CREATED) }
	}

    // Update
    // ------

    @RollbackOnResultFailure
    Result<FeaturedAnnouncement> update(Long aId, Map body) {
    	FeaturedAnnouncement announce = FeaturedAnnouncement.get(aId)
    	if (!announce) {
    		return resultFactory.failWithCodeAndStatus("announcementService.update.notFound",
                ResultStatus.NOT_FOUND, [aId])
    	}
        announce.expiresAt = Helpers.toUTCDateTime(body.expiresAt)
        if (announce.save()) {
            resultFactory.success(announce)
        }
        else { resultFactory.failWithValidationErrors(announce.errors) }
    }

    // Outgoing
    // --------

    // TODO
    protected Result<FeaturedAnnouncement> sendAnnouncement(Phone p1, String message, DateTime expiresAt, Staff staff) {
        // collect relevant classes
        List<IncomingSession> textSubs = IncomingSession.findAllByPhoneAndIsSubscribedToText(p1, true),
            callSubs = IncomingSession.findAllByPhoneAndIsSubscribedToCall(p1, true)
        String identifier = p1.name
        // build announcements class
        FeaturedAnnouncement announce = new FeaturedAnnouncement(owner:p1,
            expiresAt:expiresAt, message:message)
        //mark as to-be-saved to avoid TransientObjectExceptions
        if (!announce.save()) {
            resultFactory.failWithValidationErrors(announce.errors)
        }
        // send announcements
        Map<String, Result<TempRecordReceipt>> textRes = sendTextAnnouncement(p1, message, identifier, textSubs, staff)
        Map<String, Result<TempRecordReceipt>> callRes = startCallAnnouncement(p1, message, identifier, callSubs, staff)
        // collect sessions that we successfully reached
        Collection<IncomingSession> successTexts = textSubs.findAll {
            textRes[it.numberAsString]?.success
        }, successCalls = callSubs.findAll {
            callRes[it.numberAsString]?.success
        }
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
                resultFactory.success(announce)
            }
            else { resultFactory.failWithValidationErrors(announce.errors) }
        }
        // return error if all subscribers failed to receive announcement
        else {
            resultFactory.failWithResultsAndStatus(textRes.values() + callRes.values(),
                ResultStatus.INTERNAL_SERVER_ERROR)
        }
    }

    // TODO
    protected Map<String, Result<TempRecordReceipt>> sendTextAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {

        Author author1 = staff.toAuthor()
        Result<List<String>> res = twimlBuilder
            .translate(TextResponse.ANNOUNCEMENT, [
                identifier:identifier,
                message:message
            ])
            .logFail("AnnouncementService.sendTextAnnouncement")
        String announcement = res.success ? res.payload[0] : "$identifier: $message"
        sendAnnouncementHelper(phone, sessions, { IncomingSession s1 ->
            textService.send(phone.number, [s1.number], announcement)
        }, { Contact c1, TempRecordReceipt receipt ->
            c1.record.storeOutgoingText(message, author1)
                .then { RecordText rText1 ->
                    rText1.addReceipt(receipt)
                    resultFactory.success(rText1)
                }
        })
    }

    // TODO
    protected Map<String, Result<TempRecordReceipt>> startCallAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {

        Author author1 = staff.toAuthor()
        sendAnnouncementHelper(phone, sessions, { IncomingSession s1 ->
            callService.start(phone.number, s1.number, [
                handle:CallResponse.ANNOUNCEMENT_AND_DIGITS,
                message:message,
                identifier:identifier
            ])
        }, { Contact c1, TempRecordReceipt receipt ->
            c1.record.storeOutgoingCall(author1, message)
                .then { RecordCall rCall1 ->
                    rCall1.addReceipt(receipt)
                    resultFactory.success(rCall1)
                }
        })
    }

    // TODO
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

    // Incoming
    // --------

    Result<Closure> textSeeAnnouncements(Collection<FeaturedAnnouncement> announces, IncomingSession sess1) {
        announces.each { FeaturedAnnouncement announce ->
            announce
                .addToReceipts(RecordItemType.TEXT, sess1)
                .logFail("AnnouncementService.handleAnnouncementText: add announce receipt")
        }
        twimlBuilder.build(TextResponse.SEE_ANNOUNCEMENTS, [announcements:announces])
    }

    Result<Closure> textToggleSubscribe(IncomingSession sess1) {
        if (sess1.isSubscribedToText) {
            sess1.isSubscribedToText = false
            twimlBuilder.build(TextResponse.UNSUBSCRIBED)
        }
        else {
            sess1.isSubscribedToText = true
            twimlBuilder.build(TextResponse.SUBSCRIBED)
        }
    }

    Result<List<String>> tryBuildTextInstructions(Phone phone, IncomingSession session) {
        if (phone.getAnnouncements() && session.shouldSendInstructions) {
            session.updateLastSentInstructions()
            TextResponse code = session.isSubscribedToText ?
                TextResponse.INSTRUCTIONS_SUBSCRIBED :
                TextResponse.INSTRUCTIONS_UNSUBSCRIBED
            twimlBuilder.translate(code)
        }
        else { resultFactory.success([]) }
    }

    Result<Closure> handleAnnouncementCall(Phone phone, String digits, IncomingSession session,
        Closure<Result<Closure>> fallbackAction) {

        if (digits) {
            switch(digits) {
                case Constants.CALL_HEAR_ANNOUNCEMENTS:
                    List<FeaturedAnnouncement> announces = phone.getAnnouncements()
                    announces.each { FeaturedAnnouncement announce ->
                        announce.addToReceipts(RecordItemType.CALL, session)
                            .logFail("AnnouncementService.handleAnnouncementCall: add announce receipt")
                    }
                    twimlBuilder.build(CallResponse.HEAR_ANNOUNCEMENTS,
                        [announcements:announces, isSubscribed:session.isSubscribedToCall])
                    break
                case Constants.CALL_TOGGLE_SUBSCRIBE:
                    if (session.isSubscribedToCall) {
                        session.isSubscribedToCall = false
                        twimlBuilder.build(CallResponse.UNSUBSCRIBED)
                    }
                    else {
                        session.isSubscribedToCall = true
                        twimlBuilder.build(CallResponse.SUBSCRIBED)
                    }
                    break
                default:
                    fallbackAction()
            }
        }
        else if (phone.getAnnouncements()) {
            twimlBuilder.build(CallResponse.ANNOUNCEMENT_GREETING,
                [name:phone.owner.name, isSubscribed:session.isSubscribedToCall])
        }
        else { fallbackAction() }
    }
}
