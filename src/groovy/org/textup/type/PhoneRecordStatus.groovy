package org.textup.type

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum PhoneRecordStatus {
	UNREAD,
	ACTIVE,
	ARCHIVED,
	BLOCKED

    static final List<PhoneRecordStatus> ACTIVE_STATUSES = Collections.unmodifiableList[ACTIVE, UNREAD])
    static final List<PhoneRecordStatus> VISIBLE_STATUSES = Collections.unmodifiableList[ACTIVE, UNREAD, ARCHIVED])
}
