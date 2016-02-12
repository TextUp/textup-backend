package org.textup.types

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum ContactStatus {
	UNREAD,
	ACTIVE,
	ARCHIVED,
	BLOCKED
}
