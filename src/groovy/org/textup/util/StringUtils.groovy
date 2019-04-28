package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import org.textup.*
import org.textup.structure.*
import org.textup.type.*
import org.textup.util.domain.*
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class StringUtils {

    static final List<String> ALPHABET = Collections.unmodifiableList(buildAlphabet())
    static final String PHONE_NUMBER_INITIALS_PREFIX = "unnamed with area code"

    static String cleanPhoneNumber(String num) {
        if (num) {
            String cleaned = num.replaceAll(/\D+/, "")
            (cleaned.size() == 11 && cleaned[0] == "1") ? cleaned.substring(1) : cleaned
        }
        else { num }
    }

    static String cleanUsername(String un) { un?.trim()?.toLowerCase() }

    static boolean equalsIgnoreCase(String str1, String str2) {
        str1?.toLowerCase() == str2?.toLowerCase()
    }

    static String toQuery(Object raw) {
        if (raw == null) {
            ""
        }
        else {
            String query = "$raw"
            if (!query?.startsWith("%")) query = "%$query"
            if (!query?.endsWith("%")) query = "$query%"
            query
        }
    }

    static String buildInitials(String contents) {
        if (!contents) {
            return contents
        }
        PhoneNumber.tryCreate(contents)
            .then { PhoneNumber pNum1 ->
                IOCUtils.resultFactory.success("${PHONE_NUMBER_INITIALS_PREFIX} ${pNum1.areaCode}")
            }
            .ifFail {
                String initials = contents
                    .replaceAll(/\d+/, "") // remove digits
                    .trim()
                    .split(/\s+/)
                    .collect { String word -> word?.size() > 0 ? word[0].toUpperCase() : word }
                    .join(".") + "." // put a period after each initial
                IOCUtils.resultFactory.success(initials)
            }
            .payload
    }

    static String pluralize(Integer val, String measureWord) {
        if (val == 1) {
            "${val} ${measureWord}"
        }
        else { "${val} ${measureWord}s" }
    }

    static String randomAlphanumericString(Integer length = 22) {
        int lastIndex = ALPHABET.size() - 1
        StringBuffer result = new StringBuffer()
        length.times {
            int randIndex = Math.round(Math.random() * lastIndex) as Integer
            result << ALPHABET[randIndex]
        }
        result.toString()
    }

    // Helpers
    // -------

    protected static List<String> buildAlphabet() {
        List<String> alphabet = []
        ("a".."z").each(alphabet.&add)
        (0..9).each { int val -> alphabet << val.toString() }
        alphabet
    }
}
