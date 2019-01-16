package org.textup.util

import grails.compiler.GrailsTypeChecked
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import org.textup.*

@GrailsTypeChecked
@Log4j
class StringUtils {

    static final List<String> ALPHABET = Collections.unmodifiableList(buildAlphabet())

    static String cleanPhoneNumber(String num) {
        if (num) {
            String cleaned = num.replaceAll(/\D+/, "")
            (cleaned.size() == 11 && cleaned[0] == "1") ? cleaned.substring(1) : cleaned
        }
        else { num }
    }

    static boolean equalsIgnoreCase(String str1, String str2) {
        str1?.toLowerCase() == str2?.toLowerCase()
    }

    static String toQuery(Object raw) {
        String query = "$raw"
        if (!query?.startsWith("%")) query = "%$query"
        if (!query?.endsWith("%")) query = "$query%"
        query
    }

    static String buildInitials(String contents) {
        if (!contents) {
            return contents
        }
        contents
            .replaceAll(/\d+/, "") // remove digits
            .trim()
            .split(/\s+/)
            .collect { String word -> word?.size() > 0 ? word[0].toUpperCase() : word }
            .join(".") + "." // put a period after each initial
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

    protected static List<String> buildAlphabet() {
        List<String> alphabet = []
        ("a".."z").each(alphabet.&add)
        (0..9).each { int val -> alphabet << val.toString() }
        alphabet
    }
}
