package org.textup.rest

import org.textup.*
import org.textup.type.*
import org.textup.util.*

// Not type checked because of our use of Closures as a DSL for building Twiml.

class TextTwiml {

    static final String BODY_SEE_ANNOUNCEMENTS = "0"
    static final String BODY_TOGGLE_SUBSCRIBE = "1"

    static Result<Closure> build(Collection<String> responses) {
        TwilioUtils.wrapTwiml { responses.each { Message(it) } }
    }

    static Result<Closure> blocked() {
        message("textTwiml.blocked")
    }

    static Result<Closure> invalid() {
        message("twiml.invalidNumber")
    }

    static Result<Closure> notFound() {
        message("twiml.notFound")
    }

    static Result<Closure> subscribed() {
        message("textTwiml.subscribed", [TextTwiml.BODY_TOGGLE_SUBSCRIBE])
    }

    static Result<Closure> unsubscribed() {
        message("textTwiml.unsubscribed", [TextTwiml.BODY_TOGGLE_SUBSCRIBE])
    }

    // Helpers
    // -------

    protected static Result<Closure> message(String code, List<?> params = []) {
        String msg = IOCUtils.getMessage(code, params)
        TwilioUtils.wrapTwiml { Message(msg) }
    }
}
