package org.textup

import grails.async.Promise
import grails.async.PromiseList
import grails.async.Promises
import grails.compiler.GrailsTypeChecked
import grails.util.Holders
import groovy.json.JsonBuilder
import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import org.apache.commons.lang3.ClassUtils
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.codehaus.groovy.grails.web.util.WebUtils
import org.hibernate.FlushMode
import org.hibernate.Session
import org.joda.time.*
import org.quartz.Scheduler
import org.springframework.context.MessageSource
import org.textup.validator.*

@GrailsTypeChecked
@Log4j
class Helpers {

    // Enum
    // ----

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static <T extends Enum<T>> T convertEnum(Class<T> enumClass, def string) {
        String enumString = string?.toString()?.toUpperCase()
        enumClass?.values().find { it.toString() == enumString } ?: null
    }
    @GrailsTypeChecked(TypeCheckingMode.SKIP)
    static <T extends Enum<T>> List<T> toEnumList(Class<T> enumClass, def enumsOrStrings,
        List<T> fallbackList = []) {
        if (enumsOrStrings instanceof Collection) {
            enumsOrStrings?.collect { enumOrString ->
                (enumClass?.isInstance(enumOrString)) ? enumOrString :
                    convertEnum(enumClass, enumOrString)
            } ?: fallbackList
        }
        else { fallbackList }
    }

    // Dependency injection
    // --------------------

    static ResultFactory getResultFactory() {
        Holders
            .applicationContext
            .getBean(ResultFactory) as ResultFactory
    }
    static MessageSource getMessageSource() {
        Holders
            .applicationContext
            .getBean(MessageSource) as MessageSource
    }
    static Scheduler getQuartzScheduler() {
        Holders
            .applicationContext
            .getBean(Scheduler) as Scheduler
    }

    // Request
    // -------

    static Result<Void> trySetOnRequest(String key, Object obj) {
        ResultFactory resultFactory = getResultFactory()
        try {
            WebUtils.retrieveGrailsWebRequest().currentRequest.setAttribute(key, obj)
            resultFactory.success()
        }
        catch (IllegalStateException e) {
            resultFactory.failWithThrowable(e)
        }
    }

    static <T> Result<T> tryGetFromRequest(String key) {
        ResultFactory resultFactory = getResultFactory()
        try {
            HttpServletRequest req = WebUtils.retrieveGrailsWebRequest().currentRequest
            Object obj = req.getAttribute(key) ?: req.getParameter(key)
            resultFactory.success(to(T, obj))
        }
        catch (IllegalStateException e) {
            resultFactory.failWithThrowable(e)
        }
    }

    // Setters
    // -------

    static <T> T withDefault(T val, T defaultVal) {
        val ? val : defaultVal
    }

    // Validator
    // ---------

    static <T> T doWithoutFlush(Closure<T> doThis) {
        T result
        // doesn't matter which domain class we call this on
        Organization.withSession { Session session ->
            session.flushMode = FlushMode.MANUAL
            try {
                result = doThis()
            }
            finally { session.flushMode = FlushMode.AUTO }
        }
        result
    }

    // Async
    // -----

    static <K, T> List<T> doAsyncInBatches(Collection<K> data, Closure<T> doAction,
        int batchSize = Constants.CONCURRENT_SEND_BATCH_SIZE) {

        // step 1: process in batches
        PromiseList<T> pList1 = new PromiseList<>()
        new ArrayList<K>(data)
            .collate(batchSize)
            .collect { List<K> batch ->
                pList1 << Promises.task { batch.collect(doAction) }
            }
        // step 2: flatten batched results array
        List<T> results = []
        List<List<T>> batchedResults = (pList1.get(1, TimeUnit.MINUTES) as List<List<T>>)
        batchedResults.each(results.&addAll)
        results
    }

    // Lists
    // -----

    static boolean exactly(int num, List<String> keysToLookFor, Map params) {
        int numFound = 0
        keysToLookFor.each {
            if (params[it]) { numFound++ }
        }
        numFound == num
    }
    static List takeRight(List data, int numToTake) {
        if (!data) return []
        int totalNum = data.size()
        if (numToTake <= 0 || numToTake > totalNum) {
            []
        }
        else if (numToTake == totalNum) {
            data
        }
        else { data[(totalNum - numToTake)..(totalNum - 1)] }
    }
    static boolean inListIgnoreCase(String toFind, Collection<String> options) {
        String lowerCaseToFind = Helpers.toLowerCaseString(toFind)
        (options
            ?.collect(Helpers.&toLowerCaseString) as Collection<String>)
            ?.any { String allowed -> allowed == lowerCaseToFind }
    }

    // Maps
    // ----

    static <K> Map.Entry<K,? extends Comparable> findHighestValue(Map<K,? extends Comparable> map) {
        Map.Entry<K,? extends Comparable> highestEntry
        map?.entrySet().each { Map.Entry<K,? extends Comparable> entry ->
            if (!highestEntry || entry.value > highestEntry.value) {
                highestEntry = entry
            }
        }
        highestEntry
    }

    // Types
    // -----

