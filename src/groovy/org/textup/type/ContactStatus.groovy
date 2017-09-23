package org.textup.type

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum ContactStatus {
	UNREAD,
	ACTIVE,
	ARCHIVED,
	BLOCKED
}
