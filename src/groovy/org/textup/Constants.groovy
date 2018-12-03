package org.textup

import grails.compiler.GrailsTypeChecked
import org.textup.type.ContactStatus

@GrailsTypeChecked
class Constants {

	static final int TEXT_LENGTH = 160
	static final int MAX_NUM_TEXT_RECIPIENTS = 500

	// 65535 bytes max for `text` column divided by 4 bytes per character ut8mb4
	static final int MAX_TEXT_COLUMN_SIZE = 15000

	static final int DIRECT_MESSAGE_MAX_REPEATS = 5

	static final int DEFAULT_PAGINATION_MAX = 10
	static final int MAX_PAGINATION_MAX = 100

	static final String DEFAULT_AWAY_MESSAGE = "Sorry. I'm currently not available. I'll reply when I am back."
	// keep the space before the away message to aid in appropriate spacing when appending to ensure
	// accurate count of how much to truncate for API consumers
	static final String AWAY_EMERGENCY_MESSAGE = " If this is an emergency, contact 9 1 1."

	static final int DEFAULT_TOKEN_LENGTH = 25
	static final int MAX_NUM_ACCESS_NOTIFICATION_TOKEN = 3

	// Billing
	// -------

	static final double UNIT_COST_NUMBER = 5
	static final double UNIT_COST_TEXT = 0.01
	static final double UNIT_COST_CALL = 0.015

	// HTTP
	// ----

	static final String REQUEST_TIMEZONE = "timezone"
	static final String REQUEST_UPLOAD_ERRORS = "uploadErrors"

	static final String PROTOCOL_HTTP = "http"
	static final String PROTOCOL_HTTPS = "https"

	// Concurrency
	// -----------

	static final int CONCURRENT_SEND_BATCH_SIZE = 20
	static final int CONCURRENT_UPLOAD_BATCH_SIZE = 8

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

	// Media
	// -----

	static final long MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES = 5000000
	static final int MAX_NUM_MEDIA_PER_MESSAGE = 10

	// Schedule
	// --------

	static final List<String> DAYS_OF_WEEK = ["sunday", "monday", "tuesday",
		"wednesday", "thursday", "friday", "saturday"]

    // Calls and texts
    // ---------------

	static final String CALLBACK_STATUS = "status"
	static final String CALLBACK_CHILD_CALL_NUMBER_KEY = "childStatusNumber"

	static final String TEXT_SEE_ANNOUNCEMENTS = "0"
	static final String TEXT_TOGGLE_SUBSCRIBE = "1"

	static final String CALL_HEAR_ANNOUNCEMENTS = "1"
	static final String CALL_TOGGLE_SUBSCRIBE = "2"
	static final String CALL_ANNOUNCEMENT_UNSUBSCRIBE = "1"

	static final String CALL_HOLD_MUSIC_URL = "http://com.twilio.music.guitars.s3.amazonaws.com/Pitx_-_Long_Winter.mp3"

	// Socket events
	// -------------

	static final int SOCKET_PAYLOAD_BATCH_SIZE = 20
	static final String SOCKET_EVENT_CONTACTS = "contacts"
	static final String SOCKET_EVENT_FUTURE_MESSAGES = "futureMessages"
	static final String SOCKET_EVENT_RECORDS = "records"
	static final String SOCKET_EVENT_PHONES = "phones"

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

	static final String MEDIA_ACTION_REMOVE = "remove"
	static final String MEDIA_ACTION_ADD = "add"

	// Testing constants
	// -----------------

	static final String TEST_DEFAULT_AREA_CODE = "626"
	static final String TEST_HTTP_ENDPOINT = "https://httpbin.org"

	// Twilio test API numbers
	// -----------------------

	static final String TEST_SMS_FROM_VALID = "+15005550006"
	static final String TEST_SMS_TO_NOT_VALID = "+15005550001"
	static final String TEST_SMS_TO_BLACKLISTED = "+15005550004"

	static final String TEST_CALL_FROM_NOT_VALID = "+15005550001"
	static final String TEST_CALL_FROM_VALID = "+15005550006"
	static final String TEST_CALL_TO_NOT_VALID = "+15005550001"

	static final String TEST_NUMBER_NOT_AVAILABLE = "+15005550000"
	static final String TEST_NUMBER_INVALID = "+15005550001"
	static final String TEST_NUMBER_AVAILABLE = "+15005550006"
}
