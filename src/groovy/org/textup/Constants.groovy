package org.textup

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class Constants {

	static final int MAX_REPEATS = 20
	static final int TEXT_LENGTH = 160
	static final String DEFAULT_AWAY_MESSAGE = "Sorry. I'm currently not available. I'll reply when I am back."
	// keep the space before the away message to aid in appropriate spacing when appending to ensure
	// accurate count of how much to truncate for API consumers
	static final String AWAY_EMERGENCY_MESSAGE = " If this is an emergency, contact 9 1 1."
	static final int DEFAULT_TOKEN_LENGTH = 25

	// Lock code
	// ---------

	static final int LOCK_CODE_LENGTH = 4
	static final String DEFAULT_LOCK_CODE = "8888"
	// if changed, you MUST run a db migration to update db constraints
	static final int MAX_LOCK_TIMEOUT_MILLIS = 60000
	// if changed, you MUST run a db migration to update db constraints
	static final int DEFAULT_LOCK_TIMEOUT_MILLIS = 15000 // also minimum timeout

	// Record note
	// -----------

	static final long MIN_NOTE_SPACING_MILLIS = 100
	static final long MAX_NOTE_SPACING_MILLIS = 60000

	// Uploader
	// --------

	static final String UPLOAD_ERRORS = "uploadErrors"

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

	// REST
	// ----

	static final String FALLBACK_SINGULAR = "result"
	static final String FALLBACK_PLURAL = "results"
	static final String FALLBACK_RESOURCE_NAME = "resource"

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

	static final String MERGE_ACTION_DEFAULT = "default"
	static final String MERGE_ACTION_RECONCILE = "reconcile"

	static final String NOTIFICATION_ACTION_DEFAULT = "changedefault"
	static final String NOTIFICATION_ACTION_ENABLE = "enable"
	static final String NOTIFICATION_ACTION_DISABLE = "disable"

	static final String PHONE_ACTION_DEACTIVATE = "deactivate"
	static final String PHONE_ACTION_TRANSFER = "transfer"
	static final String PHONE_ACTION_NEW_NUM_BY_NUM  = "numbynum"
	static final String PHONE_ACTION_NEW_NUM_BY_ID = "numbyid"

	static final String NOTE_IMAGE_ACTION_REMOVE = "remove"
	static final String NOTE_IMAGE_ACTION_ADD = "add"
}
