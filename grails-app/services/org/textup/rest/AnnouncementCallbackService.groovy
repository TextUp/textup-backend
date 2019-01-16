package org.textup.rest

import grails.compiler.GrailsTypeChecked
import grails.transaction.Transactional

@GrailsTypeChecked
@Transactional
class AnnouncementCallbackService {

    Result<Closure> textSeeAnnouncements(Collection<FeaturedAnnouncement> faList, IncomingSession is1) {
        ResultGroup.collect(faList) { FeaturedAnnouncement fa1 ->
                AnnouncementReceipt.tryCreate(fa1, is1, RecordItemType.TEXT)
            }
            .logFail("textSeeAnnouncements")
        TextTwiml.seeAnnouncements(faList)
    }

    Result<Closure> textToggleSubscribe(IncomingSession is1) {
        if (is1.isSubscribedToText) {
            is1.setIsSubscribedToText(false)
            TextTwiml.unsubscribed()
        }
        else {
            is1.setIsSubscribedToText(true)
            TextTwiml.subscribed()
        }
    }

    Result<List<String>> tryBuildTextInstructions(Phone p1, IncomingSession is1) {
        List<String> textInstructions = []
        if (FeaturedAnnouncements.anyForPhoneId(p1.id) && is1.shouldSendInstructions) {
            is1.updateLastSentInstructions()
            if (is1.isSubscribedToText) {
                textInstructions << IOCUtils.getMessage("twimlBuilder.text.instructionsSubscribed",
                    [Constants.TEXT_SEE_ANNOUNCEMENTS, Constants.TEXT_TOGGLE_SUBSCRIBE])
            }
            else {
                textInstructions << IOCUtils.getMessage("twimlBuilder.text.instructionsUnsubscribed",
                    [Constants.TEXT_SEE_ANNOUNCEMENTS, Constants.TEXT_TOGGLE_SUBSCRIBE])
            }
        }
        IOCUtils.resultFactory.success(textInstructions)
    }

    Result<Closure> handleAnnouncementCall(Phone p1, String digits, IncomingSession is1,
        Closure<Result<Closure>> fallbackAction) {

        if (digits) {
            switch(digits) {
                case Constants.CALL_HEAR_ANNOUNCEMENTS:
                    callHearAnnouncements(p1.id, is1)
                    break
                case Constants.CALL_TOGGLE_SUBSCRIBE:
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

    Result<Closure> completeCallAnnouncement(String digits, String message,
        String identifier, IncomingSession is1) {

        if (digits == Constants.CALL_ANNOUNCEMENT_UNSUBSCRIBE) {
            is1.setIsSubscribedToCall(false)
            CallTwiml.unsubscribed()
        }
        else { CallTwiml.announcementAndDigits(identifier, message) }
    }

    // Helpers
    // -------

    protected Result<Closure> callHearAnnouncements(Long pId, IncomingSession is1) {
        List<FeaturedAnnouncement> fas = FeaturedAnnouncements.buildActiveForPhoneId(pId).list()
        ResultGroup.collect(fas) { FeaturedAnnouncement fa1 ->
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
