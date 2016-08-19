package org.textup

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class Constants {

	static final int MAX_REPEATS = 20
	static final int TEXT_LENGTH = 160
	static final String DEFAULT_AWAY_MESSAGE = "Sorry. I'm currently not available. I'll reply when I am back."

	// Lock code
	// ---------

	static final int LOCK_CODE_LENGTH = 4
	static final String DEFAULT_LOCK_CODE = "8888"

	// Schedule
	// --------

	static final List<String> DAYS_OF_WEEK = ["sunday", "monday", "tuesday",
		"wednesday", "thursday", "friday", "saturday"]

	// Receipt statuses
	// ----------------

    static final List<String> FAILED_STATUSES = ["failed", "undelivered"]
    static final List<String> PENDING_STATUSES = ["in-progress", "ringing",
    	"queued", "accepted", "sending", "receiving"]
    static final List<String> BUSY_STATUSES = ["busy", "no-answer"]

    // Calls and texts
    // ---------------

	static final String CALLBACK_STATUS = "status"

	static final String TEXT_SEE_ANNOUNCEMENTS = "0"
	static final String TEXT_TOGGLE_SUBSCRIBE = "1"

	static final String CALL_HEAR_ANNOUNCEMENTS = "1"
	static final String CALL_TOGGLE_SUBSCRIBE = "2"
	static final String CALL_ANNOUNCEMENT_UNSUBSCRIBE = "1"

	// Socket events
	// -------------

	static final String SOCKET_EVENT_CONTACTS = "contacts"
	static final String SOCKET_EVENT_FUTURE_MESSAGES = "futureMessages"
	static final String SOCKET_EVENT_RECORD_STATUSES = "recordStatuses"
	static final String SOCKET_EVENT_RECORDS = "records"

	// Quartz jobs
	// -----------

	static final String JOB_DATA_FUTURE_MESSAGE_KEY = "futureMessageKey"
	static final String JOB_DATA_STAFF_ID = "futureMessageStaffId"

	// REST actions
	// ------------

	static final String TAG_ACTION_ADD = "add"
	static final String TAG_ACTION_REMOVE = "remove"

	static final String TEAM_ACTION_ADD = "add"
	static final String TEAM_ACTION_REMOVE = "remove"

	static final String SHARE_ACTION_MERGE = "merge"
	static final String SHARE_ACTION_STOP = "stop"

	static final String NUMBER_ACTION_MERGE = "merge"
	static final String NUMBER_ACTION_DELETE = "delete"

	static final String PHONE_ACTION_DEACTIVATE = "deactivate"
	static final String PHONE_ACTION_TRANSFER = "transfer"
	static final String PHONE_ACTION_NEW_NUM_BY_NUM  = "numbynum"
	static final String PHONE_ACTION_NEW_NUM_BY_ID = "numbyid"
}
