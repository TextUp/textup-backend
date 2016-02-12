package org.textup.types

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum TextResponse {
	SUBSCRIBED,
	UNSUBSCRIBED,
	ANNOUNCEMENTS,
	INSTRUCTIONS_UNSUBSCRIBED,
	INSTRUCTIONS_SUBSCRIBED
}
