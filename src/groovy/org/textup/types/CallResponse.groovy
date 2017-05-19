package org.textup.types

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum CallResponse {
    SELF_GREETING,
    SELF_CONNECTING,
    SELF_INVALID_DIGITS,
    CONNECT_INCOMING,
    VOICEMAIL,
    VOICEMAIL_STUB, // no-op for Record Twiml verb to call because recording might not be ready
    VOICEMAIL_DONE, // for recording status callback when recording is guaranteed available
    FINISH_BRIDGE,
    ANNOUNCEMENT_GREETING,
    HEAR_ANNOUNCEMENTS,
    ANNOUNCEMENT_AND_DIGITS, // so we can loop single announcement
    DIRECT_MESSAGE,
    UNSUBSCRIBED,
    SUBSCRIBED,
    BLOCKED
}
