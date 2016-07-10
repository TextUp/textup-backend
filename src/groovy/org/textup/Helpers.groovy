package org.textup

import grails.compiler.GrailsCompileStatic
import groovy.json.JsonBuilder
import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Days
import org.joda.time.LocalTime
import org.textup.validator.LocalInterval
import org.textup.validator.PhoneNumber

@GrailsCompileStatic
@Log4j
class Helpers {

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1"

    // Enum
    // ----

    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    static <T extends Enum<T>> T convertEnum(Class<T> enumClass, def string) {
        String enumString = string?.toString()?.toUpperCase()
        enumClass?.values().find { it.toString() == enumString } ?: null
    }
    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    static <T extends Enum<T>> List<T> toEnumList(Class<T> enumClass, def enumsOrStrings) {
        if (enumsOrStrings instanceof Collection) {
            enumsOrStrings?.collect { enumOrString ->
                (enumClass?.isInstance(enumOrString)) ? enumOrString :
                    convertEnum(enumClass, enumOrString)
            } ?: []
        }
        else { [] }
    }

    // Formatting
    // ----------

    static boolean exactly(int num, List<String> keysToLookFor, Map params) {
        int numFound = 0
        keysToLookFor.each {
            if (params[it]) { numFound++ }
        }
        numFound == num
    }
	static boolean isLong(def val) {
        "${val}".isLong()
    }
    static Boolean toBoolean(def val) {
        String str = "${val}"
        str == "true" ? true : str == "false" ? false : null
    }
    static Long toLong(def val) {
        BigDecimal res = toBigDecimal(val)
        (res != null) ? res as Long : null
    }
    static Integer toInteger(def val) {
        BigDecimal res = toBigDecimal(val)
        (res != null) ? res as Integer : null
    }
    static BigDecimal toBigDecimal(def val) {
        String strVal = "${val}"
        strVal.isBigDecimal() ? strVal.toBigDecimal() : null
    }
    static List<Long> allToLong(List l) {
        l.collect { Helpers.toLong(it) }
    }
    static List toList(def val) {
    	(val instanceof List)  ? (val as List) : []
    }
    static List<Long> toIdsList(def data) {
        List<Long> ids = []
        for (rawId in Helpers.toList(data)) {
            Long id = Helpers.toLong(rawId)
            if (id) { ids << id }
            else { return [] }
        }
        ids
    }
    static DateTime toUTCDateTime(def val) {
        try {
            new DateTime(val, DateTimeZone.UTC)
        }
        catch (e) {
            log.debug("Helpers.toUTCDateTime: $e")
            null
        }
    }
    static String toString(def val) {
        "$val".toString()
    }
    static String toLowerCaseString(def val) {
        "$val".toString().toLowerCase()
    }
    static String toQuery(def raw) {
        String query = "$raw"
        if (!query?.startsWith("%")) query = "%$query"
        if (!query?.endsWith("%")) query = "$query%"
        query
    }

    // Date, time, timezones
    // ---------------------

    static DateTime toDateTimeWithZone(def time, def zone = null) {
        new DateTime(toString(time))
            .withZoneRetainFields(getZoneFromId(zone as String))
    }
    static String printLocalInterval(LocalInterval localInt) {
        if (localInt) {
            String start1 = localInt.start.hourOfDay.toString().padLeft(2, "0"),
                start2 = localInt.start.minuteOfHour.toString().padLeft(2, "0"),
                end1 = localInt.end.hourOfDay.toString().padLeft(2, "0"),
                end2 = localInt.end.minuteOfHour.toString().padLeft(2, "0"),
                start = "${start1}${start2}",
                end = "${end1}${end2}"
            "${start}:${end}"
        }
        else { "" }
    }
    static DateTimeZone getZoneFromId(String id) {
        try {
            return id ? DateTimeZone.forID(id) : DateTimeZone.UTC
        }
        catch (e) {
            log.debug("Helpers.getZoneFromId: ${e.message}")
            return DateTimeZone.UTC
        }
    }
    static DateTime toDateTimeTodayWithZone(LocalTime lt, DateTimeZone zone) {
        if (zone) {
            lt.toDateTimeToday(DateTimeZone.UTC).withZone(zone)
        }
        else { lt.toDateTimeToday(DateTimeZone.UTC) }
    }
    static DateTime toUTCDateTimeTodayFromZone(LocalTime lt, DateTimeZone zone) {
        if (zone) {
            lt.toDateTimeToday(zone).withZone(DateTimeZone.UTC)
        }
        else { lt.toDateTimeToday(DateTimeZone.UTC) }
    }
    static int getDaysBetween(DateTime dt1, DateTime dt2) {
        Days.daysBetween(dt1.toLocalDate(), dt2.toLocalDate()).getDays()
    }
    //0 corresponds to sunday, 6 to saturday
    static int getDayOfWeekIndex(int num) {
        Math.abs(num % 7)
    }

    // Json
    // ----

    static String toJsonString(Object data) {
        data ? new JsonBuilder(data).toString() : null
    }
    static Object toJson(Object data) throws JsonException {
        data ? new JsonSlurper().parseText(toJsonString(data)) : null
    }
    static Object toJson(String str) throws JsonException {
        str ? new JsonSlurper().parseText(str) : null
    }

    // TwimlBuilder
    // ------------

    static String formatNumberForSay(String number) {
        number?.replaceAll(/\D*/, "")?.replaceAll(/.(?!$)/, /$0 /)
    }
    static String formatNumberForSay(PhoneNumber num) {
        formatNumberForSay(num?.number)
    }
    static String formatNumberForRead(String number) {
        PhoneNumber pNum = new PhoneNumber(number:number)
        pNum.validate() ? pNum.prettyPhoneNumber : number
    }

    // Security
    // --------

    static String getBase64HmacSHA1(String data, String key) {
        String result = ""
        try {
            SecretKeySpec signingKey = new SecretKeySpec(key.bytes, HMAC_SHA1_ALGORITHM)
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM)
            mac.init(signingKey)
            byte[] rawHmac = mac.doFinal(data.bytes)
            result = rawHmac.encodeBase64().toString()
        }
        catch (e) {
            log.error("Helpers.getBase64HmacSHA1: data: $data, error: ${e.message}")
        }
        result
    }

    @GrailsCompileStatic(TypeCheckingMode.SKIP)
    static String randomAlphanumericString(Integer l) {
        int length = (l != null) ? l : 22
        Collection<String> alphabet = ("a".."z") + (0..9)
        int lastIndex = alphabet.size() - 1
        StringBuffer result = new StringBuffer()
        length.times {
            int randIndex = Math.round(Math.random() * lastIndex)
            result << alphabet[randIndex]
        }
        result.toString()
    }
}
