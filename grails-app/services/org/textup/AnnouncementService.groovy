package org.textup

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.joda.time.DateTime
import org.textup.rest.*
import org.textup.type.*
import org.textup.util.RollbackOnResultFailure
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class AnnouncementService {

    AuthService authService
    OutgoingAnnouncementService outgoingAnnouncementService
    ResultFactory resultFactory

	// Create
	// ------

    Result<FeaturedAnnouncement> createForTeam(Long tId, Map body) {
    	create(Team.get(tId)?.phone, body)
    }
	Result<FeaturedAnnouncement> createForStaff(Map body) {
		create(authService.loggedInAndActive?.phone, body)
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
        if (!p1.isActive) {
            return resultFactory.failWithCodeAndStatus("phone.isInactive", ResultStatus.NOT_FOUND)
        }
        // validate expiration
        if (!expires || expires.isBeforeNow()) {
            return resultFactory.failWithCodeAndStatus("announcementService.create.expiresInPast",
                ResultStatus.UNPROCESSABLE_ENTITY)
        }
        // validate staff
        if (!p1.owner.all.contains(authService.loggedInAndActive)) {
            return resultFactory.failWithCodeAndStatus("phone.notOwner", ResultStatus.FORBIDDEN)
        }
        outgoingAnnouncementService.send(p1, msg, expires, loggedIn)
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

    // Callbacks
    // ---------

    Result<Closure> textSeeAnnouncements(Collection<FeaturedAnnouncement> announces, IncomingSession sess1) {
        announces.each { FeaturedAnnouncement announce ->
            announce
                .addToReceipts(RecordItemType.TEXT, sess1)
                .logFail("AnnouncementService.handleAnnouncementText: add announce receipt")
        }
        TextTwiml.seeAnnouncements(announces)
    }

    Result<Closure> textToggleSubscribe(IncomingSession sess1) {
        if (sess1.isSubscribedToText) {
            sess1.isSubscribedToText = false
            TextTwiml.unsubscribed()
        }
        else {
            sess1.isSubscribedToText = true
            TextTwiml.subscribed()
        }
    }

    Result<List<String>> tryBuildTextInstructions(Phone phone, IncomingSession sess1) {
        if (phone.getAnnouncements() && sess1.shouldSendInstructions) {
            sess1.updateLastSentInstructions()
            sess1.isSubscribedToText ? TextTwiml.afterSubscribing() : TextTwiml.afterUnsubscribing()
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
                    CallTwiml.hearAnnouncements(announces, session.isSubscribedToCall)
                    break
                case Constants.CALL_TOGGLE_SUBSCRIBE:
                    if (session.isSubscribedToCall) {
                        session.isSubscribedToCall = false
                        CallTwiml.unsubscribed()
                    }
                    else {
                        session.isSubscribedToCall = true
                        CallTwiml.subscribed()
                    }
                    break
                default:
                    fallbackAction()
            }
        }
        else if (phone.getAnnouncements()) {
            CallTwiml.announcementGreeting(phone.owner.name, session.isSubscribedToCall)
        }
        else { fallbackAction() }
    }

    Result<Closure> completeCallAnnouncement(String digits, String message,
        String identifier, IncomingSession session) {
        if (digits == Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE) {
            session.isSubscribedToCall = false
            CallTwiml.unsubscribed()
        }
        else { CallTwiml.announcementAndDigits(identifier, message) }
    }
}
