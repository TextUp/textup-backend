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
