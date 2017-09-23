package org.textup.type

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum NotificationLevel {
	ALL, // notify for all records except for those on the blacklist
	NONE // notify for all records except for those on the whitelist
}