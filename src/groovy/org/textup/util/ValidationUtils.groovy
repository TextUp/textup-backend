package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.util.logging.Log4j
import java.util.regex.Pattern
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

// [NOTE] if changing constants here, need to consider if a db migration is needed

@GrailsTypeChecked
@Log4j
class ValidationUtils {

    // 65535 bytes max for `text` column divided by 4 bytes per character ut8mb4
    static final int MAX_TEXT_COLUMN_SIZE = 15000

    static final int TEXT_BODY_LENGTH = 160
    static final int LOCK_CODE_LENGTH = 4
    static final int MAX_NUM_ACCESS_NOTIFICATION_TOKEN = 3
    static final int MAX_LOCK_TIMEOUT_MILLIS = 60000

    static final int MAX_NUM_TEXT_RECIPIENTS = 500
    static final long MIN_NOTE_SPACING_MILLIS = 100
    static final long MAX_NOTE_SPACING_MILLIS = 60000

    static final long MAX_MEDIA_SIZE_PER_MESSAGE_IN_BYTES = 5000000
    static final int MAX_NUM_MEDIA_PER_MESSAGE = 10

    static final Pattern HEX_PATTERN = Pattern.compile(/^#[a-fA-F\d]{3}|#[a-fA-F\d]{6}/)
    static final Pattern LOCK_CODE_PATTERN = Pattern.compile(/\d{${LOCK_CODE_LENGTH}}/)
    static final Pattern PUSHER_VALID_CHARS_PATTERN = Pattern.compile(/^[-_=@.,;A-Za-z0-9]+$/)

    // `.matcher()` is not null safe. see: https://stackoverflow.com/a/41286846
    static boolean isValidLockCode(String lockCode) {
        if (lockCode) {
            LOCK_CODE_PATTERN.matcher(ensureString(lockCode)).matches()
        }
        else { false }
    }

    static boolean isValidForPusher(String val) {
        if (val) {
            PUSHER_VALID_CHARS_PATTERN.matcher(ensureString(val)).matches()
        }
        else { false }
    }

    static boolean isValidHexCode(String hex) {
        if (hex) {
            HEX_PATTERN.matcher(ensureString(hex)).matches()
        }
        else { false }
    }

    // Helpers
    // -------

    // call `toString` because the passed-in string may be a GString
    protected static String ensureString(String val) { val?.toString() }
}