    // For some reason, cannot combine these two method signatures into one using a default
    // value for the fallbackValue. Doing so causes the static compilation to fail to convert
    // the Object val to the generic type T, instead complaining that the return value is of
    // type T and does not match te declared type
    static <T> T to(Class<T> clazz, Object val) {
        Helpers.to(clazz, val, null)
    }
    static <T> T to(Class<T> clazz, Object val, T fallbackVal) {
        if (val == null) {
            return fallbackVal
        }
        Class<T> wrappedClazz = ClassUtils.primitiveToWrapper(clazz)
        try {
            String str = "${val}".toLowerCase()
            switch (wrappedClazz) {
                // note for String conversion we use default toString method on `val` not `str`
                case String: return val?.toString() ?: fallbackVal
                case Boolean: return (str == "true" || str == "false") ? str.toBoolean() : fallbackVal
                case Number: return str.isBigDecimal() ? str.toBigDecimal().asType(wrappedClazz) : fallbackVal
                default: return wrappedClazz.isAssignableFrom(val.getClass()) ? val.asType(wrappedClazz) : fallbackVal
            }
        }
        catch (ClassCastException e) {
            log.debug("Helpers.to: wrappedClazz: $wrappedClazz, val: $val: ${e.message}")
            fallbackVal
        }
    }
    static <T> List<T> allTo(Class<T> clazz, Collection<? extends Object> val) {
        allTo(clazz, val, null)
    }
    static <T> List<T> allTo(Class<T> clazz, Collection<? extends Object> val,
        T replaceFailWith) {
        List<T> results = []
        if (!val) {
            return results
        }
        for (obj in val) {
            results << to(clazz, obj, replaceFailWith)
        }
        results
    }

    // Formatting
    // ----------

    static String toLowerCaseString(def val) {
        "$val".toString().toLowerCase()
    }
    static String toQuery(def raw) {
        String query = "$raw"
        if (!query?.startsWith("%")) query = "%$query"
        if (!query?.endsWith("%")) query = "$query%"
        query
    }
    static String joinWithDifferentLast(List list, String delim, String lastDelim) {
        if (!list) {
            return ""
        }
        (list.size() > 2) ? (list[0..-2].join(delim) + lastDelim + list[-1]) : list.join(lastDelim)
    }
    static String appendGuaranteeLength(String contents, String toAppend, int targetLen) {
        Integer contentsLen = contents?.size(),
            appendLen = toAppend?.size()
        String myContents = contents
        if (!contentsLen || targetLen < 1) {
            return myContents
        }
        if (contentsLen > targetLen) {
            // subtract one because we are dealing with indices not lengths
            int howMuchToKeep = targetLen - 1
            myContents = contents[0..howMuchToKeep]
            contentsLen = myContents.size()
        }
        if (!appendLen || appendLen > targetLen) {
            return myContents
        }
        if (contentsLen + appendLen > targetLen) {
            // contentsLen - (contentsLen + appendLen - targetLen)
            // = contentsLen - contentsLen - appendLen + targetLen
            // = targetLen - appendLen
            // then subtract one because we are dealing with indices not lengths
            int howMuchToKeep = targetLen - appendLen - 1
            // if we want to keep a negative amount, then we are in the edge case where
            // we want to keep none of the contents and effectively return only toAppend
            myContents = (howMuchToKeep < 0) ? "" : contents[0..howMuchToKeep]
        }
        myContents + toAppend
    }

    // Date, time, timezones
    // ---------------------

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
    static DateTime toUTCDateTime(def val) {
        try {
            new DateTime(val, DateTimeZone.UTC)
        }
        catch (e) {
            log.debug("Helpers.toUTCDateTime: $e")
            null
        }
    }
    static DateTime toDateTimeWithZone(def time, def zone = null) {
        if (!time) return null
        new DateTime(to(String, time))
            // must NOT use withZoneRetainFields because doing so results in this scenario:
            // The default system time might not be UTC time. Therefore, when we pass a UTC
            // string to the DateTime constructor, it converts the UTC fields to the fields
            // in the local time zone (that is the system default). Then, if we call
            // withZoneRetainFields on this DateTime object, we convert to the UTC time zone
            // using the LOCAL values, thereby losing the original time
            .withZone(getZoneFromId(zone as String))
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
    static String formatForSayIfPhoneNumber(String possiblePhoneNumber) {
        PhoneNumber pNum = new PhoneNumber(number:possiblePhoneNumber)
        pNum.validate() ? formatNumberForSay(pNum.number) : possiblePhoneNumber
    }

    // Security
    // --------

    @GrailsTypeChecked(TypeCheckingMode.SKIP)
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

    // From http://www.baeldung.com/httpclient-4-basic-authentication
    // Twilio has inconsistent behavior with a basic authentication header string.
    // Therefore, use Credential Provider
    static CloseableHttpClient buildBasicAuthHttpClient(String un, String pwd) {
        UsernamePasswordCredentials authValues = new UsernamePasswordCredentials(un, pwd)
        // See https://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/http/examples/client/ClientAuthentication.java
        CredentialsProvider credentials = new BasicCredentialsProvider()
        credentials.setCredentials(AuthScope.ANY, authValues)
        CloseableHttpClient client = HttpClients.custom()
            .setDefaultCredentialsProvider(credentials)
            .build()
    }
}
