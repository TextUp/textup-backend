package org.textup

import groovy.util.logging.Log4j
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Days
import org.joda.time.LocalTime

@Log4j
class Helpers {

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1"

    ////////////////
    // Formatting //
    ////////////////

	static boolean isLong(def val) {
        "${val}".isLong()
    }

    static Boolean toBoolean(def val) {
        String str = "${val}"
        str == "true" ? true : str == "false" ? false : null
    }

    static Long toLong(def val) {
        String strVal = "${val}"
        strVal.isLong() ? strVal.toLong() : null
    }

    static Integer toInteger(def val) {
        String strVal = "${val}"
        strVal.isInteger() ? strVal.toInteger() : null
    }

    static List<Long> allToLong(List l) {
        l.collect { Helpers.toLong(it) }
    }

    static List toList(def val) {
    	(val instanceof List)  ? val : []
    }

    static DateTime toUTCDateTime(def val) {
        (val instanceof Date) ? new DateTime(val, DateTimeZone.UTC) : null
    }

    static String toString(def val) {
        "$val".toString()
    }

    ///////////////////////////
    // date, time, timezones //
    ///////////////////////////

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
            return DateTimeZone.forId(id)
        }
        catch (e) { return null }
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

    /////////////////////////////
    // Sending calls and texts //
    /////////////////////////////

    //0 corresponds to sunday, 6 to saturday
    static int getDayOfWeekIndex(int num) { Math.abs(num % 7) }

    static ParsedResult<PhoneNumber,String> parseIntoPhoneNumbers(List<String> nums) {
        List<PhoneNumber> pNums = []
        List<String> invalidStrings = []
        nums.each { String num ->
            PhoneNumber pNum = new PhoneNumber(number:num)
            if (pNum.validate()) { pNums << pNum }
            else { invalidStrings << pNum.number }
            pNum.discard() //temporary container
        }
        new ParsedResult<PhoneNumber,String>(valid:pNums, invalid:invalidStrings)
    }

    static ParsedResult parseFromList(List toFind, List all) {
        ParsedResult parsed = new ParsedResult()
        HashSet toFindHash = new HashSet(toFind)
        all.each {
            if (toFindHash.contains(it)) { parsed.valid << it }
            else { parsed.invalid << it }
        }
        parsed
    }

    //////////////////////////////
    // Incoming calls and texts //
    //////////////////////////////

    static String cleanIncomingText(String contents) {
        contents?.trim()
    }

    static String cleanUsername(String username) {
        username?.trim()?.toLowerCase()
    }

    static String formatNumberForSay(String number) {
        number?.replaceAll(/\D*/, "")?.replaceAll(/.(?!$)/, /$0 /)
    }
    static String formatNumberForSay(TransientPhoneNumber num) {
        formatNumberForSay(num?.number)
    }
    static String formatTextNotification(String attribution, String contents) {
        String notification = "${attribution}: ${contents}"
        if (notification.size() >= Constants.TEXT_LENGTH) {
            int trimTo = Constants.TEXT_LENGTH - 4
            notification = "${notification[0..trimTo]}..."
        }
        notification
    }

    static String translateCallStatus(String status) {
        String translated = null
        switch (status) {
            case "failed": translated = Constants.RECEIPT_FAILED; break;
            case "completed": translated = Constants.RECEIPT_SUCCESS; break;
            case "busy": translated = Constants.RECEIPT_SUCCESS; break;
            case "no-answer": translated = Constants.RECEIPT_SUCCESS; break;
            case "canceled": translated = Constants.RECEIPT_SUCCESS; break;
            case "in-progress": translated = Constants.RECEIPT_PENDING; break;
            case "ringing": translated = Constants.RECEIPT_PENDING; break;
            case "queued": translated = Constants.RECEIPT_PENDING; break;
        }
        translated
    }

    static String translateTextStatus(String status) {
        String translated = null
        switch (status) {
            case "failed": translated = Constants.RECEIPT_FAILED; break;
            case "undelivered": translated = Constants.RECEIPT_FAILED; break;
            case "sent": translated = Constants.RECEIPT_SUCCESS; break;
            case "received": translated = Constants.RECEIPT_SUCCESS; break;
            case "delivered": translated = Constants.RECEIPT_SUCCESS; break;
            case "accepted": translated = Constants.RECEIPT_PENDING; break;
            case "queued": translated = Constants.RECEIPT_PENDING; break;
            case "sending": translated = Constants.RECEIPT_PENDING; break;
            case "receiving": translated = Constants.RECEIPT_PENDING; break;
        }
        translated
    }

    //////////////
    // Security //
    //////////////

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

    static String randomAlphanumericString(int length) {
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
