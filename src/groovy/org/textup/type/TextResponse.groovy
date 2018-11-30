package org.textup.type

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum TextResponse {
	INSTRUCTIONS_UNSUBSCRIBED,
	INSTRUCTIONS_SUBSCRIBED,
	SEE_ANNOUNCEMENTS,
	ANNOUNCEMENT,
	SUBSCRIBED,
	UNSUBSCRIBED,
    BLOCKED
}
