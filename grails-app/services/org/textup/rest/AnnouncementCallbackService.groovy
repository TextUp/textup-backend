package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional
import org.textup.*
import org.textup.type.*
import org.textup.util.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Transactional
class AnnouncementCallbackService {

    Result<Closure> textSeeAnnouncements(Phone p1, IncomingSession is1,
        Closure<Result<Closure>> fallbackAction) {

        Collection<FeaturedAnnouncement> announces = FeaturedAnnouncements
            .buildActiveForPhoneId(p1.id)
            .list()
        if (announces) {
            ResultGroup
                .collect(announces) { FeaturedAnnouncement fa1 ->
                    AnnouncementReceipt.tryCreate(fa1, is1, RecordItemType.TEXT)
                }
                .logFail("textSeeAnnouncements")
            TextTwiml.build(TwilioUtils.formatAnnouncementsForRequest(announces))
        }
        else { fallbackAction() }
    }

    Result<Closure> textToggleSubscribe(IncomingSession is1) {
        if (is1.isSubscribedToText) {
            is1.isSubscribedToText = false
            TextTwiml.unsubscribed()
        }
        else {
            is1.isSubscribedToText = true
            TextTwiml.subscribed()
        }
    }

    Result<List<String>> tryBuildTextInstructions(Phone p1, IncomingSession is1) {
        List<String> textInstructions = []
        if (FeaturedAnnouncements.anyForPhoneId(p1.id) && is1.shouldSendInstructions) {
            is1.updateLastSentInstructions()
            if (is1.isSubscribedToText) {
                textInstructions << IOCUtils.getMessage("twimlBuilder.text.instructionsSubscribed",
                    [TextTwiml.BODY_SEE_ANNOUNCEMENTS, TextTwiml.BODY_TOGGLE_SUBSCRIBE])
            }
            else {
                textInstructions << IOCUtils.getMessage("twimlBuilder.text.instructionsUnsubscribed",
                    [TextTwiml.BODY_SEE_ANNOUNCEMENTS, TextTwiml.BODY_TOGGLE_SUBSCRIBE])
            }
        }
        IOCUtils.resultFactory.success(textInstructions)
    }

    Result<Closure> handleAnnouncementCall(Phone p1, IncomingSession is1, String digits,
        Closure<Result<Closure>> fallbackAction) {

        if (digits) {
            switch(digits) {
                case CallTwiml.DIGITS_HEAR_ANNOUNCEMENTS:
                    callHearAnnouncements(p1.id, is1)
                    break
                case CallTwiml.DIGITS_TOGGLE_SUBSCRIBE:
                    callToggleSubscribe(is1)
                    break
                default:
                    fallbackAction()
            }
        }
        else if (FeaturedAnnouncements.anyForPhoneId(p1.id)) {
            CallTwiml.announcementGreeting(p1.owner.buildName(), is1.isSubscribedToCall)
        }
        else { fallbackAction() }
    }

    Result<Closure> completeCallAnnouncement(IncomingSession is1, String digits, TypeMap params) {
        if (digits == CallTwiml.DIGITS_ANNOUNCEMENT_UNSUBSCRIBE) {
            is1.isSubscribedToCall = false
            CallTwiml.unsubscribed()
        }
        else { CallTwiml.announcementAndDigits(params) }
    }

    // Helpers
    // -------

    protected Result<Closure> callHearAnnouncements(Long pId, IncomingSession is1) {
        List<FeaturedAnnouncement> fas = FeaturedAnnouncements.buildActiveForPhoneId(pId).list()
        ResultGroup
            .collect(fas) { FeaturedAnnouncement fa1 ->
                AnnouncementReceipt.tryCreate(fa1, is1, RecordItemType.CALL)
            }
            .logFail("callHearAnnouncements")
        CallTwiml.hearAnnouncements(announces, is1.isSubscribedToCall)
    }

    protected Result<Closure> callToggleSubscribe(IncomingSession is1) {
        if (is1.isSubscribedToCall) {
            is1.isSubscribedToCall = false
            CallTwiml.unsubscribed()
        }
        else {
            is1.isSubscribedToCall = true
            CallTwiml.subscribed()
        }
    }
}
