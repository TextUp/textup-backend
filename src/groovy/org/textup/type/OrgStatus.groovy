package org.textup.type

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
enum OrgStatus {
	REJECTED,
	PENDING,
	APPROVED

    static final List<OrgStatus> ACTIVE_STATUSES = Collections.unmodifiableList([APPROVED])
}
