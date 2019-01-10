package org.textup

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Constants {

	// Defaults
	// --------

	static final String DEFAULT_AWAY_MESSAGE = "Sorry. I'm currently not available. I'll reply when I am back."
	// keep the space before the away message to aid in appropriate spacing when appending to ensure
	// accurate count of how much to truncate for API consumers
	static final String DEFAULT_AWAY_MESSAGE_SUFFIX = "If this is an emergency, contact 9 1 1."

	static final int DEFAULT_TOKEN_LENGTH = 25
	static final String DEFAULT_BRAND_COLOR = "#28a6de"
	static final String DEFAULT_LOCK_CODE = "8888"

	// if changed, you MUST run a db migration to update db constraints
	static final int DEFAULT_LOCK_TIMEOUT_MILLIS = 15000 // also minimum timeout

	static final String DEFAULT_CHAR_ENCODING = "UTF-8"

	// Caching
	// -------

	static final int DEFAULT_CACHE_MAX_SIZE = 1000

	// If chance this cache name, need to update values in `RecordItemReceiptsCache.groovy`
	static final String CACHE_RECEIPTS = "receiptsCache"
	static final String CACHE_RECEIPTS_MAX_SIZE = 500

	static final String CACHE_PHONES = "phonesCache"
	static final String CACHE_PHONES_MAX_SIZE = 500

	// Billing
	// -------

	static final double UNIT_COST_NUMBER = 5
	static final double UNIT_COST_TEXT = 0.01
	static final double UNIT_COST_CALL = 0.015

	// HTTP
	// ----

	static final String REQUEST_TIMEZONE = "timezone"
	static final String REQUEST_UPLOAD_ERRORS = "uploadErrors"
	static final String REQUEST_PAGINATION_OPTIONS = "paginationOptions"

	static final String PROTOCOL_HTTP = "http"
	static final String PROTOCOL_HTTPS = "https"

	// Schedule
	// --------

	static final List<String> DAYS_OF_WEEK = ["sunday", "monday", "tuesday",
		"wednesday", "thursday", "friday", "saturday"]

    // Messages
    // --------

    static final int TEXT_LENGTH = 160
	static final int DIRECT_MESSAGE_MAX_REPEATS = 5

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

	static final int DEFAULT_PAGINATION_MAX = 10
	static final int MAX_PAGINATION_MAX = 5000

	static final String FALLBACK_SINGULAR = "result"
	static final String FALLBACK_PLURAL = "results"
	static final String FALLBACK_RESOURCE_NAME = "resource"
}
