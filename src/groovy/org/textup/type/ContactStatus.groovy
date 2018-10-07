package org.textup.type

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum ContactStatus {
	UNREAD,
	ACTIVE,
	ARCHIVED,
	BLOCKED

    static List<ContactStatus> ACTIVE_STATUSES = [ACTIVE, UNREAD]
    static List<ContactStatus> VISIBLE_STATUSES = [ACTIVE, UNREAD, ARCHIVED]
}
