package org.textup.type

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
enum StaffStatus {
	BLOCKED,
	PENDING,
	STAFF,
	ADMIN

	boolean getIsPending() {
		this == PENDING
	}
	boolean getIsActive() {
		this == STAFF || this == ADMIN
	}
}
