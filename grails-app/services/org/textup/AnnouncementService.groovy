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
    OutgoingAnnouncementService outgoingAnnouncementService
    ResultFactory resultFactory
    TwimlBuilder twimlBuilder

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
}
