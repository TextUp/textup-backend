package org.textup.type

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum CallResponse {
    SELF_GREETING,
    SELF_CONNECTING,
    SELF_INVALID_DIGITS,

    CONNECT_INCOMING,
    SCREEN_INCOMING, // gives TextUp users ability to screen calls before pickup
    CHECK_IF_VOICEMAIL,
    VOICEMAIL_DONE, // for recording status callback when recording is guaranteed available

    VOICEMAIL_GREETING_RECORD,
    VOICEMAIL_GREETING_PROCESSING,
    VOICEMAIL_GREETING_PROCESSED,
    VOICEMAIL_GREETING_PLAY,

    FINISH_BRIDGE,
    ANNOUNCEMENT_GREETING,
    HEAR_ANNOUNCEMENTS,
    ANNOUNCEMENT_AND_DIGITS, // so we can loop single announcement
    DIRECT_MESSAGE,
    UNSUBSCRIBED,
    SUBSCRIBED,
    END_CALL,
    DO_NOTHING,
    BLOCKED
}
