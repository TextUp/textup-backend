package org.textup.type

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum AvailableTextAction {
    DO_NOTHING(false, false),
    NOTIFY_TEXT_IMMEDIATELY(true, false),
    NOTIFY_TEXT_30_MIN(true, false),
    NOTIFY_TEXT_1_HOUR(true, false),
    NOTIFY_EMAIL_30_MIN(false, true),
    NOTIFY_EMAIL_1_HOUR(false, true)

    final boolean viaText
    final boolean viaEmail
    AvailableTextAction(text, email) {
        viaText = text
        viaEmail = email
    }
}
