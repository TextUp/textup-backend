package org.textup

import grails.compiler.GrailsTypeChecked
import org.joda.time.*
import org.joda.time.format.*
import org.textup.type.*

@GrailsTypeChecked
class UsageUtils {

    static final String QUERY_MONTH_FORMAT = "yyyy-MM"
    static final String DISPLAYED_MONTH_FORMAT = "MMM yyyy"
    static final String CURRENT_TIME_FORMAT = "MMM dd, y h:mm a"

    static final DateTimeFormatter queryMonthFormat = DateTimeFormat.forPattern(QUERY_MONTH_FORMAT)
    static final DateTimeFormatter displayedMonthFormat = DateTimeFormat.forPattern(DISPLAYED_MONTH_FORMAT)
    static final DateTimeFormatter currentTimeFormat = DateTimeFormat.forPattern(CURRENT_TIME_FORMAT)

    // Query helpers
    // -------------

    static String getTableName(PhoneOwnershipType type) {
        switch (type) {
            case PhoneOwnershipType.INDIVIDUAL: return "staff"
            case PhoneOwnershipType.GROUP: return "team"
            default: return ""
        }
    }

    // Building data
    // -------------

    static <T extends UsageService.HasActivity> List<T> associateActivity(List<T> activityOwners,
        List<UsageService.ActivityRecord> activityList) {

        if (!activityOwners || !activityList) {
            return activityOwners
        }
        Map<BigInteger, T> ownerMap = [:]
        activityOwners.each { T ha1 -> ownerMap[ha1.id] = ha1 }
        activityList.each { UsageService.ActivityRecord a1 ->
            ownerMap.get(a1.ownerId)?.setActivity(a1)
        }
        new ArrayList<T>(ownerMap.values())
    }

    static List<UsageService.ActivityRecord> ensureMonths(List<UsageService.ActivityRecord> aList) {
        // do not short circuit if aList is an empty or null because we still want to ensure
        // the proper number of months even if all months are empty
        List<UsageService.ActivityRecord> normalized = []
        int currIndex = 0,
            numActivity = aList?.size()
        getAvailableMonthStrings().each { String monthString ->
            UsageService.ActivityRecord activity
            if (currIndex < numActivity && aList[currIndex].monthString == monthString) {
                activity = aList[currIndex]
                currIndex++
            }
            else {
                activity = new UsageService.ActivityRecord()
                activity.setMonthStringDirectly(monthString)
            }
            normalized << activity
        }
        normalized
    }

    static <T> BigDecimal sumProperty(Collection<? extends T> objList,
        Closure<BigDecimal> doGetProperty) {
        BigDecimal runningTotal = 0
        objList?.each { T obj ->
            BigDecimal propVal = doGetProperty(obj)
            if (propVal) {
                runningTotal += propVal
            }
        }
        runningTotal
    }

    // Display helpers
    // ---------------

    static List<String> getAvailableMonthStrings() {
        RecordItem rItem = RecordItem.first("whenCreated")
        DateTime now = DateTime.now(),
            dt = rItem?.whenCreated ?: now
        List<String> monthStrings = []
        while (dt.isBefore(now)) {
            monthStrings << dateTimeToMonthString(dt)
            dt = dt.plusMonths(1)
        }
        monthStrings << dateTimeToMonthString(now)
        monthStrings
    }

    static String queryMonthToMonthString(String queryMonth) {
        if (!queryMonth) {
            return null
        }
        try {
            DateTime dt = queryMonthFormat.parseDateTime(queryMonth)
            dateTimeToMonthString(dt)
        }
        catch (IllegalArgumentException e) {
            return null
        }
    }

    static String dateTimeToTimestamp(DateTime dt) {
        if (!dt) {
            return ""
        }
        currentTimeFormat.print(dt)
    }

    static String dateTimeToMonthString(DateTime dt) {
        if (!dt) {
            return ""
        }
        displayedMonthFormat.print(dt)
    }

    static DateTime monthStringToDateTime(String monthString) {
        if (!monthString) {
            return null
        }
        try {
            displayedMonthFormat.parseDateTime(monthString)
        }
        catch (IllegalArgumentException e) {
            return null
        }
    }
}
