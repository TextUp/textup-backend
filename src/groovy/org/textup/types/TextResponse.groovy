package org.textup.types

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum TextResponse {
	SUBSCRIBED,
	UNSUBSCRIBED,
	SEE_ANNOUNCEMENTS,
	ANNOUNCEMENT,
	INSTRUCTIONS_UNSUBSCRIBED,
	INSTRUCTIONS_SUBSCRIBED,
    BLOCKED
}
