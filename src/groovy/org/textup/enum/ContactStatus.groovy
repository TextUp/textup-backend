package org.textup.enum

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum ContactStatus {
	UNREAD,
	ACTIVE,
	ARCHIVED,
	BLOCKED
}
