package org.textup

import grails.compiler.GrailsTypeChecked

@GrailsTypeChecked
class Constants {

	// Defaults
	// --------

	static final int DEFAULT_CACHE_MAX_SIZE = 1000
	static final int DEFAULT_LOCK_TIMEOUT_MILLIS = 15000
	static final int DEFAULT_TOKEN_LENGTH = 25
	static final String DEFAULT_AWAY_MESSAGE = "Sorry. I'm currently not available. I'll reply when I am back."
	static final String DEFAULT_AWAY_MESSAGE_SUFFIX = "If this is an emergency, contact 9 1 1."
	static final String DEFAULT_BRAND_COLOR = "#28a6de"
	static final String DEFAULT_CHAR_ENCODING = "UTF-8"
	static final String DEFAULT_LOCK_CODE = "8888"

	// Caching
	// -------

	static final String CACHE_PHONES = "phonesCache"
	static final String CACHE_PHONES_MAX_SIZE = 500
	static final String CACHE_RECEIPTS = "receiptsCache"
	static final String CACHE_RECEIPTS_MAX_SIZE = 500
}
