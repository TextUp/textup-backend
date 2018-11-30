package org.textup

import grails.compiler.GrailsTypeChecked
import org.textup.type.ContactStatus

@GrailsTypeChecked
class TestConstants {

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
