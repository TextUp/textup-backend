package org.textup.type

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum StaffStatus {
	BLOCKED,
	PENDING,
	STAFF,
	ADMIN

	static final List<StaffStatus> ACTIVE_STATUSES = Collections.unmodifiableList([STAFF, ADMIN])

	boolean isPending() {
		this == PENDING
	}
	boolean isActive() {
		this == STAFF || this == ADMIN
	}
}
