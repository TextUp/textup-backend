package org.textup.type

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum PhoneRecordStatus {
	UNREAD,
	ACTIVE,
	ARCHIVED,
	BLOCKED

    static final Collection<PhoneRecordStatus> ACTIVE_STATUSES =
        Collections.unmodifiableSet(new HashSet<PhoneRecordStatus>([ACTIVE, UNREAD]))
    static final Collection<PhoneRecordStatus> VISIBLE_STATUSES =
        Collections.unmodifiableSet(new HashSet<PhoneRecordStatus>([ACTIVE, UNREAD, ARCHIVED]))

    static PhoneRecordStatus reconcile(Collection<PhoneRecordStatus> statuses) {
        if ([UNREAD, ACTIVE].any { PhoneRecordStatus s -> s in statuses }) {
            ACTIVE
        }
        else if (ARCHIVED in statuses) {
            ARCHIVED
        }
        else { BLOCKED }
    }
}
