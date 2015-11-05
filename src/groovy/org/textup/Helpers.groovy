package org.textup

import org.joda.time.DateTime
import org.joda.time.DateTimeZone

class Helpers {

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
}