package org.textup.rest

import org.textup.*
import org.textup.type.*

// Not type checked because of our use of Closures as a DSL for building Twiml.

class TextTwiml {

    static Result<Closure> build(Collection<String> responses) {
        TwilioUtils.wrapTwiml { responses.each { Message(it) } }
    }

    // TextResponse.BLOCKED
    static Result<Closure> blocked() {
        message("twimlBuilder.text.blocked")
    }

    // Errors
    // ------

    static Result<Closure> invalid() {
        message("twimlBuilder.invalidNumber")
    }


    static Result<Closure> notFound() {
        message("twimlBuilder.notFound")
    }

    // Announcements
    // -------------

    // TextResponse.SEE_ANNOUNCEMENTS
    static Result<Closure> seeAnnouncements(Collection<FeaturedAnnouncement> announces) {
        if (announces == null) {
            return TwilioUtils.invalidTwimlInputs(TextResponse.SEE_ANNOUNCEMENTS.toString())
        }
        build(TwilioUtils.formatAnnouncementsForRequest(announces))
    }

    // TextResponse.SUBSCRIBED
    static Result<Closure> subscribed() {
        message("twimlBuilder.text.subscribed", [Constants.TEXT_TOGGLE_SUBSCRIBE])
    }

    // TextResponse.UNSUBSCRIBED
    static Result<Closure> unsubscribed() {
        message("twimlBuilder.text.unsubscribed", [Constants.TEXT_TOGGLE_SUBSCRIBE])
    }

    // Helpers
    // -------

    protected static Result<Closure> message(String code, List<?> params = []) {
        String msg = Helpers.getMessage(code, params)
        TwilioUtils.wrapTwiml { Message(msg) }
    }
}
