package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.joda.time.DateTime
import org.textup.type.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class AnnouncementService {

    AuthService authService
    ResultFactory resultFactory
    CallService callService
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
	protected Result<FeaturedAnnouncement> create(Phone p1, Map body) {
		if (!p1) {
			return resultFactory.failWithCodeAndStatus("announcementService.create.noPhone",
                ResultStatus.UNPROCESSABLE_ENTITY)
		}
        String msg = body.message as String
        DateTime expires = Helpers.toUTCDateTime(body.expiresAt)
        Staff loggedIn = authService.loggedInAndActive
        p1.sendAnnouncement(msg, expires, loggedIn)
            .then({ FeaturedAnnouncement fa1 -> resultFactory.success(fa1, ResultStatus.CREATED) })
	}

    // Update
    // ------

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

    Map<String, Result<TempRecordReceipt>> sendTextAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {

        Author author1 = staff.toAuthor()
        Result<List<String>> res = twimlBuilder
            .translate(TextResponse.ANNOUNCEMENT, [
                identifier:identifier,
                message:message
            ])
            .logFail("AnnouncementService.sendTextAnnouncement")
        String announcement = res.success ? res.payload[0] : "$identifier: $message"
        startAnnouncement(phone, sessions, { IncomingSession s1 ->
            textService.send(phone.number, [s1.number], announcement)
        }, { Contact c1, TempRecordReceipt receipt ->
            Result<RecordText> storeRes = c1.record.storeOutgoingText(message, author1)
            storeRes.then { RecordText rText1 -> rText1.addReceipt(receipt) }
            storeRes
        })
    }
    Map<String, Result<TempRecordReceipt>> startCallAnnouncement(Phone phone, String message,
        String identifier, List<IncomingSession> sessions, Staff staff) {

        Author author1 = staff.toAuthor()
        startAnnouncement(phone, sessions, { IncomingSession s1 ->
            callService.start(phone.number, s1.number, [
                handle:CallResponse.ANNOUNCEMENT_AND_DIGITS,
                message:message,
                identifier:identifier
            ])
        }, { Contact c1, TempRecordReceipt receipt ->
            Result<RecordCall> storeRes = c1.record.storeOutgoingCall(author1, message)
            storeRes.then { RecordCall rCall1 -> rCall1.addReceipt(receipt) }
            storeRes
        })
    }

    protected Map<String, Result<TempRecordReceipt>> startAnnouncement(Phone phone,
        List<IncomingSession> sessions, Closure<Result<TempRecordReceipt>> receiptAction,
        Closure<Result<? extends RecordItem>> addToRecordAction) {

        Map<String, Result<TempRecordReceipt>> resMap = new HashMap<>()
        Map<String,TempRecordReceipt> numberAsStringToReceipt = [:]
        sessions.each { IncomingSession s1 ->
            Result<TempRecordReceipt> res = receiptAction(s1)
                .logFail("AnnouncementService.startAnnouncement: sending and returning receipt")
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
                        .logFail("AnnouncementService.startAnnouncement: create contact")
                        .thenEnd({ Contact newC ->
                            newContacts << newC
                            contacts << newC
                        })
                }
                if (receipt) {
                    contacts.each { Contact c1 ->
                        addToRecordAction(c1, receipt)
                            .logFail("AnnouncementService.startAnnouncement: add to record")
                            .thenEnd({ RecordItem item -> item.isAnnouncement = true })
                    }
                }
                else {
                    log.error("AnnouncementService.startAnnouncement: no receipt for $numAsString")
                }
            }
        // notify of new contacts
        socketService.sendContacts(newContacts)
        //return result map
        resMap
    }
}

// Incoming
// --------


Result<Closure> handleAnnouncementText(Phone phone, IncomingText text, IncomingSession session,
    Closure<Result<Closure>> fallbackAction) {

    switch (text.message) {
        case Constants.TEXT_SEE_ANNOUNCEMENTS:
            Collection<FeaturedAnnouncement> announces = phone.getAnnouncements()
            announces.each { FeaturedAnnouncement announce ->
                announce
                    .addToReceipts(RecordItemType.TEXT, session)
                    .logFail("AnnouncementService.handleAnnouncementText: add announce receipt")
            }
            twimlBuilder.build(TextResponse.SEE_ANNOUNCEMENTS,
                [announcements:announces])
            break
        case Constants.TEXT_TOGGLE_SUBSCRIBE:
            if (session.isSubscribedToText) {
                session.isSubscribedToText = false
                twimlBuilder.build(TextResponse.UNSUBSCRIBED)
            }
            else {
                session.isSubscribedToText = true
                twimlBuilder.build(TextResponse.SUBSCRIBED)
            }
            break
        default:
            fallbackAction()
    }
}
Result<List<String> tryBuildTextInstructions(Phone phone, IncomingSession session) {
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
