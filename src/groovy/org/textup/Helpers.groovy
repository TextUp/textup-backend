package org.textup

import groovy.util.logging.Log4j
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

@Log4j
class Helpers {

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1"

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

    static String cleanNumber(String num) {
        PhoneNumber pNum = new PhoneNumber(number:num)
        pNum.discard() //temporary container
        pNum.number
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
}
