package org.textup.type

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum StaffStatus {
	BLOCKED,
	PENDING,
	STAFF,
	ADMIN

	static List<StaffStatus> ACTIVE_STATUSES = [STAFF, ADMIN]

	boolean getIsPending() {
		this == PENDING
	}
	boolean getIsActive() {
		this == STAFF || this == ADMIN
	}
}
